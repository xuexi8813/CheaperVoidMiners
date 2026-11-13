package com.jdz.vmd.common.tileentities;

import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_WATER_PUMP;

import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.jdz.vmd.api.recipe.VmdRecipeMaps;

import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.render.TextureFactory;

/**
 * Wooden Void Fluid Pump: a non-electric singleblock machine that slowly taps the Overworld underground
 * fluid table into its internal buffer and pushes it into the neighbor at its front face.
 * <p>
 * Fluid selection follows the shared wooden rules: a programmed circuit (configuration 1..N) picks the
 * N-th entry of the Overworld fluid table, no circuit or configuration 0 is a weighted random pick
 * (weights = configured generation chance).
 * <p>
 * Textures reuse existing GT assets: the primitive wooden casing as body, the water pump overlay on the
 * front face; no new textures are shipped with this mod.
 */
// Skips the GT regional-name registration; the display name comes from the standard lang keys
// (the annotation is not inherited, so the concrete class carries it explicitly).
@IMetaTileEntity.SkipGenerateName
public class MTEVoidFluidPumpWooden extends MTEVoidWoodenBase {

    public MTEVoidFluidPumpWooden(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, "");
    }

    public MTEVoidFluidPumpWooden(String aName, ITexture[][][] aTextures) {
        super(aName, new String[] { "" }, aTextures);
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
     * Textures: wooden casing body with the GT water pump overlay on the front face.
     *************************************************************************/

    @Override
    public ITexture[][][] getTextureSet(ITexture[] aTextures) {
        final ITexture body = TextureFactory.of(
            ItemList.WoodenCasing.getBlock(),
            ItemList.WoodenCasing.get(0)
                .getItemDamage());
        final ITexture front = TextureFactory.builder()
            .addIcon(OVERLAY_FRONT_WATER_PUMP)
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
     * Tooltip: static rules plus a live status line for the current fluid selection.
     *************************************************************************/

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("vmd.pump.desc.1"),
            StatCollector.translateToLocal("vmd.pump.desc.2"), StatCollector.translateToLocal("vmd.pump.desc.3"),
            StatCollector.translateToLocal("vmd.pump.desc.4"), StatCollector.translateToLocal("vmd.pump.desc.5"),
            StatCollector.translateToLocalFormatted("vmd.pump.desc.selection", describeCurrentSelection()) };
    }
}
