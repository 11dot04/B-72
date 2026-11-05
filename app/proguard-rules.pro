# Only used when the build is run with -Pminify (release builds are not shrunk by default).
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
# kotlinx.serialization
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class com.b72.keep_canvas.** { *** Companion; }
-keepclasseswithmembers class com.b72.keep_canvas.** { kotlinx.serialization.KSerializer serializer(...); }
# the on-device assistant and sentence encoder reach native code and protobuf by reflection
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.auto.value.**
-dontwarn javax.annotation.**
