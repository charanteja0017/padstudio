// Pure Kotlin/JVM: pen-profile generation (Key Mapper backup JSON), app categorisation and
// group planning. No Android types, so every rule is unit-tested on the JVM without a tablet.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}
