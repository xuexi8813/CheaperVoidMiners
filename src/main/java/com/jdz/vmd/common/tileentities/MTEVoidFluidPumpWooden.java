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
 * 木制虚空流体泵：无电力木制机器，将主世界地下流体抽入内部输出储罐。
 * 电路选择：必须放入编程电路（配置 1..N）选定主世界流体候选列表第 N 项流体
 * （候选经 VoidFluidTableHelper 规范排序，与 NEI 展示同源同序）；
 * 未插入电路、配置 0 或超出范围时静默待机。
 * 储罐不混流体；罐满或流体不兼容时停滞，直到被任意非正面方向的管道抽出。
 */
// 跳过 GT 区域名注册，显示名来自 lang 键。
@IMetaTileEntity.SkipGenerateName
public class MTEVoidFluidPumpWooden extends MTEVoidWoodenBase {

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

    // ==== 储罐配置：唯一储罐即输出储罐，不从外部注入 ====

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

    // ==== 生产 ====

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

        // 必须由编程电路（配置 1..N）选定流体：未插入电路、配置 0 或超出范围时静默待机。
        final int config = readCircuitConfig();
        if (config < 1 || config > candidates.size()) return DID_NOT_FIND_RECIPE;
        final Fluid fluid = candidates.get(config - 1)
            .getFluid();
        if (fluid == null) return DID_NOT_FIND_RECIPE;

        final FluidStack output = new FluidStack(fluid, PRODUCTION_AMOUNT_MB);
        // canOutput 拒绝满罐，以及储罐仍留有其他流体时的流体切换。
        if (!canOutput(output)) return DID_NOT_FIND_RECIPE;

        mOutputFluid = output;
        mMaxProgresstime = getProductionCycleTicks();
        return FOUND_AND_SUCCESSFULLY_USED_RECIPE;
    }

    @Override
    protected ITexture getFrontOverlay() {
        return TextureFactory.builder()
            .addIcon(OVERLAY_FRONT_WATER_PUMP)
            .extFacing()
            .build();
    }

    // ==== 提示信息：静态规则 + 实时流体选择状态行 ====

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("vmd.pump.desc.1"),
            StatCollector.translateToLocal("vmd.pump.desc.2"), StatCollector.translateToLocal("vmd.pump.desc.3"),
            StatCollector.translateToLocal("vmd.pump.desc.4"), StatCollector.translateToLocal("vmd.pump.desc.5"),
            StatCollector.translateToLocalFormatted("vmd.pump.desc.selection", describeCurrentSelection()) };
    }

    private String describeCurrentSelection() {
        final int config = readCircuitConfig();
        if (config < 1) return StatCollector.translateToLocal("vmd.pump.selection.no_circuit");
        final List<GTUOFluid> candidates = VoidFluidTableHelper
            .getFluidCandidates(VoidFluidTableHelper.getOverworldDimension());
        if (config <= candidates.size()) {
            final Fluid fluid = candidates.get(config - 1)
                .getFluid();
            if (fluid != null) return StatCollector.translateToLocal(fluid.getUnlocalizedName());
        }
        return StatCollector.translateToLocal("vmd.pump.selection.invalid");
    }
}
