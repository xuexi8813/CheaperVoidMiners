package com.jdz.vmd.common.tileentities;

import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.jdz.vmd.api.recipe.VmdRecipeMaps;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.render.TextureFactory;

/**
 * Wooden Void Excavator: a non-electric singleblock machine that slowly pulls plain (non ore) resource
 * blocks of a chosen dimension out of the void, config table driven (see {@link VoidExcavatorResourceTable}).
 * <p>
 * The dimension is selected with a dimension display block in the second input slot (not consumed); an
 * empty slot falls back to the Overworld default resources and foreign items are rejected by the slot.
 * Selection follows the shared wooden rules: a programmed circuit (configuration 1..N) picks the N-th
 * resource of that dimension's table, configuration 0 or no circuit is a weighted random pick (weights =
 * configured table weights), anything beyond N idles.
 * <p>
 * Textures reuse existing GT assets: the wooden casing as body and the ore drill overlay on the front face;
 * no new textures are shipped with this mod.
 */
// Skips the GT regional-name registration; the display name comes from the standard lang keys
// (the annotation is not inherited, so the concrete class carries it explicitly).
@IMetaTileEntity.SkipGenerateName
public class MTEVoidExcavatorWooden extends MTEVoidWoodenBase {

    /** Session wide record of the dimensions already warned about for having an empty resource table. */
    private static final Set<String> WARNED_EMPTY_DIMENSIONS = new HashSet<>();

    public MTEVoidExcavatorWooden(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, "", 2, 1);
    }

    public MTEVoidExcavatorWooden(String aName, ITexture[][][] aTextures) {
        super(aName, new String[] { "" }, aTextures, 2, 1);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MTEVoidExcavatorWooden(this.mName, this.mTextures);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return VmdRecipeMaps.voidExcavatorRecipes;
    }

    @Override
    protected int getDimensionDisplaySlotOffset() {
        return 1;
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
    public int checkRecipe() {
        final String dimensionAbbr = resolveDimensionAbbr();
        final List<VoidExcavatorResourceTable.WeightedResource> resources = VoidExcavatorResourceTable
            .getResources(dimensionAbbr);
        if (resources.isEmpty()) {
            if (WARNED_EMPTY_DIMENSIONS.add(dimensionAbbr)) {
                log().warn(
                    "No excavator resources configured for dimension {}, Wooden Void Excavator idles",
                    VoidExcavatorResourceTable.getDimensionDisplayName(dimensionAbbr));
            }
            return DID_NOT_FIND_RECIPE;
        }

        final int config = readCircuitConfig();
        final VoidExcavatorResourceTable.WeightedResource pick;
        if (config == 0) {
            // No circuit or configuration 0: weighted random over the dimension table.
            pick = VoidExcavatorResourceTable.weightedRandom(resources);
        } else if (config <= resources.size()) {
            pick = resources.get(config - 1);
        } else {
            return DID_NOT_FIND_RECIPE; // Beyond the resource count: idle, mirroring the wooden machine family.
        }

        final ItemStack output = singleOutput(pick.stack());
        // A full output slot or a different resource still inside stalls the machine until the slot drains.
        if (!canOutput(output)) return DID_NOT_FIND_RECIPE;

        mOutputItems[0] = output;
        mMaxProgresstime = getProductionCycleTicks();
        return FOUND_AND_SUCCESSFULLY_USED_RECIPE;
    }

    /** Dimension of the display block; an empty slot falls back to the Overworld default resources. */
    private String resolveDimensionAbbr() {
        final String abbr = VoidExcavatorResourceTable.getDimensionAbbr(mInventory[getDimensionDisplaySlot()]);
        return abbr != null ? abbr : VoidExcavatorResourceTable.DEFAULT_DIMENSION;
    }

    /**************************************************************************
     * Textures: wooden casing body with the GT ore drill overlay on the front face.
     *************************************************************************/

    @Override
    protected ITexture getFrontOverlay() {
        return TextureFactory.builder()
            .addIcon(OVERLAY_FRONT_ORE_DRILL)
            .extFacing()
            .build();
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
