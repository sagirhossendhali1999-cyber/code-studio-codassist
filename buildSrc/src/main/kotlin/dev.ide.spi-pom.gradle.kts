// The publishing identity every artifact a plugin author resolves shares: the coordinate, the SPI version,
// the POM the Central Portal requires, the staging repository, and opt-in signing.
//
// Applied by `com.shd.codestudio.spi-publish` for the SPI jars, and directly by `:plugin-bom`, which is a
// `java-platform` and so cannot apply that one (a project is a library or a platform, not both). Publications
// are configured as they are created, so each of those decides what it publishes and this decides what the
// POM says about it.

plugins {
    `maven-publish`
    signing
}

val projectUrl = "https://github.com/sagirhossendhali1999-cyber/Code Studio"

/** The published SPI version, taken from the constant the Create-Project template also emits. */
val spiVersion: String = run {
    val source = rootProject.file("plugins/plugin-api/src/main/kotlin/com/shd/codestudio/plugin/PluginManifest.kt")
    val match = Regex("""PLUGIN_SPI_VERSION:\s*String\s*=\s*"([^"]+)"""").find(source.readText())
    requireNotNull(match) {
        "PLUGIN_SPI_VERSION not found in ${source.name}. The publish version is read from that constant so " +
            "it cannot drift from the coordinate a scaffolded plugin project asks for."
    }.groupValues[1]
}

group = "io.github.sagirhossendhali1999-cyber"
version = spiVersion

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Code Studio ${project.name}")
            description.set(
                when (project.name) {
                    "plugin-api" ->
                        "The Code Studio plugin SPI: the Plugin entry point, its manifest, the " +
                            "registration API, and the action model."
                    "plugin-ui-api" ->
                        "The Code Studio plugin UI SPI: what a plugin implements to contribute a tool " +
                            "window, a screen, or an overlay, and the context those bodies render against."
                    "model-api" ->
                        "The platform-free core of the Code Studio SPI: the symbol model, the neutral " +
                            "syntax tree, VirtualFile, and the content digest they are keyed by. " +
                            "Multiplatform, and exposed by every other SPI artifact."
                    "platform-core" ->
                        "The Code Studio platform substrate the plugin SPI exposes: extension points, " +
                            "scoped services, the message bus, logging, and the settings model."
                    "project-model-api" ->
                        "The Code Studio project model: module types, project templates, facets and " +
                            "their codecs, and file icons."
                    "language-api" ->
                        "The Code Studio language SPI: the neutral syntax tree, resolution, file types, " +
                            "completion, formatting, folding, and a language's compilation context."
                    "analysis-api" ->
                        "The Code Studio analysis SPI: analyzers, the diagnostic model, quick fixes, and " +
                            "editor action providers."
                    "index-api" ->
                        "The Code Studio index SPI: persisted, incrementally maintained project indexes."
                    "build-api" ->
                        "The Code Studio build SPI: build systems, build plugins, source generators, and " +
                            "run tasks."
                    "vfs-api" ->
                        "The Code Studio virtual file system, which the other SPI artifacts expose."
                    "interp-api" ->
                        "The Code Studio interpreter SPI: lowering a project's Kotlin and running it, or its " +
                            "compiled classes, from a plugin, for a preview or a run of the plugin's own."
                    "vcs-api" ->
                        "The Code Studio version-control SPI: the repository, branch, commit and status " +
                            "model, the provider extension point a checkout is resolved through, and the " +
                            "account, credential and forge ports a sign-in flow is built on."
                    "agent-api" ->
                        "The Code Studio coding-agent SPI: the tools an agent can call, the workspace it " +
                            "reads and edits, and the provider-neutral LLM interface behind it."
                    "block-api" ->
                        "The Code Studio block-editor SPI: the projection of a language's syntax tree onto " +
                            "a block surface, and the mapping a language contributes to define it."
                    "plugin-bom" ->
                        "Versions for everything a Code Studio plugin compiles against: the SPI artifacts " +
                            "and the Compose the IDE provides at runtime."
                    else -> "A Code Studio plugin SPI artifact."
                }
            )
            url.set(projectUrl)
            licenses {
                license {
                    name.set("GPL-3.0-or-later WITH Classpath-exception-2.0")
                    url.set("$projectUrl/blob/main/LICENSE-EXCEPTION")
                    distribution.set("repo")
                    comments.set(
                        "Code Studio is GPL-3.0-or-later. This module adds the Classpath exception so a " +
                            "plugin that links against it may be released under any license."
                    )
                }
            }
            developers {
                developer {
                    id.set("sagirhossendhali1999-cyber")
                    name.set("sagirhossendhali1999-cyber")
                    url.set("https://github.com/sagirhossendhali1999-cyber")
                }
            }
            scm {
                url.set(projectUrl)
                connection.set("scm:git:$projectUrl.git")
                developerConnection.set("scm:git:ssh://git@github.com/sagirhossendhali1999-cyber/Code Studio.git")
            }
            issueManagement {
                system.set("GitHub Issues")
                url.set("$projectUrl/issues")
            }
        }
    }

    repositories {
        // A staging directory laid out exactly as the Central Portal expects an upload bundle. Produce it
        // with `./gradlew publishSpiPublicationToCentralBundleRepository`, then zip the contents of
        // build/central-bundle and upload that. Keeps the release a deliberate manual step and needs no
        // credentials in the build.
        maven {
            name = "centralBundle"
            url = uri(rootProject.layout.buildDirectory.dir("central-bundle"))
        }
    }
}

signing {
    // Central requires signatures; a local build and publishToMavenLocal must not. Signing therefore turns
    // itself on only when a key is supplied, so `./gradlew publishToMavenLocal` works on a clean checkout:
    //   ./gradlew publishSpiPublicationToCentralBundleRepository \
    //       -PsigningInMemoryKey="$(cat key.asc)" -PsigningInMemoryKeyPassword=...
    isRequired = false
    val key = providers.gradleProperty("signingInMemoryKey").orNull
    if (!key.isNullOrBlank()) {
        useInMemoryPgpKeys(key, providers.gradleProperty("signingInMemoryKeyPassword").orNull.orEmpty())
        sign(publishing.publications)
    }
}
