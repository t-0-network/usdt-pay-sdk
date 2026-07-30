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
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
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
