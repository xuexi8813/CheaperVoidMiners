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
 * 木制虚空挖掘机：无电力单方块机器，按配置表（{@link VoidExcavatorResourceTable}）从虚空中
 * 抽出所选维度的普通资源方块。维度由第二个输入槽的维度显示方块选择（不消耗），空槽回退主世界。
 * 电路选择：配置 1..N 选表中第 N 项，0/无电路按权重随机，超过 N 空转。
 */
// 跳过 GT 区域名注册，显示名来自 lang 键。
@IMetaTileEntity.SkipGenerateName
public class MTEVoidExcavatorWooden extends MTEVoidWoodenBase {

    private static final Set<String> WARNED_EMPTY_DIMENSIONS = new HashSet<>();

    public MTEVoidExcavatorWooden(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, "", 2, OUTPUT_SLOT_COUNT);
    }

    public MTEVoidExcavatorWooden(String aName, ITexture[][][] aTextures) {
        super(aName, new String[] { "" }, aTextures, 2, OUTPUT_SLOT_COUNT);
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
        // 显示方块属于配置信息，允许再次取出。
        return aIndex == getDimensionDisplaySlot() || super.allowPullStack(aBaseMetaTileEntity, aIndex, side, aStack);
    }

    // ==== 生产 ====

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
            pick = VoidExcavatorResourceTable.weightedRandom(resources);
        } else if (config <= resources.size()) {
            pick = resources.get(config - 1);
        } else {
            return DID_NOT_FIND_RECIPE;
        }

        final ItemStack output = singleOutput(pick.stack());
        // 6 槽缓冲：任一输出槽为空或同物品未满即可继续生产（不再要求产出与输出槽 0 位置对应）；
        // 全部输出槽都无法容纳该产出时待机，直到玩家或传送带腾出空间。
        if (!hasRoomFor(output)) return DID_NOT_FIND_RECIPE;
        if (!tryStoreOutput(output)) return DID_NOT_FIND_RECIPE;

        mMaxProgresstime = getProductionCycleTicks();
        return FOUND_AND_SUCCESSFULLY_USED_RECIPE;
    }

    private String resolveDimensionAbbr() {
        final String abbr = VoidExcavatorResourceTable.getDimensionAbbr(mInventory[getDimensionDisplaySlot()]);
        return abbr != null ? abbr : VoidExcavatorResourceTable.DEFAULT_DIMENSION;
    }

    // ==== 贴图：木制机壳本体 + GT 矿石钻正面覆盖层 ====

    @Override
    protected ITexture getFrontOverlay() {
        return TextureFactory.builder()
            .addIcon(OVERLAY_FRONT_ORE_DRILL)
            .extFacing()
            .build();
    }

    // ==== 提示信息：静态规则 + 实时选择状态行 ====

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("vmd.excavator.desc.1"),
            StatCollector.translateToLocal("vmd.excavator.desc.2"),
            StatCollector.translateToLocal("vmd.excavator.desc.3"),
            StatCollector.translateToLocal("vmd.excavator.desc.4"),
            StatCollector.translateToLocal("vmd.excavator.desc.5"),
            StatCollector.translateToLocalFormatted("vmd.excavator.desc.selection", describeCurrentSelection()) };
    }

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
