package com.jdz.vmd.common.tileentities;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.GTValues.VN;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofFrame;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import bwcrossmod.galacticgreg.MTEVoidMinerBase;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * EX 虚空采矿机基类，定制结构与产量。父类档位倍率是 byte，容不下所有 ×16 后的值，
 * 因此在 {@link #addOutputPartial} 叠乘 EX 与电压加成，保留原有惰性气体加成和批量模式。
 */
public abstract class MTEVoidMinerEXBase<T extends MTEVoidMinerEXBase<T>> extends MTEVoidMinerBase<T> {

    /** 惰性气体供给速率（L/s），复刻父类 MTEVoidMinerBase 的私有常量 NOBLE_GAS_CONSUMPTION_PER_TIER（不可直接访问）。 */
    private static final int NOBLE_GAS_SUPPLY_RATE_PER_SECOND = 2;

    /**
     * GT5u 5.09.54.275 每轮 10 tick，即每秒 2 轮。父类常量私有，不能直接引用。
     */
    private static final int BASE_ORE_OUTPUT_PER_SECOND = 2;

    /**
     * EX 固定产量倍率，乘在父类已计入档位和惰性气体加成的堆叠量上。
     */
    private static final int OUTPUT_ENHANCEMENT_FACTOR = 16;

    public MTEVoidMinerEXBase(int aID, String aName, String aNameRegional, int multiplier) {
        super(aID, aName, aNameRegional, multiplier);
    }

    public MTEVoidMinerEXBase(String aName, int tier) {
        super(aName, tier);
        casingTextureIndex = getControllerTextureIndex();
    }

    abstract public Materials getMaterials();

    abstract public ItemList getItemList();

    /** 结构定义按具体类仅构建一次并缓存（GT5u MTEDrillerBase 约定）；蓝图投影仪等外部调用方会高频读取。 */
    private static final Map<Class<?>, IStructureDefinition<?>> STRUCTURE_DEFINITIONS = new ConcurrentHashMap<>();

    @Override
    public IStructureDefinition<T> getStructureDefinition() {
        return cachedStructureDefinition();
    }

    @SuppressWarnings("unchecked")
    private IStructureDefinition<T> cachedStructureDefinition() {
        return (IStructureDefinition<T>) STRUCTURE_DEFINITIONS
            .computeIfAbsent(getClass(), k -> buildBaseStructureDefinition((Class<T>) k));
    }

    private IStructureDefinition<T> buildBaseStructureDefinition(Class<T> clazz) {
        return StructureDefinition.<T>builder()
            .addShape(
                "main",
                transpose(
                    new String[][] { { "   ", " G ", "   " }, { "   ", " G ", "   " }, { "   ", " G ", "   " },
                        { " G ", "GFG", " G " }, { " G ", "GFG", " G " }, { " G ", "GFG", " G " },
                        { "H~H", "HHH", "HHH" } }))
            .addElement(
                'F',
                ofBlock(
                    getItemList().getBlock(),
                    getItemList().get(0)
                        .getItemDamage()))
            .addElement('G', ofFrame(getMaterials()))
            .addElement(
                'H',
                buildHatchAdder(clazz).atLeast(InputHatch, OutputBus, InputBus, Maintenance, Energy)
                    .hint(1)
                    .casingIndex(getControllerTextureIndex())
                    .buildAndChain(
                        getItemList().getBlock(),
                        getItemList().get(0)
                            .getItemDamage()))
            .build();
    }

    /** 工具提示文案全部走 vmd.ex.* lang 键实现双语；动态数值（EU/t、气体速率、产出速度、电压档）由 translateToLocalFormatted 注入。 */
    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("vmd.ex.machine_type"))
            .addInfo(
                StatCollector
                    .translateToLocalFormatted("vmd.ex.eu", numberFormat.format(GTValues.VP[this.getMinTier()])))
            .addInfo(StatCollector.translateToLocalFormatted("vmd.ex.gas.intro", NOBLE_GAS_SUPPLY_RATE_PER_SECOND))
            .addInfo(
                createGasString(EnumChatFormatting.LIGHT_PURPLE, StatCollector.translateToLocal("vmd.ex.gas.neon"), 4))
            .addInfo(createGasString(EnumChatFormatting.AQUA, StatCollector.translateToLocal("vmd.ex.gas.krypton"), 8))
            .addInfo(
                createGasString(EnumChatFormatting.DARK_AQUA, StatCollector.translateToLocal("vmd.ex.gas.xenon"), 16))
            .addInfo(
                createGasString(EnumChatFormatting.BLUE, StatCollector.translateToLocal("vmd.ex.gas.oganesson"), 64))
            .addInfo(StatCollector.translateToLocalFormatted("vmd.ex.output", 32 * this.TIER_MULTIPLIER))
            .addInfo(StatCollector.translateToLocal("vmd.ex.output.voltage"))
            .addInfo(StatCollector.translateToLocal("vmd.ex.whitelist.item"))
            .addInfo(StatCollector.translateToLocal("vmd.ex.whitelist.toggle"))
            .addInfo(StatCollector.translateToLocal("vmd.ex.blacklist.warn"))
            .beginStructureBlock(3, 7, 3, false)
            .addStructureInfo(
                StatCollector.translateToLocalFormatted(
                    "vmd.ex.structure.casing",
                    GTOreDictUnificator.get(OrePrefixes.itemCasing, getMaterials(), 1)
                        .getDisplayName()))
            .addStructureInfo(
                StatCollector.translateToLocalFormatted(
                    "vmd.ex.structure.frame",
                    GTOreDictUnificator.get(OrePrefixes.frameGt, getMaterials(), 1)
                        .getDisplayName()))
            .addEnergyHatch(
                "1+",
                StatCollector.translateToLocalFormatted("vmd.ex.structure.energy_hatch", VN[this.getMinTier()]),
                1)
            .addMaintenanceHatch("1", StatCollector.translateToLocal("vmd.ex.structure.maintenance_hatch"), 1)
            .addInputBus("0-1", StatCollector.translateToLocal("vmd.ex.structure.input_bus"), 1)
            .addInputHatch("0-1", StatCollector.translateToLocal("vmd.ex.structure.input_hatch"), 1)
            .addOutputBus("1+", StatCollector.translateToLocal("vmd.ex.structure.output_bus"), 1)
            .toolTipFinisher();
        return tt;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece("main", stackSize, hintsOnly, 1, 6, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece("main", stackSize, 1, 6, 0, elementBudget, env, false, true);
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        if (!checkPiece("main", 1, 6, 0, errors)) return;
        checkHatches(errors);
        if (GTUtility.getTier(getMaxInputVoltage()) < getMinTier()) {
            errors.add(StructureErrors.energyHatchTierTooLow(getMinTier()));
        }
    }

    /**
     * 在 GT 完成过滤和本周期的气体扣减后放大产出，不改变两者的行为。
     * 用 long 计算，再将堆叠数量限制到 int 上限，避免溢出。
     */
    @Override
    public void addOutputPartial(ItemStack stack) {
        if (stack != null) {
            final long enhanced = (long) stack.stackSize * OUTPUT_ENHANCEMENT_FACTOR * getVoltageYieldFactor();
            stack.stackSize = (int) Math.min(enhanced, Integer.MAX_VALUE);
        }
        super.addOutputPartial(stack);
    }

    /**
     * 能源仓中的最高单仓输入电压档（GT 标准电压索引，LV..UHV 乃至更高）。
     * 取"最高单仓档"而非 {@link #getMaxInputVoltage()} 的求和口径——多个低档仓求和会虚增电压档，
     * 与本 mod 虚空流体钻机 {@code MTEVoidFluidDrillerBase#getMaxEnergyHatchTier()} 同语义；
     * 无有效能源仓时返回失败档 -1（与钻机一致，见 {@link #getVoltageYieldFactor} 的调用时机论证）。
     */
    private int getMaxEnergyHatchTier() {
        int maxTier = -1;
        for (MTEHatchEnergy hatch : GTUtility.validMTEList(mEnergyHatches)) {
            maxTier = Math.max(maxTier, (int) hatch.getInputTier());
        }
        return maxTier;
    }

    /**
     * 最高单仓电压每超本机一档，产量乘 4；单仓不足或失效时不加成，即使总电压曾通过结构校验。
     * GT 最高档为 15，本机最低为 LV（1），因此最大左移 28 位。
     */
    private long getVoltageYieldFactor() {
        final int diff = Math.max(0, getMaxEnergyHatchTier() - getMinTier());
        return 1L << (2L * diff);
    }

    /**
     * GUI 产能按每秒 2 轮乘以运行时、EX 和电压倍率。批量同时倍增产量与用时，速率不变。
     * 首次运行前按无气体产能显示；停机后沿用最近一次运行倍率。显示值用 long，不按 int 截断。
     *
     * @param upstreamMultiplier 由 Mixin 桥接的 GT 私有倍率，已包含档位和惰性气体加成
     * @return 每秒产出矿石数
     */
    public long getCurrentOutputPerSecond(int upstreamMultiplier) {
        final int effectiveMultiplier = Math.max(upstreamMultiplier, this.TIER_MULTIPLIER);
        return (long) BASE_ORE_OUTPUT_PER_SECOND * effectiveMultiplier
            * OUTPUT_ENHANCEMENT_FACTOR
            * getVoltageYieldFactor();
    }

    /** 复刻 GT5u 5.09.51.x 的气体提示行构建（钉定版本 5.09.54.x（275）已改用 markdown 描述页，该方法不复存在）。 */
    private String createGasString(EnumChatFormatting color, String gas, int boost) {
        return String.format(
            "%s%s%s : %s%dx%s",
            color,
            gas,
            EnumChatFormatting.GRAY,
            EnumChatFormatting.GOLD,
            boost,
            EnumChatFormatting.GRAY);
    }
}
