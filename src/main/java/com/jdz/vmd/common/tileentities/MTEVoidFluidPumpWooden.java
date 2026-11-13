package com.jdz.vmd.common.tileentities;

import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_WATER_PUMP;

import java.util.List;

import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.jdz.vmd.api.recipe.VmdRecipeMaps;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.objects.GTUODimension;
import gregtech.api.objects.GTUOFluid;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.render.TextureFactory;

/**
 * Wooden Void Fluid Pump: a non-electric wooden machine that taps the Overworld underground fluids into its
 * internal output tank.
 * <p>
 * Fluid selection follows the shared wooden rules: a programmed circuit (configuration 1..N) picks the
 * N-th entry of the Overworld fluid table, no circuit or configuration 0 is a weighted random pick
 * (weights = configured generation chance), anything beyond N idles. The tank never mixes fluids; a full
 * or incompatible tank stalls the machine until the fluid is drawn out by pipes from any non-main face.
 * <p>
 * Textures reuse existing GT assets: the wooden casing as body, the water pump overlay on the front face;
 * no new textures are shipped with this mod.
 */
// Skips the GT regional-name registration; the display name comes from the standard lang keys
// (the annotation is not inherited, so the concrete class carries it explicitly).
@IMetaTileEntity.SkipGenerateName
public class MTEVoidFluidPumpWooden extends MTEVoidWoodenBase {

    /** Session wide flag so a broken Overworld table is reported once instead of every cycle. */
    private static boolean warnedNoOverworldTable = false;

    public MTEVoidFluidPumpWooden(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, "", 1, 0);
    }

    public MTEVoidFluidPumpWooden(String aName, ITexture[][][] aTextures) {
        super(aName, new String[] { "" }, aTextures, 1, 0);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MTEVoidFluidPumpWooden(this.mName, this.mTextures);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return VmdRecipeMaps.voidFluidDrillerRecipes;
    }

    /**************************************************************************
     * Tank configuration: the output tank is the only tank; nothing is filled from outside.
     *************************************************************************/

    @Override
    public boolean canTankBeFilled() {
        return false;
    }

    @Override
    public boolean canTankBeEmptied() {
        return true;
    }

    @Override
    public int getCapacity() {
        return TANK_CAPACITY_MB;
    }

    @Override
    protected int getFluidOutputSlotCount() {
        return 1;
    }

    /**************************************************************************
     * Production.
     *************************************************************************/

    @Override
    public int checkRecipe() {
        final GTUODimension overworld = VoidFluidTableHelper.getOverworldDimension();
        final List<GTUOFluid> candidates = VoidFluidTableHelper.getFluidCandidates(overworld);
        if (candidates.isEmpty()) {
            if (!warnedNoOverworldTable) {
                warnedNoOverworldTable = true;
                log().warn("Underground fluid table has no usable Overworld entry, Wooden Void Fluid Pump idles");
            }
            return DID_NOT_FIND_RECIPE;
        }

        final int config = readCircuitConfig();
        final GTUOFluid pick;
        if (config >= 1) {
            if (config > candidates.size()) return DID_NOT_FIND_RECIPE; // Out of range configuration: idle.
            pick = candidates.get(config - 1);
        } else {
            pick = VoidFluidTableHelper.weightedRandom(candidates);
        }
        final Fluid fluid = pick.getFluid();
        if (fluid == null) return DID_NOT_FIND_RECIPE;

        final FluidStack output = new FluidStack(fluid, PRODUCTION_AMOUNT_MB);
        // canOutput rejects a full tank and a fluid switch while the tank still holds another fluid.
        if (!canOutput(output)) return DID_NOT_FIND_RECIPE;

        mOutputFluid = output;
        mMaxProgresstime = getProductionCycleTicks();
        return FOUND_AND_SUCCESSFULLY_USED_RECIPE;
    }

    /**************************************************************************
     * Textures.
     *************************************************************************/

    @Override
    protected ITexture getFrontOverlay() {
        return TextureFactory.builder()
            .addIcon(OVERLAY_FRONT_WATER_PUMP)
            .extFacing()
            .build();
    }

    /**************************************************************************
     * Tooltip: static rules plus a live status line for the current fluid selection.
     *************************************************************************/

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("vmd.pump.desc.1"),
            StatCollector.translateToLocal("vmd.pump.desc.2"), StatCollector.translateToLocal("vmd.pump.desc.3"),
            StatCollector.translateToLocal("vmd.pump.desc.4"), StatCollector.translateToLocal("vmd.pump.desc.5"),
            StatCollector.translateToLocalFormatted("vmd.pump.desc.selection", describeCurrentSelection()) };
    }

    /** Localized description of the fluid the machine would currently select. */
    private String describeCurrentSelection() {
        final int config = readCircuitConfig();
        if (config >= 1) {
            final List<GTUOFluid> candidates = VoidFluidTableHelper
                .getFluidCandidates(VoidFluidTableHelper.getOverworldDimension());
            if (config <= candidates.size()) {
                final Fluid fluid = candidates.get(config - 1)
                    .getFluid();
                if (fluid != null) return StatCollector.translateToLocal(fluid.getUnlocalizedName());
            }
            return StatCollector.translateToLocal("vmd.pump.selection.invalid");
        }
        return StatCollector.translateToLocal("vmd.gui.weighted_random");
    }
}
