plugins {
    application
}

application {
    mainClassName = "il.ac.technion.cs.softwaredesign.MainKt"
}

val junitVersion: String? by extra
val hamkrestVersion: String? by extra

val mockkVersion: String? by extra

val guiceVersion: String? by extra
val kotlinGuiceVersion: String? by extra

val gsonVersion: String? by extra
val kotsonVersion: String? by extra

val kotlinLoggerVersion: String? by extra
val kotlinLoggerImplVersion: String? by extra


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
    
    compile("io.github.microutils:kotlin-logging:$kotlinLoggerVersion")
    compile ("org.slf4j:slf4j-simple:$kotlinLoggerImplVersion")

    testRuntime("org.junit.jupiter", "junit-jupiter-engine", junitVersion)
}
