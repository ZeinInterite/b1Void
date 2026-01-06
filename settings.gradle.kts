import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.repositories

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://www.jitpack.io" )}
    }

}


rootProject.name = "b1Void"
include(":app")
include(":feature:camera")
include(":core:common")
include(":core:model")
include(":core:data")
include(":core:domain")
include(":core:ui")
include(":core:camera")
include(":core:network")
include(":feature:gallery")
 
