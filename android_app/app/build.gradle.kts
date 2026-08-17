plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.albert.extractorir"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.albert.extractorir"
        minSdk = 21
        targetSdk = 36
        versionCode = 4
        versionName = "1.3.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            it.useJUnit()
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
