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
 * Registers the NEI output recipes of this mod, one recipe per minable dimension, following the pattern of
 * the GT void miner ({@code VoidMinerRecipes}): the dimension display block (stack size 0) as input and
 * every producible entry as weighted output. The weights are expressed as output chances in units of 0.01%
 * ({@code round(10000 * weight / total)}, minimum 1) and the outputs are sorted by descending weight with
 * item id / meta (fluid id for fluids) as tiebreakers, exactly like the GT template.
 * <p>
 * Timing: called from the FMLLoadCompleteEvent handler, after the poor ore append into the GT void miner
 * drop maps, so the wooden miner mixed pool built below includes those poor ores on both sides of the
 * game (the client of a remote server builds the same config and worldgen driven data locally).
 */
public final class VmdOutputRecipeLoader {

    private static final Logger LOG = LogManager.getLogger("CheaperVoidMiners");

    /** Display amount of one fluid output entry, in mB; the machines produce their own amounts. */
    private static final int FLUID_DISPLAY_AMOUNT_MB = 1000;

    private static boolean registered = false;

    private VmdOutputRecipeLoader() {}

    /** Registers every NEI output recipe once; the flag guards against accidental double registration. */
    public static void register() {
        if (registered) return;
        registered = true;

        registerFluidDrillerRecipes();
        registerExcavatorRecipes();
        registerWoodenMinerRecipe();
    }

    /**
     * Void Fluid Driller / Wooden Void Fluid Pump: one recipe per dimension that has an underground fluid
     * table, listing the table fluids weighted by their generation chance.
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

            // Descending by weight, ties broken by fluid id, mirroring the GT void miner ordering.
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
     * Void Excavators / Wooden Void Excavator: one recipe per configured dimension that has a resource
     * table and a GTNEI dimension display block; dimensions unknown to GTNEI cannot be selected with the
     * display blocks and are skipped.
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
     * Wooden Void Miner: a single Overworld recipe listing the full mixed pool (vein materials weighted by
     * the vein weights plus poor ores weighted by their generation amount), i.e. what the machine produces
     * without a circuit.
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
     * Builds and adds one weighted item output recipe; empty weight pools (all weights <= 0) fall back to
     * an even distribution instead of registering chances that can never trigger.
     */
    private static void addItemWeightedRecipe(RecipeMap<RecipeMapBackend> recipeMap, Block marker,
        List<ItemStack> stacks, List<Float> weights) {
        float totalWeight = 0;
        for (float weight : weights) {
            totalWeight += Math.max(0, weight);
        }

        // Descending by weight, ties broken by item id then meta, mirroring the GT void miner ordering.
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

    /** Converts one weight into the 0.01% chance unit of the GT recipe system, minimum 1. */
    private static int toChance(float weight, float totalWeight, int entryCount) {
        if (totalWeight > 0) return Math.max(1, Math.round(10_000f * Math.max(0, weight) / totalWeight));
        // Even fallback for degenerate tables; keeps every entry reachable in NEI.
        return Math.max(1, Math.round(10_000f / entryCount));
    }

    /** The dimension display block input of an output recipe; stack size 0 marks it as a selector. */
    private static ItemStack toMarkerStack(Block marker) {
        final ItemStack markerStack = new ItemStack(marker);
        markerStack.stackSize = 0;
        return markerStack;
    }
}
