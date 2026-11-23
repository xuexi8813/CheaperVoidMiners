package com.jdz.vmd.common.tileentities;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraftforge.fluids.FluidStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gtnhintergalactic.recipe.GasSiphonRecipes;

/**
 * 地表行星气体钻机与 NEI 共用的全局气体表，数据来自 {@link GasSiphonRecipes#RECIPES}。
 * 按行星 Tier、行星 ID、Depth 依次升序排列，电路配置 N 对应第 N 项。
 */
public final class PlanetaryGasTableHelper {

    private static final Logger LOG = LogManager.getLogger("CheaperVoidMiners");

    /**
     * 全局气体条目。{@link #fluid} 保存原版单周期产量，供机器和 NEI 折算。
     */
    public static final class GasEntry {

        /** 气体流体快照（含原版基础产量），只读。 */
        public final FluidStack fluid;
        /** 所属行星的 Tier（原版 GasSiphonRecipe.tier，木星 3 / 土星·天王星 5 / 海王星 6）。 */
        public final int planetTier;
        /** 原版 Depth（1..4），驱动 Mining Pipes 需求（Depth × 64）与原版 EUt 公式。 */
        public final int depth;
        /** 原版行星 id（如 "planet.jupiter"），仅用于日志与排序稳定性说明。 */
        public final String planetKey;

        GasEntry(FluidStack fluid, int planetTier, int depth, String planetKey) {
            this.fluid = fluid;
            this.planetTier = planetTier;
            this.depth = depth;
            this.planetKey = planetKey;
        }
    }

    /** 缓存有效气体表；null 表示尚未构建出有效数据。 */
    private static volatile List<GasEntry> cachedEntries;

    private PlanetaryGasTableHelper() {}

    /**
     * 返回供调用方只读的气体快照。清单由 gtnhintergalactic 在 postInit 填充；
     * 空表会记录错误但不缓存，以便数据就绪后重试。
     */
    public static List<GasEntry> getEntries() {
        List<GasEntry> entries = cachedEntries;
        if (entries == null) {
            synchronized (PlanetaryGasTableHelper.class) {
                entries = cachedEntries;
                if (entries == null) {
                    entries = buildEntries();
                    if (!entries.isEmpty()) {
                        cachedEntries = entries;
                    }
                }
            }
        }
        return entries;
    }

    /** 从 GT5u 气体清单构建全局表：行星 Tier 升序 → 行星 id 字典序 → Depth 升序。 */
    private static List<GasEntry> buildEntries() {
        final Map<String, GasSiphonRecipes.GasSiphonRecipe> recipes = GasSiphonRecipes.RECIPES;
        if (recipes.isEmpty()) {
            LOG.error(
                "GT5u planetary gas recipe list (gtnhintergalactic GasSiphonRecipes.RECIPES) is empty, Surface Planetary Gas Siphon is unusable");
            return new ArrayList<>();
        }

        final List<Map.Entry<String, GasSiphonRecipes.GasSiphonRecipe>> planets = new ArrayList<>(recipes.entrySet());
        // 显式排序，避免 HashMap 遍历顺序影响电路编号。
        planets.sort(
            Comparator.<Map.Entry<String, GasSiphonRecipes.GasSiphonRecipe>, Integer>comparing(e -> e.getValue().tier)
                .thenComparing(Entry::getKey));

        final List<GasEntry> entries = new ArrayList<>();
        for (Map.Entry<String, GasSiphonRecipes.GasSiphonRecipe> planet : planets) {
            final GasSiphonRecipes.GasSiphonRecipe recipe = planet.getValue();
            final List<Integer> depths = new ArrayList<>(recipe.depths.keySet());
            depths.sort(Comparator.naturalOrder());
            for (int depth : depths) {
                final FluidStack fluid = recipe.depths.get(depth);
                if (fluid == null || fluid.getFluid() == null || fluid.amount <= 0) {
                    LOG.error(
                        "Invalid gas entry in GT5u GasSiphonRecipes (planet={}, tier={}, depth={}), entry skipped",
                        planet.getKey(),
                        recipe.tier,
                        depth);
                    continue;
                }
                // 浅拷贝快照（本表气体均无 NBT，共享 tag 引用无风险）：消费方（机器/NEI）不得改写 GT5u 的共享配方数据。
                entries.add(new GasEntry(new FluidStack(fluid, fluid.amount), recipe.tier, depth, planet.getKey()));
            }
        }

        if (entries.isEmpty()) {
            LOG.error(
                "GT5u planetary gas recipe list contained no valid entries, Surface Planetary Gas Siphon is unusable");
        }
        return entries;
    }
}
