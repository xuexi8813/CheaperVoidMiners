package com.jdz.vmd.api;

/**
 * Void Miner（虚空矿机）维度覆写能力的公开契约，由 {@code VMDVoidMinerBaseMixin}
 * 注入 {@code bwcrossmod.galacticgreg.MTEVoidMinerBase} 后实现。
 *
 * <p>
 * 动机：mixin 类在运行时不可加载、不可直接引用，因此以该接口作为其他代码
 * （GUI mixin 及潜在外部调用方）访问注入字段与逻辑的稳定入口。
 * 移植自 Void-Miner-Tweak-Mod 的 {@code IVMTweakOverride}，方法前缀统一为 vmd$。
 */
public interface IVoidMinerOverride {

    /**
     * @return 当前警告消息（lang 键原文），无警告时为空字符串。
     */
    String vmd$getWarning();

    /**
     * @return 当前维度覆写的可显示原始数据："" 表示无覆写；"!" 前缀 + lang 键表示
     *         覆写失败的警告；其余为维度缩写（如 "Ow"、"Ne"、"EA"），本地化由客户端完成，
     *         避免在服务端线程加载客户端专属类。
     */
    String vmd$getOverrideDisplayText();

    /**
     * @return 维度覆写每次变化时自增的单调计数器，供 GUI 检测变化并刷新过滤面板。
     */
    int vmd$getDimChangeVersion();

    /**
     * 当前每秒矿石产出（产能显示口径，含惰性气体加成与电压产量加成），供 EX 虚空矿机 GUI 的
     * 实时产能行轮询。
     *
     * <p>
     * 动机：产能三要素中的惰性气体加成存储于上游 MTEVoidMinerBase 私有字段 multiplier
     * （上游 handleFluidConsumption 每周期写入），仅注入该类的 mixin 可读，而 mixin 类
     * 运行时不可直接引用，故以本接口为 GUI 访问入口，由注入类桥接到
     * MTEVoidMinerEXBase 的计算方法。
     *
     * @return 每秒产出矿石数（long 显示不截断）；0 表示当前机器不支持产能显示（非 EX 系列机型），
     *         GUI 行据此隐藏。
     */
    long vmd$getCurrentOutputPerSecond();
}
