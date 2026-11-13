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
 * Shared access layer over the GT5u ore generation tables for the poor (small) ore forms.
 * <p>
 * Motivation: the Wooden Void Miner builds its poor ore pool from {@link WorldgenGTOreSmallPieces} and the
 * one-time append of the same poor ores into the vanilla void miner drop maps needs the exact same
 * resolution, stone type expansion and blacklist rules, so that logic lives here once instead of being
 * duplicated per machine (mirrors {@link VoidFluidTableHelper} on the fluid side).
 * <p>
 * The drop shape follows the GT5u void miner data sources: a small pieces entry is resolved as
 * {@code OreInfo(material, stoneType, isSmall = true)} through {@link OreManager#getStack}, which yields the
 * poor ore (orePoor) item form, weighted by the entry's generation amount ({@code mAmount}).
 */
public final class VoidOreTableHelper {

    private static final Logger LOG = LogManager.getLogger("CheaperVoidMiners");

    /** Session wide flag so the one-time append to the void miner drop maps never runs twice. */
    private static boolean appendedToVoidMinerDropMaps = false;

    private VoidOreTableHelper() {}

    /** Receives one resolved poor ore drop of a small pieces worldgen entry, together with its weight. */
    @FunctionalInterface
    public interface SmallOreDropConsumer {

        void acceptSmallOreDrop(ItemStack stack, float weight);
    }

    /**
     * Resolves the ore item of a (material, stone type) pair through the GT ore adapter system; small = true
     * yields the poor ore (orePoor) form. Returns null when no adapter supports the pair.
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
     * Same identifier format ({@code modid:block:meta}) as the void miner drop map blacklist
     * ({@code VoidMinerUtility.DropMap}).
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
     * Expands one small pieces worldgen entry into its poor ore drops over the given stone types and hands
     * every accepted drop to the consumer, weighted by the entry's generation amount.
     * <p>
     * Expansion rules mirror {@code VoidMinerUtility.DropMap#addDrop(IOreMaterial, List, float)}: an
     * unresolvable (material, stone) pair stops the expansion, void miner blacklisted drops are skipped.
     */
    public static void forEachSmallOreDrop(WorldgenGTOreSmallPieces piece, List<StoneType> stones,
        SmallOreDropConsumer consumer) {
        final IOreMaterial material = piece.getMaterial();
        if (material == null) return;
        for (IStoneType stone : stones) {
            final ItemStack stack = resolveOreStack(material, stone, true);
            if (stack == null) {
                // Mirrors VoidMinerUtility: an unresolvable (material, stone) pair stops the expansion.
                break;
            }
            if (isVoidMinerBlacklisted(stack)) continue;
            consumer.acceptSmallOreDrop(stack, piece.mAmount);
        }
    }

    /**
     * One-time append of every enabled poor ore worldgen entry into the vanilla void miner drop maps
     * ({@link VoidMinerUtility#addBlockToDimensionList}), so the GT void miners can also produce poor ores.
     * <p>
     * Timing contract: must run after {@code VoidMinerUtility.generateDropMaps()} (BartWorks runs it in its
     * FMLLoadCompleteEvent handler, which would otherwise rebuild the maps from scratch) and before the first
     * void miner machine calls {@code calculateDropMap()} on its first tick (the alias distribution cache is
     * built there and would freeze the map without the appended entries). The server starting event satisfies
     * both bounds in every environment; the flag makes repeated server starts idempotent.
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
                // Same dimension filter semantics as VoidMinerUtility.generateDropMaps: a dimension unknown
                // to the GT dimension registry still gets a drop map, non void minable ones are skipped.
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
     * Routes one poor ore drop into the void miner drop map of a dimension through the public
     * {@link VoidMinerUtility#addBlockToDimensionList(String, Block, int, float)} API, which only accepts a
     * Block + meta pair. The GT ore items are block items, so the reverse lookup is lossless; anything else
     * is reported as an error instead of being silently dropped.
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
