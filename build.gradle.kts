plugins {
    kotlin("jvm") version "1.4.21" apply false
    kotlin("kapt") version "1.4.21" apply false
    id("com.github.gmazzo.buildconfig") version "2.0.2" apply false
    id("com.gradle.plugin-publish") version "0.11.0" apply false
}

group = "com.rnett.tf-function"

allprojects {
    version = "1.0.0-ALPHA"
    group = "com.rnett.tf-function"

    repositories {
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
        mavenCentral()
        maven("https://oss.jfrog.org/artifactory/oss-snapshot-local/")
        google()
        jcenter()
        mavenLocal()
        maven("https://oss.sonatype.org/content/repositories/snapshots") {
            mavenContent { snapshotsOnly() }
        }
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            useIR = true
            freeCompilerArgs = listOf("-Xjvm-default=compatibility")
        }
    }
}