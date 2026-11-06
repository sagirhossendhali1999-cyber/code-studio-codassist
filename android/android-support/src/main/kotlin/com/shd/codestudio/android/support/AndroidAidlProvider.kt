package com.shd.codestudio.android.support

import com.shd.codestudio.android.support.aidl.AidlCompiler
import com.shd.codestudio.android.support.aidl.AidlDecl
import com.shd.codestudio.android.support.aidl.AidlDiagnostic
import com.shd.codestudio.android.support.aidl.AidlEnum
import com.shd.codestudio.android.support.aidl.AidlFile
import com.shd.codestudio.android.support.aidl.AidlInterface
import com.shd.codestudio.android.support.aidl.AidlMethod
import com.shd.codestudio.android.support.aidl.AidlSeverity
import com.shd.codestudio.android.support.aidl.AidlStructuredParcelable
import com.shd.codestudio.android.support.aidl.AidlTypeResolver
import com.shd.codestudio.android.support.aidl.AidlTypeTable
import com.shd.codestudio.lang.synthetic.SyntheticClass
import com.shd.codestudio.lang.synthetic.SyntheticClassContext
import com.shd.codestudio.lang.synthetic.SyntheticClassProvider
import com.shd.codestudio.lang.synthetic.SyntheticField
import com.shd.codestudio.lang.synthetic.SyntheticMethod
import com.shd.codestudio.lang.synthetic.SyntheticModifier
import com.shd.codestudio.lang.synthetic.SyntheticParam
import com.shd.codestudio.lang.synthetic.SyntheticTypeKind
import com.shd.codestudio.model.ContentRole
import com.shd.codestudio.model.DependencyScope
import com.shd.codestudio.model.Module
import com.shd.codestudio.model.ModuleDependency
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Contributes the light classes an `.aidl` file will generate, so `IFoo.Stub`, `asInterface`, and the
 * interface's own methods resolve for completion, analysis and go-to-definition **before** a build has run.
 *
 * Writing an AIDL service is a two-file operation (declare the interface, then write a `Service` that
 * extends its `Stub`), and without this the second file is a wall of unresolved symbols until the first
 * successful build. The shapes here mirror exactly what [com.shd.codestudio.android.support.aidl.AidlJavaGenerator]
 * emits, so nothing appears or disappears when the build catches up. Only the public surface is described:
 * `Stub.Proxy` is private in the generated code and has no business being completable.
 *
 * Returns nothing for a module with no `.aidl`, so it is inert until the feature is used.
 */
class AndroidAidlProvider : SyntheticClassProvider {

    override fun classesFor(context: SyntheticClassContext): List<SyntheticClass> {
        val module = context.module
        if (module.facets.get(AndroidFacet.KEY) == null) return emptyList()
        val ownRoots = aidlRoots(module)
        if (ownRoots.isEmpty()) return emptyList()

        val ignored = ArrayList<AidlDiagnostic>()
        val own = ownRoots.flatMap { AidlCompiler.aidlFilesUnder(it) }
            .mapNotNull { AidlCompiler.parse(it, ignored, AidlSeverity.WARNING) }
        if (own.isEmpty()) return emptyList()

        // Dependencies' declarations resolve the module's own type references; they are not contributed here,
        // since each module's provider is asked about itself.
        val imported = dependencyAidlRoots(module, context)
            .flatMap { AidlCompiler.aidlFilesUnder(it) }
            .mapNotNull { AidlCompiler.parse(it, ignored, AidlSeverity.WARNING) }
        val table = AidlTypeTable.of(own + imported)

        return own.flatMap { file -> file.declarations.mapNotNull { decl -> syntheticFor(file, decl, table) } }
    }

    /** The module's own (non-test) `aidl/` roots. */
    private fun aidlRoots(module: Module): List<Path> =
        module.sourceSets.filter { it.scope != DependencyScope.TEST_IMPLEMENTATION }
            .flatMap { it.contentRoots }
            .filter { ContentRole.AIDL in it.roles }
            .map { Paths.get(it.dir.path) }

    /** The `aidl/` roots of the modules this one depends on directly: its import path. */
    private fun dependencyAidlRoots(module: Module, context: SyntheticClassContext): List<Path> {
        val byId = context.workspace.projects.flatMap { it.modules }.associateBy { it.id }
        return module.dependencies.filterIsInstance<ModuleDependency>()
            .mapNotNull { byId[it.target] }
            .flatMap { aidlRoots(it) }
    }

    private fun syntheticFor(file: AidlFile, decl: AidlDecl, table: AidlTypeTable): SyntheticClass? {
        if ('.' in decl.name) return null
        val resolver = AidlTypeResolver(table, file.packageName, file.imports)
        val fq = if (file.packageName.isEmpty()) decl.name else "${file.packageName}.${decl.name}"
        return when (decl) {
            is AidlInterface -> if (decl.forwardDeclaration) null else interfaceClass(decl, fq, resolver)
            is AidlStructuredParcelable -> parcelableClass(decl, fq, resolver)
            is AidlEnum -> enumClass(decl, fq)
            // A `parcelable Foo;` names a hand-written class, and a union generates nothing yet.
            else -> null
        }
    }

