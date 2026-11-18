import com.ketabkhan.build.VazirmatnFontValidator
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

abstract class DecodeVazirmatnFontsTask : DefaultTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val fontSrcDir: DirectoryProperty

  @get:OutputDirectory
  abstract val fontOutDir: DirectoryProperty

  @TaskAction
  fun decode() {
    val srcDir = fontSrcDir.get().asFile
    val outDir = fontOutDir.get().asFile

    if (!srcDir.exists() || !srcDir.isDirectory) {
      throw GradleException(
        "خطا در اعتبارسنجی فونت | فایل مشکل‌دار: app/fontsrc | نوع مشکل: پوشهٔ منبع فونت وجود ندارد | " +
          "اقدام لازم maintainer: پوشهٔ app/fontsrc را بسازید و سه فایل .b64 فونت وزیرمتن " +
          "(vazirmatn_regular.b64، vazirmatn_medium.b64، vazirmatn_bold.b64) را در آن قرار دهید. Waiting for fonts placed."
      )
    }

    val decodedEntries = mutableListOf<Triple<File, ByteArray, VazirmatnFontValidator.ValidatedFontInfo>>()
    val errors = mutableListOf<String>()

    for (baseName in VazirmatnFontValidator.REQUIRED_FONT_BASES) {
      val candidates = listOf(
        File(srcDir, "$baseName.b64"),
        File(srcDir, "$baseName.ttf.b64"),
      )
      val sourceFile = candidates.firstOrNull { it.isFile }
      if (sourceFile == null) {
        errors += "فایل مشکل‌دار: app/fontsrc/$baseName.b64 | نوع مشکل: فایل یافت نشد | " +
          "اقدام لازم maintainer: فایل رسمی $baseName.ttf را با «base64 -w 0» تبدیل کرده و در app/fontsrc/$baseName.b64 ذخیره کنید."
        continue
      }

      try {
        val rawContent = sourceFile.readText(Charsets.UTF_8)
        val result = VazirmatnFontValidator.decodeAndValidate(rawContent, "app/fontsrc/${sourceFile.name}")
        decodedEntries += Triple(File(outDir, "$baseName.ttf"), result.bytes, result.info)
      } catch (e: VazirmatnFontValidator.FontValidationException) {
        errors += e.message ?: "خطای نامشخص در ${sourceFile.name}"
      }
    }

    if (errors.isNotEmpty()) {
      throw GradleException(
        "بیلد به دلیل نامعتبر بودن یا نبود فونت‌های وزیرمتن متوقف شد:\n" +
          errors.joinToString(separator = "\n") +
          "\n— Waiting for fonts placed."
      )
    }

    if (!outDir.exists() && !outDir.mkdirs()) {
      throw GradleException("امکان ساخت پوشهٔ خروجی فونت در ${outDir.absolutePath} وجود ندارد.")
    }

    for ((targetFile, bytes, info) in decodedEntries) {
      val existingIsValidAndIdentical = if (targetFile.exists()) {
        try {
          val existingBytes = targetFile.readBytes()
          VazirmatnFontValidator.validateFontBytes(existingBytes, targetFile.name)
          existingBytes.contentEquals(bytes)
        } catch (_: RuntimeException) {
          false
        }
      } else {
        false
      }

      if (!existingIsValidAndIdentical) {
        targetFile.writeBytes(bytes)
      }

      logger.lifecycle(
        "Vazirmatn font validated: {} -> {} bytes, signature={}, tables={}",
        targetFile.name,
        info.sizeBytes,
        info.signatureLabel,
        info.tableCount,
      )
    }
  }
}

abstract class VerifyReleaseSigningTask : DefaultTask() {
  @get:Input
  abstract val releaseSigningReady: Property<Boolean>

  @get:Input
  abstract val missingItems: ListProperty<String>

  @TaskAction
  fun verify() {
    if (!releaseSigningReady.get()) {
      val details = missingItems.get().joinToString(separator = "\n  - ")
      throw GradleException(
        "امضای ریلیز پیکربندی نشده است و تولید خروجی ریلیز بدون امضای معتبر مجاز نیست " +
          "(هیچ fallback به keystore دیباگ یا کلید قالب وجود ندارد).\n" +
          "موارد ناقص:\n  - $details\n" +
          "متغیرهای محیطی الزامی (بخش ۶.۲ AGENTS.md):\n" +
          "  1) SIGNING_STORE_FILE (مسیر فایل keystore معتبر، خواندنی و غیرخالی)\n" +
          "  2) SIGNING_STORE_PASSWORD\n" +
          "  3) SIGNING_KEY_ALIAS\n" +
          "  4) SIGNING_KEY_PASSWORD\n" +
          "راه تأمین: این چهار مقدار را در CI از طریق GitHub Secrets " +
          "(KEYSTORE_BASE64، KEYSTORE_PASSWORD، KEY_ALIAS، KEY_PASSWORD) در گام آماده‌سازی امضا " +
          "یا به‌صورت متغیر محیطی امن در محیط بیلد تنظیم کنید."
      )
    }
  }
}

