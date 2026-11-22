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
 * 地表行星气体钻机的全局气体表：把 GT5u 原版 Planetary Gas Siphon 的全部可抽气体
 * （{@code GasSiphonRecipes#RECIPES}，gtnhintergalactic 各行星的大气气体清单，数据于该 mod 的
 * postInit 填充）汇总为一张全局表。原版按"行星 + Depth（1..4）"二维选择；本表拉平为一维，
 * 按行星 Tier 升序（同 Tier 按行星 id 字典序，再按 Depth 升序）编号，编程电路 1..N 选第 N 项。
 * 每个条目绑定：气体流体（含原版基础产量）+ 行星 Tier（驱动产量 16^ 加成与"机器档不足"判定）
 * + 原版 Depth（驱动 Mining Pipes 需求与原版 EUt 公式）。
 * <p>
 * 机器侧（{@link MTESurfacePlanetaryGasSiphon#checkProcessing}）与 NEI 产出配方
 * （{@code VmdOutputRecipeLoader}）共享本表，电路编号与 NEI 展示顺序同源同序。
 * <p>
 * Fail Fast：数据在首次访问时从 GT5u 气体清单构建并缓存（空表不落缓存，后续访问重试构建）；
 * 清单为空（gtnhintergalactic 未启用或
 * 配方数据异常）或全部条目无效时输出错误日志并返回空表——机器拒绝启动（提示无气体数据）、
 * NEI 不注册配方，禁止静默空表。构建结果只读，条目内 FluidStack 为深拷贝快照，
 * 消费方不得改写（机器产出前会再行 copy）。
 */
public final class PlanetaryGasTableHelper {

    private static final Logger LOG = LogManager.getLogger("CheaperVoidMiners");

    /**
     * 全局气体条目。{@link #fluid} 的数量为原版基础产量（原版该行星该 Depth 的单周期产出），
     * 机器实际产出在其上叠加 16^ 电压加成与钻头加成。
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

    /** 缓存的只读全局表；null 表示尚无有效缓存（尚未构建，或历次构建均为空表——空表不落缓存，见 getEntries）。 */
    private static volatile List<GasEntry> cachedEntries;

    private PlanetaryGasTableHelper() {}

    /**
     * 全局气体表（只读快照）。首次访问时从 GT5u 气体清单构建；之后复用缓存。
     * 失败（空表）时同样返回空列表——调用方按"无气体数据"处理，不抛异常不崩溃。
     * 空表不落缓存：GT5u 气体清单未就绪（如本 mod postInit 先于 gtnhintergalactic postInit 执行）
     * 时返回临时空表，后续访问重试构建直至数据就绪，不把"未就绪"永久误记为"确认无数据"。
     */
    public static List<GasEntry> getEntries() {
        List<GasEntry> entries = cachedEntries;
        if (entries == null) {
            synchronized (PlanetaryGasTableHelper.class) {
                entries = cachedEntries;
                if (entries == null) {
                    entries = buildEntries();
                    // 纵深防御：空表不写入缓存（写入须位于空表判断之后），后续调用重试构建；
                    // 避免在清单尚未填充的早期调用把空表永久钉死进缓存。
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
        // 行星 Tier 升序、同 Tier 行星 id 字典序（HashMap 遍历序不稳定，必须显式排序保证电路编号确定）。
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
