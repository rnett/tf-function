plugins {
    kotlin("jvm") version "1.4.21"
    id("com.rnett.tf-function") version "1.0.1-ALPHA"
}
version = "1.0.1-ALPHA"
group = "com.rnett.tf-function"

val tensorflowVersion = "0.3.0-SNAPSHOT"

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

dependencies {
    implementation("com.rnett.tf-function:tf-function:1.0.0-ALPHA")
    implementation("org.tensorflow:tensorflow-core-platform:${tensorflowVersion}")


    testImplementation(kotlin("test-junit"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        useIR = true
        freeCompilerArgs = listOf("-Xjvm-default=compatibility")
    }
}