package com.jdz.vmd.common.loaders;

import static gregtech.api.enums.GTValues.RA;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;

import net.minecraft.init.Blocks;
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
        registerWoodenExcavatorCraftingRecipe();
        registerExcavatorAssemblerRecipes();
    }

    private static void registerVoidMinerConversionRecipes() {
        GTModHandler
            .addShapelessCraftingRecipe(ItemRegistry.VoidMinerEX1, new Object[] { ItemList.Machine_LV_Miner.get(1) });
        GTModHandler
            .addShapelessCraftingRecipe(ItemRegistry.VoidMinerEX2, new Object[] { ItemList.Machine_HV_Miner.get(1) });
        GTModHandler.addShapelessCraftingRecipe(ItemRegistry.VoidMinerEX3, new Object[] { ItemList.OreDrill1.get(1) });
    }

    /**
     * 五个虚空流体钻机阶段的装配机配方。每份输入物品堆都在注册配方前进行校验；GT 物品缺失时
     * 会以一条错误日志中止该机器的配方，而不是静默地生成一条损坏的配方。
     * <p>
     * 五条配方均消耗一个配置 12 的编程电路，框架数量与配方时长随阶段阶梯递增。
     */
    private static void registerDrillerAssemblerRecipes() {
        // 钻头是这一系列机器共用的工具；它们必须全部存在，否则整个阶梯都是坏的。
        validateDrillHeads();

        // 铁阶段，LV：4 机壳 + 4 框架 + 超级储罐 + 泵 + 入门钻头 + 电路 12。
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

    /** 任一共用钻头物品堆未注册时立即失败（输出错误日志）。 */
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
        // 已确认设计中要求的规定编程电路（配置 12）。
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
     * 木制虚空流体泵的有序合成配方：
     *
     * <pre>
     * 原木       青铜流体管道（中型）         原木
     * 原木       烧制粘土桶                   原木
     * 原木       原木                         原木
     * </pre>
     *
     * 烧制粘土桶来自 Iguana Tweaks；该模组缺失时会以一条错误日志跳过该配方，
     * 机器本身仍保持注册状态，但无法获取。
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
     * 木制虚空采矿机的有序合成配方：
     *
     * <pre>
     * 原木    燧石    原木
     * 原木    木棍    原木
     * 原木    燧石    原木
     * </pre>
     *
     * 原木使用 {@code logWood} 矿物词典条目（通过 Wood 的 {@link OrePrefixes#log}，与木制虚空流体泵
     * 配方的写法相同），燧石为原版物品（带通配耐久值，与 GT5u 合成配方加载器的写法相同），
     * 木棍为 {@code stickWood} 矿物词典条目（GT 的配方处理器会将普通字符串按矿物词典解析，
     * 与 BartWorks 合成配方的写法相同）。
     */
    private static void registerWoodenMinerCraftingRecipe() {
        GTModHandler.addCraftingRecipe(
            ItemRegistry.VoidMinerWooden,
            GTModHandler.RecipeBits.BUFFERED | GTModHandler.RecipeBits.DO_NOT_CHECK_FOR_COLLISIONS,
            new Object[] { "LFL", "LSL", "LFL", 'L', OrePrefixes.log.get(Materials.Wood), 'F',
                new ItemStack(Items.flint, 1, 32767), 'S', "stickWood" });
    }

    /**
     * 木制虚空挖掘机的有序合成配方：
     *
     * <pre>
     * 原木    燧石    原木
     * 原木    泥土    原木
     * 原木    燧石    原木
     * </pre>
     *
     * 写法与木制虚空流体泵及采矿机配方相同；泥土为原版方块物品
     * （GT 自身对泥土物品堆所用的写法，例如废纸箱掉落表）。
     */
    private static void registerWoodenExcavatorCraftingRecipe() {
        GTModHandler.addCraftingRecipe(
            ItemRegistry.VoidExcavatorWooden,
            GTModHandler.RecipeBits.BUFFERED | GTModHandler.RecipeBits.DO_NOT_CHECK_FOR_COLLISIONS,
            new Object[] { "LFL", "LDL", "LFL", 'L', OrePrefixes.log.get(Materials.Wood), 'F',
                new ItemStack(Items.flint, 1, 32767), 'D', new ItemStack(Blocks.dirt, 1, 0) });
    }

    /**
     * 五个虚空挖掘机阶段的装配机配方，每级均为同一扁平模式：
     * 机壳 x4 + frameGt x4（Naquadah/Infinity 为 x8）+ 钻头 x1 + 传送带模块 x2 + 电路 12 x1。
     * <p>
     * 每个阶段的传送带模块使用与机器电压匹配的传送带等级
     * （{@code ItemList.Conveyor_Module_*}，GT 自身装配机配方中传送带模块所用的写法，
     * 例如无人机中心配方）。所有输入物品堆在注册前都会校验；GT 物品缺失时会以一条错误日志
     * 中止该机器的配方，而不是静默地生成一条损坏的配方。
     */
    private static void registerExcavatorAssemblerRecipes() {
        addExcavatorAssemblerRecipe(
            ItemRegistry.VoidExcavatorIron,
            ItemList.Casing_ULV,
            Materials.Iron,
            4,
            ItemList.Conveyor_Module_LV,
            TierEU.RECIPE_LV,
            100);
        addExcavatorAssemblerRecipe(
            ItemRegistry.VoidExcavatorSteel,
            ItemList.Casing_SolidSteel,
            Materials.Steel,
            4,
            ItemList.Conveyor_Module_MV,
            TierEU.RECIPE_MV,
            200);
        addExcavatorAssemblerRecipe(
            ItemRegistry.VoidExcavatorTitanium,
            ItemList.Casing_StableTitanium,
            Materials.Titanium,
            4,
            ItemList.Conveyor_Module_HV,
            TierEU.RECIPE_HV,
            400);
        addExcavatorAssemblerRecipe(
            ItemRegistry.VoidExcavatorNaquadah,
            // 不存在专用的 Naquadah 机器机壳；以钨钢钻井机壳代替。
            ItemList.Casing_RobustTungstenSteel,
            Materials.Naquadah,
            8,
            ItemList.Conveyor_Module_LuV,
            TierEU.RECIPE_LuV,
            800);
        addExcavatorAssemblerRecipe(
            ItemRegistry.VoidExcavatorInfinity,
            // 不存在专用的 Infinity 机器机壳；以中子素采矿机壳代替。
            ItemList.Casing_MiningNeutronium,
            Materials.Infinity,
            8,
            ItemList.Conveyor_Module_UV,
            TierEU.RECIPE_UV,
            1600);
    }

    /**
     * 注册一条挖掘机装配机配方；共用钻头由 {@link #validateDrillHeads()} 校验。
     * 钻头材料遵循阶段阶梯，与框架材料一致（铁/钢/钛/Naquadah/Infinity）。
     */
    private static void addExcavatorAssemblerRecipe(ItemStack output, ItemList casing, Materials frameMaterial,
        int frameCount, ItemList conveyor, long euTier, int durationTicks) {
        final ItemStack casingStack = casing.get(4);
        final ItemStack frameStack = GTOreDictUnificator.get(OrePrefixes.frameGt, frameMaterial, frameCount);
        final ItemStack conveyorStack = conveyor.get(2);
        final ItemStack drillStack = GTOreDictUnificator.get(OrePrefixes.toolHeadDrill, frameMaterial, 1);
        final ItemStack circuitStack = ItemList.Circuit_Integrated.getWithDamage(1L, 12L);

        if (casingStack == null || frameStack == null
            || conveyorStack == null
            || drillStack == null
            || circuitStack == null) {
            LOG.error(
                "Cannot register assembler recipe for {}: a required GT item stack is missing (casing={}, frame={}, conveyor={}, drill={}, circuit={})",
                output.getDisplayName(),
                casingStack != null,
                frameStack != null,
                conveyorStack != null,
                drillStack != null,
                circuitStack != null);
            return;
        }

        RA.stdBuilder()
            .itemInputs(casingStack, frameStack, conveyorStack, drillStack, circuitStack)
            .itemOutputs(output)
            .duration(durationTicks)
            .eut(euTier)
            .addTo(assemblerRecipes);
    }

    /** 烧制粘土桶，采用与 GT 自身容器注册相同的查找方式。 */
    private static ItemStack getFiredClayBucket() {
        if (!Mods.IguanaTweaksTinkerConstruct.isModLoaded()) return null;
        return GTModHandler.getModItem(Mods.IguanaTweaksTinkerConstruct.ID, "clayBucketFired", 1L, 0);
    }
}
