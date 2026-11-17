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
 * 以能源仓电压档对应的 EU/t（1 安培）从 GT 地下流体表抽取已配置维度的流体
 * （对比 {@link gregtech.common.tileentities.machines.multi.MTEOilDrillBase}）。
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
     * 五档钻机的机器档阶梯（GT 电压索引，LV..UV），仅用于机器档项（16^(机器档−LV)）定位与
     * 无尽钻机（机器档 UV）电压项分支判定。能源仓电压项不经过本阶梯——直接使用 GT 标准电压档
     * （{@code getMaxEnergyHatchTier()} 返回的 {@code GTValues} tier，LV..UHV 乃至更高线性计算），
     * 不做五档归并取整。
     */
    private static final int[] TIER_LADDER = { LV, MV, HV, LuV, UV };

    /**
     * 产量 long 位移的安全上界：1000（≈2^10）左移 53 位 = 9.007e18 仍在 long 范围内，54 位起回绕。
     * 超界组合（如 UHV 仓 + 无尽机 + 无尽钻头）按 {@code Long.MAX_VALUE} 饱和，
     * 最终 int 截断（见 checkProcessing）下输出语义不变，仅防回绕为负。
     */
    private static final int MAX_OUTPUT_SHIFT_BITS = 53;

    /** 全阶段共享的钻头材质阶梯（列表序 = 钻头阶级 0..4）：钻头项 = 4^(阶级+1)；输入总线取阶级最高者。 */
    private static final ImmutableList<Materials> DRILL_MATERIALS = ImmutableList
        .of(Materials.Iron, Materials.Steel, Materials.Titanium, Materials.Naquadah, Materials.Infinity);

    private static final class DrillSelection {

        public final ItemStack stack;
        public final String materialName;
        /** 钻头阶级（{@link #DRILL_MATERIALS} 列表序 0..4），驱动钻头项 4^(阶级+1)。 */
        public final int rung;

        DrillSelection(ItemStack stack, String materialName, int rung) {
            this.stack = stack;
            this.materialName = materialName;
            this.rung = rung;
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

    /** 机器档位（LV..UV，即 {@link #TIER_LADDER} 的五档）；决定产量公式的机器档项（EU/t 不随机器档位，见 checkProcessing）。 */
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
        // 此前按机器档位拦截的是本方法），EU/t 与产量均由实际最高输入电压档驱动（见 computeOutputAmount
        // 与 checkProcessing 的 mEUt 赋值）。
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
        if (circuitConfig < 1) {
            // 未插入编程电路或配置 0：视为未配置。
            mEUt = 0;
            mMaxProgresstime = 0;
            return SimpleCheckRecipeResult.ofFailure("vmd_circuit_required");
        }
        if (circuitConfig > candidates.size()) {
            // 配置超出该维度候选流体数量：与"未插电路"区分，给出针对性错误提示。
            mEUt = 0;
            mMaxProgresstime = 0;
            return SimpleCheckRecipeResult.ofFailure("vmd_circuit_out_of_range");
        }
        final GTUOFluid selectedFluid = candidates.get(circuitConfig - 1);
        final Fluid fluid = selectedFluid.getFluid();

        final DrillSelection drill = findDrill();

        final long outputAmount = computeOutputAmount(getMaxEnergyHatchTier(), drill == null ? -1 : drill.rung);
        final FluidStack output = new FluidStack(fluid, (int) Math.min(outputAmount, Integer.MAX_VALUE));
        if (protectsExcessFluid() && !canOutputAll(new FluidStack[] { output })) {
            mEUt = 0;
            mMaxProgresstime = 0;
            return SimpleCheckRecipeResult.ofFailure("vmd_output_full");
        }

        mOutputFluids = new FluidStack[] { output };
        // EUt 跟随能源仓实际最高电压档收取 1 安培（不随机器档位）：LV=32、MV=128、HV=512、EV=2048、
        // IV=8192、LuV=32768、ZPM=131072、UV=524288、UHV=2097152 EU/t（GTValues.V 表，更高档顺延）。
        // 本行仅在 mMachine=true 时可达（checkProcessing 由 runMachine→checkRecipe 触发，而 mMachine
        // 要求 checkMachine 通过——后者已校验能源仓 ≥LV），故 getMaxEnergyHatchTier() 必为有效档位。
        mEUt = (int) -GTValues.V[getMaxEnergyHatchTier()];
        mMaxProgresstime = CYCLE_TICKS;
        mEfficiency = getCurrentEfficiency(null);
        mEfficiencyIncrease = 10000;

        final String selectedDimensionAbbr = resolveDisplayedAbbr(dimension);
        displayDimensionAbbr = selectedDimensionAbbr == null ? "" : selectedDimensionAbbr;
        displayFluidKey = fluid.getUnlocalizedName();
        displayDrillMaterial = drill == null ? "" : drill.materialName;

        // 钻头消耗时点 = 周期启动成功时（全部失败返回分支之后）：多方块周期一经启动必完成
        // （断电仅暂停进度，不取消周期），故"每周期启动成功"与"每完成周期"一一对应，
        // 单周期至多消耗 1 个，概率不变（DRILL_CONSUMPTION_CHANCE）。必须在配方窗口内
        // （checkRecipe 先以 startRecipeProcessing 开窗、后以 endRecipeProcessing 关窗）扣减：
        // 普通输入总线中 drill.stack 为库存槽位活跃引用，扣减即生效；ME 输入总线中 drill.stack
        // 为窗口内提取出的 slot.extracted 活跃引用，扣减后由窗口关闭时的差额结算
        // （extractedAmount − stackSize，MTEHatchInputBusME.endRecipeProcessing）自动从 AE 网络补扣
        // ——原实现（周期完成时经 outputAfterRecipe 消耗）在窗口关闭后执行，ME 总线的
        // getStackInSlot 已返回 null，导致加成生效而钻头永不消耗。
        if (drill != null && XSTR_INSTANCE.nextFloat() < DRILL_CONSUMPTION_CHANCE) {
            consumeDrillHead(drill.stack);
        }

        return SimpleCheckRecipeResult.ofSuccess("vmd_drilling");
    }

    /**
     * 单周期产出 = 1000mB × 电压项 × 机器档项 × 钻头项（全 long 位移计算，4^x=1L&lt;&lt;2x、16^x=1L&lt;&lt;4x）。
     * 电压项 = 4^(能源仓 GT 电压档 − LV + 1)，按 {@code GTValues.VoltageIndex} 线性顺延：
     * LV=4、MV=16、HV=64、EV=256、IV=1024、LuV=4096、ZPM=16384、UV=65536、UHV=262144；
     * 无尽钻机（机器档 UV）电压基数换 16：16^(能源仓 GT 电压档 − LV + 1)。
     * 机器档项 = 16^(机器档 − LV)（LV 机=1、钢=16、钛=256、硅岩=4096、无尽=65536）。
     * 钻头项 = 4^(钻头阶级 + 1)（铁=4、钢=16、钛=64、硅岩=256、无尽=1024），无钻头（drillRung &lt; 0）为 1。
     * 位移之和超过 {@link #MAX_OUTPUT_SHIFT_BITS} 时按 {@code Long.MAX_VALUE} 饱和；
     * 写入 FluidStack 时再按 int 上限截断（见 checkProcessing）。
     */
    protected long computeOutputAmount(int energyTier, int drillRung) {
        final int machineRung = getMachineRung();
        // 电压项：每 GT 电压档 ×4（2^2）；无尽钻机（机器档 UV）每档 ×16（2^4），即"无尽流体后电压翻16倍"。
        final int voltageBits = (machineRung == TIER_LADDER.length - 1 ? 4 : 2) * (energyTier - LV + 1);
        // 机器档项：每机器档 ×16（2^4）。
        final int machineBits = 4 * machineRung;
        // 钻头项：每阶级 ×4（2^2），无钻头为 0。
        final int drillBits = drillRung < 0 ? 0 : 2 * (drillRung + 1);
        final int totalBits = voltageBits + machineBits + drillBits;
        if (totalBits > MAX_OUTPUT_SHIFT_BITS) return Long.MAX_VALUE;
        return (long) BASE_OUTPUT_MB << totalBits;
    }

    /**
     * 扣减一个钻头（周期启动成功时调用，见 checkProcessing 内消耗时点说明）。
     * 直接修改传入的活跃引用：普通输入总线的槽位库存、ME 输入总线的 slot.extracted 快照
     * （{@code MTEHatchInputBusME} 在 {@code startRecipeProcessing} 时从 AE 网络模拟提取，
     * {@code endRecipeProcessing} 按 extractedAmount − stackSize 的差额对网络补扣真实提取量）。
     */
    private void consumeDrillHead(ItemStack stack) {
        if (stack.stackSize <= 1) {
            stack.stackSize = 0;
        } else {
            stack.stackSize--;
        }
        updateSlots();
    }

    // ==== 维度、流体、能源仓与钻头的解析辅助 ====

    /**
     * 能源仓中的最高输入电压档（GT 标准电压索引，LV..UHV 乃至更高，按 {@code GTValues.VoltageIndex}）；
     * 无有效能源仓时为 -1。产量电压项直接消费本档位线性计算，不做归并。
     */
    private int getMaxEnergyHatchTier() {
        int maxTier = -1;
        for (MTEHatchEnergy hatch : validMTEList(mEnergyHatches)) {
            maxTier = Math.max(maxTier, (int) hatch.getInputTier());
        }
        return maxTier;
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

    /** 钻头项：4^(阶级+1)（铁=4、钢=16、钛=64、硅岩=256、无尽=1024）。 */
    private static long drillBonus(int drillRung) {
        return 1L << (2 * (drillRung + 1));
    }

    /** 输入总线中钻头阶级最高者，无则为 null（同阶级先到先得，材质名仅影响 GUI 显示）。 */
    @Nullable
    private DrillSelection findDrill() {
        DrillSelection best = null;
        for (ItemStack input : getStoredInputs()) {
            if (input == null) continue;
            for (int rung = 0; rung < DRILL_MATERIALS.size(); rung++) {
                final Materials material = DRILL_MATERIALS.get(rung);
                final ItemStack drillStack = GTOreDictUnificator.get(OrePrefixes.toolHeadDrill, material, 1);
                // 正常构建中不会为 null（RecipeLoader 已校验）；GT 数据损坏时跳过而不崩溃。
                if (drillStack == null) continue;
                if (input.isItemEqual(drillStack) && (best == null || rung > best.rung)) {
                    best = new DrillSelection(input, material.mName, rung);
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

    /** 钻头行：总线中钻头的材质名称与阶级加成（x4^(阶级+1)），或"无"标签。 */
    private String resolveDrillDisplayName() {
        if (displayDrillMaterial.isEmpty()) return StatCollector.translateToLocal("vmd.gui.drill.none");
        for (int rung = 0; rung < DRILL_MATERIALS.size(); rung++) {
            final Materials material = DRILL_MATERIALS.get(rung);
            if (material.mName.equals(displayDrillMaterial)) {
                return material.mDefaultLocalName + " (x" + drillBonus(rung) + ")";
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
            .addInfo(StatCollector.translateToLocal("vmd.mbtt.info.8"))
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
                "3-7",
                getCasingBlock().get(0)
                    .getDisplayName(),
                false)
            .addEnergyHatch(
                "1+",
                StatCollector
                    .translateToLocalFormatted("vmd.mbtt.structure.any_bottom_casing_min_tier", GTValues.VN[LV]),
                1)
            .addMaintenanceHatch("1", StatCollector.translateToLocal("vmd.mbtt.structure.any_bottom_casing"), 1)
            .addInputBus("1+", StatCollector.translateToLocal("vmd.mbtt.structure.input_bus"), 1)
            .addOutputBus("0-1", StatCollector.translateToLocal("vmd.mbtt.structure.output_bus"), 1)
            .addOutputHatch("1+", StatCollector.translateToLocal("vmd.mbtt.structure.output_hatch"), 1)
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
    public boolean supportsBatchMode() {
        // 产出由能源仓电压档与机器档位决定，批量模式开关对本机无意义，禁用。
        return false;
    }

    @Override
    public boolean supportsSingleRecipeLocking() {
        return false;
    }
}
