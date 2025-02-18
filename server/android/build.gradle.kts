/*
 * This file was generated by the Gradle "init" task.
 *
 * This generated file contains a sample Java Library project to get you started.
 * For more details take a look at the Java Libraries chapter in the Gradle
 * User Manual available at https://docs.gradle.org/6.3/userguide/java_library_plugin.html
 */
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("android")
	kotlin("plugin.serialization")
	id("com.github.gmazzo.buildconfig")

	id("com.android.application") version "8.0.2"
	id("org.ajoberstar.grgit")
}

kotlin {
	jvmToolchain {
		languageVersion.set(JavaLanguageVersion.of(17))
	}
}
java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(17))
	}
}

tasks.register<Copy>("copyGuiAssets") {
	from(rootProject.layout.projectDirectory.dir("gui/dist"))
	into(layout.projectDirectory.dir("src/main/resources/web-gui"))
	if (inputs.sourceFiles.isEmpty) {
		throw GradleException("You need to run \"pnpm run build\" on the gui folder first!")
	}
}
tasks.preBuild {
	dependsOn(":server:android:copyGuiAssets")
}

tasks.withType<KotlinCompile> {
	kotlinOptions.jvmTarget = "17"
}

// Set compiler to use UTF-8
tasks.withType<JavaCompile> {
	options.encoding = "UTF-8"
}
tasks.withType<Test> {
	systemProperty("file.encoding", "UTF-8")
}
tasks.withType<Javadoc> {
	options.encoding = "UTF-8"
}

allprojects {
	repositories {
		google()
		mavenCentral()
		maven(url = "https://jitpack.io")
	}
}

dependencies {
	implementation(project(":server:core"))

	implementation("commons-cli:commons-cli:1.5.0")
	implementation("org.apache.commons:commons-lang3:3.12.0")

	// Android stuff
	implementation("androidx.appcompat:appcompat:1.6.1")
	implementation("androidx.core:core-ktx:1.10.1")
	implementation("com.google.android.material:material:1.9.0")
	implementation("androidx.constraintlayout:constraintlayout:2.1.4")
	implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
	androidTestImplementation("androidx.test.ext:junit:1.1.5")
	androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
	// For hosting web GUI
	implementation("io.ktor:ktor-server-core:2.3.0")
	implementation("io.ktor:ktor-server-netty:2.3.0")
	implementation("io.ktor:ktor-server-caching-headers:2.3.0")

	// Serial
	implementation("com.github.mik3y:usb-serial-for-android:3.7.0")
}

/**
 * The android block is where you configure all your Android-specific
 * build options.
 */

extra.apply {
	set("gitVersionCode", grgit.tag.list().size)
	set("gitVersionName", grgit.describe(mapOf("tags" to true, "always" to true)))
}
android {
	/**
	 * The app's namespace. Used primarily to access app resources.
	 */

	namespace = "dev.slimevr.android"

	/**
	 * compileSdk specifies the Android API level Gradle should use to
	 * compile your app. This means your app can use the API features included in
	 * this API level and lower.
	 */

	compileSdk = 33

	/**
	 * The defaultConfig block encapsulates default settings and entries for all
	 * build variants and can override some attributes in main/AndroidManifest.xml
	 * dynamically from the build system. You can configure product flavors to override
	 * these values for different versions of your app.
	 */

	packaging {
		resources.excludes.add("META-INF/*")
	}

	defaultConfig {

		// Uniquely identifies the package for publishing.
		applicationId = "dev.slimevr.server"

		// Defines the minimum API level required to run the app.
		minSdk = 26

		// Specifies the API level used to test the app.
		targetSdk = 33

		// Defines the version number of your app.
		versionCode = extra["gitVersionCode"] as? Int

		// Defines a user-friendly version name for your app.
		versionName = extra["gitVersionName"] as? String

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	/**
	 * The buildTypes block is where you can configure multiple build types.
	 * By default, the build system defines two build types: debug and release. The
	 * debug build type is not explicitly shown in the default build configuration,
	 * but it includes debugging tools and is signed with the debug key. The release
	 * build type applies ProGuard settings and is not signed by default.
	 */

	buildTypes {

		/**
		 * By default, Android Studio configures the release build type to enable code
		 * shrinking, using minifyEnabled, and specifies the default ProGuard rules file.
		 */

		getByName("release") {
			isMinifyEnabled = true // Enables code shrinking for the release build type.
			proguardFiles(
				getDefaultProguardFile("proguard-android.txt"),
				"proguard-rules.pro"
			)
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
	kotlinOptions {
		jvmTarget = "17"
	}
}
