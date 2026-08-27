plugins {
    application
    id("com.gradleup.shadow") version "9.4.1"
}

group = rootProject.property("group") as String
version = rootProject.property("version") as String

val picocliVersion = "4.7.7"

repositories {
    mavenCentral()
}

dependencies {
    // For Signer/HexUtils — the generated keypair has to be a real secp256k1 one,
    // derived the same way the runtime derives it.
    implementation(project(":sdk"))
    implementation("info.picocli:picocli:$picocliVersion")
    annotationProcessor("info.picocli:picocli-codegen:$picocliVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// The extractor reads the starters out of this jar's own resources, so the test needs
// them processed first — `test` alone would otherwise run against an empty /templates.
tasks.test {
    dependsOn(tasks.processResources)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    // Same floor as the SDK: `java -jar usdt-pay-init.jar` should work on a 17 JRE.
    options.release = 17
}

application {
    mainClass = "network.t0.pay.cli.InitCommand"
    applicationName = "usdt-pay-init"
}

// `**/.gitignore` is one of Ant's default excludes, which every Gradle file scan
// inherits — a `from()` naming the file directly is filtered just the same — so a
// starter's .gitignore cannot ride into the jar with the rest of its directory. Copied
// by hand instead, undotted: the name would only be dropped a second time by the scan
// shadowJar does, and TemplateExtractor puts the dot back when it scaffolds.
//
// The blunt alternative, `DirectoryScanner.removeDefaultExclude` in settings.gradle.kts,
// leaks into every other task in the build: Gradle 9 sees the mutated defaults mid-build
// in a reused daemon and fails `:starter:acquirer:installDist` with "Cannot change
// default excludes during the build".
//
// Without any of this a scaffolded project has no .gitignore, and the first `git add -A`
// commits the .env holding its private key.
val starterIgnores = file("../starter").listFiles().orEmpty()
    .filter { it.isDirectory }
    .mapNotNull { starter -> starter.resolve(".gitignore").takeIf { it.isFile }?.let { starter.name to it } }

// Every starter directory becomes /templates/<role> in the jar, and that listing is
// the CLI's `--starter` menu — adding a starter needs no registry, just a directory.
//
// One task, not a separate Sync into the resources dir: `starter/acquirer` is a live
// subproject here, so a second task reading it would be reading another task's
// declared output and Gradle 9 fails the build. The excludes keep build/ out of the
// snapshot entirely.
tasks.processResources {
    from("../starter") {
        into("templates")
        exclude("**/build", "**/.gradle", "**/.env", "**/*.class", "**/.idea", "**/*.iml")
    }

    // Root-level only: this must never touch the template spec, where `expand` would
    // corrupt gradle-wrapper.jar and choke on every `$` in gradlew.
    filesMatching("version.properties") {
        expand("version" to project.version)
    }

    inputs.files(starterIgnores.map { it.second }).withPropertyName("starterIgnores")
    val destination = destinationDir
    doLast {
        for ((role, ignore) in starterIgnores) {
            ignore.copyTo(File(destination, "templates/$role/gitignore"), overwrite = true)
        }
    }
}

tasks.shadowJar {
    archiveBaseName = "usdt-pay-init"
    archiveClassifier = ""
    // No `minimize {}`: picocli is reflection-driven, and the savings would be noise
    // next to the grpc and BouncyCastle bytes that have to be there anyway.
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
