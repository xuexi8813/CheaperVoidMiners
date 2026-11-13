package com.jdz.vmd.common.tileentities;

import static gregtech.api.objects.XSTR.XSTR_INSTANCE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.BiMap;

import gregtech.GTMod;
import gregtech.api.objects.GTUODimension;
import gregtech.api.objects.GTUOFluid;
import gtneioreplugin.util.DimensionHelper;

/**
 * Shared access layer over the GT5u underground fluid tables ({@code GTMod.proxy.mUndergroundOil}).
 * <p>
 * Motivation: both the Void Fluid Drillers and the Wooden Void Fluid Pump select fluids from the same
 * per-dimension fluid tables, so dimension key mapping, fluid candidate extraction and the weighted random
 * pick live here once instead of being duplicated per machine.
 */
public final class VoidFluidTableHelper {

    /** Key of the Overworld entry in the underground fluid dimension list ({@link gtneioreplugin} "Ow"). */
    public static final String OVERWORLD_DIMENSION_KEY = "0";

    private static final Logger LOG = LogManager.getLogger("CheaperVoidMiners");

    private VoidFluidTableHelper() {}

    /** Root access to the underground fluid dimension list, keyed by dimension id or provider name fragment. */
    public static BiMap<String, GTUODimension> getDimensionList() {
        return GTMod.proxy.mUndergroundOil.getDimensionList();
    }

    /**
     * The Overworld underground fluid table. Returns null when GT shipped no Overworld entry (broken or
     * emptied config); callers treat that as "no fluid data".
     */
    @Nullable
    public static GTUODimension getOverworldDimension() {
        return getDimensionList().get(OVERWORLD_DIMENSION_KEY);
    }

    /**
     * Finds the underground fluid dimension entry behind a GTNEI dimension abbreviation (e.g. "Ow", "Mo").
     * Returns null when the abbreviation is unknown to the GT5u fluid tables.
     */
    @Nullable
    public static GTUODimension findDimensionByAbbr(String abbr) {
        for (Map.Entry<String, GTUODimension> entry : getDimensionList().entrySet()) {
            if (abbr.equals(getAbbrForUoKey(entry.getKey()))) return entry.getValue();
        }
        return null;
    }

    /** Reverse lookup: abbreviation of a resolved dimension entry, or null when it has no GTNEI entry. */
    @Nullable
    public static String getAbbrOfDimension(GTUODimension dimension) {
        for (Map.Entry<String, GTUODimension> entry : getDimensionList().entrySet()) {
            if (entry.getValue() == dimension) return getAbbrForUoKey(entry.getKey());
        }
        return null;
    }

    /**
     * Maps a key of the underground fluid dimension list to its GTNEI abbreviation.
     * <p>
     * The list is keyed either by numeric dimension id (only the Overworld, id 0) or by a world provider class
     * name fragment ("Moon", "BarnardaC", ...). This replicates the mapping of
     * {@code gtneioreplugin.util.GT5UndergroundFluidHelper#init}, which is the canonical bridge between the two
     * data sets.
     */
    @Nullable
    public static String getAbbrForUoKey(String rawDimension) {
        try {
            final int id = Integer.parseInt(rawDimension);
            return id == 0 ? "Ow" : null;
        } catch (NumberFormatException e) {
            // Not a numeric key, fall through to the name fragment matching below.
            LOG.debug("Skipping non-numeric underground fluid dimension key '{}'", rawDimension, e);
        }
        switch (rawDimension) {
            case "aCentauriBb" -> {
                return "CB";
            }
            case "BarnardaC" -> {
                return "BC";
            }
            case "BarnardaE" -> {
                return "BE";
            }
            case "BarnardaF" -> {
                return "BF";
            }
            case "TCetiE" -> {
                return "TE";
            }
        }
        for (DimensionHelper.Dimension record : DimensionHelper.getAllDim()) {
            if (record.trimmedName()
                .equalsIgnoreCase(rawDimension)) return record.abbr();
        }
        return null;
    }

    /**
     * Extractable fluids of a dimension, in table (config) order, skipping fluids unknown to the
     * FluidRegistry. Never null; empty when the dimension has no usable fluid data.
     */
    public static List<GTUOFluid> getFluidCandidates(@Nullable GTUODimension dimension) {
        final List<GTUOFluid> candidates = new ArrayList<>();
        if (dimension == null) return candidates;
        for (GTUOFluid fluid : dimension.getFluids()
            .values()) {
            if (fluid.getFluid() != null) candidates.add(fluid);
        }
        return candidates;
    }

    /**
     * Weighted random pick over the fluid table using the configured generation chance as weight.
     * The loop partitions the range [0, total) exactly, so the final return is unreachable but required by
     * the compiler.
     */
    public static GTUOFluid weightedRandom(List<GTUOFluid> candidates) {
        long total = 0;
        for (GTUOFluid fluid : candidates) {
            total += Math.max(0, fluid.Chance);
        }
        if (total <= 0) return candidates.get(XSTR_INSTANCE.nextInt(candidates.size()));

        int roll = XSTR_INSTANCE.nextInt((int) Math.min(total, Integer.MAX_VALUE));
        for (GTUOFluid fluid : candidates) {
            roll -= Math.max(0, fluid.Chance);
            if (roll < 0) return fluid;
        }
        return candidates.get(candidates.size() - 1);
    }
}
