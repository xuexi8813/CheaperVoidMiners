package com.jdz.vmd.common.tileentities;

import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL;
import static gregtech.api.objects.XSTR.XSTR_INSTANCE;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.github.bsideup.jabel.Desugar;
import com.jdz.vmd.api.recipe.VmdRecipeMaps;

import galacticgreg.api.enums.DimensionDef;
import gregtech.api.enums.StoneType;
import gregtech.api.interfaces.IOreMaterial;
import gregtech.api.interfaces.IStoneType;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.render.TextureFactory;
import gregtech.common.WorldgenGTOreLayer;
import gregtech.common.WorldgenGTOreSmallPieces;
import gtneioreplugin.util.DimensionHelper;

/**
 * 木制虚空采矿机：一台无电力的单方块机器，缓慢地从主世界矿石生成表中抽出矿石物品
 * （"从虚空中抽取"，不破坏方块）。
 * <p>
 * 矿石数据从虚空采矿机所用的同一批 GT5u 数据源重建，参见
 * {@code bwcrossmod.galacticgreg.VoidMinerUtility#generateDropMaps}：限定于主世界的已启用
 * {@link WorldgenGTOreLayer} 矿脉，加上已启用的 {@link WorldgenGTOreSmallPieces} 条目
 * （贫矿掉落由 {@link VoidOreTableHelper} 解析）。选择遵循木制机器的共用规则：编程电路
 * （配置 1..N）选择第 N 条矿脉，N+1 选择贫矿（小型矿石）池，配置 0 或没有电路时把所有内容
 * 混合进一个加权池，超过 N+1 的配置则空转。
 * <p>
 * 确定性：矿脉按 {@code sList} 注册顺序遍历，石头类型展开遵循
 * {@link DimensionHelper#getStoneTypes}，与原版虚空采矿机掉落表的构建方式完全一致。
 */
// 跳过 GT 的区域名称注册；显示名称来自标准 lang 键
// （该注解不会被继承，因此由具体类显式携带）。
@IMetaTileEntity.SkipGenerateName
public class MTEVoidMinerWooden extends MTEVoidWoodenBase {

    /** 两次生产尝试之间的间隔 tick 数；木制采矿机的速度是木制泵的两倍。 */
    public static final int MINER_PRODUCTION_CYCLE_TICKS = 100;

    /** 矿石世界生成表与 GTNEI 辅助类中所使用的主世界维度名称。 */
    private static final String OVERWORLD_DIM_NAME = DimensionDef.DimNames.OW;

    /** Sporadic（零星）与 Between（夹层）材料按矿脉权重的八分之一计算，与虚空采矿机保持一致。 */
    private static final float SPORADIC_WEIGHT_DIVISOR = 8.0f;

    /** 矿石池中的一条带权重条目。 */
    @Desugar
    public record WeightedOre(ItemStack stack, float weight) {}

    /** 一条矿脉：四种矿脉材料在主世界石头类型上的展开。 */
    public static final class OreVein {

        public final String name;
        public final List<WeightedOre> drops = new ArrayList<>();

        public OreVein(String name) {
            this.name = name;
        }
    }

    /**
     * 由 {@link #buildOverworldPools()} 构建的三个主世界矿石池；在机器实例与 NEI 产出配方注册之间
     * 共享，因此两侧看到的数据完全一致。
     */
    public static final class OverworldPools {

        public final List<OreVein> veins = new ArrayList<>();
        public final List<WeightedOre> smallOres = new ArrayList<>();
        public final List<WeightedOre> mixed = new ArrayList<>();
    }

    /** 会话级标志，确保空矿石表只报告一次，而不是每个周期都报告。 */
    private static boolean warnedEmptyOverworldPools = false;

    // 矿石池，每个机器实例惰性构建一次（服务端与客户端都需要）。
    private boolean poolsBuilt = false;
    private List<OreVein> veinPool = new ArrayList<>();
    private List<WeightedOre> smallOrePool = new ArrayList<>();
    private List<WeightedOre> mixedPool = new ArrayList<>();

