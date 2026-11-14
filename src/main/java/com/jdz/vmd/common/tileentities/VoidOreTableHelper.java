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
 * GT5u 贫矿（小型矿）形态矿石生成表的共享访问层。
 * <p>
 * 动机：Wooden Void Miner（木制虚空采矿机）要基于 {@link WorldgenGTOreSmallPieces} 构建贫矿奖池，
 * 而把同一批贫矿一次性追加进原版虚空采矿机掉落表也需要完全相同的解析、石类型展开与黑名单规则，
 * 因此该逻辑只在此处实现一次，避免在每台机器中重复（流体侧对应 {@link VoidFluidTableHelper}）。
 * <p>
 * 掉落形态遵循 GT5u 虚空采矿机的数据源：small pieces 条目通过 {@link OreManager#getStack}
 * 以 {@code OreInfo(material, stoneType, isSmall = true)} 解析，得到贫矿（orePoor）物品形态，
 * 并以条目的生成量（{@code mAmount}）为权重。
 */
public final class VoidOreTableHelper {

    private static final Logger LOG = LogManager.getLogger("CheaperVoidMiners");

    /** 会话级标记，确保向虚空采矿机掉落表的一次性追加永远不会执行两次。 */
    private static boolean appendedToVoidMinerDropMaps = false;

    private VoidOreTableHelper() {}

    /** 接收一条由 small pieces 世界生成条目解析出的贫矿掉落及其权重。 */
    @FunctionalInterface
    public interface SmallOreDropConsumer {

        void acceptSmallOreDrop(ItemStack stack, float weight);
    }

    /**
     * 通过 GT 矿石适配器系统解析 (材料, 石类型) 组合的矿石物品；small = true 得到贫矿（orePoor）形态。
     * 没有适配器支持该组合时返回 null。
     */
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

    /**
     * 判断方式与虚空采矿机掉落表黑名单（{@code VoidMinerUtility.DropMap}）相同，
     * 使用同样的标识格式 {@code modid:block:meta}。
     */
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
     * 把一条 small pieces 世界生成条目按给定石类型展开为贫矿掉落，并将每个被接受的掉落连同权重
     * 交给消费者，权重取条目的生成量。
     * <p>
     * 展开规则复刻 {@code VoidMinerUtility.DropMap#addDrop(IOreMaterial, List, float)}：
     * 无法解析的 (材料, 石头) 组合会中止展开，命中虚空采矿机黑名单的掉落会被跳过。
     */
    public static void forEachSmallOreDrop(WorldgenGTOreSmallPieces piece, List<StoneType> stones,
        SmallOreDropConsumer consumer) {
        final IOreMaterial material = piece.getMaterial();
        if (material == null) return;
        for (IStoneType stone : stones) {
            final ItemStack stack = resolveOreStack(material, stone, true);
            if (stack == null) {
                // 与 VoidMinerUtility 一致：无法解析的 (材料, 石头) 组合会中止展开。
                break;
            }
            if (isVoidMinerBlacklisted(stack)) continue;
            consumer.acceptSmallOreDrop(stack, piece.mAmount);
        }
    }

    /**
     * 一次性把所有已启用的贫矿世界生成条目追加进原版虚空采矿机掉落表
     * （{@link VoidMinerUtility#addBlockToDimensionList}），使 GT 虚空采矿机也能产出贫矿。
     * <p>
     * 时序契约：必须在 {@code VoidMinerUtility.generateDropMaps()} 之后运行（BartWorks 在其
     * FMLLoadCompleteEvent 处理器中执行它，否则会从头重建掉落表），并且必须在第一台虚空采矿机
     * 首个 tick 调用 {@code calculateDropMap()} 之前运行（别名分布缓存在那里构建，
     * 若没有追加的条目，掉落表会被冻结）。服务器启动事件在所有环境中都满足这两个边界；
     * 标记位保证重复启动服务器时具有幂等性。
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
                // 与 VoidMinerUtility.generateDropMaps 相同的维度过滤语义：GT 维度注册表未收录的维度
                // 仍会获得掉落表，不可虚空开采的维度则被跳过。
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
     * 通过公开的 {@link VoidMinerUtility#addBlockToDimensionList(String, Block, int, float)} API，
     * 把一条贫矿掉落路由到某维度的虚空采矿机掉落表；该 API 只接受 Block + meta 组合。
     * GT 矿石物品都是方块物品，因此反向查找是无损的；其他情况会以错误日志报告，
     * 而不是被静默丢弃。
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
