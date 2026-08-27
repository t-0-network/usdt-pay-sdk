plugins {
    java
    application
}

group = "network.t-0"
version = "0.1.0-SNAPSHOT"

// Two ways to build this starter, both supported:
//
//  * standalone — what a scaffolded project does, and the usual one. The SDK comes
//    from Maven Central at the version in `gradle.properties`; the scaffolder pins
//    it there, and `-PusdtPaySdkVersion=<version>` overrides it for a one-off.
//    Deliberately not a `+` range: an SDK bump is your decision, not a surprise.
//
//  * inside the SDK repo — `cd java && ./gradlew :starter:acquirer:installDist`.
//    The starter compiles against the local `:sdk` project instead, so a change to
//    the SDK reaches the starter without being published first.
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
                "usdtPaySdkVersion is not set. It belongs in gradle.properties — the " +
                    "scaffolder writes it there — or pass -PusdtPaySdkVersion=<version> " +
                    "for a one-off build. Inside the SDK repo, build with " +
                    "`cd java && ./gradlew :starter:acquirer:installDist` instead."
            )
        implementation("network.t-0:usdt-pay-sdk-java:$sdkVersion")
    }

    // .env loading
    implementation("io.github.cdimascio:dotenv-java:3.2.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.32")

    // grpc-java logs through java.util.logging, which logback cannot intercept on its
    // own — without this bridge its records ignore logback.xml and print in raw JUL
    // format. Pinned to the slf4j-api version logback-classic resolves: the bridge and
    // the API are one release train, and a split between them fails at runtime.
    implementation("org.slf4j:jul-to-slf4j:2.0.18")

    // javax.annotation for generated gRPC code
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Lets a test stand up a fake t-0 in memory and hand the helper a real stub.
    // The generated stubs are final with private constructors, so they cannot be
    // subclassed or mocked — an in-process server is how you fake this side.
    testImplementation("io.grpc:grpc-inprocess:1.83.1")
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
    mainClass.set("network.t0.pay.acquirer.Main")
    applicationName = "acquirer"
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
