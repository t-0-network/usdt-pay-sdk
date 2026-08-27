plugins {
    `java-library`
    id("build.buf") version "0.11.0"
    `maven-publish`
    signing
    id("com.gradleup.nmcp")
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

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
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

    // Central rejects a deployment that is missing either of these.
    withJavadocJar()
    withSourcesJar()
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

// javadoc is not a Jar, so the wiring above misses it, and the generated-source
// srcDir below is a bare Provider with no producer task attached. Without this it
// fails with "package does not exist" — which -Xdoclint:none does not suppress,
// because that is a symbol error, not a doc lint.
tasks.named("javadoc").configure {
    dependsOn("bufGenerate")
}

sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("bufbuild/generated/gen/java"))
        }
    }
}

// The stubs are machine-generated from protos we do not own; doc lint on them is
// noise nobody can act on. The jar still has to exist for Central.
tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            // The module is called `sdk`; the artifact is not. This is the coordinate
            // the docs and the starter's standalone mode name.
            artifactId = "usdt-pay-sdk-java"
            from(components["java"])

            pom {
                name = "USDt Pay SDK"
                description = "Java SDK for the t-0 USDt Pay network — generated stubs for the acquirer, issuer and LP roles"
                url = "https://github.com/t-0-network/usdt-pay-sdk"

                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/licenses/MIT"
                    }
                }

                developers {
                    developer {
                        id = "t-0"
                        name = "T-0 Network"
                        email = "dev@t-0.network"
                    }
                }

                scm {
                    connection = "scm:git:git://github.com/t-0-network/usdt-pay-sdk.git"
                    developerConnection = "scm:git:ssh://github.com/t-0-network/usdt-pay-sdk.git"
                    url = "https://github.com/t-0-network/usdt-pay-sdk"
                }
            }
        }
    }
}

signing {
    // Only CI has the key. A local `build` or `publishToMavenLocal` must not stop
    // at "no signatory available".
    val signingKey: String? = System.getenv("GPG_PRIVATE_KEY")
    isRequired = signingKey != null
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, "")
        sign(publishing.publications["mavenJava"])
    }
}
