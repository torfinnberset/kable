buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.api)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlinter) apply false
    alias(libs.plugins.maven.publish) apply false
}

dependencies {
    dokka(project("kable-core"))
    dokka(project("kable-log-engine-khronicle"))
}

apiValidation {
    ignoredProjects.add("kable-btleplug-ffi")
    ignoredProjects.add("kable-default-permissions")
}

allprojects {
    group = "com.juul.kable"

    repositories {
        google()
        mavenCentral()
    }

    // Fork-only. The Bloomlife patient app needs the L2CAP channel support of PR #1231 before it is
    // released, and the alternatives are worse: a Gradle source dependency cannot build this project at
    // all (Gradle refuses the nested `includeBuild("uniffi-plugin")`), and a vendored copy of the sources
    // silently drifts from upstream. Publishing a real artifact from a tag keeps the app on a pinned,
    // reproducible version. The destination is a plain directory that the publish workflow commits to the
    // `maven` branch. Delete this block, the workflow, and the branch once #1231 is released.
    pluginManager.withPlugin("com.vanniktech.maven.publish") {
        configure<PublishingExtension> {
            repositories {
                maven {
                    name = "fork"
                    url = rootProject.layout.buildDirectory.dir("fork-maven").get().asFile.toURI()
                }
            }
        }
    }

    listOf(
        org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile::class,
        org.jetbrains.kotlin.gradle.tasks.KotlinCompileCommon::class,
        org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile::class,
        org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile::class,
    ).forEach { kClass ->
        tasks.withType(kClass).configureEach {
            compilerOptions.suppressWarnings = (findProperty("suppressWarnings") as? String).toBoolean()
        }
    }

    tasks.withType<Test>().configureEach {
        testLogging {
            events("started", "passed", "skipped", "failed", "standardOut", "standardError")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showExceptions = true
            showStackTraces = true
            showCauses = true
        }
    }
}
