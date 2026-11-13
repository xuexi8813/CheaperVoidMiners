package com.jdz.vmd.common.tileentities;

import static gregtech.api.objects.XSTR.XSTR_INSTANCE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.bsideup.jabel.Desugar;
import com.jdz.vmd.Config;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.Mods;
import gtneioreplugin.plugin.item.ItemDimensionDisplay;
import gtneioreplugin.util.DimensionHelper;

/**
 * Shared resource table of the Void Excavators, driven by the config file (see {@link Config}).
 * <p>
 * Motivation: both the wooden and the multiblock excavators dig "non ore resources" of a chosen dimension
 * from the void, so the config parsing, block resolution and weighted random pick live here once instead of
 * being duplicated per machine. Tables are keyed by the dimension abbreviations of the GTNEI dimension
 * display blocks ("Ow", "Mo", ...).
 * <p>
 * The config is parsed lazily once per session; block ids are resolved against the GameRegistry and broken
 * entries (unknown block, malformed numbers) are reported as errors and skipped, so a broken or absent mod
 * block degrades to a smaller table instead of crashing the server.
 */
public final class VoidExcavatorResourceTable {

    private static final Logger LOG = LogManager.getLogger("CheaperVoidMiners");

    /** Abbreviation of the dimension whose table is used when no dimension display block is present. */
    public static final String DEFAULT_DIMENSION = "Ow";

    /** One weighted resource entry of a dimension table. */
    @Desugar
    public record WeightedResource(ItemStack stack, float weight) {}

    private static final Map<String, List<WeightedResource>> TABLES = new HashMap<>();

    private static boolean parsed = false;

    private VoidExcavatorResourceTable() {}

    /**
     * Resources of a dimension, in config order. Never null; empty when the dimension has no (valid) table.
     * Triggers the one time config parse on first use.
     */
    public static List<WeightedResource> getResources(@Nullable String dimensionAbbr) {
        if (!parsed) parseConfig();
        if (dimensionAbbr == null) return new ArrayList<>();
        return TABLES.getOrDefault(dimensionAbbr, new ArrayList<>());
    }

    /**
     * Weighted random pick over a table using the configured weight; the roll partitions [0, total) exactly
     * like the other wooden family helpers.
     */
    public static WeightedResource weightedRandom(List<WeightedResource> resources) {
        float total = 0;
        for (WeightedResource resource : resources) {
            total += Math.max(0, resource.weight());
        }
        if (total <= 0) return resources.get(XSTR_INSTANCE.nextInt(resources.size()));

        float roll = XSTR_INSTANCE.nextFloat() * total;
        for (WeightedResource resource : resources) {
            roll -= Math.max(0, resource.weight());
            if (roll < 0) return resource;
        }
        return resources.get(resources.size() - 1);
    }

    /**
     * Dimension abbreviation of a dimension display block, or null when the stack is not one; used by the
     * machines to reject foreign items from their display slots.
     */
    @Nullable
    public static String getDimensionAbbr(@Nullable ItemStack stack) {
        if (stack == null) return null;
        return ItemDimensionDisplay.getDimension(stack);
    }

    /** Localized dimension name of an abbreviation, for tooltips and GUI lines; falls back to the abbr. */
    public static String getDimensionDisplayName(String dimensionAbbr) {
        final String internal = DimensionHelper.ABBR_TO_INTERNAL.get(dimensionAbbr);
        if (internal != null) return DimensionHelper.getDimLocalizedName(internal);
        return dimensionAbbr;
    }

    /** Parses every configured table once; malformed entries are logged and skipped. */
    private static void parseConfig() {
        parsed = true;
        for (Map.Entry<String, String[]> entry : Config.getExcavatorResourceTables()
            .entrySet()) {
            final List<WeightedResource> resources = new ArrayList<>();
            for (String rawEntry : entry.getValue()) {
                final WeightedResource resource = parseResource(rawEntry);
                if (resource != null) resources.add(resource);
            }
            TABLES.put(entry.getKey(), resources);
        }
    }

    /**
     * Parses one {@code blockid:meta:weight} entry; the blockid may carry a modid
     * ({@code GalacticraftCore:moonBlock}), making the entry four segments. Unresolvable blocks (e.g. a
     * Galacticraft block without that mod installed) are reported as errors instead of being skipped
     * silently.
     */
    @Nullable
    private static WeightedResource parseResource(String rawEntry) {
        final String[] segments = rawEntry.trim()
            .split(":");
        // 3 segments: "blockid:meta:weight"; 4 segments: "modid:blockname:meta:weight".
        if (segments.length != 3 && segments.length != 4) {
            LOG.error(
                "Invalid excavator resource entry '{}', expected [blockid:meta:weight] or [modid:blockname:meta:weight]",
                rawEntry);
            return null;
        }

        final String blockId = segments.length == 4 ? segments[0] + ":" + segments[1] : segments[0];
        // FML has no single argument findBlock; vanilla blocks live in the "Minecraft" namespace.
        final Block block = segments.length == 4 ? GameRegistry.findBlock(segments[0], segments[1])
            : GameRegistry.findBlock(Mods.Minecraft.ID, segments[0]);
        if (block == null || block == Blocks.air) {
            LOG.error("Excavator resource block '{}' not found, entry '{}' skipped", blockId, rawEntry);
            return null;
        }

        final int meta;
        final float weight;
        try {
            meta = Integer.parseInt(segments[segments.length - 2]);
            weight = Float.parseFloat(segments[segments.length - 1]);
        } catch (NumberFormatException e) {
            LOG.error("Invalid meta or weight in excavator resource entry '{}'", rawEntry, e);
            return null;
        }
        if (weight <= 0) {
            LOG.error("Non positive weight in excavator resource entry '{}'", rawEntry);
            return null;
        }
        return new WeightedResource(new ItemStack(block, 1, meta), weight);
    }
}
