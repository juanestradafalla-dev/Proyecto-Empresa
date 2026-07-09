# Proguard rules for the app module
# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\Almacen\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
# v3.2.0 - Reglas conservadoras para release con Firebase/JSON.
# Mantienen metadatos usados por SDKs y evitan advertencias innecesarias.
-keepattributes Signature,*Annotation*
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class org.json.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
