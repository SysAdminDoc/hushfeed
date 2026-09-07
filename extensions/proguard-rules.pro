-dontobfuscate
-dontoptimize
-keepattributes *
# Extension classes are called from injected target bytecode and by reflection.
# Keep the extension namespace intact while allowing unrelated Morphe classes to shrink.
-keep class app.morphe.extension.** {
  *;
}
-keep class com.google.** {
  *;
}
-keep class com.eclipsesource.v8.** {
  *;
}
# Proguard can strip away kotlin intrinsics methods that are used by extension Kotlin code. Unclear why.
-keep class kotlin.jvm.internal.Intrinsics {
    public static *;
}
-dontwarn javax.lang.model.element.Modifier
