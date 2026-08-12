plugins {
    `java-library`
    id("build.buf") version "0.11.0"
}

group = rootProject.property("group") as String
version = rootProject.property("version") as String

val providerSdkVersion = rootProject.property("providerSdkVersion") as String

repositories {
    mavenCentral()
}

dependencies {
    // Crypto (Signer, SignatureVerifier) and the signed transport (ProviderServer,
    // BlockingNetworkClient) come from the provider SDK. It also brings grpc,
    // protobuf-java and protovalidate along as `api` dependencies, which is what the
    // generated pay stubs compile and run against.
    api("network.t-0:provider-sdk-java:$providerSdkVersion")

    // javax.annotation for generated gRPC code
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// One JDK builds everything, whatever the developer happens to have on PATH.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"

    // The published jar stays consumable on 17. `release` (unlike source/target
    // compatibility) checks calls against the Java 17 API too, so a 21-only method
    // fails the build here instead of at a customer's runtime. 17 is also where
    // provider-sdk-java itself is compiled.
    options.release = 17
}

// The protos under ../../proto are a snapshot synced from the backend, not code
// authored here — formatting and lint are the backend's business.
buf {
    enforceFormat = false
}

tasks.configureEach {
    if (name == "bufLint") {
        enabled = false
    }
}

// buf appends to its output directory rather than owning it, so a proto that is
// deleted upstream keeps a stale class in the jar until someone runs `clean`.
tasks.named("bufGenerate").configure {
    doFirst { delete(layout.buildDirectory.dir("bufbuild/generated")) }
}

tasks.named("compileJava").configure {
    dependsOn("bufGenerate")
}

tasks.withType<Jar>().configureEach {
    dependsOn("bufGenerate")
}

sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("bufbuild/generated/gen/java"))
        }
    }
}
