plugins {
    checkstyle
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    jacoco
}

android {
    namespace = "com.example.carlauncher"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.carlauncher"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += listOf("x86_64")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            enableAndroidTestCoverage = true
        }
        release {
            optimization {
                enable = false
            }
        }
        create("profiling") {
            initWith(getByName("release"))
            applicationIdSuffix = ".profiling"
            versionNameSuffix = "-profiling"
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            enableAndroidTestCoverage = false
            enableUnitTestCoverage = false
            // Resolve Android library dependencies through their release variant.
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    lint {
        abortOnError = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Source-only analysis has no dependency on compiled classes or generated code,
// so it can safely gate preBuild and direct Kotlin/Java compilation tasks.
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

// Keep JMH annotation processing and forked measurements outside JUnit tests.
val jmhRuntime by configurations.creating
dependencies {
    jmhRuntime("org.openjdk.jmh:jmh-core:1.37")
    jmhRuntime("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}
val debugUnitTestClasspath = providers.provider {
    tasks.named<Test>("testDebugUnitTest").get().classpath
}
val compileJvmBenchmarks = tasks.register<JavaCompile>("compileJvmBenchmarks") {
    dependsOn("testDebugUnitTest")
    source(fileTree("src/jmh/java") { include("**/*.java") })
    classpath = files(debugUnitTestClasspath, jmhRuntime)
    options.annotationProcessorPath = jmhRuntime
    destinationDirectory.set(layout.buildDirectory.dir("classes/jmh"))
    options.release.set(11)
}
tasks.register<JavaExec>("jvmPerformanceReport") {
    group = "verification"
    description = "Measure production codecs with forked JMH; write machine-readable results"
    dependsOn(compileJvmBenchmarks)
    classpath = files(compileJvmBenchmarks.flatMap { it.destinationDirectory },
        debugUnitTestClasspath, jmhRuntime)
    mainClass.set("org.openjdk.jmh.Main")
    val report = layout.buildDirectory.file("reports/jmh/results.json")
    doFirst { report.get().asFile.parentFile.mkdirs() }
    args("com.example.carlauncher.perf.VehicleBenchmarks", "-wi", "3", "-i", "5",
        "-w", "1s", "-r", "1s", "-f", "2", "-t", "1", "-foe", "true",
        "-prof", "gc", "-rf", "json", "-rff", report.get().asFile.absolutePath,
        "-jvmArgs", "-Xms256m -Xmx512m")
}

tasks.named("preBuild") { dependsOn(detektGate) }
tasks.named("check") { dependsOn(detektGate) }
tasks.matching {
    name.startsWith("compile") &&
        (name.endsWith("Kotlin") || name.endsWith("JavaWithJavac"))
}.configureEach { dependsOn(detektGate) }

dependencies {
    implementation(project(":someip-android"))
    implementation(libs.okhttp)
    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.viewmodel.ktx)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.coroutines.test)
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.fragment)
    implementation(libs.material)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.fragment.testing)
    compileOnly(
        files(
            androidComponents.sdkComponents.sdkDirectory.map {
                it.file("platforms/android-36.1/optional/android.car.jar")
            }
        )
    )
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    // Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test.junit4)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)
}

// ---------------------------------------------------------------------------
// JaCoCo 全模块覆盖率（Debug 变体）：报告 + 校验门禁
// 生产类目录与执行数据路径按 AGP 9.x 实际产物确定；排除生成代码。
// ---------------------------------------------------------------------------
val jacocoClassDir = layout.buildDirectory.dir(
    "intermediates/javac/debug/compileDebugJavaWithJavac/classes"
)
val jacocoKotlinClassDir = layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")
val jacocoExecFile = layout.buildDirectory.file("jacoco/testDebugUnitTest.exec")
val instrumentationCoverage = fileTree(layout.buildDirectory.dir("outputs/code_coverage/debugAndroidTest")) {
    include("*.ec")
}
val jacocoSourceDirs = files("src/main/java", "src/main/kotlin")

val jacocoGeneratedExcludes = listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.class",
    "**/Manifest*.class",
    "**/databinding/**",
    "**/*Binding.class",
    "**/*BindingImpl.class"
)

fun mainClassTree() = files(jacocoClassDir, jacocoKotlinClassDir).asFileTree.matching {
    exclude(jacocoGeneratedExcludes)
    exclude("**/*_Impl*.class")
}

tasks.register<JacocoReport>("fullDebugUnitTestCoverageReport") {
    dependsOn("testDebugUnitTest")
    mustRunAfter("connectedDebugAndroidTest")
    group = "verification"
    description = "生成 Debug 全模块单元测试覆盖率报告（HTML + XML）"

    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(false)
    }

    classDirectories.setFrom(mainClassTree())
    sourceDirectories.setFrom(jacocoSourceDirs)
    executionData.setFrom(jacocoExecFile, instrumentationCoverage)
}

tasks.register<JacocoCoverageVerification>("verifyFullDebugUnitTestCoverage") {
    dependsOn("fullDebugUnitTestCoverageReport")
    group = "verification"
    description = "校验 Debug 全模块覆盖率门禁（行 90% / 分支 85%）"

    classDirectories.setFrom(mainClassTree())
    sourceDirectories.setFrom(jacocoSourceDirs)
    executionData.setFrom(jacocoExecFile, instrumentationCoverage)

    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
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
