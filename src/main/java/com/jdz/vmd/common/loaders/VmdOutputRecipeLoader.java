package com.jdz.vmd.common.loaders;

import static gregtech.api.enums.GTValues.RA;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.jdz.vmd.api.recipe.VmdRecipeMaps;
import com.jdz.vmd.common.tileentities.MTEVoidMinerWooden;
import com.jdz.vmd.common.tileentities.PlanetaryGasTableHelper;
import com.jdz.vmd.common.tileentities.VoidFluidTableHelper;

import gregtech.api.enums.ItemList;
import gregtech.api.objects.GTUODimension;
import gregtech.api.objects.GTUOFluid;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;
import gtneioreplugin.plugin.block.ModBlocks;
import gtneioreplugin.util.DimensionHelper;
import gtnhintergalactic.recipe.GasSiphonRecipes;

/**
 * 注册本模组的 NEI 产出配方。流体钻机配方：维度显示方块作输入，该维度全部候选流体按规范排序
 * （{@code VoidFluidTableHelper#CANDIDATE_ORDER}，与机器电路 N 选择同源同序）作无概率输出。
 * 木制采矿机配方遵循 GT 虚空采矿机（{@code VoidMinerRecipes}）模式：维度显示方块作输入，带权重输出
 * （权重为 0.01% 概率单位、按权重降序），并按机器编程电路切分条目：每条主世界矿脉一条
 * （电路 1..N，贫矿按材料归属并入对应矿脉）。调用时机为
 * {@code GregTechAPI.sAfterGTPostload}（GT postInit 末尾）：
 * GT 地下流体表（GT preInit）、矿脉/贫矿世界生成表（{@code WorldgenGTOreLayer}/
 * {@code WorldgenGTOreSmallPieces}.sList，GT postInit 经 GTWorldgenloader 填充）与 GTNEI 维度显示方块
 * （其 init）此时均已就绪。与向 GT 虚空采矿机掉落表追加贫矿
 * （{@code VoidOreTableHelper#appendPoorOresToVoidMinerDropMaps}）互不依赖：
 * 木制采矿机奖池直接读取 WorldgenGTOreSmallPieces.sList，贫矿是否已追加进掉落表不影响奖池内容。
 */
public final class VmdOutputRecipeLoader {

    private static final Logger LOG = LogManager.getLogger("CheaperVoidMiners");

    /** 单条流体输出的展示数量（mB）；机器实际产出量由其自身决定。 */
    private static final int FLUID_DISPLAY_AMOUNT_MB = 1000;

    /** 地表行星气体钻机的 Mining Pipes 常备量系数（原版 Siphon 1:1：Depth × 64，不消耗）。 */
    private static final int MINING_PIPES_PER_DEPTH = 64;

    /** 主世界的 GTNEI 维度缩写：木制采矿机固定开采主世界。 */
    private static final String OVERWORLD_DIMENSION_ABBREVIATION = "Ow";

    private static boolean registered = false;
    private static boolean surfaceGasSiphonRegistered = false;

    private VmdOutputRecipeLoader() {}

    public static void register() {
        if (registered) return;
        registered = true;

        registerFluidDrillerRecipes();
        registerWoodenMinerRecipe();
    }

