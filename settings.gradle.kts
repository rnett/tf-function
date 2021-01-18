pluginManagement {
    repositories {
        mavenCentral()
//        maven("https://plugins.gradle.org/m2/")
        gradlePluginPortal()
        mavenLocal()
    }
}

rootProject.name = "tf-function-parent"

include("tf-function", "tf-function-compiler-plugin", "tf-function-gradle-plugin")
