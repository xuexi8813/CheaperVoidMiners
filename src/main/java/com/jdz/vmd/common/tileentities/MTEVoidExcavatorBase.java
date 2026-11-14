package com.jdz.vmd.common.tileentities;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.getCasingTextureForId;
import static gregtech.api.objects.XSTR.XSTR_INSTANCE;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofFrame;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

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

import galacticgreg.api.ModDimensionDef;
import galacticgreg.api.enums.DimensionDef;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
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
import gtneioreplugin.util.DimensionHelper;

/**
 * Void Excavator（虚空挖掘机）的基类：多方块控制器，从虚空中挖掘指定维度的普通（非矿石）资源方块，
 * 由共享的配置资源表（{@link VoidExcavatorResourceTable}）驱动。
 * <p>
 * 动机：该家族是木制挖掘机的电气对应版本。刻意<b>不</b>继承 {@code bwcrossmod.galacticgreg.MTEVoidMinerBase}，
 * 以便与上游 GT 矿石/采矿机逻辑完全解耦（VMT 兼容性原则）；转而复用通用的 {@link MTEEnhancedMultiBlockBase}
 * 机制，并采用与 Void Fluid Driller（虚空流体钻机）完全相同的形状——该结构是
 * {@code gregtech.common.tileentities.machines.multi.MTEDrillerBase} 的 1:1 复刻（3x3x7，控制器下方的
 * 框架竖轴，三层十字形框架/机壳层，3x3 机壳顶层，控制器位于前侧底部中央）。
 * <p>
 * 钻头处理（共享阶梯、取最高加成、每周期 0.1% 消耗）遵循 {@link MTEVoidFluidDrillerBase} 的实现模式；
 * 后者本身无法重构为共享辅助类，因为它已是本模组已发布且被冻结的行为。
 *
 * @param <T> 泛型多方块基类的自身类型
 */
