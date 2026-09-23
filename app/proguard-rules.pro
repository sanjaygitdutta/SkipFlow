# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends android.app.Service
-keep class com.adskiper.skipflow.service.** { *; }
-keep class com.adskiper.skipflow.data.** { *; }
