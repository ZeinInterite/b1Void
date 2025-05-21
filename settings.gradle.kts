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
 