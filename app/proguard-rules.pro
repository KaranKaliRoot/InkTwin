# ProGuard rules for InkTwin

# Keep Hilt generated code
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }

# Keep Room entities and DAOs
-keep class com.app.inktwin.data.room.** { *; }

# Keep serialization classes
-keep class kotlinx.serialization.** { *; }

# Keep Compose related
-keep class androidx.compose.** { *; }

# Keep ML Kit
-keep class com.google.mlkit.** { *; }

# Keep CameraX
-keep class androidx.camera.** { *; }

# Keep OpenCV
-keep class org.opencv.** { *; }

# Keep data classes for Room/Serialization
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Prevent obfuscation of enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}