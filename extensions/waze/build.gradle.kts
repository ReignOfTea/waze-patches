extension {
    name = "extensions/waze/forsigh-settings.mpe"
}

android {
    namespace = "io.github.forsigh.waze.extension"

    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
