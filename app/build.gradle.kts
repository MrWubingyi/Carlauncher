import java.util.Properties

val localProperties = Properties().apply {
    rootProject.file("local.properties")
        .inputStream()
        .use(::load)
}

val sdkDir = localProperties.getProperty("sdk.dir")
    ?: error("sdk.dir is not defined in local.properties")

plugins {
    alias(libs.plugins.android.application)
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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
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
            "${sdkDir}/platforms/android-36.1/optional/android.car.jar"
        )
    )
}

// ---------------------------------------------------------------------------
// JaCoCo 全模块覆盖率（Debug 变体）：报告 + 校验门禁
// 生产类目录与执行数据路径按 AGP 9.x 实际产物确定；排除生成代码。
// ---------------------------------------------------------------------------
val jacocoClassDir = layout.buildDirectory.dir(
    "intermediates/javac/debug/compileDebugJavaWithJavac/classes"
)
val jacocoExecFile = layout.buildDirectory.file("jacoco/testDebugUnitTest.exec")
val instrumentationCoverage = fileTree(layout.buildDirectory) {
    include("**/*.ec")
    exclude("outputs/code_coverage/debugAndroidTest/connected/**/*.ec")
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

fun mainClassTree() = fileTree(jacocoClassDir) {
    exclude(jacocoGeneratedExcludes)
}

tasks.register<JacocoReport>("fullDebugUnitTestCoverageReport") {
    dependsOn("testDebugUnitTest")
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
