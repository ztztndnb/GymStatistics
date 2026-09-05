plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}

// Generate the wrapper without downloading/validating the distribution
// (this sandbox cannot reach downloads.gradle.org, so validation is disabled;
//  the pinned version below matches a locally cached Gradle distribution).
tasks.wrapper {
    distributionType = Wrapper.DistributionType.BIN
    gradleVersion = "8.10"
    validateDistributionUrl = false
}
