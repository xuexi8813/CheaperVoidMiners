package com.jdz.vmd.common.gui;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.jdz.vmd.common.tileentities.MTEVoidWoodenBase;

import gregtech.api.recipe.BasicUIProperties;
import gregtech.common.gui.modularui.singleblock.base.MTEBasicMachineBaseGui;

/**
 * MUI2 window of the wooden void machines: the standard singleblock machine layout (input slots with the
 * progress bar, output slots, error area, power switch and muffler buttons) with the auto-output toggles
 * removed, because the wooden machines keep their output inside until it is taken out.
 * <p>
 * The pump shows its fluid output tank through the standard fluid output slot
 * ({@code properties.maxFluidOutputs = 1}), the excavator shows its dimension display block slot as the
 * second item input slot ({@code properties.maxItemInputs = 2}).
 */
public class MTEVoidWoodenBaseGui extends MTEBasicMachineBaseGui<MTEVoidWoodenBase> {

    public MTEVoidWoodenBaseGui(MTEVoidWoodenBase machine, BasicUIProperties properties) {
        super(machine, properties);
    }

    @Override
    protected Flow createBottomLeftCornerFlow(ModularPanel panel, PanelSyncManager syncManager) {
        // Plain corner flow without the fluid/item auto-output toggles: outputs stay inside the machine
        // until they are taken out, so the toggles would have no effect and only confuse.
        return Flow.row()
            .coverChildren()
            .verticalCenter()
            .leftRel(0);
    }
}
