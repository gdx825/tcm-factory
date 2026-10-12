# ==================================================================
#  中医工厂 · ProGuard / R8 规则
# ==================================================================
#
# 当前构建**默认未启用**压缩混淆（见 app/build.gradle.kts：
# isMinifyEnabled = false、isShrinkResources = false）。
#
# 为什么准备好了规则却不开：
#   混淆引发的问题（反射找不到类、序列化器被裁掉、Room 列名映射错位）
#   只在**运行时**暴露。构建能成功、单元测试能全绿，但用户一进某个页面
#   就崩 —— 这类问题必须逐页真机回归才能发现。在缺少真机回归条件时，
#   发布一个「体积小两三成、但可能随机崩溃」的包，风险明显大于收益。
#
# 需要瘦身时，把 build.gradle.kts 里两个开关改为 true，然后按此清单回归：
#   1. 启动 → 背诵首页（Hilt 注入 + Room 查询）
#   2. 背卡页滑动一次（TcmSrs 为纯逻辑，理论上不受影响）
#   3. 古籍导入一份 TXT（Room 事务 + 文本管线）
#   4. 刷题一轮 + 错题本（题库 JSON 反序列化 —— 最易出问题）
#   5. 知识库页检查更新（网络 + 知识包 JSON 反序列化）
#   6. 卡片管理导出一次（知识包序列化）
#   7. 学习统计页（无反射依赖，可作为对照）
#   8. 触发一次定时提醒（Worker 反射实例化）
# ==================================================================

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, SourceFile, LineNumberTable

# ---------------- kotlinx.serialization ----------------
# R8 看不到编译器插件生成的 serializer，必须整体保留。
# 漏掉这条的典型症状：某个数据类能正常创建，但读写 JSON 时抛
# SerializationException: Serializer for class 'X' is not found。
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.tcmfactory.**$$serializer { *; }
-keepclassmembers class com.tcmfactory.** {
    *** Companion;
}
-keepclasseswithmembers class com.tcmfactory.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------------- Room ----------------
# Room 的 consumer rules 覆盖了生成代码，但实体/DAO 本身要显式保留，
# 否则字段重命名会让 Cursor 按列名取值时错位（不报错，只是数据错了）。
-keep class com.tcmfactory.core.database.entity.** { *; }
-keep class com.tcmfactory.core.database.dao.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# ---------------- Hilt / Dagger ----------------
# Hilt 自带 consumer rules，这里补充入口点与生成组件的保留。
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep,allowobfuscation @interface dagger.hilt.android.lifecycle.HiltViewModel
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }

# ---------------- WorkManager ----------------
# Worker 由 WorkManager 通过反射按类名实例化，构造签名必须保留。
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ---------------- Compose ----------------
# Compose 的 consumer rules 已覆盖运行时；保留 @Composable 元数据以防
# 编译器生成的合成方法被裁。
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable <methods>;
}

# ---------------- OkHttp / Okio ----------------
# 这些是可选依赖平台实现，缺失不影响主流程，抑制警告即可。
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------------- 调试可读性 ----------------
# 保留行号便于线上崩溃栈定位；只抹掉源文件名，不隐藏行号。
-renamesourcefileattribute SourceFile
