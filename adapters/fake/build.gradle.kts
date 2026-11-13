plugins {
    alias(libs.plugins.kotlin.jvm)
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
    api(project(":core:remote"))
    testImplementation(testFixtures(project(":core:remote")))
    testImplementation(libs.kotlin.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
