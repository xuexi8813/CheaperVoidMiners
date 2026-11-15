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
 * 木制虚空采矿机：无电力单方块机器，从主世界矿石生成表抽取矿石（数据源与展开规则同
 * {@code VoidMinerUtility#generateDropMaps}，贫矿掉落由 {@link VoidOreTableHelper} 解析）。
 * 电路选择：配置 1..N 选第 N 条矿脉，N+1 选贫矿池，0/无电路为混合加权池，超过 N+1 空转。
 */
// 跳过 GT 区域名注册，显示名来自 lang 键；注解不继承，需每个具体类显式携带。
@IMetaTileEntity.SkipGenerateName
public class MTEVoidMinerWooden extends MTEVoidWoodenBase {

    public static final int MINER_PRODUCTION_CYCLE_TICKS = 100;

    private static final String OVERWORLD_DIM_NAME = DimensionDef.DimNames.OW;

    /** Sporadic 与 Between 材料按矿脉权重的 1/8 计算，与虚空采矿机一致。 */
    private static final float SPORADIC_WEIGHT_DIVISOR = 8.0f;

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

    /** 三个主世界矿石池；由机器与 NEI 配方注册共享，两侧数据一致。 */
    public static final class OverworldPools {

        public final List<OreVein> veins = new ArrayList<>();
        public final List<WeightedOre> smallOres = new ArrayList<>();
        public final List<WeightedOre> mixed = new ArrayList<>();
    }

    private static boolean warnedEmptyOverworldPools = false;

    // 矿石池：每个机器实例惰性构建一次（服务端与客户端都需要）。
    private boolean poolsBuilt = false;
    private List<OreVein> veinPool = new ArrayList<>();
    private List<WeightedOre> smallOrePool = new ArrayList<>();
    private List<WeightedOre> mixedPool = new ArrayList<>();

    public MTEVoidMinerWooden(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, "", 1, OUTPUT_SLOT_COUNT);
    }

    public MTEVoidMinerWooden(String aName, ITexture[][][] aTextures) {
        super(aName, new String[] { "" }, aTextures, 1, OUTPUT_SLOT_COUNT);
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

    // ==== 生产 ====

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
            pool = mixedPool;
        } else if (config <= veinPool.size()) {
            pool = veinPool.get(config - 1).drops;
        } else if (config == veinPool.size() + 1) {
            pool = smallOrePool;
        } else {
            return DID_NOT_FIND_RECIPE;
        }
        if (pool.isEmpty()) return DID_NOT_FIND_RECIPE;

        final ItemStack output = singleOutput(weightedPick(pool).stack());
        // 6 槽缓冲：任一输出槽为空或同物品未满即可继续生产（不再要求产出与输出槽 0 位置对应）；
        // 全部输出槽都无法容纳该产出时待机，直到玩家或传送带腾出空间。
        if (!hasRoomFor(output)) return DID_NOT_FIND_RECIPE;
        if (!tryStoreOutput(output)) return DID_NOT_FIND_RECIPE;

        mMaxProgresstime = getProductionCycleTicks();
        return FOUND_AND_SUCCESSFULLY_USED_RECIPE;
    }

    /** 加权随机选取；掷点对 [0, total) 的划分与流体辅助类一致。 */
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

    // ==== 矿石池构建：为主世界复刻 VoidMinerUtility.generateDropMaps ====

    private void buildOrePools() {
        poolsBuilt = true;
        final OverworldPools pools = buildOverworldPools();
        this.veinPool = pools.veins;
        this.smallOrePool = pools.smallOres;
        this.mixedPool = pools.mixed;
    }

    /** 从 GT5u 世界生成表构建三个主世界矿石池；公开供 NEI 产出配方注册复用。 */
    public static OverworldPools buildOverworldPools() {
        final OverworldPools pools = new OverworldPools();

        final List<StoneType> stones = DimensionHelper.getStoneTypes(OVERWORLD_DIM_NAME);

        // sList 注册顺序确定性，矿脉索引顺序因此确定。
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
        return new String[] { StatCollector.translateToLocal("vmd.miner.desc.1"),
            StatCollector.translateToLocal("vmd.miner.desc.2"), StatCollector.translateToLocal("vmd.miner.desc.3"),
            StatCollector.translateToLocal("vmd.miner.desc.4"), StatCollector.translateToLocal("vmd.miner.desc.5"),
            StatCollector.translateToLocalFormatted("vmd.miner.desc.selection", describeCurrentSelection()) };
    }

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
