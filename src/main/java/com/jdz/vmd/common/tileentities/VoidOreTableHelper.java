package com.jdz.vmd.common.tileentities;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import bartworks.common.configs.Configuration;
import bwcrossmod.galacticgreg.VoidMinerUtility;
import cpw.mods.fml.common.registry.GameRegistry;
import galacticgreg.api.ModDimensionDef;
import galacticgreg.api.enums.DimensionDef;
import gregtech.api.enums.Mods;
import gregtech.api.enums.StoneType;
import gregtech.api.interfaces.IOreMaterial;
import gregtech.api.interfaces.IStoneType;
import gregtech.common.WorldgenGTOreSmallPieces;
import gregtech.common.ores.OreInfo;
import gregtech.common.ores.OreManager;
import gtneioreplugin.util.DimensionHelper;

/**
 * GT5u 贫矿（小型矿）生成表（{@link WorldgenGTOreSmallPieces}）的共享访问层：解析、石类型展开与
 * 黑名单规则，供木制采矿机奖池与向原版虚空采矿机掉落表追加贫矿共用（流体侧为 {@link VoidFluidTableHelper}）。
 * 掉落经 {@link OreManager#getStack} 以 {@code OreInfo(material, stoneType, isSmall = true)} 解析为 orePoor，权重取
 * {@code mAmount}。
 */
public final class VoidOreTableHelper {

    private static final Logger LOG = LogManager.getLogger("CheaperVoidMiners");

    private static boolean appendedToVoidMinerDropMaps = false;

    private VoidOreTableHelper() {}

    @FunctionalInterface
    public interface SmallOreDropConsumer {

        void acceptSmallOreDrop(ItemStack stack, float weight);
    }

    /** 经 GT 矿石适配器解析 (材料, 石类型) 的矿石物品；small=true 为 orePoor，无适配器时为 null。 */
    @Nullable
    public static ItemStack resolveOreStack(IOreMaterial material, IStoneType stone, boolean small) {
        try (OreInfo<IOreMaterial> info = OreInfo.getNewInfo()) {
            info.material = material;
            info.stoneType = stone;
            info.isSmall = small;
            final ItemStack stack = OreManager.getStack(info, 1);
            if (stack == null) {
                LOG.warn("Could not resolve ore stack for {} on stone {}", material, stone);
            }
            return stack;
        }
    }

    /** 判断方式与虚空采矿机掉落表黑名单（{@code VoidMinerUtility.DropMap}）一致（标识 {@code modid:block:meta}）。 */
    public static boolean isVoidMinerBlacklisted(ItemStack stack) {
        final String identifier = String.format(
            "%s:%d",
            GameRegistry.findUniqueIdentifierFor(Block.getBlockFromItem(stack.getItem()))
                .toString(),
            stack.getItemDamage());
        return Arrays.asList(Configuration.multiblocks.voidMinerBlacklist)
            .contains(identifier);
    }

    /**
     * 将 small pieces 条目按石类型展开为贫矿掉落交给消费者，权重取生成量；
     * 展开规则复刻 {@code VoidMinerUtility.DropMap#addDrop}（解析失败中止展开、黑名单跳过）。
     */
    public static void forEachSmallOreDrop(WorldgenGTOreSmallPieces piece, List<StoneType> stones,
        SmallOreDropConsumer consumer) {
        final IOreMaterial material = piece.getMaterial();
        if (material == null) return;
        for (IStoneType stone : stones) {
            final ItemStack stack = resolveOreStack(material, stone, true);
            if (stack == null) {
                break;
            }
            if (isVoidMinerBlacklisted(stack)) continue;
            consumer.acceptSmallOreDrop(stack, piece.mAmount);
        }
    }

