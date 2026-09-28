plugins {
    id("mihon.library")
    kotlin("android")
}

android {
    namespace = "nyanime.privacy.display"
}

dependencies {
    testImplementation(libs.bundles.test)
}
