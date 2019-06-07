plugins {
    application
}

application {
    mainClassName = "il.ac.technion.cs.softwaredesign.MainKt"
}

val junitVersion: String? by extra
val hamkrestVersion: String? by extra
val mockkVersion = "1.9"
val guiceVersion: String? by extra
val kotlinGuiceVersion: String? by extra
val gsonVersion = "2.8.5"
val kotsonVersion = "2.5.0"


dependencies {
    compile(project(":library"))
    compile("com.google.inject", "guice", guiceVersion)
    compile("com.authzee.kotlinguice4", "kotlin-guice", kotlinGuiceVersion)

    testCompile("org.junit.jupiter", "junit-jupiter-api", junitVersion)
    testCompile("org.junit.jupiter", "junit-jupiter-params", junitVersion)
    testCompile("com.natpryce", "hamkrest", hamkrestVersion)

    testImplementation("io.mockk:mockk:$mockkVersion")
    compile("com.google.code.gson", "gson", gsonVersion)
    compile("com.github.salomonbrys.kotson", "kotson", kotsonVersion)

    testRuntime("org.junit.jupiter", "junit-jupiter-engine", junitVersion)
}
