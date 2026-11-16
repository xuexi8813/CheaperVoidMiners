package com.jdz.vmd.common.gui;

import java.util.ArrayList;
import java.util.List;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.jdz.vmd.common.tileentities.MTEVoidWoodenBase;

import gregtech.api.recipe.BasicUIProperties;
import gregtech.common.gui.modularui.singleblock.base.MTEBasicMachineBaseGui;
import it.unimi.dsi.fastutil.chars.CharList;

/**
 * 木制虚空机器的 MUI2 界面：标准单方块机器布局，移除自动输出开关（产物只会留在机器内）。
 * 泵以流体输出槽展示流体仓（{@code maxFluidOutputs = 1}）。
 * 编程电路由基类链在窗口右下角自动渲染为 GT5u 标准幽灵电路槽（机器 {@code allowSelectCircuit()}
 * 为 true 时生效，无需在本类添加控件）；物品输入网格仅渲染机器真实使用的输入槽。
 */
public class MTEVoidWoodenBaseGui extends MTEBasicMachineBaseGui<MTEVoidWoodenBase> {

    public MTEVoidWoodenBaseGui(MTEVoidWoodenBase machine, BasicUIProperties properties) {
        super(machine, properties);
    }

    @Override
    protected Flow createBottomLeftCornerFlow(ModularPanel panel, PanelSyncManager syncManager) {
        // 不含自动输出开关：产物留在机器内直到取出，开关不起作用只会造成困惑。
        return Flow.row()
            .coverChildren()
            .verticalCenter()
            .leftRel(0);
    }

    /**
     * 物品输入网格只渲染真实使用的输入槽：电路迁移至右下角幽灵槽后，首个输入槽（原电路槽）
     * 已被 {@link MTEVoidWoodenBase#isValidSlot} 封闭，且当前没有任何木制机器使用物品输入槽，
     * 若按基类 maxItemInputs 渲染会得到一个任何物品都放不进、也不再承担功能的死槽位，故返回空矩阵。
     */
    @Override
    protected List<CharList> mapInSlotsToMatrix() {
        return new ArrayList<>();
    }
}
