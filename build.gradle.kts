plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// NEI 传递引入的 antlr4 工具由 Java 17 编译，Java 8 开发环境的 FML 扫描时会崩溃。
// 仅排除工具，保留 Java 8 可用的 antlr4-runtime。
configurations.all {
    exclude(mapOf("group" to "org.antlr", "module" to "antlr4"))
}