    public MTEVoidMinerWooden(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, "", 1, 1);
    }

    public MTEVoidMinerWooden(String aName, ITexture[][][] aTextures) {
        super(aName, new String[] { "" }, aTextures, 1, 1);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MTEVoidMinerWooden(this.mName, this.mTextures);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return VmdRecipeMaps.woodenMinerRecipes;
    }

    @Override
    protected int getProductionCycleTicks() {
        return MINER_PRODUCTION_CYCLE_TICKS;
    }

    /**************************************************************************
     * 生产。
     *************************************************************************/

    @Override
    public int checkRecipe() {
        if (!poolsBuilt) buildOrePools();
        if (mixedPool.isEmpty()) {
            if (!warnedEmptyOverworldPools) {
                warnedEmptyOverworldPools = true;
                log().warn("Overworld ore vein and small ore tables are empty, Wooden Void Miner idles");
            }
            return DID_NOT_FIND_RECIPE;
        }

        final int config = readCircuitConfig();
        final List<WeightedOre> pool;
        if (config == 0) {
            // 没有电路或配置 0：所有内容混合进一个加权池。
            pool = mixedPool;
        } else if (config <= veinPool.size()) {
            pool = veinPool.get(config - 1).drops;
        } else if (config == veinPool.size() + 1) {
            pool = smallOrePool;
        } else {
            return DID_NOT_FIND_RECIPE; // 超出池数量：空转，与木制机器家族保持一致。
        }
        if (pool.isEmpty()) return DID_NOT_FIND_RECIPE;

        final ItemStack output = singleOutput(weightedPick(pool).stack());
        // 输出槽已满或槽内仍有不同矿石时机器停滞，直到该槽被清空。
        if (!canOutput(output)) return DID_NOT_FIND_RECIPE;

        mOutputItems[0] = output;
        mMaxProgresstime = getProductionCycleTicks();
        return FOUND_AND_SUCCESSFULLY_USED_RECIPE;
    }

    /** 在池中做加权随机选取；掷点对 [0, total) 的划分方式与流体辅助类完全一致。 */
    private WeightedOre weightedPick(List<WeightedOre> pool) {
        float total = 0;
        for (WeightedOre entry : pool) {
            total += Math.max(0, entry.weight());
        }
        if (total <= 0) return pool.get(XSTR_INSTANCE.nextInt(pool.size()));

        float roll = XSTR_INSTANCE.nextFloat() * total;
        for (WeightedOre entry : pool) {
            roll -= Math.max(0, entry.weight());
            if (roll < 0) return entry;
        }
        return pool.get(pool.size() - 1);
    }

    /**************************************************************************
     * 矿石池构建，为主世界复刻 VoidMinerUtility.generateDropMaps 的逻辑。
     *************************************************************************/

    private void buildOrePools() {
        poolsBuilt = true;
        final OverworldPools pools = buildOverworldPools();
        this.veinPool = pools.veins;
        this.smallOrePool = pools.smallOres;
        this.mixedPool = pools.mixed;
    }

    /**
     * 从 GT5u 世界生成表构建三个主世界矿石池；数据源、展开规则与排序均与机器自身所用的一致，
     * 公开此方法供 NEI 产出配方注册使用。
     */
    public static OverworldPools buildOverworldPools() {
        final OverworldPools pools = new OverworldPools();

        final List<StoneType> stones = DimensionHelper.getStoneTypes(OVERWORLD_DIM_NAME);

        // sList 的注册顺序是确定性的，因此得到的矿脉索引顺序也是确定的。
        for (WorldgenGTOreLayer layer : WorldgenGTOreLayer.sList) {
            if (!layer.mEnabled) continue;
            if (!layer.getAllowedDimensions()
                .contains(OVERWORLD_DIM_NAME)) continue;

            final OreVein vein = new OreVein(layer.mWorldGenName);
            addMaterialDrops(vein, layer.mPrimary, stones, layer.mWeight);
            addMaterialDrops(vein, layer.mSecondary, stones, layer.mWeight);
            addMaterialDrops(vein, layer.mSporadic, stones, layer.mWeight / SPORADIC_WEIGHT_DIVISOR);
            addMaterialDrops(vein, layer.mBetween, stones, layer.mWeight / SPORADIC_WEIGHT_DIVISOR);
            if (vein.drops.isEmpty()) continue;
            pools.veins.add(vein);
            pools.mixed.addAll(vein.drops);
        }

        for (WorldgenGTOreSmallPieces piece : WorldgenGTOreSmallPieces.sList) {
            if (!piece.mEnabled) continue;
            if (!piece.getAllowedDimensions()
                .contains(OVERWORLD_DIM_NAME)) continue;

            VoidOreTableHelper.forEachSmallOreDrop(piece, stones, (stack, weight) -> {
                final WeightedOre drop = new WeightedOre(stack, weight);
                pools.smallOres.add(drop);
                pools.mixed.add(drop);
            });
        }
        return pools;
    }

    /** 将一种矿脉材料在石头类型上展开；缺少适配器时会提前终止展开。 */
    private static void addMaterialDrops(OreVein vein, IOreMaterial material, List<StoneType> stones, float weight) {
        if (material == null) return;
        for (IStoneType stone : stones) {
            final ItemStack stack = VoidOreTableHelper.resolveOreStack(material, stone, false);
            if (stack == null) {
                // 与 VoidMinerUtility 一致：无法解析的 (材料, 石头) 组合会终止展开。
                break;
            }
            if (VoidOreTableHelper.isVoidMinerBlacklisted(stack)) continue;
            vein.drops.add(new WeightedOre(stack, weight));
        }
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
        return new String[] { StatCollector.translateToLocal("vmd.miner.desc.1"),
            StatCollector.translateToLocal("vmd.miner.desc.2"), StatCollector.translateToLocal("vmd.miner.desc.3"),
            StatCollector.translateToLocal("vmd.miner.desc.4"), StatCollector.translateToLocal("vmd.miner.desc.5"),
            StatCollector.translateToLocalFormatted("vmd.miner.desc.selection", describeCurrentSelection()) };
    }

    /** 已插入电路当前所选内容的人类可读名称。 */
    protected String describeCurrentSelection() {
        if (!poolsBuilt) buildOrePools();

        final int config = readCircuitConfig();
        if (config >= 1 && config <= veinPool.size()) {
            return veinPool.get(config - 1).name;
        }
        if (config == veinPool.size() + 1) {
            return StatCollector.translateToLocal("vmd.miner.selection.poor");
        }
        if (config == 0) {
            return StatCollector.translateToLocal("vmd.miner.selection.mixed");
        }
        return StatCollector.translateToLocal("vmd.pump.selection.invalid");
    }
}
