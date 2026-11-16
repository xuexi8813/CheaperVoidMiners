package com.jdz.vmd.common.tileentities;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_OIL_DRILL;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_OIL_DRILL_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_OIL_DRILL_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_OIL_DRILL_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.getCasingTextureForId;
import static gregtech.api.enums.VoltageIndex.HV;
import static gregtech.api.enums.VoltageIndex.LV;
import static gregtech.api.enums.VoltageIndex.LuV;
import static gregtech.api.enums.VoltageIndex.MV;
import static gregtech.api.enums.VoltageIndex.UV;
import static gregtech.api.objects.XSTR.XSTR_INSTANCE;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofFrame;
import static gregtech.api.util.GTUtility.validMTEList;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.jdz.vmd.api.recipe.VmdRecipeMaps;

import gregtech.GTMod;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.objects.GTUODimension;
import gregtech.api.objects.GTUOFluid;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gtneioreplugin.plugin.item.ItemDimensionDisplay;
import gtneioreplugin.util.DimensionHelper;

/**
 * Void Fluid Driller（虚空流体钻机）基类：多方块控制器，无需钻井管道、不耗尽区块矿脉，
 * 以固定 EU 成本从 GT 地下流体表抽取已配置维度的流体（对比 {@link gregtech.common.tileentities.machines.multi.MTEOilDrillBase}）。
 * 结构为 {@code MTEDrillerBase} 的 1:1 复刻（3x3x7）；星球/流体/钻头选择见 {@link #createTooltip()}。
 *
 * @param <T> 泛型多方块基类的自身类型
 */
