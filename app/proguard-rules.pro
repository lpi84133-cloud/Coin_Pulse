# Readable stack traces for crash reports.
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod

# ── The bridge the page talks to ─────────────────────────────────────────────
# These methods are called by name from JavaScript. R8 has no way to see that,
# and the failure is silent: release builds work everywhere except the one
# feature that needs the bridge, which is the keyboard.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ── Entry points the system creates by name ──────────────────────────────────
-keep class com.coinpulse.coinpulsegame.pulsegate.PulseApp
-keep class com.coinpulse.coinpulsegame.pulsegate.EntryGate
-keep class com.coinpulse.coinpulsegame.dispatchbox.PushGate
-keep class com.coinpulse.coinpulsegame.deck.WebDeck
-keep class com.coinpulse.coinpulsegame.deck.PermitDeck
-keep class com.coinpulse.coinpulsegame.deck.LinkLostDeck
-keep class com.coinpulse.coinpulsegame.MainActivity
-keep class com.coinpulse.coinpulsegame.WebViewActivity

# ── Firebase ─────────────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ── Attribution ──────────────────────────────────────────────────────────────
-keep class com.appsflyer.** { *; }
-keep class com.android.installreferrer.** { *; }
-dontwarn com.appsflyer.**

# ── Logging ──────────────────────────────────────────────────────────────────
# Everything the shell writes already sits behind a BuildConfig.DEBUG check that
# folds away here. What this removes is the platform-level chatter underneath.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