val signingStoreFilePath = providers.environmentVariable("SIGNING_STORE_FILE").orNull?.trim().orEmpty()
val signingStorePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull.orEmpty()
val signingKeyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").orNull?.trim().orEmpty()
val signingKeyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull.orEmpty()

val releaseStoreFile = signingStoreFilePath.takeIf { it.isNotEmpty() }?.let { file(it) }
val isReleaseStoreValid =
  releaseStoreFile != null &&
    releaseStoreFile.isFile &&
    releaseStoreFile.canRead() &&
    releaseStoreFile.length() > 0L

val isReleaseSigningReady =
  signingStoreFilePath.isNotEmpty() &&
    isReleaseStoreValid &&
    signingStorePassword.isNotEmpty() &&
    signingKeyAlias.isNotEmpty() &&
    signingKeyPassword.isNotEmpty()

val missingReleaseSigningItems = buildList {
  if (signingStoreFilePath.isEmpty()) {
    add("SIGNING_STORE_FILE (تنظیم نشده)")
  } else if (!isReleaseStoreValid) {
    add("SIGNING_STORE_FILE (فایل keystore در مسیر مشخص‌شده یافت نشد، خواندنی نیست یا خالی است)")
  }
  if (signingStorePassword.isEmpty()) add("SIGNING_STORE_PASSWORD (تنظیم نشده)")
  if (signingKeyAlias.isEmpty()) add("SIGNING_KEY_ALIAS (تنظیم نشده)")
  if (signingKeyPassword.isEmpty()) add("SIGNING_KEY_PASSWORD (تنظیم نشده)")
}

val propVersionCode = providers.gradleProperty("versionCode").orNull?.trim()
val envVersionCode = providers.environmentVariable("VERSION_CODE").orNull?.trim()
val ciVersionCode = if (!propVersionCode.isNullOrEmpty()) {
  propVersionCode.toIntOrNull()?.takeIf { it > 0 }
    ?: throw GradleException("مقدار -PversionCode نامعتبر است: '$propVersionCode' (باید عدد صحیح مثبت باشد).")
} else {
  envVersionCode?.takeIf { it.isNotEmpty() }?.toIntOrNull()?.takeIf { it > 0 }
}

val propVersionName = providers.gradleProperty("versionName").orNull?.trim()
val envVersionName = providers.environmentVariable("VERSION_NAME").orNull?.trim()
val ciVersionName = when {
  !propVersionName.isNullOrEmpty() -> propVersionName
  !envVersionName.isNullOrEmpty() -> envVersionName
  else -> null
}

android {
  namespace = "com.ketabkhan.app"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.ketabkhan.app"
    minSdk = 26
    targetSdk = 36
    versionCode = ciVersionCode ?: 1
    versionName = ciVersionName ?: "0.1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    ndk {
      abiFilters += listOf("arm64-v8a", "x86_64")
    }
  }

  sourceSets {
    getByName("test") {
      java.directories.add("../buildSrc/src/main/java")
    }
  }

  splits {
    abi {
      isEnable = true
      reset()
      include("arm64-v8a", "x86_64")
      isUniversalApk = true
    }
  }

  signingConfigs {
    if (isReleaseSigningReady) {
      create("release") {
        storeFile = releaseStoreFile
        storePassword = signingStorePassword
        keyAlias = signingKeyAlias
        keyPassword = signingKeyPassword
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      isShrinkResources = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (isReleaseSigningReady) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
    debug {
      isMinifyEnabled = false
      isShrinkResources = false
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

val decodeVazirmatnFonts = tasks.register<DecodeVazirmatnFontsTask>("decodeVazirmatnFonts") {
  group = "resources"
  description = "Decodes and validates Base64 Vazirmatn font files from app/fontsrc into app/src/main/res/font"
  fontSrcDir.set(layout.projectDirectory.dir("fontsrc"))
  fontOutDir.set(layout.projectDirectory.dir("src/main/res/font"))
}

tasks.named("preBuild").configure {
  dependsOn(decodeVazirmatnFonts)
}

val verifyReleaseSigning = tasks.register<VerifyReleaseSigningTask>("verifyReleaseSigning") {
  group = "verification"
  description = "Fails release artifact builds at execution time when SIGNING_* environment variables or keystore file are missing."
  releaseSigningReady.set(isReleaseSigningReady)
  missingItems.set(missingReleaseSigningItems)
}

tasks.matching { task ->
  task.name == "preReleaseBuild" ||
    task.name == "assembleRelease" ||
    task.name == "bundleRelease" ||
    task.name == "packageRelease" ||
    task.name == "packageReleaseBundle" ||
    task.name == "signReleaseBundle" ||
    task.name == "createReleaseApkListingFileRedirect" ||
    (task.name.endsWith("Release") &&
      (task.name.startsWith("assemble") ||
        task.name.startsWith("bundle") ||
        task.name.startsWith("package") ||
        task.name.startsWith("sign")))
}.configureEach {
  dependsOn(verifyReleaseSigning)
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.pdfbox.android)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)

  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