    /**
     * 地表行星气体钻机：全局气体表（{@code PlanetaryGasTableHelper}，源自 GT5u 原版
     * {@code GasSiphonRecipes} 各行星大气气体清单）每气体一条——输入 = 配置 N 的编程电路标记
     * （stackSize=0，与机器电路选择同源同序）+ 该气体 Depth × 64 的 Mining Pipes 需求展示
     * （机器侧常备不消耗，NEI 展示实际所需数量）→ 输出 = 该气体的原版基础产量（机器实际产出在其上
     * 叠加 16^ 电压加成、钻头加成与完美超频）。
     * <p>
     * 注册时机为本 mod 的 postInit（GT5u 气体清单由 gtnhintergalactic 的 postInit 填充，本 mod
     * 声明 {@code after:gtnhintergalactic} 使其先于本 postInit），并在 GT-PostLoad 链
     * （{@code CommonProxy#registerVmdOutputRecipes}）兜底重试：FML 对 init/postInit 的分发顺序
     * 并不严格保证依赖语义（本项目实测，见 CommonProxy 注释），若本方法先于 gtnhintergalactic
     * postInit 执行，则清单仍为空表——此时不构建、不缓存空表、不置已注册标记，交由 GT-PostLoad
     * 后备点重试（GT5u 2.9 的 postInit 经调度排在所有普通 mod 之后，届时清单必然就绪）。
     * Fail Fast：两次注册点均未等到数据时仅告警跳过（NEI 无条目），机器运行期构建的空表会给出
     * 针对性失败提示，绝不静默空表。
     */
    public static void registerSurfaceGasSiphonRecipes() {
        if (surfaceGasSiphonRegistered) return;
        if (GasSiphonRecipes.RECIPES.isEmpty()) {
            LOG.warn(
                "GT5u planetary gas list not populated yet, Surface Planetary Gas Siphon NEI registration deferred to the GT-PostLoad fallback");
            return;
        }
        surfaceGasSiphonRegistered = true;

        final List<PlanetaryGasTableHelper.GasEntry> gasTable = PlanetaryGasTableHelper.getEntries();
        if (gasTable.isEmpty()) {
            // 全部条目无效（构建时已记错误日志）：维持 Fail Fast，不注册空配方。
            LOG.error("Planetary gas table has no valid entries, Surface Planetary Gas Siphon NEI recipes skipped");
            return;
        }

        // 电路编号 = 全局表位次 + 1（与机器 checkProcessing 的 gasTable.get(config - 1) 同源同序）。
        for (int i = 0; i < gasTable.size(); i++) {
            final PlanetaryGasTableHelper.GasEntry gas = gasTable.get(i);
            RA.stdBuilder()
                .itemInputs(
                    // 电路标记（stackSize=0）：配置 i+1 对应全局表第 i+1 项（与机器选择同源同序）。
                    ItemList.Circuit_Integrated.getWithDamage(0L, i + 1L),
                    // Mining Pipes 需求展示：机器要求总线常备 Depth × 64 根（不消耗）。
                    GTUtility.copyAmount(gas.depth * MINING_PIPES_PER_DEPTH, getMiningPipeStack()))
                .fluidOutputs(new FluidStack(gas.fluid, gas.fluid.amount))
                .duration(1)
                .eut(0)
                .addTo(VmdRecipeMaps.surfaceGasSiphonRecipes);
        }

        LOG.info("Surface Planetary Gas Siphon NEI: {} gas recipes registered", gasTable.size());
    }

    /** IC2 Mining Pipe 物品堆（与原版 Siphon 的判定物品一致）；缺失时 Fail Fast 抛出。 */
    private static ItemStack getMiningPipeStack() {
        final ItemStack pipe = GTModHandler.getIC2Item("miningPipe", 0);
        if (pipe == null) {
            throw new IllegalStateException("IC2 mining pipe item is missing, cannot register gas siphon NEI recipes");
        }
        return pipe;
    }

    /**
     * 虚空流体钻机 / 木制流体泵：每个有地下流体表的维度一条配方，列出该维度全部候选流体
     * （固定 {@value #FLUID_DISPLAY_AMOUNT_MB}mB 展示，无概率标记——机器实际产出量由其自身决定）。
     * 候选列表经 {@link VoidFluidTableHelper#getFluidCandidates} 按规范排序，与机器编程电路 N 的
     * 选择同源同序，NEI 中第 N 位即电路 N 的产出。
     */
    private static void registerFluidDrillerRecipes() {
        for (String dimAbbreviation : DimensionHelper.getAllDisplayedNames()) {
            final Block marker = ModBlocks.blocks.get(dimAbbreviation);
            if (marker == null) continue;

            final GTUODimension dimension = VoidFluidTableHelper.findDimensionByAbbr(dimAbbreviation);
            final List<GTUOFluid> candidates = VoidFluidTableHelper.getFluidCandidates(dimension);
            if (candidates.isEmpty()) continue;

            final FluidStack[] outputs = new FluidStack[candidates.size()];
            for (int i = 0; i < candidates.size(); i++) {
                outputs[i] = new FluidStack(
                    candidates.get(i)
                        .getFluid(),
                    FLUID_DISPLAY_AMOUNT_MB);
            }

            // 单参 fluidOutputs 重载：GTRecipeBuilder 默认全部 10000（100%），NEI 不显示概率。
            RA.stdBuilder()
                .itemInputs(toMarkerStack(marker))
                .fluidOutputs(outputs)
                .duration(1)
                .eut(0)
                .addTo(VmdRecipeMaps.voidFluidDrillerRecipes);
        }
    }

