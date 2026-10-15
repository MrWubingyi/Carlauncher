plugins {
    checkstyle
    alias(libs.plugins.android.library)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.example.someip"
    ndkVersion = "28.2.13676358"
    compileSdk {
        version = release(36) { minorApiLevel = 1 }
    }
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += providers.gradleProperty("someipAbis")
                .getOrElse("x86_64").split(",")
        }
        externalNativeBuild {
            cmake { arguments += "-DANDROID_STL=c++_shared" }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    lint { abortOnError = true }
}

val detektGate = tasks.named<dev.detekt.gradle.Detekt>("detekt") {
    setSource(fileTree("src") { include("**/*.kt") })
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig.set(false)
    ignoreFailures.set(false)
    failOnSeverity.set(dev.detekt.gradle.extensions.FailOnSeverity.Warning)
    basePath.set(rootProject.projectDir.absolutePath)
    reports {
        html.required.set(true)
        checkstyle.required.set(true)
        sarif.required.set(true)
    }
}
tasks.named("preBuild") { dependsOn(detektGate) }
tasks.named("check") { dependsOn(detektGate) }
tasks.matching {
    name.startsWith("compile") &&
        (name.endsWith("Kotlin") || name.endsWith("JavaWithJavac"))
}.configureEach { dependsOn(detektGate) }

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.ext.junit)
}

// Scan handwritten Java in every source set, including tests and JMH.
checkstyle {
    toolVersion = "12.3.0"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    maxWarnings = 0
}
val checkstyleGate = tasks.register<Checkstyle>("checkstyleAll") {
    group = "verification"
    description = "Check all handwritten Java sources"
    source(fileTree("src") { include("**/*.java") })
    classpath = files()
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
tasks.named("preBuild") { dependsOn(checkstyleGate) }
tasks.named("check") { dependsOn(checkstyleGate) }
tasks.matching {
    name.startsWith("compile") &&
        (name.endsWith("Kotlin") || name.endsWith("JavaWithJavac") || name == "compileJvmBenchmarks")
}.configureEach { dependsOn(checkstyleGate) }
