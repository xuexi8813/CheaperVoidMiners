package com.jdz.vmd.common.gui;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.jdz.vmd.common.tileentities.MTEVoidWoodenBase;

import gregtech.api.recipe.BasicUIProperties;
import gregtech.common.gui.modularui.singleblock.base.MTEBasicMachineBaseGui;

/**
 * 木制虚空机器的 MUI2 界面：标准单方块机器布局，移除自动输出开关（产物只会留在机器内）。
 * 泵以流体输出槽展示流体仓（{@code maxFluidOutputs = 1}），
 * 挖掘机将维度显示方块槽展示为第二个物品输入槽（{@code maxItemInputs = 2}）。
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
}
