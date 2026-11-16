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
     * 五个虚空流体钻机阶段的装配机配方；输入物品堆注册前校验，GT 物品缺失时记错误日志并跳过，
     * 不生成损坏配方。均消耗配置 12 的编程电路，框架数与时长随阶段递增。
     */
    private static void registerDrillerAssemblerRecipes() {
        // 钻头为全系列共用，缺一则整个阶梯失效。
        validateDrillHeads();

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
            // 不存在专用的 Naquadah 机器机壳；以钨钢钻井机壳代替。
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
            // 不存在专用的 Infinity 机器机壳；以中子素采矿机壳代替。
            ItemList.Casing_MiningNeutronium,
            Materials.Infinity,
            8,
            ItemList.Super_Tank_IV,
            ItemList.Electric_Pump_UV,
            Materials.Infinity,
            TierEU.RECIPE_UV,
            1600);
    }

    /** 校验钻机装配机配方所需钻头物品堆的存在性，缺失时输出错误日志。 */
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
     * 木制虚空流体泵的有序合成配方（原木 + 青铜中型流体管道 + 烧制粘土桶）。
     * 烧制粘土桶来自 Iguana Tweaks；缺失时记错误日志跳过该配方，机器仍保持注册。
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
     * 木制虚空采矿机的有序合成配方（原木 + 燧石 + 木棍）；
     * 字符串输入（"stickWood"）由 GT 配方处理器按矿物词典解析，与 BartWorks 写法相同。
     */
    private static void registerWoodenMinerCraftingRecipe() {
        GTModHandler.addCraftingRecipe(
            ItemRegistry.VoidMinerWooden,
            GTModHandler.RecipeBits.BUFFERED | GTModHandler.RecipeBits.DO_NOT_CHECK_FOR_COLLISIONS,
            new Object[] { "LFL", "LSL", "LFL", 'L', OrePrefixes.log.get(Materials.Wood), 'F',
                new ItemStack(Items.flint, 1, 32767), 'S', "stickWood" });
    }

    private static ItemStack getFiredClayBucket() {
        if (!Mods.IguanaTweaksTinkerConstruct.isModLoaded()) return null;
        return GTModHandler.getModItem(Mods.IguanaTweaksTinkerConstruct.ID, "clayBucketFired", 1L, 0);
    }
}
