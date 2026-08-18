import java.time.Duration

// The root project builds nothing. It exists to aggregate the publishable modules
// into one Central Portal deployment, so `publishAggregationToCentralPortal` uploads
// a single bundle instead of one per module.
//
// Deliberately no shared `subprojects { java { ... } }` convention: each module pins
// its own toolchain (21) and `release` (17), and a convention here would fight them.
plugins {
    id("com.gradleup.nmcp.aggregation")
}

// The aggregation plugin resolves its own `nmcp-tasks` artifact through the root
// project's `:nmcpTasks` configuration, so the root needs a repository even though it
// builds nothing. Every module declares its own, but a module's repositories are not
// visible here. Without this, `publishAggregationToCentralPortal` dies at
// `nmcpCheckAggregationFiles` with "no repositories are defined" — and only there:
// `./gradlew build` never resolves that configuration, so CI stays green.
repositories {
    mavenCentral()
}

nmcpAggregation {
    centralPortal {
        username = providers.environmentVariable("MAVEN_CENTRAL_USERNAME")
        password = providers.environmentVariable("MAVEN_CENTRAL_PASSWORD")
        publicationName = "USDt Pay SDK ${project.version}"

        // Wait for Central to validate the bundle — that is where a bad pom or a
        // missing signature surfaces — but do not sit through the release itself.
        validationTimeout = Duration.ofMinutes(30)
        publishingTimeout = Duration.ZERO
    }
}

dependencies {
    nmcpAggregation(project(":sdk"))
}
