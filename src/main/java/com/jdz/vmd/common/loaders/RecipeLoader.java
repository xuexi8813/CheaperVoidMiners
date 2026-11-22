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
        registerSurfaceGasSiphonAssemblerRecipe();
        registerWoodenPumpCraftingRecipe();
        registerWoodenMinerCraftingRecipe();
    }

    /**
     * 三台虚空采矿机的 EX 升级档无序兑换配方（原版对应档机器 → EX 版）；输入物品堆注册前校验，
     * GT 物品缺失时记错误日志并跳过该条，不生成损坏配方。
     */
    private static void registerVoidMinerConversionRecipes() {
        addVoidMinerConversionRecipe(ItemRegistry.VoidMinerEX1, ItemList.Machine_LV_Miner);
        addVoidMinerConversionRecipe(ItemRegistry.VoidMinerEX2, ItemList.Machine_HV_Miner);
        addVoidMinerConversionRecipe(ItemRegistry.VoidMinerEX3, ItemList.OreDrill1);
    }

    private static void addVoidMinerConversionRecipe(ItemStack output, ItemList inputMachine) {
        final ItemStack inputStack = inputMachine.get(1);
        if (inputStack == null) {
            LOG.error(
                "Cannot register shapeless conversion recipe for {}: a required GT item stack is missing (input={})",
                output.getDisplayName(),
                inputMachine.name());
            return;
        }
        GTModHandler.addShapelessCraftingRecipe(output, new Object[] { inputStack });
    }

    /**
     * 五个虚空流体钻机阶段的装配机配方；输入物品堆注册前校验，GT 物品缺失时记错误日志并跳过，
     * 不生成损坏配方。配置 12 的编程电路为不消耗的配置标记（GT5u notConsumable 惯例：stackSize=0
     * 的输入仍为启动必需项，但配方执行与 SingleRecipeCheck 消耗阶段均跳过 0 消耗量），框架数与时长随阶段递增。
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
            // 不存在专用的 Naquadah 机器机壳；以坚固钨钢机器机壳代替。
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

    /**
     * 地表行星气体钻机的装配机配方：硅岩档虚空流体钻机成品 + GT5u 原版行星气体钻机成品
     * （{@code ItemList.PlanetarySiphon}，gtnhintergalactic 于 init 阶段注册）+ 按项目组装机风格的
     * 配套材料（坚固钨钢机器机壳、钨钢框架、超级储罐、电泵、硅岩钻头）。配置 1 的编程电路为
     * 不消耗的配置标记（stackSize=0 的 notConsumable 惯例，与钻机配方一致，语义对应本机电路最低档）。
     */
    private static void registerSurfaceGasSiphonAssemblerRecipe() {
        final ItemStack casingStack = ItemList.Casing_RobustTungstenSteel.get(8);
        final ItemStack frameStack = GTOreDictUnificator.get(OrePrefixes.frameGt, Materials.TungstenSteel, 8);
        final ItemStack tankStack = ItemList.Super_Tank_IV.get(1);
        final ItemStack pumpStack = ItemList.Electric_Pump_LuV.get(1);
        final ItemStack drillStack = GTOreDictUnificator.get(OrePrefixes.toolHeadDrill, Materials.Naquadah, 1);
        final ItemStack vanillaSiphonStack = ItemList.PlanetarySiphon.get(1);
        final ItemStack drillerStack = ItemRegistry.VoidFluidDrillerNaquadah;
        // 数量 0 = notConsumable 标记：配置 1 的电路仍为启动必需项，但装配机运行时不消耗。
        final ItemStack circuitStack = ItemList.Circuit_Integrated.getWithDamage(0L, 1L);

        if (casingStack == null || frameStack == null
            || tankStack == null
            || pumpStack == null
            || drillStack == null
            || vanillaSiphonStack == null
            || drillerStack == null
            || circuitStack == null) {
            LOG.error(
                "Cannot register assembler recipe for {}: a required item stack is missing (casing={}, frame={}, tank={}, pump={}, drill={}, vanillaSiphon={}, driller={}, circuit={})",
                ItemRegistry.SurfacePlanetaryGasSiphon.getDisplayName(),
                casingStack != null,
                frameStack != null,
                tankStack != null,
                pumpStack != null,
                drillStack != null,
                vanillaSiphonStack != null,
                drillerStack != null,
                circuitStack != null);
            return;
        }

        RA.stdBuilder()
            .itemInputs(
                casingStack,
                frameStack,
                tankStack,
                pumpStack,
                drillStack,
                vanillaSiphonStack,
                drillerStack,
                circuitStack)
            .itemOutputs(ItemRegistry.SurfacePlanetaryGasSiphon)
            .duration(1600)
            .eut(TierEU.RECIPE_UV)
            .addTo(assemblerRecipes);
    }

    private static void addDrillerAssemblerRecipe(ItemStack output, ItemList casing, Materials frameMaterial,
        int frameCount, ItemList superTank, ItemList pump, Materials drillMaterial, long euTier, int durationTicks) {
        final ItemStack casingStack = casing.get(4);
        final ItemStack frameStack = GTOreDictUnificator.get(OrePrefixes.frameGt, frameMaterial, frameCount);
        final ItemStack tankStack = superTank.get(1);
        final ItemStack pumpStack = pump.get(1);
        final ItemStack drillStack = GTOreDictUnificator.get(OrePrefixes.toolHeadDrill, drillMaterial, 1);
        // 数量 0 = notConsumable 标记：电路仍为启动必需的配置标记（NEI 电路槽位显示），
        // 但装配机运行时不消耗。GT5u 同款惯例见 Shape_Extruder_* 模具配方（get(0L)）。
        final ItemStack circuitStack = ItemList.Circuit_Integrated.getWithDamage(0L, 12L);

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
