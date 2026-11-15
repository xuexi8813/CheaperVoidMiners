package com.jdz.vmd.common.tileentities;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_OIL_DRILL;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_OIL_DRILL_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_OIL_DRILL_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_OIL_DRILL_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.getCasingTextureForId;
import static gregtech.api.objects.XSTR.XSTR_INSTANCE;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofFrame;

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
import gregtech.api.objects.GTUODimension;
import gregtech.api.objects.GTUOFluid;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
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

    protected static final class DrillTier {

        public final Materials material;
        public final double bonus;

        DrillTier(Materials material, double bonus) {
            this.material = material;
            this.bonus = bonus;
        }
    }

    /** 全阶段共享的钻头阶梯；顺序仅影响显示，实际取输入总线中加成最高者。 */
    private static final ImmutableList<DrillTier> DRILL_TIERS = ImmutableList.of(
        new DrillTier(Materials.Iron, 1.25),
        new DrillTier(Materials.Steel, 1.5),
        new DrillTier(Materials.Titanium, 1.75),
        new DrillTier(Materials.Naquadah, 2.0),
        new DrillTier(Materials.Infinity, 3.0));

    private static final class DrillSelection {

        public final ItemStack stack;
        public final double bonus;
        public final String materialName;

        DrillSelection(ItemStack stack, double bonus, String materialName) {
            this.stack = stack;
            this.bonus = bonus;
            this.materialName = materialName;
        }
    }

    // 服务端每周期状态，在 checkProcessing() 中重新计算。
    private int cachedParallel = 1;
    private double cachedDrillBonus = 1.0;

    // GUI 显示状态，在 drawTexts() 中通过 FakeSyncWidget 同步。
    private String displayDimensionAbbr = "";
    private String displayFluidKey = "";
    private String displayDrillMaterial = "";
    private int displayParallel = 1;

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

    /** 最低能源仓等级；同时决定每周期 EU/t 与输出电压系数档位。 */
    protected abstract int getMinTier();

    /** 电压系数，乘到基础产出（LV=16、MV=64、HV=256、LuV=1024、UV=4096）。 */
    public abstract int getVoltageFactor();

    /** 控制器正面贴图索引，兼作仓体基础机壳贴图。 */
    protected abstract int getControllerTextureIndex();

    /** 输入电压等级下的并行数，默认 1；Infinity 阶段以指数规则覆盖。 */
    protected int calculateParallel(int inputTier) {
        return 1;
    }

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
        if (GTUtility.getTier(getMaxInputVoltage()) < getMinTier()) {
            errors.add(StructureErrors.energyHatchTierTooLow(getMinTier()));
        }
    }

    protected void checkHatches(List<StructureError> errors) {
        checkHasEnergyHatch(errors);
        checkOneMaintenanceHatch(errors);
        checkHasOutputHatch(errors);
        checkHasInputBus(errors);
    }

    protected List<IHatchElement<? super T>> getAllowedHatches() {
        return ImmutableList.of(InputHatch, OutputHatch, InputBus, OutputBus, Maintenance, Energy);
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
        final int inputTier = GTUtility.getTier(getMaxInputVoltage());
        cachedParallel = calculateParallel(inputTier);

        final GTUODimension dimension = resolveTargetDimension();
        final List<GTUOFluid> candidates = VoidFluidTableHelper.getFluidCandidates(dimension);
        if (candidates.isEmpty()) {
            mEUt = 0;
            mMaxProgresstime = 0;
            stopMachine(ShutDownReasonRegistry.NONE);
            return SimpleCheckRecipeResult.ofFailurePersistOnShutdown("vmd_no_fluid_data");
        }

        // 必须由编程电路（配置 1..N）选定流体：未插入电路、配置 0 或超出范围时一律不启动。
        final int circuitConfig = readCircuitConfig();
        if (circuitConfig < 1 || circuitConfig > candidates.size()) {
            mEUt = 0;
            mMaxProgresstime = 0;
            return SimpleCheckRecipeResult.ofFailure("vmd_circuit_required");
        }
        final GTUOFluid selectedFluid = candidates.get(circuitConfig - 1);
        final Fluid fluid = selectedFluid.getFluid();

        final DrillSelection drill = findBestDrill();
        cachedDrillBonus = drill == null ? 1.0 : drill.bonus;

        final long outputAmount = computeOutputAmount(cachedParallel, cachedDrillBonus);
        final FluidStack output = new FluidStack(fluid, (int) Math.min(outputAmount, Integer.MAX_VALUE));
        if (protectsExcessFluid() && !canOutputAll(new FluidStack[] { output })) {
            mEUt = 0;
            mMaxProgresstime = 0;
            return SimpleCheckRecipeResult.ofFailure("vmd_output_full");
        }

        // GT 2.9 多方块 mEUt 为 int：多安培消耗在 Integer.MAX_VALUE 处饱和（Infinity 在 UIV+ 触发）。
        mOutputFluids = new FluidStack[] { output };
        final long totalEUt = GTValues.V[getMinTier()] * (long) cachedParallel;
        mEUt = (int) -Math.min(totalEUt, Integer.MAX_VALUE);
        mMaxProgresstime = CYCLE_TICKS;
        mEfficiency = getCurrentEfficiency(null);
        mEfficiencyIncrease = 10000;

        final String selectedDimensionAbbr = resolveDisplayedAbbr(dimension);
        displayDimensionAbbr = selectedDimensionAbbr == null ? "" : selectedDimensionAbbr;
        displayFluidKey = fluid.getUnlocalizedName();
        displayDrillMaterial = drill == null ? "" : drill.materialName;
        displayParallel = cachedParallel;

        return SimpleCheckRecipeResult.ofSuccess("vmd_drilling");
    }

    /** 单周期产出 = 基础产出 × 电压系数 × 并行 × 钻头加成；long 空间计算，最终截断前避免溢出。 */
    protected long computeOutputAmount(int parallel, double drillBonus) {
        return (long) ((long) BASE_OUTPUT_MB * getVoltageFactor() * parallel * drillBonus);
    }

    /** 每个完成的周期结束时按固定概率消耗一个钻头。 */
    @Override
    protected void outputAfterRecipe() {
        final DrillSelection drill = findBestDrill();
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

    // ==== 维度、流体与钻头的解析辅助 ====

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

    /** 输入总线中共享阶梯里加成最高的钻头，无则为 null。 */
    @Nullable
    private DrillSelection findBestDrill() {
        DrillSelection best = null;
        for (ItemStack input : getStoredInputs()) {
            if (input == null) continue;
            for (DrillTier tier : DRILL_TIERS) {
                final ItemStack drillStack = GTOreDictUnificator.get(OrePrefixes.toolHeadDrill, tier.material, 1);
                // 正常构建中不会为 null（RecipeLoader 已校验）；GT 数据损坏时跳过而不崩溃。
                if (drillStack == null) continue;
                if (input.isItemEqual(drillStack) && (best == null || tier.bonus > best.bonus)) {
                    best = new DrillSelection(input, tier.bonus, tier.material.mName);
                }
            }
        }
        return best;
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
        screenElements.widget(
            TextWidget.dynamicString(() -> StatCollector.translateToLocalFormatted("vmd.gui.parallel", displayParallel))
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get()))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> displayParallel, val -> displayParallel = val));
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

    /** 钻头行：总线中加成最高钻头的名称与加成，或"无"标签。 */
    private String resolveDrillDisplayName() {
        if (displayDrillMaterial.isEmpty()) return StatCollector.translateToLocal("vmd.gui.drill.none");
        for (DrillTier tier : DRILL_TIERS) {
            if (tier.material.mName.equals(displayDrillMaterial)) {
                return tier.material.mDefaultLocalName + " (x" + tier.bonus + ")";
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
                StatCollector.translateToLocalFormatted(
                    "vmd.mbtt.structure.any_bottom_casing_min_tier",
                    GTValues.VN[getMinTier()]),
                1)
            .addMaintenanceHatch(StatCollector.translateToLocal("vmd.mbtt.structure.any_bottom_casing"))
            .addInputBus(StatCollector.translateToLocal("vmd.mbtt.structure.input_bus"))
            .addInputHatch(StatCollector.translateToLocal("vmd.mbtt.structure.input_hatch"))
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
        // 扩量走并行数而非批量模式，该开关无效，禁用。
        return false;
    }

    @Override
    public boolean supportsSingleRecipeLocking() {
        return false;
    }
}
