import org.gradle.api.tasks.testing.Test

plugins {
    id("com.android.application")
}

android {
    namespace = "com.neonote"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.neonote"
        minSdk = 26
        targetSdk = 36
    }
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.2.21")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
