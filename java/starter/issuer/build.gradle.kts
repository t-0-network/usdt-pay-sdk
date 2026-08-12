plugins {
    java
    application
}

group = "network.t-0"
version = "0.1.0-SNAPSHOT"

// Two ways to build this starter:
//
//  * inside the SDK repo — `cd java && ./gradlew :starter:issuer:installDist`.
//    The starter compiles against the local `:sdk` project. This is the only
//    supported mode before 1.0, because the SDK artifact is not published yet.
//
//  * standalone, after you copy this directory out of the repo — pass the SDK
//    version explicitly, e.g. `./gradlew build -PusdtPaySdkVersion=1.0.0`.
//    Deliberately not a `+` range: an SDK bump is your decision, not a surprise.
val isSubproject = rootProject.name == "usdt-pay-sdk-java"

repositories {
    mavenCentral()
}

dependencies {
    if (isSubproject) {
        implementation(project(":sdk"))
    } else {
        val sdkVersion = providers.gradleProperty("usdtPaySdkVersion").orNull
            ?: error(
                "usdtPaySdkVersion is not set. Build from the SDK repo " +
                    "(cd java && ./gradlew :starter:issuer:installDist), or pass " +
                    "-PusdtPaySdkVersion=<version> once the artifact is published."
            )
        implementation("network.t-0:usdt-pay-sdk-java:$sdkVersion")
    }

    // .env loading
    implementation("io.github.cdimascio:dotenv-java:3.2.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.32")

    // javax.annotation for generated gRPC code
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// One JDK builds this, whatever the developer happens to have on PATH.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass.set("network.t0.pay.issuer.Main")
    applicationName = "issuer"
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
