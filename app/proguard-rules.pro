# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.personalfinances.** {
    *** Companion;
}
-keepclasseswithmembers class com.personalfinances.** {
    kotlinx.serialization.KSerializer serializer(...);
}
