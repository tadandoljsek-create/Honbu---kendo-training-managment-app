# Keep Room entities
-keep class com.honbu.app.data.db.** { *; }

# Keep DataStore preference keys
-keep class com.honbu.app.data.preferences.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
