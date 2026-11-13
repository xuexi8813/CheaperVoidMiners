package com.jdz.vmd.api.recipe;

import static bwcrossmod.galacticgreg.VoidMinerFrontend.MAX_ITEM_OUTPUTS;

import com.gtnewhorizons.modularui.common.widget.ProgressBar;

import bwcrossmod.galacticgreg.VoidMinerFrontend;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;

/**
 * NEI output recipe maps of this mod, following the pattern of the GT void miner
 * ({@code RecipeMaps.voidMinerRecipes}): one recipe per minable dimension, the dimension display block
 * (stack size 0) as input and every producible entry as weighted output (chances in units of 0.01%).
 * <p>
 * The maps are plain static fields: constructing a {@link RecipeMap} automatically registers its recipe
 * category with GT, and GT's NEI plugin builds a NEI handler for every registered category and registers
 * every {@code RecipeMapWorkable} machine as catalyst through
 * {@code getAvailableRecipeMaps()}; no additional NEI plugin code is needed on our side.
 * <p>
 * {@link #register()} only forces the class to load early in the mod loading phase, so the categories
 * exist before NEI builds its handler list (which may happen during postInit, before LoadComplete).
 */
public final class VmdRecipeMaps {

    /**
     * Fluid output limit of the fluid driller map; the underground fluid tables of GTNH stay well below
     * this, and dimensions with more entries are simply not represented fully in NEI.
     */
    public static final int MAX_FLUID_OUTPUTS = 32;

    /** Output recipes of the Void Fluid Drillers and the Wooden Void Fluid Pump: fluids of a dimension. */
    public static final RecipeMap<RecipeMapBackend> voidFluidDrillerRecipes = RecipeMapBuilder
        .of("vmd.recipe.voidfluiddriller")
        .maxIO(1, 0, 0, MAX_FLUID_OUTPUTS)
        .minInputs(1, 0)
        .neiHandlerInfo(
            builder -> builder.setHeight(240)
                .setMultipleWidgetsAllowed(false))
        .build();

    /** Output recipes of the Void Excavators and the Wooden Void Excavator: config table resources. */
    public static final RecipeMap<RecipeMapBackend> voidExcavatorRecipes = RecipeMapBuilder
        .of("vmd.recipe.voidexcavator")
        .maxIO(1, MAX_ITEM_OUTPUTS, 0, 0)
        .minInputs(1, 0)
        .progressBar(GTUITextures.PROGRESSBAR_HAMMER, ProgressBar.Direction.DOWN)
        .progressBarPos(75, 26)
        .neiHandlerInfo(
            builder -> builder.setHeight(240)
                .setMultipleWidgetsAllowed(false))
        .frontend(VoidMinerFrontend::new)
        .build();

    /** Output recipes of the Wooden Void Miner: the full Overworld mixed pool (veins + poor ores). */
    public static final RecipeMap<RecipeMapBackend> woodenMinerRecipes = RecipeMapBuilder.of("vmd.recipe.woodenminer")
        .maxIO(1, MAX_ITEM_OUTPUTS, 0, 0)
        .minInputs(1, 0)
        .progressBar(GTUITextures.PROGRESSBAR_HAMMER, ProgressBar.Direction.DOWN)
        .progressBarPos(75, 26)
        .neiHandlerInfo(
            builder -> builder.setHeight(240)
                .setMultipleWidgetsAllowed(false))
        .frontend(VoidMinerFrontend::new)
        .build();

    private VmdRecipeMaps() {}

    /** No-op hook that forces the class to load; see the class comment for the timing rationale. */
    public static void register() {}
}