    /**
     * 木制采矿机：与机器编程电路选择语义对应的 NEI 条目——每条主世界矿脉一条
     * （配置 1..N，输入附配置 i 的编程电路）。
     * 贫矿归属（每个贫矿条目只归第一条材料匹配的矿脉、未归属者仅存贫矿池与混合池）由
     * {@link MTEVoidMinerWooden#buildOverworldPools} 统一构建，机器与 NEI 消费同一份数据，不会漂移；
     * 输出堆叠数量亦与机器每周期产出一致（{@link MTEVoidMinerWooden#OUTPUT_PER_CYCLE}，一组 64 件），
     * 加权概率（outputChances）仅由权重决定，不受展示数量影响。
     */
    private static void registerWoodenMinerRecipe() {
        final Block marker = ModBlocks.blocks.get(OVERWORLD_DIMENSION_ABBREVIATION);
        if (marker == null) {
            LOG.error("No GTNEI dimension display block for the Overworld, Wooden Void Miner NEI recipe skipped");
            return;
        }

        final MTEVoidMinerWooden.OverworldPools pools = MTEVoidMinerWooden.buildOverworldPools();

        // 矿脉配方（配置 1..N）：电路 i 与机器矿脉选择完全一致（veins.get(i - 1)），vein.drops 已含归属贫矿。
        for (int i = 0; i < pools.veins.size(); i++) {
            final MTEVoidMinerWooden.OreVein vein = pools.veins.get(i);
            addItemWeightedRecipe(
                VmdRecipeMaps.woodenMinerRecipes,
                new ItemStack[] { toMarkerStack(marker), ItemList.Circuit_Integrated.getWithDamage(1L, i + 1L) },
                toStacks(vein.drops),
                toWeights(vein.drops));
        }

        LOG.info(
            "Wooden Void Miner NEI: {} vein recipes registered, {} Overworld small ore pieces merged into veins, {} unmatched pieces kept in the poor ore pool and mixed pool",
            pools.veins.size(),
            pools.mergedSmallOrePieces,
            pools.unmatchedSmallOrePieces);
    }

    /**
     * 加权条目的输出物品列表：按机器每周期产出（{@link MTEVoidMinerWooden#OUTPUT_PER_CYCLE}，一组 64 件）
     * 复制展示堆叠，与机器实际产出同源；必须经 {@code GTUtility.copyAmount} 生成新堆叠，
     * 不得改写奖池中的共享堆叠（机器侧仍在消费同一份数据）。
     */
    private static List<ItemStack> toStacks(List<MTEVoidMinerWooden.WeightedOre> entries) {
        final List<ItemStack> stacks = new ArrayList<>(entries.size());
        for (MTEVoidMinerWooden.WeightedOre entry : entries) {
            stacks.add(GTUtility.copyAmount(MTEVoidMinerWooden.OUTPUT_PER_CYCLE, entry.stack()));
        }
        return stacks;
    }

    /** 加权条目的权重列表。 */
    private static List<Float> toWeights(List<MTEVoidMinerWooden.WeightedOre> entries) {
        final List<Float> weights = new ArrayList<>(entries.size());
        for (MTEVoidMinerWooden.WeightedOre entry : entries) {
            weights.add(entry.weight());
        }
        return weights;
    }

    /** 构建并添加带权重的物品输出配方（输入数组可附加编程电路）；总权重 <= 0 时回退为均匀分配。 */
    private static void addItemWeightedRecipe(RecipeMap<RecipeMapBackend> recipeMap, ItemStack[] inputs,
        List<ItemStack> stacks, List<Float> weights) {
        float totalWeight = 0;
        for (float weight : weights) {
            totalWeight += Math.max(0, weight);
        }

        // 权重降序，并列先按物品 id、再按 meta，与 GT 虚空采矿机排序一致。
        final List<Integer> order = new ArrayList<>(stacks.size());
        for (int i = 0; i < stacks.size(); i++) {
            order.add(i);
        }
        order.sort(
            Comparator.<Integer, Float>comparing(index -> weights.get(index), Comparator.reverseOrder())
                .thenComparingInt(
                    index -> Item.getIdFromItem(
                        stacks.get(index)
                            .getItem()))
                .thenComparingInt(
                    index -> stacks.get(index)
                        .getItemDamage()));

        final ItemStack[] outputs = new ItemStack[order.size()];
        final int[] chances = new int[order.size()];
        for (int i = 0; i < order.size(); i++) {
            final int index = order.get(i);
            outputs[i] = stacks.get(index);
            chances[i] = toChance(weights.get(index), totalWeight, order.size());
        }

        RA.stdBuilder()
            .itemInputs(inputs)
            .itemOutputs(outputs)
            .outputChances(chances)
            .duration(1)
            .eut(0)
            .addTo(recipeMap);
    }

    /** 将单个权重换算为 GT 配方系统的 0.01% 概率单位，最小值为 1。 */
    private static int toChance(float weight, float totalWeight, int entryCount) {
        if (totalWeight > 0) return Math.max(1, Math.round(10_000f * Math.max(0, weight) / totalWeight));
        // 针对退化表的均匀回退；保证 NEI 中每个条目都可达。
        return Math.max(1, Math.round(10_000f / entryCount));
    }

    /** 产出配方的维度显示方块输入；堆叠数为 0 表示它是一个选择器。 */
    private static ItemStack toMarkerStack(Block marker) {
        final ItemStack markerStack = new ItemStack(marker);
        markerStack.stackSize = 0;
        return markerStack;
    }
}