    /**
     * 一次性把已启用的贫矿条目追加进原版虚空采矿机掉落表（经 {@link VoidMinerUtility#addBlockToDimensionList}）。
     * 时序契约：必须在 {@code VoidMinerUtility.generateDropMaps()} 之后（BartWorks 于 FMLLoadCompleteEvent
     * 执行，否则掉落表被重建），且在首台虚空采矿机首个 tick 调用 {@code calculateDropMap()} 之前。
     * 双调用点互为补充，标记位保证全局只追加一次：
     * <ul>
     * <li>服务器启动事件（{@code serverStarting}）：服务端（单人/集成服/专服）主路径，先于任何 tick，
     * 两个边界天然满足，语义不变。</li>
     * <li>虚空矿机 mixin 的 {@code onFirstTick} 注入（见
     * {@code VMDVoidMinerBaseMixin#vmd$onFirstTick}）：客户端连远程服的唯一入口——客户端进程不触发
     * FMLServerStartingEvent，但本 mod 双端 required，客户端同样有 BartWorks，其 FMLLoadCompleteEvent
     * （早于任何 tick）已生成掉表，首个 tick 时追加必然成功，且先于客户端首次
     * {@code isDistributionCached} 缓存（recalculateDropMap 与 GUI 弹窗路径均晚于首个 tick），
     * 客户端 GUI 过滤弹窗因此包含贫矿独立按钮；服务端场景该调用点命中幂等标志静默跳过。</li>
     * </ul>
     * 类加载时点：mixin 路径使本类首次加载提前到运行期（游戏内首个 tick），不在 initializeMods/
     * loadComplete 时段（见 {@code CommonProxy#serverStarting} 注释记录的 RFB 类加载禁区），
     * 约束不适用。追加只做一次，普通静态标志即可：serverStarting 与各机器 onFirstTick 均在
     * 同一主线程串行执行，无跨线程可见性问题。
     */
    public static void appendPoorOresToVoidMinerDropMaps() {
        if (appendedToVoidMinerDropMaps) return;
        appendedToVoidMinerDropMaps = true;

        if (!Mods.BartWorks.isModLoaded()) {
            LOG.error(
                "BartWorks is not loaded, VoidMinerUtility.generateDropMaps() will never run; skipping the poor ore append to the void miner drop maps");
            return;
        }

        final int[] appended = { 0 };
        for (WorldgenGTOreSmallPieces piece : WorldgenGTOreSmallPieces.sList) {
            if (!piece.mEnabled) continue;
            for (String dim : piece.getAllowedDimensions()) {
                // 维度过滤同 generateDropMaps：未收录维度仍获掉落表，不可虚空开采的维度跳过。
                final ModDimensionDef dimensionDef = DimensionDef.getDefByName(dim);
                if (dimensionDef != null && !dimensionDef.canBeVoidMined()) continue;

                final List<StoneType> stones = DimensionHelper.getStoneTypes(dim);
                forEachSmallOreDrop(
                    piece,
                    stones,
                    (stack, weight) -> { if (appendPoorOreToDimension(dim, stack, weight)) appended[0]++; });
            }
        }
        LOG.info("Appended {} poor ore entries to the void miner drop maps", appended[0]);
    }

    /**
     * 经 {@link VoidMinerUtility#addBlockToDimensionList(String, Block, int, float)} 将贫矿掉落路由到
     * 维度掉落表（API 只接受 Block+meta）；GT 矿石均为方块物品，反向查找无损，失败时记错误日志。
     */
    private static boolean appendPoorOreToDimension(String dim, ItemStack stack, float weight) {
        final Block block = Block.getBlockFromItem(stack.getItem());
        if (block == null || block == Blocks.air) {
            LOG.error(
                "Poor ore drop {} ({}:{}) is not backed by a block item and cannot be added to the void miner drop map of dimension {}",
                stack.getDisplayName(),
                GameRegistry.findUniqueIdentifierFor(Block.getBlockFromItem(stack.getItem())),
                stack.getItemDamage(),
                dim);
            return false;
        }
        VoidMinerUtility.addBlockToDimensionList(dim, block, stack.getItemDamage(), weight);
        return true;
    }
}
