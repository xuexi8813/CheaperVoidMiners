package com.jdz.vmd.common.tileentities;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.GTValues.VN;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofFrame;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import bwcrossmod.galacticgreg.MTEVoidMinerBase;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.tileentities.machines.multi.MTEDrillerBase;

/**
 * EX 系列虚空采矿机基类：在 GT 虚空采矿机（{@link MTEVoidMinerBase}）之上定制结构与文案。
 * 产出加强：父类 {@code TIER_MULTIPLIER} 为 byte（上限 127），无法直接传入 ×16 后的产出档位，
 * 因此在最终输出入口 {@link #addOutputPartial} 处将产出堆叠数量统一 ×16
 * （见 {@link #OUTPUT_ENHANCEMENT_FACTOR}），惰性气体加成与批量模式均不受影响。
 */
public abstract class MTEVoidMinerEXBase<T extends MTEVoidMinerEXBase<T>> extends MTEVoidMinerBase<T> {

    /** 惰性气体供给速率（L/s），复刻父类 MTEVoidMinerBase 的私有常量 NOBLE_GAS_CONSUMPTION_PER_TIER（不可直接访问）。 */
    private static final int NOBLE_GAS_SUPPLY_RATE_PER_SECOND = 2;

    /**
     * EX 系列产出加强系数（数值加强）：父类 handleOutputs 对每个输出堆设置
     * {@code stackSize = multiplier}（TIER_MULTIPLIER × 惰性气体加成）后经
     * {@code addOutputPartial(ItemStack)} 弹出，这是本机器唯一的物品输出入口；
     * 本类在弹出前将最终值 ×16。数值上限：TIER_MULTIPLIER(16) × 氪气加成(64) × 16 = 16384，int 安全。
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

    protected IStructureDefinition<T> buildBaseStructureDefinition(Class<T> clazz) {
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
                    .translateToLocalFormatted("vmd.ex.eu", numberFormat.format(GTValues.V[this.getMinTier()])))
            .addInfo(StatCollector.translateToLocalFormatted("vmd.ex.gas.intro", NOBLE_GAS_SUPPLY_RATE_PER_SECOND))
            .addInfo(
                createGasString(EnumChatFormatting.LIGHT_PURPLE, StatCollector.translateToLocal("vmd.ex.gas.neon"), 4))
            .addInfo(createGasString(EnumChatFormatting.AQUA, StatCollector.translateToLocal("vmd.ex.gas.krypton"), 8))
            .addInfo(
                createGasString(EnumChatFormatting.DARK_AQUA, StatCollector.translateToLocal("vmd.ex.gas.xenon"), 16))
            .addInfo(
                createGasString(EnumChatFormatting.BLUE, StatCollector.translateToLocal("vmd.ex.gas.oganesson"), 64))
            .addInfo(StatCollector.translateToLocalFormatted("vmd.ex.output", 32 * this.TIER_MULTIPLIER))
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
                StatCollector.translateToLocalFormatted("vmd.ex.structure.energy_hatch", VN[this.getMinTier()]))
            .addMaintenanceHatch(StatCollector.translateToLocal("vmd.ex.structure.maintenance_hatch"))
            .addInputBus(StatCollector.translateToLocal("vmd.ex.structure.input_bus"))
            .addInputHatch(StatCollector.translateToLocal("vmd.ex.structure.input_hatch"))
            .addOutputBus(StatCollector.translateToLocal("vmd.ex.structure.output_bus"))
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

    @Override
    protected List<IHatchElement<? super MTEDrillerBase>> getAllowedHatches() {
        return ImmutableList.of(InputBus, OutputBus, Maintenance, Energy);
    }

    /**
     * 产出加强 ×16 的注入点：将父类已按 TIER_MULTIPLIER 与惰性气体加成计算好的产出堆叠数量
     * 再乘以 {@link #OUTPUT_ENHANCEMENT_FACTOR} 后交由父类弹出。
     * 父类 handleOutputs 中的过滤器匹配发生在本方法之前，×16 不影响白/黑名单判定；
     * 父类 checkProcessing 中的气体消耗按周期扣减，同样不受影响。
     */
    @Override
    public void addOutputPartial(ItemStack stack) {
        if (stack != null) stack.stackSize *= OUTPUT_ENHANCEMENT_FACTOR;
        super.addOutputPartial(stack);
    }

    /** 复刻 GT5u 5.09.51.x 的气体提示行构建（该方法在 5.09.55.x 中被移除）。 */
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
