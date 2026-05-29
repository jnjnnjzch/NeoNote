plugins {
    id("com.android.application")
}

android {
    namespace = "com.neonote"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.neonote"
        minSdk = 26
    }
}

dependencies {
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
