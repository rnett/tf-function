plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    kotlin("kapt")
    `maven-publish`
    id("com.github.gmazzo.buildconfig")
    id("com.gradle.plugin-publish")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:1.4.21")

    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.21")

    compileOnly("com.google.auto.service:auto-service-annotations:1.0-rc6")
    kapt("com.google.auto.service:auto-service:1.0-rc6")
}

buildConfig {
    val project = project(":tf-function-compiler-plugin")
    packageName(project.group.toString().replace("-", "_"))
    buildConfigField("String", "PROJECT_GROUP_ID", "\"${project.group}\"")
    buildConfigField("String", "PROJECT_ARTIFACT_ID", "\"${project.name}\"")
    buildConfigField("String", "PROJECT_VERSION", "\"${project.version}\"")
}

gradlePlugin {
    plugins {
        create("tfFunctionPlugin") {
            id = "com.rnett.tf-function"
            displayName = "TFFunction Plugin"
            description = "TFFunction Kotlin Compiler plugin"
            implementationClass = "com.rnett.tf_function.TFFunctionGradlePlugin"
        }
    }
}