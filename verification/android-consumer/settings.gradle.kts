pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }
dependencyResolutionManagement {
    repositories {
        // An explicit producer staging repository is allowed; never mavenLocal or source substitution.
        providers.gradleProperty("crossbeamRepository").orNull?.let { location ->
            exclusiveContent {
                forRepository { maven { url = uri(location) } }
                filter {
                    includeModule("io.github.kotlinmania", "crossbeam-kotlin")
                    includeModule("io.github.kotlinmania", "crossbeam-kotlin-android")
                }
            }
        }
        google()
        mavenCentral()
    }
    versionCatalogs { create("libs") { from(files("../../gradle/libs.versions.toml")) } }
}
rootProject.name = "crossbeam-android-publication-consumer"
