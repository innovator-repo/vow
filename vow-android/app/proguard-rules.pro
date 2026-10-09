# Vow proguard rules
-keep class com.vow.app.data.model.** { *; }
-keep class com.vow.app.service.admin.VowDeviceAdminReceiver { *; }
-keepclassmembers class * extends android.app.admin.DeviceAdminReceiver { *; }
