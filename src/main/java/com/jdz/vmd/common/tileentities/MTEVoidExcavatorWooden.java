package com.jdz.vmd.common.tileentities;

import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;

import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;

/**
 * Wooden Void Excavator: a non-electric singleblock machine that slowly pulls plain (non ore) resource
 * blocks of a chosen dimension out of the void, config table driven (see {@link VoidExcavatorResourceTable}).
 * <p>
 * The dimension is selected with a dimension display block in the dedicated display slot (not consumed);
 * an empty slot falls back to the Overworld default resources and foreign items are rejected by the slot.
 * Selection follows the shared wooden rules: a programmed circuit (configuration 1..N) picks the N-th
 * resource of that dimension's table, configuration 0 or no circuit is a weighted random pick (weights =
 * configured table weights), anything beyond N idles.
 * <p>
 * Textures reuse existing GT assets: the wooden casing as body and the ore drill overlay on the front face;
 * no new textures are shipped with this mod.
 */
public class MTEVoidExcavatorWooden extends MTEVoidWoodenBase {

    /**
     * {@link gregtech.api.metatileentity.implementations.MTEBasicTank} reserves slot 0 (input, doubles as the
     * circuit slot) and slot 1 (output); its third slot is a display only slot, so the excavator re-enables
     * it as the dimension display block slot.
     */
    public static final int DIMENSION_DISPLAY_SLOT = 2;

    /** Session wide record of the dimensions already warned about for having an empty resource table. */
    private static final Set<String> WARNED_EMPTY_DIMENSIONS = new HashSet<>();

    public MTEVoidExcavatorWooden(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, "");
    }

    public MTEVoidExcavatorWooden(String aName, ITexture[][][] aTextures) {
        super(aName, new String[] { "" }, aTextures);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MTEVoidExcavatorWooden(this.mName, this.mTextures);
    }

    /** Slot of the dimension display block; the block is configuration, not a consumable. */
    public int getDimensionDisplaySlot() {
        return DIMENSION_DISPLAY_SLOT;
    }

    /**************************************************************************
     * Slot configuration.
     *************************************************************************/

    @Override
    public boolean isValidSlot(int aIndex) {
        // The tank base marks the third slot as display only; the excavator needs it as a real slot.
        return aIndex == getDimensionDisplaySlot() || super.isValidSlot(aIndex);
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        if (aIndex == getDimensionDisplaySlot()) {
            // Illegal items are ignored: only dimension display blocks may enter the display slot.
            return VoidExcavatorResourceTable.getDimensionAbbr(aStack) != null;
        }
        return super.allowPutStack(aBaseMetaTileEntity, aIndex, side, aStack);
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        // The display block is configuration and can be retrieved again.
        return aIndex == getDimensionDisplaySlot() || super.allowPullStack(aBaseMetaTileEntity, aIndex, side, aStack);
    }

    /**************************************************************************
     * Production.
     *************************************************************************/

    @Override
    protected void tryProduce() {
        final String dimensionAbbr = resolveDimensionAbbr();
        final List<VoidExcavatorResourceTable.WeightedResource> resources = VoidExcavatorResourceTable
            .getResources(dimensionAbbr);
        if (resources.isEmpty()) {
            if (WARNED_EMPTY_DIMENSIONS.add(dimensionAbbr)) {
                log().warn(
                    "No excavator resources configured for dimension {}, Wooden Void Excavator idles",
                    VoidExcavatorResourceTable.getDimensionDisplayName(dimensionAbbr));
            }
            return;
        }

        final int config = readCircuitConfig();
        final VoidExcavatorResourceTable.WeightedResource pick;
        if (config == 0) {
            // No circuit or configuration 0: weighted random over the dimension table.
            pick = VoidExcavatorResourceTable.weightedRandom(resources);
        } else if (config <= resources.size()) {
            pick = resources.get(config - 1);
        } else {
            return; // Beyond the resource count: idle, mirroring the wooden machine family.
        }

        // addStackToSlot merges into a same stack up to its max size; a blocked slot simply skips the cycle
        // until the output slot drains through the front face.
        if (getBaseMetaTileEntity().addStackToSlot(getOutputSlot(), GTUtility.copyAmount(1, pick.stack()))) {
            markDirty();
        }
    }

    /** Dimension of the display block; an empty slot falls back to the Overworld default resources. */
    private String resolveDimensionAbbr() {
        final String abbr = VoidExcavatorResourceTable.getDimensionAbbr(mInventory[getDimensionDisplaySlot()]);
        return abbr != null ? abbr : VoidExcavatorResourceTable.DEFAULT_DIMENSION;
    }

    /**************************************************************************
     * GUI: extra slot widget for the dimension display block.
     *************************************************************************/

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        super.addUIWidgets(builder, buildContext);
        builder.widget(
            new SlotWidget(inventoryHandler, getDimensionDisplaySlot()).setBackground(getGUITextureSet().getItemSlot())
                .setPos(97, 16)
                .addTooltip(StatCollector.translateToLocal("vmd.excavator.gui.dimension_slot")));
    }

    /**************************************************************************
     * Textures: wooden casing body with the GT ore drill overlay on the front face.
     *************************************************************************/

    @Override
    public ITexture[][][] getTextureSet(ITexture[] aTextures) {
        final ITexture body = TextureFactory.of(
            ItemList.WoodenCasing.getBlock(),
            ItemList.WoodenCasing.get(0)
                .getItemDamage());
        final ITexture front = TextureFactory.builder()
            .addIcon(OVERLAY_FRONT_ORE_DRILL)
            .extFacing()
            .build();

        final ITexture[][][] rTextures = new ITexture[2][17][];
        for (byte c = -1; c < 16; c++) {
            rTextures[0][c + 1] = new ITexture[] { body };
            rTextures[1][c + 1] = new ITexture[] { body, front };
        }
        return rTextures;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        return mTextures[side == aFacing ? 1 : 0][colorIndex + 1];
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return true;
    }

    /**************************************************************************
     * Tooltip: static rules plus a live status line for the current selection.
     *************************************************************************/

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("vmd.excavator.desc.1"),
            StatCollector.translateToLocal("vmd.excavator.desc.2"),
            StatCollector.translateToLocal("vmd.excavator.desc.3"),
            StatCollector.translateToLocal("vmd.excavator.desc.4"),
            StatCollector.translateToLocal("vmd.excavator.desc.5"),
            StatCollector.translateToLocalFormatted("vmd.excavator.desc.selection", describeCurrentSelection()) };
    }

    /** Human readable name of the resource the inserted circuit currently selects. */
    @Override
    protected String describeCurrentSelection() {
        final String dimensionAbbr = resolveDimensionAbbr();
        final List<VoidExcavatorResourceTable.WeightedResource> resources = VoidExcavatorResourceTable
            .getResources(dimensionAbbr);

        final int config = readCircuitConfig();
        if (config >= 1 && config <= resources.size()) {
            return resources.get(config - 1)
                .stack()
                .getDisplayName();
        }
        if (config > resources.size()) {
            return StatCollector.translateToLocal("vmd.pump.selection.invalid");
        }
        return StatCollector.translateToLocal("vmd.gui.weighted_random");
    }
}
