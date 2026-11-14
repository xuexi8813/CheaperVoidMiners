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

import com.jdz.vmd.Config;
import com.jdz.vmd.api.recipe.VmdRecipeMaps;
import com.jdz.vmd.common.tileentities.MTEVoidMinerWooden;
import com.jdz.vmd.common.tileentities.VoidExcavatorResourceTable;
import com.jdz.vmd.common.tileentities.VoidFluidTableHelper;

import gregtech.api.objects.GTUODimension;
import gregtech.api.objects.GTUOFluid;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gtneioreplugin.plugin.block.ModBlocks;
import gtneioreplugin.util.DimensionHelper;

/**
 * 注册本模组的 NEI 产出配方，每个可开采维度对应一条配方，遵循 GT 虚空采矿机
 * （{@code VoidMinerRecipes}）的模式：以维度显示方块（堆叠数为 0）作为输入，所有可产出条目作为
 * 带权重的输出。权重以 0.01% 为单位的输出概率表示（{@code round(10000 * weight / total)}，最小为 1），
 * 输出按权重降序排列，并以物品 id / meta（流体则用流体 id）作为并列时的排序依据，与 GT 模板完全一致。
 * <p>
 * 时机：由 FMLLoadCompleteEvent 处理器调用，在向 GT 虚空采矿机掉落表追加贫矿之后执行，
 * 因此下方构建的木制采矿机混合奖池在游戏的客户端与服务端两侧都会包含这些贫矿
 * （远程服务器的客户端会在本地构建相同的、由配置与世界生成驱动的数据）。
 */
public final class VmdOutputRecipeLoader {

    private static final Logger LOG = LogManager.getLogger("CheaperVoidMiners");

    /** 单条流体输出条目的展示数量，单位 mB；机器实际产出量由其自身决定。 */
    private static final int FLUID_DISPLAY_AMOUNT_MB = 1000;

    private static boolean registered = false;

    private VmdOutputRecipeLoader() {}

    /** 注册全部 NEI 产出配方，仅执行一次；该标志用于防止意外的重复注册。 */
    public static void register() {
        if (registered) return;
        registered = true;

        registerFluidDrillerRecipes();
        registerExcavatorRecipes();
        registerWoodenMinerRecipe();
    }

    /**
     * 虚空流体钻机 / 木制虚空流体泵：每个拥有地下流体表的维度对应一条配方，
     * 按生成概率列出表中的流体。
     */
    private static void registerFluidDrillerRecipes() {
        for (String dimAbbreviation : DimensionHelper.getAllDisplayedNames()) {
            final Block marker = ModBlocks.blocks.get(dimAbbreviation);
            if (marker == null) continue;

            final GTUODimension dimension = VoidFluidTableHelper.findDimensionByAbbr(dimAbbreviation);
            final List<GTUOFluid> candidates = VoidFluidTableHelper.getFluidCandidates(dimension);
            if (candidates.isEmpty()) continue;

            long totalWeight = 0;
            for (GTUOFluid candidate : candidates) {
                totalWeight += Math.max(0, candidate.Chance);
            }

            // 按权重降序排列，权重并列时按流体 id 排序，与 GT 虚空采矿机的排序方式一致。
            final List<GTUOFluid> sorted = new ArrayList<>(candidates);
            sorted.sort(
                Comparator.comparingInt((GTUOFluid candidate) -> candidate.Chance)
                    .reversed()
                    .thenComparingInt(
                        candidate -> candidate.getFluid()
                            .getID()));

            final FluidStack[] outputs = new FluidStack[sorted.size()];
            final int[] chances = new int[sorted.size()];
            for (int i = 0; i < sorted.size(); i++) {
                final GTUOFluid candidate = sorted.get(i);
                outputs[i] = new FluidStack(candidate.getFluid(), FLUID_DISPLAY_AMOUNT_MB);
                chances[i] = toChance(candidate.Chance, totalWeight, sorted.size());
            }

            RA.stdBuilder()
                .itemInputs(toMarkerStack(marker))
                .fluidOutputs(outputs, chances)
                .duration(1)
                .eut(0)
                .addTo(VmdRecipeMaps.voidFluidDrillerRecipes);
        }
    }

    /**
     * 虚空挖掘机 / 木制虚空挖掘机：每个已配置且同时拥有资源表与 GTNEI 维度显示方块的维度对应
     * 一条配方；GTNEI 未收录的维度无法通过显示方块选中，因此跳过。
     */
    private static void registerExcavatorRecipes() {
        for (String dimAbbreviation : Config.getExcavatorResourceTables()
            .keySet()) {
            final Block marker = ModBlocks.blocks.get(dimAbbreviation);
            if (marker == null) continue;

            final List<VoidExcavatorResourceTable.WeightedResource> resources = VoidExcavatorResourceTable
                .getResources(dimAbbreviation);
            if (resources.isEmpty()) continue;

            final List<ItemStack> stacks = new ArrayList<>(resources.size());
            final List<Float> weights = new ArrayList<>(resources.size());
            for (VoidExcavatorResourceTable.WeightedResource resource : resources) {
                stacks.add(resource.stack());
                weights.add(resource.weight());
            }
            addItemWeightedRecipe(VmdRecipeMaps.voidExcavatorRecipes, marker, stacks, weights);
        }
    }

    /**
     * 木制虚空采矿机：唯一一条主世界配方，列出完整的混合奖池（矿脉材料按矿脉权重加权，
     * 贫矿按其生成数量加权），即机器在不使用电路时的产出。
     */
    private static void registerWoodenMinerRecipe() {
        final Block marker = ModBlocks.blocks.get(VoidExcavatorResourceTable.DEFAULT_DIMENSION);
        if (marker == null) {
            LOG.error("No GTNEI dimension display block for the Overworld, Wooden Void Miner NEI recipe skipped");
            return;
        }

        final MTEVoidMinerWooden.OverworldPools pools = MTEVoidMinerWooden.buildOverworldPools();

        final List<ItemStack> stacks = new ArrayList<>(pools.mixed.size());
        final List<Float> weights = new ArrayList<>(pools.mixed.size());
        for (MTEVoidMinerWooden.WeightedOre entry : pools.mixed) {
            stacks.add(entry.stack());
            weights.add(entry.weight());
        }
        addItemWeightedRecipe(VmdRecipeMaps.woodenMinerRecipes, marker, stacks, weights);
    }

    /**
     * 构建并添加一条带权重的物品输出配方；权重池为空（所有权重 <= 0）时回退为均匀分配，
     * 而不是注册永远不可能触发的概率。
     */
    private static void addItemWeightedRecipe(RecipeMap<RecipeMapBackend> recipeMap, Block marker,
        List<ItemStack> stacks, List<Float> weights) {
        float totalWeight = 0;
        for (float weight : weights) {
            totalWeight += Math.max(0, weight);
        }

        // 按权重降序排列，权重并列时先按物品 id、再按 meta 排序，与 GT 虚空采矿机的排序方式一致。
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
            .itemInputs(toMarkerStack(marker))
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
