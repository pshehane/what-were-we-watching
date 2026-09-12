# kotlinx.serialization keeps generated serializers on the serializable types.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class net.shehane.watching.model.** {
    *** Companion;
}
-keepclasseswithmembers class net.shehane.watching.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
