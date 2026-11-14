# Retrofit / OkHttp
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn javax.annotation.**

# Gson models and generic type tokens
-keep class com.mira.app.data.model.** { *; }
-keepclassmembers class com.mira.app.data.model.** { <fields>; }
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# AndroidX Security / Keystore
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# Keep line numbers for readable crash reports without exposing source
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