@IMetaTileEntity.SkipGenerateDescription
public abstract class MTEVoidExcavatorBase<T extends MTEVoidExcavatorBase<T>> extends MTEEnhancedMultiBlockBase<T>
    implements ISurvivalConstructable {

    /** 单个生产周期的时长（tick）。 */
    public static final int CYCLE_TICKS = 100;
    /** 每个完成的周期结束时消耗一个钻头的概率。 */
    public static final double DRILL_CONSUMPTION_CHANCE = 0.001;

    protected static final String STRUCTURE_PIECE_MAIN = "main";

    /** 一个钻头等级：哪种材料的钻头物品堆对应哪一档产出倍率。 */
    protected static final class DrillTier {

        public final Materials material;
        public final double bonus;

        DrillTier(Materials material, double bonus) {
            this.material = material;
            this.bonus = bonus;
        }
    }

    /**
     * 家族全部阶段共享的钻头阶梯；五种物品堆在五台机器中均有效。
     * 顺序仅影响显示，实际选择取输入总线中存在的最高加成。
     */
    private static final ImmutableList<DrillTier> DRILL_TIERS = ImmutableList.of(
        new DrillTier(Materials.Iron, 1.25),
        new DrillTier(Materials.Steel, 1.5),
        new DrillTier(Materials.Titanium, 1.75),
        new DrillTier(Materials.Naquadah, 2.0),
        new DrillTier(Materials.Infinity, 3.0));

    /** 当前周期解析出的最优钻头。 */
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
    private boolean selectedByCircuit = false;

    // GUI 显示状态，在 drawTexts() 中通过 FakeSyncWidget 同步。
    private String displayDimensionAbbr = "";
    private String displayResourceName = "";
    private boolean displayResourceSelectedByCircuit = false;
    private String displayDrillMaterial = "";
    private int displayParallel = 1;

    protected int casingTextureIndex;

    public MTEVoidExcavatorBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        casingTextureIndex = getControllerTextureIndex();
    }

    public MTEVoidExcavatorBase(String aName) {
        super(aName);
        casingTextureIndex = getControllerTextureIndex();
    }

    /**************************************************************************
     * 阶段配置，由五台具体机器各自实现。
     *************************************************************************/

    /** 本阶段的机器机壳方块，用于结构的机壳槽位。 */
    public abstract ItemList getCasingBlock();

    /** 本阶段的框架材料，用于结构的 frameGt 槽位。 */
    public abstract Materials getFrameMaterial();

    /** 允许的最低能源仓等级；同时决定每周期消耗的 EU/t。 */
    protected abstract int getMinTier();

    /**
     * 并行与钻头加成生效前，每周期产出的资源方块数；保持为常量以便各阶段可独立调整。
     */
    public abstract int getStageMultiplier();

    /** 控制器正面使用的贴图索引，同时作为仓体槽位的基础机壳贴图。 */
    protected abstract int getControllerTextureIndex();

    /**
     * 给定输入电压等级下的并行数。默认为 1；Infinity 阶段用指数规则覆盖
     * （见 {@code MTEVoidExcavators.VoidExcavatorInfinity}）。
     */
    protected int calculateParallel(int inputTier) {
        return 1;
    }

    /**************************************************************************
     * 结构：MTEDrillerBase 的 1:1 复刻，机壳/框架随阶段变化。
     *************************************************************************/

    protected IStructureDefinition<T> buildBaseStructureDefinition(Class<T> clazz) {
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

    /** 仓体要求：能源仓与维护仓必需，输入总线与输出总线必需。 */
    protected void checkHatches(List<StructureError> errors) {
        checkHasEnergyHatch(errors);
        checkOneMaintenanceHatch(errors);
        checkHasInputBus(errors);
        checkHasOutputBus(errors);
    }

    /** 'b' 槽位接受的仓体元素；由结构定义构建器消费。 */
    protected List<IHatchElement<? super T>> getAllowedHatches() {
        return ImmutableList.of(InputBus, OutputBus, Maintenance, Energy);
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        // 挖掘机不可旋转，且不得朝上或朝下，与钻机家族的形状约定一致。
        return (d, r, f) -> (d.flag & (ForgeDirection.UP.flag | ForgeDirection.DOWN.flag)) == 0 && r.isNotRotated()
            && !f.isVerticallyFliped();
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        if (side == aFacing) {
            if (aActive) return new ITexture[] { getCasingTextureForId(casingTextureIndex), TextureFactory.builder()
                .addIcon(OVERLAY_FRONT_ORE_DRILL_ACTIVE)
                .extFacing()
                .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ORE_DRILL_ACTIVE_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
            return new ITexture[] { getCasingTextureForId(casingTextureIndex), TextureFactory.builder()
                .addIcon(OVERLAY_FRONT_ORE_DRILL)
                .extFacing()
                .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ORE_DRILL_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
        }
        return new ITexture[] { getCasingTextureForId(casingTextureIndex) };
    }

    /**************************************************************************
     * 配方逻辑。
     *************************************************************************/

    @Override
    public @NotNull CheckRecipeResult checkProcessing() {
        final int inputTier = GTUtility.getTier(getMaxInputVoltage());
        cachedParallel = calculateParallel(inputTier);

        // 1. 解析目标维度及其配置的资源表。
        final String dimensionAbbr = resolveDimensionAbbr();
        final List<VoidExcavatorResourceTable.WeightedResource> resources = VoidExcavatorResourceTable
            .getResources(dimensionAbbr);
        if (resources.isEmpty()) {
            mEUt = 0;
            mMaxProgresstime = 0;
            stopMachine(ShutDownReasonRegistry.NONE);
            return SimpleCheckRecipeResult.ofFailurePersistOnShutdown("vmd_excavator_no_resources");
        }

        // 2. 解析资源：编程电路配置 1..N 选择第 N 项，其他情况按权重随机。
        final int circuitConfig = readCircuitConfig();
        final VoidExcavatorResourceTable.WeightedResource selected;
        if (circuitConfig >= 1) {
            if (circuitConfig > resources.size()) {
                mEUt = 0;
                mMaxProgresstime = 0;
                return SimpleCheckRecipeResult.ofFailure("vmd_excavator_circuit_out_of_range");
            }
            selected = resources.get(circuitConfig - 1);
            selectedByCircuit = true;
        } else {
            selected = VoidExcavatorResourceTable.weightedRandom(resources);
            selectedByCircuit = false;
        }
        // 3. 解析输入总线中存在的最优钻头（可选，缺失时加成为 1.0）。
        final DrillSelection drill = findBestDrill();
        cachedDrillBonus = drill == null ? 1.0 : drill.bonus;

        // 4. 计算产出并在提交周期前检查输出空间。
        final long amount = computeOutputAmount(cachedParallel, cachedDrillBonus);
        final ItemStack output = GTUtility.copyAmount(
            (int) Math.min(amount, Integer.MAX_VALUE),
            selected.stack()
                .copy());
        if (!canOutputAll(new ItemStack[] { output })) {
            mEUt = 0;
            mMaxProgresstime = 0;
            return SimpleCheckRecipeResult.ofFailure("vmd_excavator_output_full");
        }

        // 5. 应用周期参数：每份并行消耗一份该阶段的固定电压，周期 100 tick。
        // GT 2.9 多方块中 mEUt 为 int，因此多安培消耗会在 Integer.MAX_VALUE 处饱和
        // （Infinity 阶段在 UIV 以上等级会触发）；产出与并行数不截断。
        mOutputItems = new ItemStack[] { output };
        final long totalEUt = GTValues.V[getMinTier()] * (long) cachedParallel;
        mEUt = (int) -Math.min(totalEUt, Integer.MAX_VALUE);
        mMaxProgresstime = CYCLE_TICKS;
        mEfficiency = getCurrentEfficiency(null);
        mEfficiencyIncrease = 10000;

        // 6. 更新 GUI 显示状态。
        displayDimensionAbbr = dimensionAbbr == null ? "" : dimensionAbbr;
        displayResourceName = selected.stack()
            .getDisplayName();
        displayResourceSelectedByCircuit = selectedByCircuit;
        displayDrillMaterial = drill == null ? "" : drill.materialName;
        displayParallel = cachedParallel;

        return SimpleCheckRecipeResult.ofSuccess("vmd_excavator_digging");
    }

    /**
     * 单周期产出的资源方块总数：阶段倍率 × 并行数 × 钻头加成。
     * 在 long 空间中计算，以在最终截断到 int 范围前避免溢出。
     */
    protected long computeOutputAmount(int parallel, double drillBonus) {
        return (long) ((long) getStageMultiplier() * parallel * drillBonus);
    }

    /** 每个完成的周期结束时按固定概率消耗一个钻头。 */
    @Override
    protected void outputAfterRecipe() {
        final DrillSelection drill = findBestDrill();
        if (drill == null) return;
        if (XSTR_INSTANCE.nextFloat() >= DRILL_CONSUMPTION_CHANCE) return;

        // getStoredInputs() 返回的物品堆是输入总线库存的活跃引用（直接修改即生效）。
        ItemStack stack = drill.stack;
        if (stack.stackSize <= 1) {
            stack.stackSize = 0;
        } else {
            stack.stackSize--;
        }
        updateSlots();
    }

    /**************************************************************************
     * 维度、资源与钻头的解析辅助方法。
     *************************************************************************/

    /**
     * 本挖掘机所挖资源表对应的维度缩写。
     * <p>
     * 控制器槽位中的维度显示方块可显式指定维度；无效物品会被忽略，机器回退到其所在维度。
     * GTNEI 注册表未收录的维度会解析为 null，无法匹配任何资源表，周期以"无资源"失败。
     */
    @Nullable
    protected String resolveDimensionAbbr() {
        final ItemStack display = getControllerSlot();
        if (display != null) {
            final String abbr = VoidExcavatorResourceTable.getDimensionAbbr(display);
            if (abbr != null) return abbr;
        }

        final ModDimensionDef dimensionDef = DimensionDef.getDefForWorld(getBaseMetaTileEntity().getWorld());
        if (dimensionDef != null) {
            return DimensionHelper.INTERNAL_TO_ABBR.get(dimensionDef.getDimensionName());
        }
        return null;
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

    /** 输入总线中当前存放的共享阶梯里加成最高的钻头，无则为 null。 */
    @Nullable
    private DrillSelection findBestDrill() {
        DrillSelection best = null;
        for (ItemStack input : getStoredInputs()) {
            if (input == null) continue;
            for (DrillTier tier : DRILL_TIERS) {
                final ItemStack drillStack = GTOreDictUnificator.get(OrePrefixes.toolHeadDrill, tier.material, 1);
                // 在正确的构建中不会出现 null：五种物品堆均在配方注册时（RecipeLoader）校验过。
                // 若 GT 数据损坏，选择跳过而不是让服务器线程崩溃。
                if (drillStack == null) continue;
                if (input.isItemEqual(drillStack) && (best == null || tier.bonus > best.bonus)) {
                    best = new DrillSelection(input, tier.bonus, tier.material.mName);
                }
            }
        }
        return best;
    }

    /**************************************************************************
     * GUI 与工具提示。
     *************************************************************************/

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
                        () -> StatCollector.translateToLocalFormatted("vmd.gui.resource", resolveResourceDisplayName()))
                    .setTextAlignment(Alignment.CenterLeft)
                    .setDefaultColor(COLOR_TEXT_WHITE.get()))
            .widget(new FakeSyncWidget.StringSyncer(() -> displayResourceName, val -> displayResourceName = val))
            .widget(
                new FakeSyncWidget.BooleanSyncer(
                    () -> displayResourceSelectedByCircuit,
                    val -> displayResourceSelectedByCircuit = val));
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
            return VoidExcavatorResourceTable.getDimensionDisplayName(displayDimensionAbbr);
        }
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().getWorld() != null) {
            return getBaseMetaTileEntity().getWorld().provider.getDimensionName();
        }
        return StatCollector.translateToLocal("vmd.gui.planet.unknown");
    }

    /** 资源行：电路选定的资源名称，或权重随机标签。 */
    private String resolveResourceDisplayName() {
        if (!displayResourceSelectedByCircuit || displayResourceName.isEmpty()) {
            return StatCollector.translateToLocal("vmd.gui.weighted_random");
        }
        return displayResourceName;
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
        tt.addMachineType(StatCollector.translateToLocal("vmd.mbe.machine_type"))
            .addInfo(StatCollector.translateToLocal("vmd.mbe.info.1"))
            .addInfo(StatCollector.translateToLocal("vmd.mbe.info.2"))
            .addInfo(StatCollector.translateToLocal("vmd.mbe.info.3"))
            .addInfo(StatCollector.translateToLocal("vmd.mbe.info.4"))
            .addInfo(StatCollector.translateToLocal("vmd.mbe.info.5"))
            .addInfo(StatCollector.translateToLocalFormatted("vmd.mbe.info.6", getStageMultiplier()))
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
            .addInputBus(StatCollector.translateToLocal("vmd.mbe.structure.input_bus"))
            .addOutputBus(StatCollector.translateToLocal("vmd.mbe.structure.output_bus"))
            .toolTipFinisher();
        return tt;
    }

    /**************************************************************************
     * 多方块基类要求的样板代码。
     *************************************************************************/

    @Override
    public RecipeMap<?> getRecipeMap() {
        return VmdRecipeMaps.voidExcavatorRecipes;
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
        // 批量模式通过配方逻辑翻倍产出；本机器改为通过并行数扩量，
        // 该开关将是无效操作，因此禁用。
        return false;
    }

    @Override
    public boolean supportsSingleRecipeLocking() {
        return false;
    }
}
