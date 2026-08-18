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
