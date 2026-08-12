// Provisions the Java 21 toolchain when the developer does not already have one,
// so a first build does not stop at "No matching toolchains found".
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "usdt-pay-sdk-java"

include("sdk")
include("starter:acquirer")
