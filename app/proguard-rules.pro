# =============================================================================
# Echo Android — R8/ProGuard 保守规则
# minifyEnabled true + shrinkResources true 时生效
# 原则：宁可多 keep 也不能漏，导致 release 崩溃
# =============================================================================

# -----------------------------------------------------------------------------
# 一、崩溃栈可读性：保留源文件名和行号映射
# 这两条是调试 release 崩溃的必要前提，缺少后崩溃栈全是混淆符号
# -----------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# -----------------------------------------------------------------------------
# 二、app.yukine.model.* — Java Parcelable 数据模型
# Track 实现了 Parcelable，系统通过反射访问静态字段 CREATOR，
# 以及 describeContents/writeToParcel；若被混淆或删除，运行时会 NoSuchFieldException
# Playlist / RemoteSource / PlaybackQueueState 等 model 类通过 JSON / SQLite 构造，
# 字段名若被混淆会导致反序列化失败
# -----------------------------------------------------------------------------
-keep class app.yukine.model.** { *; }
-keepclassmembers class app.yukine.model.** {
    public static final android.os.Parcelable$Creator CREATOR;
}

# 通用 Parcelable 保护：保留所有实现 Parcelable 的类的 CREATOR 字段
# Android 框架通过 Class.getField("CREATOR") 查找，字段名不能被混淆
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# -----------------------------------------------------------------------------
# 三、Room 数据库实体 (app.yukine.streaming.cache.*)
# Room 在运行时通过反射读取 @Entity / @ColumnInfo 注解和字段名，
# 实体类字段若被混淆会导致数据库升级 / 查询失败
# 注意：room-compiler 生成的 _Impl 类已包含 consumer rules，
# 但手写实体类需显式 keep 以防被 shrink 删除
# -----------------------------------------------------------------------------
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# 显式保留 streaming cache 实体（即使 @Entity 规则已覆盖，双重保险）
-keep class app.yukine.streaming.cache.** { *; }

# -----------------------------------------------------------------------------
# 四、Hilt 依赖注入
# Hilt 已自带 consumer proguard rules（hilt-android AAR 内含），
# 但为防万一，显式保留 @HiltAndroidApp / @AndroidEntryPoint 标注的入口类
# -----------------------------------------------------------------------------
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
}
-keepclasseswithmembers class * {
    @javax.inject.Inject <fields>;
}

# -----------------------------------------------------------------------------
# 五、Media3 / ExoPlayer
# media3-exoplayer、media3-session、media3-datasource 均自带 consumer rules，
# 通常不需要手写；但 EchoPlaybackService（extends MediaSessionService）
# 是系统通过 Intent 启动的，类名不能被混淆
# -----------------------------------------------------------------------------
-keep class app.yukine.playback.EchoPlaybackService { *; }
-keep class app.yukine.playback.PlaybackRestoreReceiver { *; }

# -----------------------------------------------------------------------------
# 六、enum 保护
# 项目中 StreamingProviderName / StreamingAudioQuality / StreamingMediaType 等
# 大量 enum 通过 fromWireName() 做字符串→枚举映射；
# 序列化（JSON / Bundle）也可能用到 name() / ordinal()；
# values() / valueOf() 是反射调用，R8 默认可能删除
# -----------------------------------------------------------------------------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public final java.lang.String name();
    public final int ordinal();
}

# -----------------------------------------------------------------------------
# 七、org.json 手写解析器
# DashboardJson / StreamingGatewayJson 等大量使用 org.json；
# org.json 在 Android SDK 中内置，不会被 shrink，但 JSON key 字符串字面量
# 对应的 Kotlin data class 字段若被混淆需保留（已在 model 规则中覆盖）
# -----------------------------------------------------------------------------
# 无需额外 keep，org.json 是系统库；data class 字段已在上方 model 规则保留

# -----------------------------------------------------------------------------
# 八、Compose — Compose runtime 自带 consumer rules，通常无需手写
# 保留 @Composable 注解标注的类成员防止意外裁剪
# -----------------------------------------------------------------------------
-keepclasseswithmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# -----------------------------------------------------------------------------
# 九、Kotlin 反射 / 协程 / Serialization 通用保护
# kotlinx.coroutines 自带 consumer rules；
# 保留 Kotlin 元数据注解让运行时反射能正确识别 Kotlin 类型
# -----------------------------------------------------------------------------
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses,EnclosingMethod

# Kotlin 内置注解（data class 的 component 函数、copy 等在反射中用到）
-keep class kotlin.Metadata { *; }

# -----------------------------------------------------------------------------
# 十、MainActivity 和入口 Activity/Service/BroadcastReceiver
# 系统组件通过 AndroidManifest.xml 启动，类名不能被混淆
# (Application / Activity / Service / BroadcastReceiver / ContentProvider
#  默认已被 Android Gradle Plugin 的内置规则保护，此处额外显式声明)
# -----------------------------------------------------------------------------
-keep class app.yukine.MainActivity { *; }
-keep class app.yukine.EchoApplication { *; }

# -----------------------------------------------------------------------------
# 十一、诊断工具类 — CrashLogger
# 通过反射/初始化调用，需保留
# -----------------------------------------------------------------------------
-keep class app.yukine.diagnostics.CrashLogger { *; }

# -----------------------------------------------------------------------------
# 十二、调试辅助：输出 mapping 文件（Gradle 已配置，此处作注释说明）
# release build 会自动生成 app/build/outputs/mapping/release/mapping.txt
# 上传到 Firebase Crashlytics 或保存归档，用于解混淆线上崩溃栈
# -----------------------------------------------------------------------------
