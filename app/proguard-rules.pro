# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep ARCore classes
-keep class com.google.ar.** { *; }
-dontwarn com.google.ar.**

# Keep Room entities and DAOs
-keep class com.wifiradarx.app.data.db.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

# Keep MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# Keep Gson serialized classes
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Keep application class
-keep class com.wifiradarx.app.WiFiRadarXApplication { *; }

# Keep all activities, views, and intelligence classes
-keep class com.wifiradarx.app.ui.** { *; }
-keep class com.wifiradarx.app.intelligence.** { *; }
-keep class com.wifiradarx.app.ar.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
