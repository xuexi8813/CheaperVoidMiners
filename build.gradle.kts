plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// 开发运行时排除 Java 17 编译的 antlr4 工具本体（NEI 2.8.175 的传递依赖）：
// Java 8 裸 FML 的旧 ASM 扫描到它的 class 文件即抛 "probably a corrupt zip" LoaderException，
// 致 runClient 崩溃。保留 antlr4-runtime（Java 8 可读）。玩家整包不受影响（运行环境 Java 17-26 + lwjgl3ify）。
configurations.all {
    exclude(mapOf("group" to "org.antlr", "module" to "antlr4"))
}
