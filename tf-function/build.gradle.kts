plugins {
    kotlin("jvm")
    `maven-publish`
}

val tensorflowVersion = "0.3.0-SNAPSHOT"

dependencies {

    api("org.tensorflow:tensorflow-core-api:${tensorflowVersion}")

    //implementation(kotlin("reflect"))

    implementation("org.slf4j:slf4j-simple:1.7.30")


    testImplementation(kotlin("test-junit"))
    testApi("org.tensorflow:tensorflow-core-platform:${tensorflowVersion}")
}

kotlin {
    sourceSets.all {
        languageSettings.apply {
            useExperimentalAnnotation("kotlin.contracts.ExperimentalContracts")
        }
    }
}

val sourcesJar = tasks.create<Jar>("sourcesJar") {
    classifier = "sources"
    from(kotlin.sourceSets["main"].kotlin.srcDirs)
}

publishing {
    publications {
        create<MavenPublication>("default") {
            from(components["java"])
            artifact(sourcesJar)
        }
    }
}

tasks.getByName("compileKotlin") {
    dependsOn(":tf-function-compiler-plugin:publishToMavenLocal")
}