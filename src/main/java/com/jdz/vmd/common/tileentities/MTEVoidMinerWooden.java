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
 * 电路选择：配置 1..N 选第 N 条矿脉（矿脉材料大矿 + 归属该矿脉材料的贫矿），N+1 选贫矿池（全部贫矿），
 * 0/无电路为混合加权池（全部矿脉条目 + 未归属贫矿），超过 N+1 空转。
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
        /** 矿脉携带的材料对象（primary/secondary/sporadic/between，与 drops 展开同源），供 NEI 侧贫矿按材料归属匹配。 */
        public final List<IOreMaterial> materials = new ArrayList<>();
        public final List<WeightedOre> drops = new ArrayList<>();

        public OreVein(String name) {
            this.name = name;
        }
    }

    /** 三个主世界矿石池与贫矿归属统计；由机器与 NEI 配方注册共享，两侧数据一致。 */
    public static final class OverworldPools {

        public final List<OreVein> veins;
        public final List<WeightedOre> smallOres;
        public final List<WeightedOre> mixed;
        /** 归属进矿脉的贫矿条目数（展开前的 WorldgenGTOreSmallPieces 条目计数）。 */
        public final int mergedSmallOrePieces;
        /** 未被任何矿脉匹配、仅存于贫矿池与混合池的贫矿条目数（展开前计数）。 */
        public final int unmatchedSmallOrePieces;

        OverworldPools(List<OreVein> veins, List<WeightedOre> smallOres, List<WeightedOre> mixed,
            int mergedSmallOrePieces, int unmatchedSmallOrePieces) {
            this.veins = veins;
            this.smallOres = smallOres;
            this.mixed = mixed;
            this.mergedSmallOrePieces = mergedSmallOrePieces;
            this.unmatchedSmallOrePieces = unmatchedSmallOrePieces;
        }
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

    /**
     * 从 GT5u 世界生成表构建三个主世界矿石池；公开供 NEI 产出配方注册复用。
     * 贫矿按材料归属：每个贫矿条目只并入第一条材料匹配的矿脉（vein.drops 同时供机器矿脉模式与
     * NEI 矿脉配方消费，两侧不会漂移）；贫矿池保持全部贫矿；混合池由全部矿脉条目与未归属贫矿组成，
     * 每个贫矿只出现一份。
     */
    public static OverworldPools buildOverworldPools() {
        final List<StoneType> stones = DimensionHelper.getStoneTypes(OVERWORLD_DIM_NAME);

        // sList 注册顺序确定性，矿脉索引顺序因此确定。
        final List<OreVein> veins = new ArrayList<>();
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
            veins.add(vein);
        }

        final List<WeightedOre> smallOres = new ArrayList<>();
        final List<WeightedOre> unmatchedSmallOres = new ArrayList<>();
        final int[] mergedPieces = { 0 };
        final int[] unmatchedPieces = { 0 };

        for (WorldgenGTOreSmallPieces piece : WorldgenGTOreSmallPieces.sList) {
            if (!piece.mEnabled) continue;
            if (!piece.getAllowedDimensions()
                .contains(OVERWORLD_DIM_NAME)) continue;

            final IOreMaterial material = piece.getMaterial();
            // 无材料条目无法展开，任何池均不含（与 forEachSmallOreDrop 的 null 短路一致）。
            if (material == null) continue;

            final OreVein vein = firstVeinWithMaterial(veins, material);
            if (vein != null) mergedPieces[0]++;
            else unmatchedPieces[0]++;

            VoidOreTableHelper.forEachSmallOreDrop(piece, stones, (stack, weight) -> {
                final WeightedOre drop = new WeightedOre(stack, weight);
                // 贫矿池（电路 N+1）始终包含全部贫矿，语义不变。
                smallOres.add(drop);
                if (vein != null) {
                    // 归属贫矿并入矿脉条目；混合池经 vein.drops 呈现，不再重复列出。
                    vein.drops.add(drop);
                } else {
                    unmatchedSmallOres.add(drop);
                }
            });
        }

        // 混合池 = 全部矿脉条目（大矿 + 归属贫矿）+ 未归属贫矿；条目集合与旧版逐池相加一致，无重复加权。
        final List<WeightedOre> mixed = new ArrayList<>();
        for (OreVein vein : veins) {
            mixed.addAll(vein.drops);
        }
        mixed.addAll(unmatchedSmallOres);

        return new OverworldPools(veins, smallOres, mixed, mergedPieces[0], unmatchedPieces[0]);
    }

    /** 返回第一条包含该材料的矿脉；无匹配为 null。矿脉顺序即机器编程电路 1..N 的选择顺序。 */
    private static OreVein firstVeinWithMaterial(List<OreVein> veins, IOreMaterial material) {
        for (OreVein vein : veins) {
            for (IOreMaterial veinMaterial : vein.materials) {
                if (isSameOreMaterial(veinMaterial, material)) return vein;
            }
        }
        return null;
    }

    /**
     * 矿脉与贫矿的材料匹配：同一 {@link IOreMaterial} 对象直接相等；跨材料系统（Werkstoff/Materials）
     * 可能出现同材料不同实例，以内部名称兜底。归属匹配仅此一处实现，机器与 NEI 共用。
     */
    private static boolean isSameOreMaterial(IOreMaterial left, IOreMaterial right) {
        if (left == null || right == null) return false;
        if (left == right) return true;
        final String leftName = left.getInternalName();
        return leftName != null && leftName.equals(right.getInternalName());
    }

    private static void addMaterialDrops(OreVein vein, IOreMaterial material, List<StoneType> stones, float weight) {
        if (material == null) return;
        if (!vein.materials.contains(material)) vein.materials.add(material);
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
