package com.jdz.vmd.common.loaders;

import static gregtech.api.enums.GTValues.RA;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Mods;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;

public class RecipeLoader {

    private static final Logger LOG = LogManager.getLogger("CheaperVoidMiners");

    public static void run() {
        registerVoidMinerConversionRecipes();
        registerDrillerAssemblerRecipes();
        registerWoodenPumpCraftingRecipe();
        registerWoodenMinerCraftingRecipe();
    }

    private static void registerVoidMinerConversionRecipes() {
        GTModHandler
            .addShapelessCraftingRecipe(ItemRegistry.VoidMinerEX1, new Object[] { ItemList.Machine_LV_Miner.get(1) });
        GTModHandler
            .addShapelessCraftingRecipe(ItemRegistry.VoidMinerEX2, new Object[] { ItemList.Machine_HV_Miner.get(1) });
        GTModHandler.addShapelessCraftingRecipe(ItemRegistry.VoidMinerEX3, new Object[] { ItemList.OreDrill1.get(1) });
    }

    /**
     * Assembler recipes of the five Void Fluid Driller stages. Every input stack is validated before the
     * recipe is registered; a missing GT item aborts that machine's recipe with an error log instead of
     * silently producing a broken recipe.
     * <p>
     * All five recipes consume one programmed circuit with configuration 12 and scale frame count and
     * duration with the stage ladder.
     */
    private static void registerDrillerAssemblerRecipes() {
        // Drill heads are shared tooling of the family; they must all exist or the whole ladder is broken.
        validateDrillHeads();

        // Iron stage, LV: 4 casings + 4 frames + super tank + pump + starter drill head + circuit 12.
        addDrillerAssemblerRecipe(
            ItemRegistry.VoidFluidDrillerIron,
            ItemList.Casing_ULV,
            Materials.Iron,
            4,
            ItemList.Super_Tank_LV,
            ItemList.Electric_Pump_LV,
            Materials.Iron,
            TierEU.RECIPE_LV,
            100);
        addDrillerAssemblerRecipe(
            ItemRegistry.VoidFluidDrillerSteel,
            ItemList.Casing_SolidSteel,
            Materials.Steel,
            4,
            ItemList.Super_Tank_MV,
            ItemList.Electric_Pump_MV,
            Materials.Steel,
            TierEU.RECIPE_MV,
            200);
        addDrillerAssemblerRecipe(
            ItemRegistry.VoidFluidDrillerTitanium,
            ItemList.Casing_StableTitanium,
            Materials.Titanium,
            4,
            ItemList.Super_Tank_HV,
            ItemList.Electric_Pump_HV,
            Materials.Titanium,
            TierEU.RECIPE_HV,
            400);
        addDrillerAssemblerRecipe(
            ItemRegistry.VoidFluidDrillerNaquadah,
            // No dedicated Naquadah machine casing exists; the tungstensteel drilling casing stands in.
            ItemList.Casing_RobustTungstenSteel,
            Materials.Naquadah,
            8,
            ItemList.Super_Tank_IV,
            ItemList.Electric_Pump_LuV,
            Materials.Naquadah,
            TierEU.RECIPE_LuV,
            800);
        addDrillerAssemblerRecipe(
            ItemRegistry.VoidFluidDrillerInfinity,
            // No dedicated Infinity machine casing exists; the neutronium mining casing stands in.
            ItemList.Casing_MiningNeutronium,
            Materials.Infinity,
            8,
            ItemList.Super_Tank_IV,
            ItemList.Electric_Pump_UV,
            Materials.Infinity,
            TierEU.RECIPE_UV,
            1600);
    }

    /** Fails fast (with an error log) when one of the shared drill head stacks is not registered. */
    private static void validateDrillHeads() {
        for (Materials material : new Materials[] { Materials.Iron, Materials.Steel, Materials.Titanium,
            Materials.Naquadah, Materials.Infinity }) {
            if (GTOreDictUnificator.get(OrePrefixes.toolHeadDrill, material, 1) == null) {
                LOG.error(
                    "OrePrefixes.toolHeadDrill stack for material {} is missing, Void Fluid Driller recipes may be broken",
                    material.mName);
            }
        }
    }

