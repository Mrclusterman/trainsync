# kotlinx.serialization y Ktor usan reflexión limitada: conservar sus metadatos
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class io.github.mrclusterman.trainsync.**$$serializer { *; }
-keepclassmembers class io.github.mrclusterman.trainsync.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
