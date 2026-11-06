rootProject.name = "code-studio"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        maven("https://redirector.kotlinlang.org/maven/kotlin-ide-plugin-dependencies") {
            content { includeGroup("org.jetbrains.kotlin") }
        }
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies") {
            content {
                includeGroupByRegex("org\\.jetbrains\\.intellij.*")
                includeGroup("org.jetbrains.kotlin")
                includeModule("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm")
                includeModuleByRegex("org\\.jetbrains", "syntax-api.*")
            }
        }
        maven("https://cache-redirector.jetbrains.com/intellij-repository/releases") {
            content {
                includeGroupByRegex("com\\.jetbrains\\.intellij.*")
                includeModule("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm")
            }
        }
        maven("https://artifact.bytedance.com/repository/pangle") {
            content { includeGroupByRegex("com\\.pangle\\..*") }
        }
        maven("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea") {
            content { includeGroupByRegex("com\\.mbridge\\..*") }
        }
        maven("https://storage.googleapis.com/download.flutter.io") {
            content { includeGroup("io.flutter") }
        }
    }
}

gradle.rootProject {
    extra["nativeModelRepositories"] = dependencyResolutionManagement.repositories
        .filterIsInstance<MavenArtifactRepository>()
        .map { "${it.name}|${it.url}" }
}

include(
    ":platform-core",
    ":platform-json",
    ":vfs-api",
    ":project-model-api",
    ":project-model-impl",
    ":applog-runtime",
    ":language-api",
    ":index-api",
    ":index-impl",
    ":analysis-api",
    ":analysis-impl",
    ":lang-jdt",
    ":lang-java",
    ":lang-xml",
    ":lsp-api",
    ":lsp-client",
    ":lang-kotlin-index",
    ":kotlin-compiler-deps",
    ":intellij-psi-host",
    ":lang-kotlin",
    ":lang-ksp",
    ":decompiler",
    ":interp-core",
    ":interp-api",
    ":interp-impl",
    ":jvm-interp",
    ":jvm-vm",
    ":jvm-vm-fixtures",
    ":deps-api",
    ":deps-impl",
    ":vcs-api",
    ":vcs-impl",
    ":analytics-api",
    ":analytics-impl",
    ":store-api",
    ":store-impl",
    ":block-api",
    ":block-impl",
    ":plugin-api",
    ":plugin-ui-api",
    ":plugin-bom",
    ":plugin-impl",
    ":spi-compat",
    ":agent-api",
    ":agent-impl",
    ":agent-mcp",
    ":model-api",
    ":project-templates",
    ":kotlin-classfile",
    ":awt-toolkit",
    ":bench-support",
    ":test-support",
)

if (System.getenv("CI_CORE_ONLY") != "true") {
    include(
        ":interp-compose",
        ":ide-ui-api",
        ":store-bridge",
        ":model-bridge",
        ":ide-ui-resources",
        ":ide-ui-core",
        ":ide-ui-components",
        ":ide-ui-editor",
        ":ide-ui-screens",
        ":ide-ui",
        ":agent-ui",
        ":vcs-ui",
        ":ide-core",
        ":build-cli",
        ":art-compat",
        ":ide-android",
    )
}

val layers = mapOf(
    "platform" to listOf(
        "model-api", "platform-core", "platform-json", "vfs-api", "project-model-api",
        "project-model-impl", "project-templates",
    ),
    "lang" to listOf(
        "kotlin-classfile",
        "language-api", "index-api", "index-impl", "analysis-api", "analysis-impl",
        "lang-jdt", "lang-java", "lang-kotlin", "lang-kotlin-index", "lang-ksp", "lang-xml", "lsp-api", "lsp-client",
        "kotlin-compiler-deps", "intellij-psi-host", "decompiler", "block-api", "block-impl",
    ),
    "run" to listOf("interp-api", "interp-core", "interp-impl", "interp-compose", "jvm-interp", "jvm-vm", "jvm-vm-fixtures", "awt-toolkit"),
    "android" to listOf(
        "applog-runtime", "art-compat"
    ),
    "services" to listOf(
        "deps-api", "deps-impl", "vcs-api", "vcs-impl", "vcs-ui",
        "store-api", "store-impl", "store-bridge",
        "analytics-api", "analytics-impl", "agent-api", "agent-impl", "agent-mcp", "agent-ui",
    ),
    "plugins" to listOf("plugin-api", "plugin-ui-api", "plugin-bom", "plugin-impl", "spi-compat"),
    "app" to listOf(
        "ide-ui-api", "ide-ui-resources", "ide-ui-core", "ide-ui-components", "ide-ui-editor",
        "ide-ui-screens", "ide-ui", "model-bridge", "ide-core",
        "ide-android",
        "build-cli",
    ),
    "tools" to listOf("test-support", "bench-support"),
)

val unlayered = rootProject.children.map { it.name } - layers.values.flatten().toSet() - setOf("samples")
require(unlayered.isEmpty()) {
    "settings.gradle.kts: $unlayered are included but not assigned a layer directory"
}

val included = rootProject.children.map { it.name }.toSet()
layers.forEach { (layer, modules) ->
    modules.filter { it in included }.forEach { project(":$it").projectDir = file("$layer/$it") }
}
