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
 * 木制虚空挖掘机：一台无电力的单方块机器，缓慢地从虚空中抽出所选维度的普通（非矿石）资源方块，
 * 由配置表驱动（参见 {@link VoidExcavatorResourceTable}）。
 * <p>
 * 维度通过第二个输入槽中的维度显示方块选择（不消耗）；槽位为空时回退到主世界默认资源，
 * 外来物品会被该槽位拒绝。选择遵循木制机器的共用规则：编程电路（配置 1..N）选择该维度表的
 * 第 N 项资源，配置 0 或没有电路时按权重随机选取（权重 = 配置表中的权重），超过 N 的配置则空转。
 * <p>
 * 贴图复用现有的 GT 资源：以木制机壳作为本体，正面使用矿石钻覆盖层；
 * 本模组不附带任何新贴图。
 */
// 跳过 GT 的区域名称注册；显示名称来自标准 lang 键
// （该注解不会被继承，因此由具体类显式携带）。
@IMetaTileEntity.SkipGenerateName
public class MTEVoidExcavatorWooden extends MTEVoidWoodenBase {

    /** 会话级记录，记录已经因资源表为空而警告过的维度，避免重复警告。 */
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
        // 显示方块属于配置信息，可以再次取出。
        return aIndex == getDimensionDisplaySlot() || super.allowPullStack(aBaseMetaTileEntity, aIndex, side, aStack);
    }

    /**************************************************************************
     * 生产。
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
            // 没有电路或配置 0：在该维度表上按权重随机选取。
            pick = VoidExcavatorResourceTable.weightedRandom(resources);
        } else if (config <= resources.size()) {
            pick = resources.get(config - 1);
        } else {
            return DID_NOT_FIND_RECIPE; // 超出资源数量：空转，与木制机器家族保持一致。
        }

        final ItemStack output = singleOutput(pick.stack());
        // 输出槽已满或槽内仍有不同资源时机器停滞，直到该槽被清空。
        if (!canOutput(output)) return DID_NOT_FIND_RECIPE;

        mOutputItems[0] = output;
        mMaxProgresstime = getProductionCycleTicks();
        return FOUND_AND_SUCCESSFULLY_USED_RECIPE;
    }

    /** 显示方块对应的维度；槽位为空时回退到主世界默认资源。 */
    private String resolveDimensionAbbr() {
        final String abbr = VoidExcavatorResourceTable.getDimensionAbbr(mInventory[getDimensionDisplaySlot()]);
        return abbr != null ? abbr : VoidExcavatorResourceTable.DEFAULT_DIMENSION;
    }

    /**************************************************************************
     * 贴图：木制机壳本体，正面叠加 GT 矿石钻覆盖层。
     *************************************************************************/

    @Override
    protected ITexture getFrontOverlay() {
        return TextureFactory.builder()
            .addIcon(OVERLAY_FRONT_ORE_DRILL)
            .extFacing()
            .build();
    }

    /**************************************************************************
     * 提示信息：静态规则加上当前选择的实时状态行。
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

    /** 已插入电路当前所选资源的人类可读名称。 */
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