@IMetaTileEntity.SkipGenerateDescription
public abstract class MTEVoidFluidDrillerBase<T extends MTEVoidFluidDrillerBase<T>> extends MTEEnhancedMultiBlockBase<T>
    implements ISurvivalConstructable {

    public static final int CYCLE_TICKS = 100;
    public static final int BASE_OUTPUT_MB = 1000;
    public static final double DRILL_CONSUMPTION_CHANCE = 0.001;

    protected static final String STRUCTURE_PIECE_MAIN = "main";

    /**
     * 五档钻机的档位阶梯（GT 电压索引，LV..UV）。产量公式的电压项与机器档项均以本阶梯的档位序号
     * （LV=0..UV=4）驱动；介于阶梯档位之间的能源仓电压（EV/IV/ZPM/UHV 及以上）向下取整到最近的
     * 阶梯档位，保证任意 ≥LV 的能源仓均有确定产量。
     */
    private static final int[] TIER_LADDER = { LV, MV, HV, LuV, UV };

    /** 钻头加成：输入总线中存在钻头（材质不限）时产量统一 ×128。 */
    private static final long DRILL_BONUS = 128;

    /** 全阶段共享的钻头材质阶梯：输入总线命中任意材质即视为有钻头；顺序仅影响 GUI 显示。 */
    private static final ImmutableList<Materials> DRILL_MATERIALS = ImmutableList
        .of(Materials.Iron, Materials.Steel, Materials.Titanium, Materials.Naquadah, Materials.Infinity);

    private static final class DrillSelection {

        public final ItemStack stack;
        public final String materialName;

        DrillSelection(ItemStack stack, String materialName) {
            this.stack = stack;
            this.materialName = materialName;
        }
    }

    // GUI 显示状态，在 drawTexts() 中通过 FakeSyncWidget 同步。
    private String displayDimensionAbbr = "";
    private String displayFluidKey = "";
    private String displayDrillMaterial = "";

    protected int casingTextureIndex;

    public MTEVoidFluidDrillerBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        casingTextureIndex = getControllerTextureIndex();
    }

    public MTEVoidFluidDrillerBase(String aName) {
        super(aName);
        casingTextureIndex = getControllerTextureIndex();
    }

    // ==== 阶段配置，由五台具体机器各自实现 ====

    public abstract ItemList getCasingBlock();

    public abstract Materials getFrameMaterial();

    /** 机器档位（LV..UV，即 {@link #TIER_LADDER} 的五档）；决定每周期 EU/t 与产量公式的机器档项。 */
    protected abstract int getMinTier();

    /**
     * 控制器正面贴图索引，兼作仓体基础机壳贴图。实现须取自 {@code Casings.XxxCasing.getTextureId()}：
     * {@code GTUtility.getCasingTextureIndex} 对 meta 0 的层级机壳返回 0，违反
     * {@code casingIndex > 0} 契约（结构投影仪构建即抛 IllegalArgumentException）。
     */
    protected abstract int getControllerTextureIndex();

    // ==== 结构：MTEDrillerBase 1:1 复刻，机壳/框架随阶段变化 ====

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
                STRUCTURE_PIECE_MAIN,
                transpose(
                    new String[][] { { "   ", " f ", "   " }, { "   ", " f ", "   " }, { "   ", " f ", "   " },
                        { " f ", "fcf", " f " }, { " f ", "fcf", " f " }, { " f ", "fcf", " f " },
                        { "b~b", "bbb", "bbb" } }))
            .addElement('f', ofFrame(getFrameMaterial()))
            .addElement(
                'c',
                ofBlock(
                    getCasingBlock().getBlock(),
                    getCasingBlock().get(0)
                        .getItemDamage()))
            .addElement(
                'b',
                buildHatchAdder(clazz).atLeastList(getAllowedHatches())
                    .hint(1)
                    .casingIndex(casingTextureIndex)
                    .buildAndChain(
                        getCasingBlock().getBlock(),
                        getCasingBlock().get(0)
                            .getItemDamage()))
            .build();
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, 1, 6, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(STRUCTURE_PIECE_MAIN, stackSize, 1, 6, 0, elementBudget, env, false, true);
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        if (!checkPiece(STRUCTURE_PIECE_MAIN, 1, 6, 0, errors)) return;
        checkHatches(errors);
        // 能源仓按"≥LV 即可"放宽：五档机器均接受 LV 起的任意电压档能源仓（GT 本身不限制能源仓档位，
        // 此前按机器档位拦截的是本方法），产量由实际最高输入电压档驱动（见 computeOutputAmount）。
        if (getMaxEnergyHatchTier() < LV) {
            errors.add(StructureErrors.energyHatchTierTooLow(LV));
        }
    }

    protected void checkHatches(List<StructureError> errors) {
        checkHasEnergyHatch(errors);
        checkOneMaintenanceHatch(errors);
        checkHasOutputHatch(errors);
        checkHasInputBus(errors);
    }

    /**
     * 允许摆入结构槽位的仓体类型：能源仓、输出仓、输入/输出总线与维护仓。
     * 不含流体输入仓——本机直接从地下流体表抽取，不接收任何外部流体。
     */
    protected List<IHatchElement<? super T>> getAllowedHatches() {
        return ImmutableList.of(OutputHatch, InputBus, OutputBus, Maintenance, Energy);
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        // 不可旋转、不得朝上/朝下，与 MTEDrillerBase 一致。
        return (d, r, f) -> (d.flag & (ForgeDirection.UP.flag | ForgeDirection.DOWN.flag)) == 0 && r.isNotRotated()
            && !f.isVerticallyFliped();
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        if (side == aFacing) {
            if (aActive) return new ITexture[] { getCasingTextureForId(casingTextureIndex), TextureFactory.builder()
                .addIcon(OVERLAY_FRONT_OIL_DRILL_ACTIVE)
                .extFacing()
                .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_OIL_DRILL_ACTIVE_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
            return new ITexture[] { getCasingTextureForId(casingTextureIndex), TextureFactory.builder()
                .addIcon(OVERLAY_FRONT_OIL_DRILL)
                .extFacing()
                .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_OIL_DRILL_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
        }
        return new ITexture[] { getCasingTextureForId(casingTextureIndex) };
    }

    // ==== 配方逻辑 ====

    @Override
    public @NotNull CheckRecipeResult checkProcessing() {
        final GTUODimension dimension = resolveTargetDimension();
        final List<GTUOFluid> candidates = VoidFluidTableHelper.getFluidCandidates(dimension);
        if (candidates.isEmpty()) {
            mEUt = 0;
            mMaxProgresstime = 0;
            stopMachine(ShutDownReasonRegistry.NONE);
            return SimpleCheckRecipeResult.ofFailurePersistOnShutdown("vmd_no_fluid_data");
        }

        // 必须由编程电路（配置 1..N）选定候选列表第 N 项（规范排序与 NEI 展示同源一致，见
        // VoidFluidTableHelper#CANDIDATE_ORDER）：未插入电路、配置 0 或超出范围时一律不启动。
        final int circuitConfig = readCircuitConfig();
        if (circuitConfig < 1 || circuitConfig > candidates.size()) {
            mEUt = 0;
            mMaxProgresstime = 0;
            return SimpleCheckRecipeResult.ofFailure("vmd_circuit_required");
        }
        final GTUOFluid selectedFluid = candidates.get(circuitConfig - 1);
        final Fluid fluid = selectedFluid.getFluid();

        final DrillSelection drill = findDrill();

        final long outputAmount = computeOutputAmount(getEnergyHatchRung(), drill != null);
        final FluidStack output = new FluidStack(fluid, (int) Math.min(outputAmount, Integer.MAX_VALUE));
        if (protectsExcessFluid() && !canOutputAll(new FluidStack[] { output })) {
            mEUt = 0;
            mMaxProgresstime = 0;
            return SimpleCheckRecipeResult.ofFailure("vmd_output_full");
        }

        mOutputFluids = new FluidStack[] { output };
        // EUt 维持各档现状：按机器档位取 GT 电压（LV=32 … UV=524288），不随能源仓档位变化。
        mEUt = (int) -GTValues.V[getMinTier()];
        mMaxProgresstime = CYCLE_TICKS;
        mEfficiency = getCurrentEfficiency(null);
        mEfficiencyIncrease = 10000;

        final String selectedDimensionAbbr = resolveDisplayedAbbr(dimension);
        displayDimensionAbbr = selectedDimensionAbbr == null ? "" : selectedDimensionAbbr;
        displayFluidKey = fluid.getUnlocalizedName();
        displayDrillMaterial = drill == null ? "" : drill.materialName;

        return SimpleCheckRecipeResult.ofSuccess("vmd_drilling");
    }

    /**
     * 单周期产出 = 1000mB × 16^(能源仓阶梯档位+1) × 32^(机器阶梯档位) × 钻头加成（有钻头时 ×128）。
     * 电压项由能源仓实际最高输入电压档驱动（LV 仓=16^1 … UV 仓=16^5），机器档项由本机档位驱动
     * （LV 机=32^0 … UV 机=32^4）。两个阶梯档位均 ≤4，乘积上限 1000 × 2^20 × 2^20 × 2^7 &lt; 2^57，
     * long 空间内精确无溢出；写入 FluidStack 时再按 int 上限截断（见 checkProcessing）。
     */
    protected long computeOutputAmount(int energyRung, boolean hasDrill) {
        long amount = BASE_OUTPUT_MB;
        // 电压项：能源仓每升一档 ×16。
        amount *= 1L << (4 * (energyRung + 1));
        // 机器档项：本机每升一档 ×32。
        amount *= 1L << (5 * getMachineRung());
        // 钻头加成：有钻头时统一 ×128。
        if (hasDrill) amount *= DRILL_BONUS;
        return amount;
    }

    /** 每个完成的周期结束时按固定概率消耗一个钻头。 */
    @Override
    protected void outputAfterRecipe() {
        final DrillSelection drill = findDrill();
        if (drill == null) return;
        if (XSTR_INSTANCE.nextFloat() >= DRILL_CONSUMPTION_CHANCE) return;

        // getStoredInputs() 返回输入总线库存的活跃引用（直接修改即生效）。
        ItemStack stack = drill.stack;
        if (stack.stackSize <= 1) {
            stack.stackSize = 0;
        } else {
            stack.stackSize--;
        }
        updateSlots();
    }

    // ==== 维度、流体、能源仓与钻头的解析辅助 ====

    /** 能源仓中的最高输入电压档（GT 电压索引）；无有效能源仓时为 -1。 */
    private int getMaxEnergyHatchTier() {
        int maxTier = -1;
        for (MTEHatchEnergy hatch : validMTEList(mEnergyHatches)) {
            maxTier = Math.max(maxTier, (int) hatch.getInputTier());
        }
        return maxTier;
    }

    /**
     * 能源仓阶梯档位：能源仓最高输入电压档在 {@link #TIER_LADDER} 中的位置（LV=0..UV=4）；
     * 介于五档之间的电压档（EV/IV/ZPM/UHV 及以上）向下取整到最近的阶梯档位。
     * checkMachine 已按"能源仓 ≥LV"拦截，生产路径中恒 ≥0。
     */
    private int getEnergyHatchRung() {
        final int maxTier = getMaxEnergyHatchTier();
        for (int rung = TIER_LADDER.length - 1; rung >= 0; rung--) {
            if (maxTier >= TIER_LADDER[rung]) return rung;
        }
        return -1;
    }

    /** 机器档位：本机 {@link #getMinTier()} 在 {@link #TIER_LADDER} 中的位置（LV=0..UV=4）。 */
    private int getMachineRung() {
        for (int rung = 0; rung < TIER_LADDER.length; rung++) {
            if (getMinTier() == TIER_LADDER[rung]) return rung;
        }
        // 五台具体机器的 getMinTier() 均取自 TIER_LADDER，此分支不可达；触发即为档位配置错误。
        throw new IllegalStateException("Machine tier is not on the driller tier ladder: " + getMinTier());
    }

    /** 目标维度：控制器槽位的维度显示方块优先，无效物品忽略后回退机器所在维度。 */
    @Nullable
    protected GTUODimension resolveTargetDimension() {
        final ItemStack planet = getControllerSlot();
        if (planet != null) {
            final String abbr = ItemDimensionDisplay.getDimension(planet);
            if (abbr != null) return VoidFluidTableHelper.findDimensionByAbbr(abbr);
        }
        return GTMod.proxy.mUndergroundOil.GetDimension(getBaseMetaTileEntity().getWorld().provider.dimensionId);
    }

    @Nullable
    private String resolveDisplayedAbbr(@Nullable GTUODimension dimension) {
        return VoidFluidTableHelper.getAbbrOfDimension(dimension);
    }

    /** 输入总线中第一个编程电路的配置值，不存在时为 0。 */
    private int readCircuitConfig() {
        for (ItemStack stack : getStoredInputs()) {
            if (ItemList.Circuit_Integrated.isStackEqual(stack, true, true)) {
                return stack.getItemDamage();
            }
        }
        return 0;
    }

    /** 输入总线中第一个命中钻头材质阶梯的钻头，无则为 null（加成统一 ×128，材质仅影响 GUI 显示）。 */
    @Nullable
    private DrillSelection findDrill() {
        for (ItemStack input : getStoredInputs()) {
            if (input == null) continue;
            for (Materials material : DRILL_MATERIALS) {
                final ItemStack drillStack = GTOreDictUnificator.get(OrePrefixes.toolHeadDrill, material, 1);
                // 正常构建中不会为 null（RecipeLoader 已校验）；GT 数据损坏时跳过而不崩溃。
                if (drillStack == null) continue;
                if (input.isItemEqual(drillStack)) return new DrillSelection(input, material.mName);
            }
        }
        return null;
    }

    // ==== GUI 与工具提示 ====

    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);

        screenElements
            .widget(
                TextWidget
                    .dynamicString(
                        () -> StatCollector.translateToLocalFormatted("vmd.gui.planet", resolvePlanetDisplayName()))
                    .setTextAlignment(Alignment.CenterLeft)
                    .setDefaultColor(COLOR_TEXT_WHITE.get()))
            .widget(new FakeSyncWidget.StringSyncer(() -> displayDimensionAbbr, val -> displayDimensionAbbr = val));
        screenElements
            .widget(
                TextWidget
                    .dynamicString(
                        () -> StatCollector.translateToLocalFormatted("vmd.gui.fluid", resolveFluidDisplayName()))
                    .setTextAlignment(Alignment.CenterLeft)
                    .setDefaultColor(COLOR_TEXT_WHITE.get()))
            .widget(new FakeSyncWidget.StringSyncer(() -> displayFluidKey, val -> displayFluidKey = val));
        screenElements
            .widget(
                TextWidget
                    .dynamicString(
                        () -> StatCollector.translateToLocalFormatted("vmd.gui.drill", resolveDrillDisplayName()))
                    .setTextAlignment(Alignment.CenterLeft)
                    .setDefaultColor(COLOR_TEXT_WHITE.get()))
            .widget(new FakeSyncWidget.StringSyncer(() -> displayDrillMaterial, val -> displayDrillMaterial = val));
    }

    /** 星球行：所选维度的本地化名称，或机器所在维度的名称。 */
    private String resolvePlanetDisplayName() {
        if (!displayDimensionAbbr.isEmpty()) {
            final String internal = DimensionHelper.ABBR_TO_INTERNAL.get(displayDimensionAbbr);
            if (internal != null) return DimensionHelper.getDimLocalizedName(internal);
        }
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().getWorld() != null) {
            return getBaseMetaTileEntity().getWorld().provider.getDimensionName();
        }
        return StatCollector.translateToLocal("vmd.gui.planet.unknown");
    }

    /** 流体行：本周期由编程电路选定的流体本地化名称；机器尚未运行时显示"无"。 */
    private String resolveFluidDisplayName() {
        if (displayFluidKey.isEmpty()) return StatCollector.translateToLocal("vmd.gui.fluid.none");
        return StatCollector.translateToLocal(displayFluidKey);
    }

    /** 钻头行：总线中钻头的材质名称与统一加成（x128），或"无"标签。 */
    private String resolveDrillDisplayName() {
        if (displayDrillMaterial.isEmpty()) return StatCollector.translateToLocal("vmd.gui.drill.none");
        for (Materials material : DRILL_MATERIALS) {
            if (material.mName.equals(displayDrillMaterial)) {
                return material.mDefaultLocalName + " (x" + DRILL_BONUS + ")";
            }
        }
        return displayDrillMaterial;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("vmd.mbtt.machine_type"))
            .addInfo(StatCollector.translateToLocal("vmd.mbtt.info.1"))
            .addInfo(StatCollector.translateToLocal("vmd.mbtt.info.2"))
            .addInfo(StatCollector.translateToLocal("vmd.mbtt.info.3"))
            .addInfo(StatCollector.translateToLocal("vmd.mbtt.info.4"))
            .addInfo(StatCollector.translateToLocal("vmd.mbtt.info.5"))
            .addInfo(StatCollector.translateToLocal("vmd.mbtt.info.6"))
            .addInfo(StatCollector.translateToLocal("vmd.mbtt.info.7"))
            .beginStructureBlock(3, 7, 3, false)
            .addController(StatCollector.translateToLocal("vmd.mbtt.structure.front_bottom_center"))
            .addCasing("3", StatCollector.translateToLocal("vmd.mbtt.structure.center_shaft_frame"), false)
            .addCasing(
                "12",
                StatCollector.translateToLocalFormatted(
                    "vmd.mbtt.structure.frame",
                    GTOreDictUnificator.get(OrePrefixes.frameGt, getFrameMaterial(), 1)
                        .getDisplayName()),
                false)
            .addCasing(
                "7-8",
                getCasingBlock().get(0)
                    .getDisplayName(),
                false)
            .addEnergyHatch(
                "1+",
                StatCollector
                    .translateToLocalFormatted("vmd.mbtt.structure.any_bottom_casing_min_tier", GTValues.VN[LV]),
                1)
            .addMaintenanceHatch(StatCollector.translateToLocal("vmd.mbtt.structure.any_bottom_casing"))
            .addInputBus(StatCollector.translateToLocal("vmd.mbtt.structure.input_bus"))
            .addOutputBus(StatCollector.translateToLocal("vmd.mbtt.structure.output_bus"))
            .addOutputHatch(StatCollector.translateToLocal("vmd.mbtt.structure.output_hatch"))
            .toolTipFinisher();
        return tt;
    }

    // ==== 多方块基类要求的样板代码 ====

    @Override
    public RecipeMap<?> getRecipeMap() {
        return VmdRecipeMaps.voidFluidDrillerRecipes;
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    @Override
    public boolean isCorrectMachinePart(ItemStack aStack) {
        return true;
    }

    @Override
    public int getDamageToComponent(ItemStack aStack) {
        return 0;
    }

    @Override
    public boolean explodesOnComponentBreak(ItemStack aStack) {
        return false;
    }

    @Override
    public boolean supportsBatchMode() {
        // 产出由能源仓电压档与机器档位决定，批量模式开关对本机无意义，禁用。
        return false;
    }

    @Override
    public boolean supportsSingleRecipeLocking() {
        return false;
    }
}
