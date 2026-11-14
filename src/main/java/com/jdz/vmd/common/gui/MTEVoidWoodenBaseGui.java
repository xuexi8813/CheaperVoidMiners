package com.jdz.vmd.common.gui;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.jdz.vmd.common.tileentities.MTEVoidWoodenBase;

import gregtech.api.recipe.BasicUIProperties;
import gregtech.common.gui.modularui.singleblock.base.MTEBasicMachineBaseGui;

/**
 * 木制虚空机器的 MUI2 界面：采用标准的单方块机器布局（带进度条的输入槽、输出槽、错误提示区、
 * 电力开关与消音器按钮），并移除了自动输出开关，因为木制机器会将产物保留在机器内部，
 * 直到被玩家取出。
 * <p>
 * 泵通过标准的流体输出槽展示其流体输出仓（{@code properties.maxFluidOutputs = 1}），
 * 挖掘机将维度显示方块槽展示为第二个物品输入槽（{@code properties.maxItemInputs = 2}）。
 */
public class MTEVoidWoodenBaseGui extends MTEBasicMachineBaseGui<MTEVoidWoodenBase> {

    public MTEVoidWoodenBaseGui(MTEVoidWoodenBase machine, BasicUIProperties properties) {
        super(machine, properties);
    }

    @Override
    protected Flow createBottomLeftCornerFlow(ModularPanel panel, PanelSyncManager syncManager) {
        // 简单的角落布局，不包含流体/物品自动输出开关：产物会一直留在机器内直到被取出，
        // 这些开关不会起任何作用，只会造成困惑。
        return Flow.row()
            .coverChildren()
            .verticalCenter()
            .leftRel(0);
    }
}
