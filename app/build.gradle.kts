import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------------------------------------------------------------- 版本号
// versionName = 0.<MINOR>.<PATCH>：MINOR / PATCH 由根目录 version.properties 决定 ——
// BUILD 每次成功打出 release APK 后自增（见文件末尾 bumpBuildNumber），
// PATCH = BUILD − PATCH_BASE，于是 0.2.0 之后每次打包依次 0.2.1 / 0.2.2 …
// 换小版本时改 MINOR，并把 PATCH_BASE 改成当时的 BUILD 即可从 x.0 重新起算。
val versionFile = rootProject.file("version.properties")

val versionProps: Properties = Properties().apply {
    if (versionFile.exists()) versionFile.inputStream().use { load(it) }
}

fun readVersionInt(key: String, fallback: Int): Int =
    versionProps.getProperty(key)?.toIntOrNull() ?: fallback

val buildNumber = readVersionInt("BUILD", 1).coerceAtLeast(1)
val minorNumber = readVersionInt("MINOR", 0)
/** 起算补丁号的 BUILD：缺省等于当前 BUILD，即这一次构建就是 x.<MINOR>.0。 */
val patchBase = readVersionInt("PATCH_BASE", buildNumber)
val patchNumber = (buildNumber - patchBase).coerceAtLeast(0)
val versionNameText = "0.$minorNumber.$patchNumber"

/** 配置阶段的时间戳：打包结束时用它判断 APK 是不是本次新产物（失败构建不改号）。 */
val configStartedAt = System.currentTimeMillis()

android {
    namespace = "com.yuzuki.app"
    compileSdk = 35
    // 显式对齐本机已装的 Build Tools，避免 AGP 转去自动安装默认版本 34.0.0
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.yuzuki.app"
        minSdk = 26
        targetSdk = 35
        // versionCode 必须随版本单调递增，直接用 BUILD 换算，留足余量
        versionCode = 1000 + buildNumber
        versionName = versionNameText
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            // 个人自用：release 复用 debug 签名，方便直接装机对比性能（不用于上架）
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // 文档级注入 addDocumentStartJavaScript 需要 androidx.webkit
    // N0：全原生方案已彻底移除 WebView 与 JS 注入，webkit 依赖不再需要
    // TokenStore / 本地偏好：DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // 全量 REST 客户端（OkHttp + org.json，无需额外序列化/注解处理器依赖）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // S3：Media3 播放内核与系统媒体会话（版本按方案 §8 锁定）
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-session:1.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // S1 白名单策略的 JVM 断言（方案 §10）
    testImplementation("junit:junit:4.13.2")
    // JVM 单测里 android.jar 的 org.json 是 stub，补一个真实实现供模型解析断言使用
    testImplementation("org.json:json:20240303")
}

// ---------------------------------------------------------------- 版本号自增
// 只在真正产出 release APK 后加一：
// - 挂在 packageRelease（assembleRelease / installRelease / bundleRelease 都会经过它）的 finalizer 上；
// - 再用「APK 修改时间晚于本次配置时刻」判断，构建失败时 APK 没被重写 → 版本号不变，不会跳号。
val bumpBuildNumber = tasks.register("bumpBuildNumber") {
    description = "Increment BUILD in version.properties after a successful release build"
    val file = versionFile
    val startedAt = configStartedAt
    val releaseApk = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        .resolve("app-release.apk")
    doLast {
        if (!releaseApk.exists() || releaseApk.lastModified() < startedAt) {
            logger.lifecycle("No new release APK, keep versionName $versionNameText")
            return@doLast
        }
        val props = Properties()
        if (file.exists()) file.inputStream().use { props.load(it) }
        val current = props.getProperty("BUILD")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val next = current + 1
        props.setProperty("BUILD", next.toString())
        file.outputStream().use {
            props.store(it, "自动维护：下一个构建使用的 BUILD（versionName = 0.<MINOR>.<BUILD-PATCH_BASE>）")
        }
        logger.lifecycle("Built versionName $versionNameText -> next build will bump BUILD to $next")
    }
}

tasks.matching { it.name == "packageRelease" }.configureEach {
    finalizedBy(bumpBuildNumber)
}