    private static void addDrillerAssemblerRecipe(ItemStack output, ItemList casing, Materials frameMaterial,
        int frameCount, ItemList superTank, ItemList pump, Materials drillMaterial, long euTier, int durationTicks) {
        final ItemStack casingStack = casing.get(4);
        final ItemStack frameStack = GTOreDictUnificator.get(OrePrefixes.frameGt, frameMaterial, frameCount);
        final ItemStack tankStack = superTank.get(1);
        final ItemStack pumpStack = pump.get(1);
        final ItemStack drillStack = GTOreDictUnificator.get(OrePrefixes.toolHeadDrill, drillMaterial, 1);
        // The configured programming circuit (configuration 12) required by the confirmed design.
        final ItemStack circuitStack = ItemList.Circuit_Integrated.getWithDamage(1L, 12L);

        if (casingStack == null || frameStack == null
            || tankStack == null
            || pumpStack == null
            || drillStack == null
            || circuitStack == null) {
            LOG.error(
                "Cannot register assembler recipe for {}: a required GT item stack is missing (casing={}, frame={}, tank={}, pump={}, drill={}, circuit={})",
                output.getDisplayName(),
                casingStack != null,
                frameStack != null,
                tankStack != null,
                pumpStack != null,
                drillStack != null,
                circuitStack != null);
            return;
        }

        RA.stdBuilder()
            .itemInputs(casingStack, frameStack, tankStack, pumpStack, drillStack, circuitStack)
            .itemOutputs(output)
            .duration(durationTicks)
            .eut(euTier)
            .addTo(assemblerRecipes);
    }

    /**
     * Shaped crafting recipe of the Wooden Void Fluid Pump:
     *
     * <pre>
     * log        bronze fluid pipe (medium)   log
     * log        fired clay bucket            log
     * log        log                          log
     * </pre>
     *
     * The fired clay bucket comes from Iguana Tweaks; when that mod is absent the recipe is skipped with an
     * error log, the machine itself stays registered but is unobtainable.
     */
    private static void registerWoodenPumpCraftingRecipe() {
        final ItemStack clayBucket = getFiredClayBucket();
        if (clayBucket == null) {
            LOG.error("Iguana Tweaks fired clay bucket not found, Wooden Void Fluid Pump crafting recipe skipped");
            return;
        }

        GTModHandler.addCraftingRecipe(
            ItemRegistry.VoidFluidPumpWooden,
            GTModHandler.RecipeBits.BUFFERED | GTModHandler.RecipeBits.DO_NOT_CHECK_FOR_COLLISIONS,
            new Object[] { "LPL", "LBL", "LLL", 'L', OrePrefixes.log.get(Materials.Wood), 'P',
                OrePrefixes.pipeMedium.get(Materials.Bronze), 'B', clayBucket });
    }

    /**
     * Shaped crafting recipe of the Wooden Void Miner:
     *
     * <pre>
     * log    flint    log
     * log    stick    log
     * log    flint    log
     * </pre>
     *
     * The log uses the {@code logWood} ore dictionary entry (via {@link OrePrefixes#log} of Wood, the same
     * spelling as the Wooden Void Fluid Pump recipe), the flint is the vanilla item (with wildcard damage,
     * the same spelling as the GT5u crafting recipe loader) and the stick is the {@code stickWood} ore
     * dictionary entry (GT's recipe handler resolves plain strings against the ore dictionary, the same
     * spelling as the BartWorks crafting recipes).
     */
    private static void registerWoodenMinerCraftingRecipe() {
        GTModHandler.addCraftingRecipe(
            ItemRegistry.VoidMinerWooden,
            GTModHandler.RecipeBits.BUFFERED | GTModHandler.RecipeBits.DO_NOT_CHECK_FOR_COLLISIONS,
            new Object[] { "LFL", "LSL", "LFL", 'L', OrePrefixes.log.get(Materials.Wood), 'F',
                new ItemStack(Items.flint, 1, 32767), 'S', "stickWood" });
    }

    /** The fired clay bucket, following the same lookup as GT's own container registration. */
    private static ItemStack getFiredClayBucket() {
        if (!Mods.IguanaTweaksTinkerConstruct.isModLoaded()) return null;
        return GTModHandler.getModItem(Mods.IguanaTweaksTinkerConstruct.ID, "clayBucketFired", 1L, 0);
    }
}
