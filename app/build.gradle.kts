import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.tcmfactory"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tcmfactory"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "1.5.3"
        vectorDrawables { useSupportLibrary = true }
    }

    // ---------- 签名配置 ----------
    // 从工程根目录的 keystore.properties 读取（该文件已在 .gitignore 中，不会进入版本库）。
    //
    // 找不到时回退到 debug 签名：这样他人 clone 后无需任何配置就能直接构建运行，
    // 但产物的签名与正式版不同，无法覆盖安装正式版，
    // 也就不可能伪造出「官方更新」去欺骗已安装的用户。
    val keystoreFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
    }
    val hasReleaseKey = !keystoreProps.getProperty("storeFile").isNullOrBlank()

    signingConfigs {
        if (hasReleaseKey) {
            create("releaseLocal") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            // 首个可用版本先关闭混淆，确保 Hilt / Compose 反射路径绝对安全。
            // 待功能稳定后再开启 R8 并回归验证。
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("releaseLocal")
            } else {
                // 未配置正式密钥时用 debug 签名，保证任何人都能构建出可安装的包
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose = true
        // 知识库更新需要读取应用版本号，用于与数据源的 minAppVersion 比对。
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // lintVitalRelease 会把发布构建的峰值内存显著抬高，在内存受限环境
        // （容器 / CI）下容易触发 OOM 把 Gradle daemon 杀掉。
        // 这里关闭发布构建时的自动 lint，需要时手动执行 ./gradlew :app:lint
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.serialization.json)

    // 知识库联网更新。选 OkHttp 而非 HttpURLConnection：需要下载进度回调、
    // 连接/读超时分离、自动重定向与 gzip，手写这些容易在弱网下出问题。
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    // 用本地 MockWebServer 在 JVM 上验证完整下载链路（含 sha256 校验与多源回退），
    // 不依赖外网，保证 CI 可复现。
    testImplementation(libs.okhttp.mockwebserver)
}
