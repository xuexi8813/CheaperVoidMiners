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
 * 木制虚空流体泵：一台无电力的木制机器，将主世界的地下流体抽取到其内部输出储罐中。
 * <p>
 * 流体选择遵循木制机器的共用规则：编程电路（配置 1..N）选择主世界流体表的第 N 项，
 * 没有电路或配置 0 时按权重随机选取（权重 = 配置的生成概率），超过 N 的配置则空转。
 * 储罐绝不混合流体；储罐已满或流体不兼容时机器停滞，直到流体被从任意非正面方向
 * 的管道抽出。
 * <p>
 * 贴图复用现有的 GT 资源：以木制机壳作为本体，正面使用水泵覆盖层；
 * 本模组不附带任何新贴图。
 */
// 跳过 GT 的区域名称注册；显示名称来自标准 lang 键
// （该注解不会被继承，因此由具体类显式携带）。
@IMetaTileEntity.SkipGenerateName
public class MTEVoidFluidPumpWooden extends MTEVoidWoodenBase {

    /** 会话级标志，确保损坏的主世界流体表只报告一次，而不是每个周期都报告。 */
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
     * 储罐配置：输出储罐是唯一的储罐；不从外部注入任何东西。
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
     * 生产。
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
            if (config > candidates.size()) return DID_NOT_FIND_RECIPE; // 配置超出范围：空转。
            pick = candidates.get(config - 1);
        } else {
            pick = VoidFluidTableHelper.weightedRandom(candidates);
        }
        final Fluid fluid = pick.getFluid();
        if (fluid == null) return DID_NOT_FIND_RECIPE;

        final FluidStack output = new FluidStack(fluid, PRODUCTION_AMOUNT_MB);
        // canOutput 会拒绝满罐的情况，以及储罐中仍留有其他流体时的流体切换。
        if (!canOutput(output)) return DID_NOT_FIND_RECIPE;

        mOutputFluid = output;
        mMaxProgresstime = getProductionCycleTicks();
        return FOUND_AND_SUCCESSFULLY_USED_RECIPE;
    }

    /**************************************************************************
     * 贴图。
     *************************************************************************/

    @Override
    protected ITexture getFrontOverlay() {
        return TextureFactory.builder()
            .addIcon(OVERLAY_FRONT_WATER_PUMP)
            .extFacing()
            .build();
    }

    /**************************************************************************
     * 提示信息：静态规则加上当前流体选择的实时状态行。
     *************************************************************************/

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("vmd.pump.desc.1"),
            StatCollector.translateToLocal("vmd.pump.desc.2"), StatCollector.translateToLocal("vmd.pump.desc.3"),
            StatCollector.translateToLocal("vmd.pump.desc.4"), StatCollector.translateToLocal("vmd.pump.desc.5"),
            StatCollector.translateToLocalFormatted("vmd.pump.desc.selection", describeCurrentSelection()) };
    }

    /** 机器当前将选择的流体的本地化描述。 */
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
