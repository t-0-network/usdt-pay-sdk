pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.gradleup.nmcp") version "1.5.0"
        id("com.gradleup.nmcp.aggregation") version "1.5.0"
    }
}

// Provisions the Java 21 toolchain when the developer does not already have one,
// so a first build does not stop at "No matching toolchains found".
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// `**/.gitignore` is one of Ant's default excludes, which Gradle inherits for every
// Copy *and* Jar task. Without this, the starter's .gitignore is silently dropped on
// the way into usdt-pay-init.jar and again on the way out of it.
org.apache.tools.ant.DirectoryScanner.removeDefaultExclude("**/.gitignore")

rootProject.name = "usdt-pay-sdk-java"

include("sdk")
include("starter:acquirer")
include("cli")
