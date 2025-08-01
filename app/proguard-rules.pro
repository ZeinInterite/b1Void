# Правила ProGuard для оптимизации B1Void на слабых устройствах

# Основные правила оптимизации
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

# Сохраняем основные классы приложения
-keep class com.example.b1void.** { *; }
-keep class com.example.b1void.activities.** { *; }
-keep class com.example.b1void.adapters.** { *; }
-keep class com.example.b1void.utils.** { *; }
-keep class com.example.b1void.models.** { *; }

# Glide оптимизация
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind();
}

# CameraView оптимизация
-keep class com.otaliastudios.cameraview.** { *; }
-dontwarn com.otaliastudios.cameraview.**

# Dropbox оптимизация
-keep class com.dropbox.** { *; }
-dontwarn com.dropbox.**

# Firebase оптимизация
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Kotlin оптимизация
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# Coroutines оптимизация
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Удаляем неиспользуемые классы
-dontwarn android.support.**
-dontwarn androidx.**
-dontwarn org.jetbrains.annotations.**

# Оптимизация для слабых устройств
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions

# Удаляем логи в релизе
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# Оптимизация строк
-optimizations !code/removal/duplicate
-optimizations !code/removal/unused

# Сохраняем нативные методы
-keepclasseswithmembernames class * {
    native <methods>;
}

# Сохраняем методы с аннотациями
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Оптимизация для работы с файлами
-keep class java.io.** { *; }
-keep class java.nio.** { *; }

# Оптимизация для работы с изображениями
-keep class android.graphics.** { *; }
-keep class android.media.** { *; }

# Сохраняем ViewBinding
-keep class * implements androidx.viewbinding.ViewBinding {
    public static *** inflate(android.view.LayoutInflater);
    public static *** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
    public static *** bind(android.view.View);
}

# Оптимизация для RecyclerView
-keep class androidx.recyclerview.widget.** { *; }
-keep class * extends androidx.recyclerview.widget.RecyclerView$ViewHolder {
    public <init>(android.view.View);
}

# Оптимизация для WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Оптимизация для DataStore
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# Удаляем неиспользуемые ресурсы
-keep class **.R$* {
    public static <fields>;
}

# Оптимизация для слабых устройств - удаляем тяжелые операции
-assumenosideeffects class java.lang.System {
    public static void gc();
    public static void runFinalization();
}

# Сохраняем только необходимые методы для работы с памятью
-keepclassmembers class * {
    public void onTrimMemory(int);
    public void onLowMemory();
}

# Оптимизация для ZIP операций
-keep class net.lingala.zip4j.** { *; }
-dontwarn net.lingala.zip4j.**

# Оптимизация для GSON
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Оптимизация для Picasso
-keep class com.squareup.picasso.** { *; }
-dontwarn com.squareup.picasso.**

# Удаляем неиспользуемые атрибуты
-keepattributes !LocalVariableTable
-keepattributes !LineNumberTable

# Оптимизация для слабых устройств - минимизируем размер
-repackageclasses ''
-allowaccessmodification