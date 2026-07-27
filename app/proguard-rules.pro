# Shizuku - keep all classes needed for runtime reflection
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.api.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.api.**

# GameShift — keep ALL app classes (R8 strips Shizuku UserService, rotation logic, and
# other runtime-bound components when individual rules are used)
-keep class com.gameshift.app.** { *; }

# GameShift - keep Parcelable classes
-keep class * implements android.os.Parcelable { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