    private fun interfaceClass(decl: AidlInterface, fq: String, resolver: AidlTypeResolver): SyntheticClass {
        val methods = decl.methods.map { signature(it, resolver) }
        val constants = decl.constants.map {
            SyntheticField(it.name, type = resolver.resolve(it.type).java, constant = it.value, doc = it.doc)
        }
        return SyntheticClass(
            fqName = fq,
            kind = SyntheticTypeKind.INTERFACE,
            modifiers = setOf(SyntheticModifier.PUBLIC),
            interfaces = listOf(IINTERFACE),
            fields = constants,
            methods = methods,
            nestedClasses = listOf(stubClass(fq, methods), defaultClass(fq, methods)),
            doc = decl.doc ?: "Generated from AIDL.",
        )
    }

    /** `IFoo.Stub`: the abstract Binder a service extends, plus the `asInterface`/`DESCRIPTOR` entry points. */
    private fun stubClass(fq: String, methods: List<SyntheticMethod>): SyntheticClass = SyntheticClass(
        fqName = "$fq.Stub",
        modifiers = setOf(SyntheticModifier.PUBLIC, SyntheticModifier.STATIC, SyntheticModifier.ABSTRACT),
        superClass = "android.os.Binder",
        interfaces = listOf(fq),
        fields = listOf(SyntheticField("DESCRIPTOR", type = "java.lang.String", constant = "\"$fq\"")),
        // The interface's own methods stay abstract on the Stub: a service that forgets one should be told
        // so, the same way it will be once the real stub is generated.
        methods = methods.map { it.copy(modifiers = it.modifiers + SyntheticModifier.ABSTRACT) } + listOf(
            SyntheticMethod("Stub", isConstructor = true),
            SyntheticMethod(
                "asInterface", returnType = fq,
                parameters = listOf(SyntheticParam("obj", "android.os.IBinder")),
                modifiers = setOf(SyntheticModifier.PUBLIC, SyntheticModifier.STATIC),
                doc = "Cast an IBinder into a $fq, generating a proxy if needed.",
            ),
            SyntheticMethod("asBinder", returnType = "android.os.IBinder"),
            SyntheticMethod(
                "setDefaultImpl", returnType = "boolean",
                parameters = listOf(SyntheticParam("impl", fq)),
                modifiers = setOf(SyntheticModifier.PUBLIC, SyntheticModifier.STATIC),
            ),
            SyntheticMethod(
                "getDefaultImpl", returnType = fq,
                modifiers = setOf(SyntheticModifier.PUBLIC, SyntheticModifier.STATIC),
            ),
        ),
        doc = "Local-side IPC implementation stub class for $fq.",
    )

    /** `IFoo.Default`: the no-op implementation `setDefaultImpl` takes. */
    private fun defaultClass(fq: String, methods: List<SyntheticMethod>): SyntheticClass = SyntheticClass(
        fqName = "$fq.Default",
        modifiers = setOf(SyntheticModifier.PUBLIC, SyntheticModifier.STATIC),
        interfaces = listOf(fq),
        methods = methods + SyntheticMethod("asBinder", returnType = "android.os.IBinder"),
        doc = "Default implementation for $fq: every call is a no-op.",
    )

    private fun parcelableClass(decl: AidlStructuredParcelable, fq: String, resolver: AidlTypeResolver): SyntheticClass {
        val fields = decl.fields.map {
            SyntheticField(it.name, type = resolver.resolve(it.type).java, modifiers = INSTANCE_FIELD, doc = it.doc)
        }
        val constants = decl.constants.map {
            SyntheticField(it.name, type = resolver.resolve(it.type).java, constant = it.value, doc = it.doc)
        }
        return SyntheticClass(
            fqName = fq,
            modifiers = setOf(SyntheticModifier.PUBLIC),
            interfaces = listOf("android.os.Parcelable"),
            fields = constants + fields + SyntheticField("CREATOR", type = "android.os.Parcelable.Creator<$fq>"),
            methods = listOf(
                SyntheticMethod(decl.name, isConstructor = true),
                SyntheticMethod(
                    "writeToParcel",
                    parameters = listOf(SyntheticParam("parcel", PARCEL), SyntheticParam("flags", "int")),
                ),
                SyntheticMethod("readFromParcel", parameters = listOf(SyntheticParam("parcel", PARCEL))),
                SyntheticMethod("describeContents", returnType = "int"),
            ),
            doc = decl.doc ?: "Generated from AIDL.",
        )
    }

    /**
     * An AIDL enum generates an annotation type of backing-typed constants, not a Java enum, which is why a
     * value of the type is just an `int`/`byte` in every signature the generator emits.
     */
    private fun enumClass(decl: AidlEnum, fq: String): SyntheticClass = SyntheticClass(
        fqName = fq,
        kind = SyntheticTypeKind.ANNOTATION,
        modifiers = setOf(SyntheticModifier.PUBLIC),
        fields = decl.enumerators.map { SyntheticField(it.name, type = decl.backingType, doc = it.doc) },
        doc = decl.doc ?: "Generated from AIDL.",
    )

    private fun signature(method: AidlMethod, resolver: AidlTypeResolver): SyntheticMethod = SyntheticMethod(
        method.name,
        returnType = resolver.resolve(method.returnType, asReturn = true).java,
        parameters = method.params.map { SyntheticParam(it.name, resolver.resolve(it.type).java) },
        doc = method.doc,
    )

    private companion object {
        const val IINTERFACE = "android.os.IInterface"
        const val PARCEL = "android.os.Parcel"
        val INSTANCE_FIELD = setOf(SyntheticModifier.PUBLIC)
    }
}
