plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
}

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
}

kotlin {
    compilerOptions {
        jvmTarget.set(
            org.jetbrains.kotlin.gradle.dsl.JvmTarget
                .fromTarget(libs.versions.jvmTarget.get()),
        )
    }
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testFixturesApi(libs.kotlinx.coroutines.test)
    testFixturesApi(libs.kotlin.test)
    testFixturesApi(platform(libs.junit.bom))
    testFixturesApi(libs.junit.jupiter)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
