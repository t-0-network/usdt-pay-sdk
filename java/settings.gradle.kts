pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.gradleup.nmcp") version "1.6.2"
        id("com.gradleup.nmcp.aggregation") version "1.5.0"
    }
}

// Provisions the Java 21 toolchain when the developer does not already have one,
// so a first build does not stop at "No matching toolchains found".
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "usdt-pay-sdk-java"

include("sdk")
include("starter:acquirer")
