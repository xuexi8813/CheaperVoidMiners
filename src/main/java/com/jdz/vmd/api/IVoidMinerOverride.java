package com.jdz.vmd.api;

import com.jdz.vmd.common.tileentities.ExVoidMinerFilter.Operation;
import com.jdz.vmd.common.tileentities.ExVoidMinerFilter.View;

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

    /** Refreshes the server's EX ore table while the controller is stopped or its filter is closed. */
    void vmd$refreshExFilter();

    /** Refreshes the EX view on the server and returns its rows, revision and current selection. */
    View vmd$getExFilterView();

    /** Applies an EX filter action only if its revision and row still identify the current server table. */
    void vmd$applyExFilterAction(long revision, int row, Operation operation);
}
