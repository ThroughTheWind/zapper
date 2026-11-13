plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.spotless)
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
}

/**
 * SPEC AC-19 and ADR-0002: no UI or core code may reference an adapter's types. The app may name
 * adapters only where it registers them. Run by CI; see AGENTS.md's validation matrix.
 */
val checkAdapterBoundaries by tasks.registering {
    group = "verification"
    description = "Fails if core or app code imports a brand adapter outside its registration files."
    val sources =
        fileTree(layout.projectDirectory) {
            include("core/**/src/**/*.kt", "app/src/**/*.kt")
            exclude("app/src/**/ZapperApplication.kt", "app/src/**/BuildAdapters.kt")
        }
    inputs.files(sources)
    doLast {
        val offenders =
            sources.files.filter { file ->
                file.readLines().any { it.startsWith("import io.github.throughthewind.zapper.adapters.") }
            }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "These files import a brand adapter (ADR-0002, SPEC AC-19):\n" + offenders.joinToString("\n") { "  $it" },
            )
        }
    }
}
