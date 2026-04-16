// ROOT build.gradle.kts
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}


dependencies {
    implementation(kotlin("stdlib"))
}

kotlin {
    sourceSets["main"].kotlin.srcDir("src/main/java")
    sourceSets["test"].kotlin.srcDir("src/test/java")
}
