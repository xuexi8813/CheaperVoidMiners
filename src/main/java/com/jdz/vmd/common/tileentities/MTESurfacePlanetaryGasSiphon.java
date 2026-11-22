package com.jdz.vmd.common.tileentities;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.activeCoils;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofCoil;
import static gregtech.api.util.GTStructureUtility.ofFrame;
import static gtnhintergalactic.recipe.GasSiphonRecipes.calculateEUt;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import bartworks.system.material.WerkstoffLoader;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.misc.GTStructureChannels;
import gtnhintergalactic.client.IGTextures;

/**
 * 地表行星气体钻机（Surface Planetary Gas Siphon）：GT5u 原版 Planetary Gas Siphon
 * （{@code gtnhintergalactic.tile.multi.MTEPlanetaryGasSiphon}）的"地表"加强版。
 * <p>
 * 与原版的差异（本机设计目标）：
 * <ul>
 * <li>摆脱空间站限制——原版要求建在环绕气态行星的空间站轨道
 * （{@code checkProcessing} 校验 {@code IOrbitDimension}，失败即 no_space_station），
 * 本机删除该校验，任意维度的地表均可搭建；其余结构要求与原版 1:1（含 Heating Coil 层、
 * TungstenSteel 框架、Rebolted Rhodium Palladium 机壳与 Siphon 机壳数量下限）。</li>
 * <li>不做维度方块选择：原版按"所在轨道对应的行星 + Depth（1..4）"二维选择气体；本机把原版
 * 全部可抽气体汇总为一张全局表（{@link PlanetaryGasTableHelper}，按行星 Tier 升序拉平编号），
 * 编程电路 1..N 选第 N 项（钻机同款语义：未插电路、配置 0 或超范围 → 失败提示）。</li>
 * <li>产量电压公式：产量 = 原版基础产量 × 16^max(0, 机器电压档 − 该气体行星 Tier)；
 * 机器电压档低于行星 Tier 的气体不可选（失败提示）。EU/t 仍按原版公式（Depth × 4^(行星Tier+2)）
 * 与原版完美超频规则运行，<b>不随 16^ 产量加成放大</b>。</li>
 * <li>钻头加成：与虚空流体钻机同款阶级制（铁×4/钢×16/钛×64/硅岩×256/无尽×1024，取最高阶级），
 * 每周期启动成功 1% 概率消耗 1 个钻头（钻机为 0.1%，独立常量；判定/扣减共用
 * {@link DrillSelection}）。</li>
 * </ul>
 * 其余机制与原版 1:1：Heating Coil 每级 +10% 速度（{@link #speedBoost}）、Mining Pipes 需求
 * （Depth × 64 根常备、不消耗）、完美超频（产量 ×2^oc、EUt ×4^oc）、批量模式、流体输出保护
 * （supportsVoidProtection）。原版的轨道空间站 chunk loading（IChunkLoader + 焊接枪切换）随空间站
 * 限制一并移除——地表多方块随所在区块正常加载/暂停，GT 标准行为。
 */
@IMetaTileEntity.SkipGenerateDescription
@IMetaTileEntity.SkipGenerateName
public class MTESurfacePlanetaryGasSiphon extends MTEExtendedPowerMultiBlockBase<MTESurfacePlanetaryGasSiphon>
    implements ISurvivalConstructable {

    private static final Logger LOG = LogManager.getLogger("CheaperVoidMiners");

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int OFFSET_X = 6;
    private static final int OFFSET_Y = 7;
    private static final int OFFSET_Z = 5;
    private static final double SPEED_PER_COIL = 0.10;
    private static final double LOG4 = Math.log10(4);
    private static final int MINING_PIPES_PER_DEPTH = 64;

    /** 本机钻头消耗概率：每周期启动成功 1%（虚空流体钻机为 0.1%，按需求独立成常量）。 */
    public static final double DRILL_CONSUMPTION_CHANCE = 0.01;

    private HeatingCoilLevel coilLevel;
    private int casingAmount;

    /**
     * 结构定义与原版 1:1（逐字符复刻）：13×23×13，'B' = TungstenSteel 框架，'D' = Rebolted
     * Rhodium Palladium 机壳（BWBlockCasingsAdvanced meta 88），'C' = Siphon 机壳或仓体位
     * （能源/维护/输入总线/输出仓），'A' = Heating Coil 层（GTStructureChannels.HEATING_COIL 通道）。
     * casingIndex 取原版 Siphon 机壳贴图 id（154，Casings 注册表已登记，满足 casingIndex&gt;0 契约）。
     */
    private static final IStructureDefinition<MTESurfacePlanetaryGasSiphon> STRUCTURE_DEFINITION = StructureDefinition
        .<MTESurfacePlanetaryGasSiphon>builder()
        .addShape(
            STRUCTURE_PIECE_MAIN,
            new String[][] { { "             ", "             ", "             ", "             ", "             ",
                "             ", "             ", "      C      ", "      C      ", "   CCCACCC   ", "     CAC     ",
                "     CAC     ", "      C      ", "      C      ", "             ", "             ", "             ",
                "             ", "             ", "             ", "             ", "             ", "             " },
                { "             ", "             ", "             ", "             ", "             ", "             ",
                    "             ", "             ", "             ", "  C   B   C  ", "             ",
                    "             ", "             ", "             ", "             ", "             ",
                    "             ", "             ", "             ", "             ", "             ",
                    "             ", "             " },
                { "    CCCCC    ", "             ", "             ", "             ", "             ", "             ",
                    "             ", "             ", "             ", " C    B    C ", "             ",
                    "             ", "             ", "             ", "             ", "             ",
                    "             ", "             ", "             ", "             ", "             ",
                    "             ", "             " },
                { "   C  B  C   ", "             ", "             ", "             ", "             ", "             ",
                    "             ", "             ", "             ", "C     B     C", "             ",
                    "             ", "             ", "             ", "             ", "             ",
                    "             ", "             ", "             ", "             ", "             ",
                    "             ", "             " },
                { "  C   B   C  ", "             ", "             ", "             ", "             ", "             ",
                    "    B   B    ", "    B   B    ", "    B   B    ", "C   B B B   C", "    B   B    ",
                    "    B   B    ", "    B   B    ", "    B   B    ", "    B   B    ", "    B   B    ",
                    "             ", "             ", "             ", "             ", "             ",
                    "             ", "             " },
                { "  C   B   C  ", "      C      ", "      C      ", "     CCC     ", "     CCC     ", "     CCC     ",
                    "     CCC     ", "     C~C     ", "     CCC     ", "C    CCC    C", "C    BCB    C",
                    "C    BCB    C", "     BCB     ", "      C      ", "      C      ", "      C      ",
                    "      C      ", "      B      ", "      B      ", "      B      ", "             ",
                    "             ", "             " },
                { "  CBBBBBBBC  ", "     CCC     ", "     CCC     ", "     C C     ", "     C C     ", "     C C     ",
                    "     C C     ", "C    C C    C", "C    C C    C", "ABBBBC CBBBBA", "A    CCC    A",
                    "A    CCC    A", "C    CCC    C", "C    CCC    C", "     CCC     ", "     CCC     ",
                    "     CCC     ", "     BDB     ", "     BDB     ", "     BDB     ", "      D      ",
                    "      D      ", "      D      " },
                { "  C   B   C  ", "      C      ", "      C      ", "     CCC     ", "     CCC     ", "     CCC     ",
                    "     CCC     ", "     CCC     ", "     CCC     ", "C    CCC    C", "C    BCB    C",
                    "C    BCB    C", "     BCB     ", "      C      ", "      C      ", "      C      ",
                    "      C      ", "      B      ", "      B      ", "      B      ", "             ",
                    "             ", "             " },
                { "  C   B   C  ", "             ", "             ", "             ", "             ", "             ",
                    "    B   B    ", "    B   B    ", "    B   B    ", "C   B B B   C", "    B   B    ",
                    "    B   B    ", "    B   B    ", "    B   B    ", "    B   B    ", "    B   B    ",
                    "             ", "             ", "             ", "             ", "             ",
                    "             ", "             " },
                { "   C  B  C   ", "             ", "             ", "             ", "             ", "             ",
                    "             ", "             ", "             ", "C     B     C", "             ",
                    "             ", "             ", "             ", "             ", "             ",
                    "             ", "             ", "             ", "             ", "             ",
                    "             ", "             " },
                { "    CCCCC    ", "             ", "             ", "             ", "             ", "             ",
                    "             ", "             ", "             ", " C    B    C ", "             ",
                    "             ", "             ", "             ", "             ", "             ",
                    "             ", "             ", "             ", "             ", "             ",
                    "             ", "             " },
                { "             ", "             ", "             ", "             ", "             ", "             ",
                    "             ", "             ", "             ", "  C   B   C  ", "             ",
                    "             ", "             ", "             ", "             ", "             ",
                    "             ", "             ", "             ", "             ", "             ",
                    "             ", "             " },
                { "             ", "             ", "             ", "             ", "             ", "             ",
                    "             ", "      C      ", "      C      ", "   CCCACCC   ", "     CAC     ",
                    "     CAC     ", "      C      ", "      C      ", "             ", "             ",
                    "             ", "             ", "             ", "             ", "             ",
                    "             ", "             " } })
        .addElement('B', ofFrame(Materials.TungstenSteel))
        .addElement('D', ofBlock(WerkstoffLoader.BWBlockCasingsAdvanced, 88))
        .addElement(
            'C',
            ofChain(
                buildHatchAdder(MTESurfacePlanetaryGasSiphon.class).atLeast(Maintenance, InputBus, OutputHatch, Energy)
                    .hint(1)
                    .casingIndex(IGTextures.CASING_INDEX_SIPHON)
                    .build(),
                onElementPass(x -> ++x.casingAmount, ofBlock(GregTechAPI.sBlockCasingsSiphon, 0))))
        .addElement(
            'A',
            GTStructureChannels.HEATING_COIL.use(
                activeCoils(
                    ofCoil(MTESurfacePlanetaryGasSiphon::setCoilLevel, MTESurfacePlanetaryGasSiphon::getCoilLevel))))
        .build();

    public MTESurfacePlanetaryGasSiphon(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTESurfacePlanetaryGasSiphon(String aName) {
        super(aName);
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, OFFSET_X, OFFSET_Y, OFFSET_Z);
    }

    @Override
    public int survivalConstruct(ItemStack stack, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stack,
            OFFSET_X,
            OFFSET_Y,
            OFFSET_Z,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tileEntity) {
        return new MTESurfacePlanetaryGasSiphon(this.mName);
    }

    @Override
    public IStructureDefinition<MTESurfacePlanetaryGasSiphon> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity baseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstone) {
        if (side == facing) {
            if (active) return new ITexture[] {
                Textures.BlockIcons.getCasingTextureForId(IGTextures.CASING_INDEX_SIPHON), TextureFactory.builder()
                    .addIcon(IGTextures.SIPHON_OVERLAY_FRONT)
                    .extFacing()
                    .build(),
                TextureFactory.builder()
                    .addIcon(IGTextures.SIPHON_OVERLAY_FRONT_ACTIVE_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(IGTextures.CASING_INDEX_SIPHON),
                TextureFactory.builder()
                    .addIcon(IGTextures.SIPHON_OVERLAY_FRONT)
                    .extFacing()
                    .build(),
                TextureFactory.builder()
                    .addIcon(IGTextures.SIPHON_OVERLAY_FRONT_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(IGTextures.CASING_INDEX_SIPHON) };
    }

    @Override
    public @NotNull CheckRecipeResult checkProcessing() {
        final List<PlanetaryGasTableHelper.GasEntry> gasTable = PlanetaryGasTableHelper.getEntries();
        if (gasTable.isEmpty()) {
            // Fail Fast：GT5u 气体清单为空（gtnhintergalactic 未启用或数据异常，构建时已记错误日志），
            // 机器不可用，禁止静默空表。
            resetMachine();
            return SimpleCheckRecipeResult.ofFailure("vmd_siphon_no_gas_data");
        }

        final List<ItemStack> storedInputs = getStoredInputs();

        // 编程电路（配置 1..N）选择全局气体表第 N 项（钻机同款语义）：未插入、配置 0 或超出范围一律不启动。
        final int circuitConfig = readCircuitConfig(storedInputs);
        if (circuitConfig < 1) {
            resetMachine();
            return SimpleCheckRecipeResult.ofFailure("vmd_siphon_circuit_required");
        }
        if (circuitConfig > gasTable.size()) {
            resetMachine();
            return SimpleCheckRecipeResult.ofFailure("vmd_siphon_circuit_out_of_range");
        }
        final PlanetaryGasTableHelper.GasEntry gas = gasTable.get(circuitConfig - 1);

        // 机器电压档 = 能源仓输入电压档（checkMachine 已强制恰好 1 台能源仓，getMaxInputVoltage 的
        // 求和即单仓电压）。低于气体行星 Tier 的气体不可选。
        final long maxInputVoltage = getMaxInputVoltage();
        final int machineTier = (int) GTUtility.getTier(maxInputVoltage);
        if (machineTier < gas.planetTier) {
            resetMachine();
            return SimpleCheckRecipeResult.ofFailure("vmd_siphon_machine_tier_too_low");
        }

        // Mining Pipes 常备量校验（原版 1:1：Depth × 64 根，仅要求常备、不消耗）。
        final ItemStack miningPipe = GTModHandler.getIC2Item("miningPipe", 0);
        if (miningPipe == null) {
            LOG.error("IC2 mining pipe item is missing, the pipe requirement cannot be satisfied");
            resetMachine();
            return SimpleCheckRecipeResult.ofFailure("vmd_siphon_no_mining_pipe");
        }
        long pipeCount = 0;
        for (ItemStack stack : storedInputs) {
            if (stack != null && stack.getItem() == miningPipe.getItem()) {
                pipeCount += stack.stackSize;
            }
        }
        if (pipeCount < (long) gas.depth * MINING_PIPES_PER_DEPTH) {
            resetMachine();
            return SimpleCheckRecipeResult.ofFailure("vmd_siphon_no_mining_pipe");
        }

        final DrillSelection drill = DrillSelection.findDrill(storedInputs);

        // 输出仓容量按原版基础产量校验（原版 1:1：canOutputAll 先于超频/加成放大；过量产出由 GT
        // 销毁模式处理，本机 supportsVoidProtection=true）。
        if (!canOutputAll(new FluidStack[] { gas.fluid })) {
            return CheckRecipeResultRegistry.FLUID_OUTPUT_FULL;
        }

        // EUt = 原版公式 Depth × 4^(行星Tier+2)（不随 16^ 电压加成放大），完美超频规则与原版一致：
        // oc = floor(log4(输入电压 / EUt))，oc ≥ 1 时产量 ×2^oc、EUt ×4^oc。
        final int recipeEUt = calculateEUt(gas.depth, gas.planetTier);
        final int ocLevel = MathHelper.floor_double(Math.log10((double) maxInputVoltage / recipeEUt) / LOG4);
        if (ocLevel < 0) {
            resetMachine();
            return CheckRecipeResultRegistry.insufficientPower(recipeEUt);
        }

        // 产量 = 原版基础产量 × 16^max(0, 机器档 − 行星Tier) × 钻头项 4^(阶级+1) × 超频产量项 2^oc。
        // 全部为 2 的幂乘积，按位移位数累加（16^x = 1<<4x、4^x = 1<<2x）并经饱和位移防 long 回绕；
        // 写 FluidStack 时再按 int 上限截断。
        int amountBits = 4 * Math.max(0, machineTier - gas.planetTier);
        if (drill != null) amountBits += 2 * (drill.rung + 1);
        if (ocLevel > 0) amountBits += ocLevel;
        final long outputAmount = saturatingShift(gas.fluid.amount, amountBits);

        final int processTime = (int) (20 * speedBoost(getCoilTier()));
        int batchMultiplierMax = 1;
        if (isBatchModeEnabled()) {
            batchMultiplierMax = getMaxBatchSize() / processTime;
        }

        final FluidStack output = gas.fluid.copy();
        output.amount = (int) Math.min(saturatingMultiply(outputAmount, batchMultiplierMax), Integer.MAX_VALUE);
        mOutputFluids = new FluidStack[] { output };

        if (ocLevel == 0) {
            lEUt = -recipeEUt;
        } else {
            // EUt ×4^oc（原版 1:1：oc-- 后 lEUt = -recipeEUt × (4 << 2×(oc-1)) = -recipeEUt × 4^oc）：
            // 位移位数 = 2×oc，饱和防回绕（原版 int 乘法在极端电压下会溢出为负）。
            lEUt = -saturatingShift(recipeEUt, 2 * ocLevel);
        }
        mEfficiency = 10000 - (getIdealStatus() - getRepairStatus()) * 1000;
        mEfficiencyIncrease = 10000;
        mMaxProgresstime = processTime * batchMultiplierMax;

        // 钻头消耗：每周期启动成功 1% 概率消耗 1 个（配方窗口内扣减，ME 总线兼容语义见 DrillSelection）。
        if (drill != null) {
            drill.consumeIfRolled(DRILL_CONSUMPTION_CHANCE, this);
        }

        return SimpleCheckRecipeResult.ofSuccess("vmd_siphoning");
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        casingAmount = 0;
        setCoilLevel(HeatingCoilLevel.None);
        if (!checkPiece(STRUCTURE_PIECE_MAIN, OFFSET_X, OFFSET_Y, OFFSET_Z, errors)) return;
        checkHatchExact(errors, InputBus, 1);
        checkHatchExact(errors, OutputHatch, 1);
        checkHatchExact(errors, Energy, 1);
        checkHatchMax(errors, HatchElement.InputHatch, 0);
        checkHatchMax(errors, HatchElement.OutputBus, 0);
        checkHasMaintenanceHatch(errors);
        checkCasingMin(errors, casingAmount, 175);
    }

    private void resetMachine() {
        lEUt = 0;
        mEfficiency = 0;
    }

    /**
     * 饱和左移：base × 2^bits。结果超出 long 范围时按 {@code Long.MAX_VALUE} 饱和——输出语义不变
     * （FluidStack 写入时按 int 上限截断），仅防回绕为负。bits ≥ 64 时 Java 位移位数按 64 取模，
     * 必须先于位移显式判饱和。
     */
    private static long saturatingShift(long base, int bits) {
        if (base <= 0) return 0;
        if (bits >= Long.SIZE || base > (Long.MAX_VALUE >> bits)) return Long.MAX_VALUE;
        return base << bits;
    }

    /** 饱和乘法：任一因子非正得 0，结果超出 long 范围时按 {@code Long.MAX_VALUE} 饱和。 */
    private static long saturatingMultiply(long a, long b) {
        if (a <= 0 || b <= 0) return 0;
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;
        return a * b;
    }

    /** 输入堆叠中第一个编程电路的配置值（GT 标准幽灵电路堆叠同样命中），不存在时为 0。 */
    private static int readCircuitConfig(List<ItemStack> storedInputs) {
        for (ItemStack stack : storedInputs) {
            if (ItemList.Circuit_Integrated.isStackEqual(stack, true, true)) {
                return stack.getItemDamage();
            }
        }
        return 0;
    }

    // ==== Heating Coil（原版 1:1） ====

    public void setCoilLevel(HeatingCoilLevel coilLevel) {
        this.coilLevel = coilLevel;
    }

    public HeatingCoilLevel getCoilLevel() {
        return this.coilLevel;
    }

    /** 线圈档位（HeatingCoilLevel.getTier() + 1，None = 0），驱动 WAILA 与速度公式。 */
    public int getCoilTier() {
        return this.coilLevel == null ? 0 : (int) this.coilLevel.getTier() + 1;
    }

    /** 周期时长系数（原版 1:1）：1 / (2 + 0.1 × max(1, 线圈档))，即每级线圈 +10% 速度。 */
    public float speedBoost(int coilTier) {
        return (float) 1 / (2 + (float) SPEED_PER_COIL * Math.max(1, coilTier));
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        // 不可朝上/朝下、不可旋转、不得垂直翻转（原版 1:1）。
        return (d, r, f) -> d.offsetY == 0 && r.isNotRotated() && !f.isVerticallyFliped();
    }

    @Override
    public boolean supportsVoidProtection() {
        // 开启 GT5u 销毁模式切换（原版 1:1）：输出仓满时过量产出由 GT 销毁逻辑处理。
        return true;
    }

    @Override
    public boolean supportsBatchMode() {
        // 批量模式（原版 1:1）：产出与时长同乘 batch 倍数。
        return true;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("vmd.siphon.machine_type"))
            .addInfo(StatCollector.translateToLocal("vmd.siphon.info.1"))
            .addInfo(StatCollector.translateToLocal("vmd.siphon.info.2"))
            .addInfo(StatCollector.translateToLocal("vmd.siphon.info.3"))
            .addInfo(StatCollector.translateToLocal("vmd.siphon.info.4"))
            .addInfo(StatCollector.translateToLocal("vmd.siphon.info.5"))
            .addInfo(StatCollector.translateToLocal("vmd.siphon.info.6"))
            .addInfo(StatCollector.translateToLocal("vmd.siphon.info.7"))
            .beginStructureBlock(13, 23, 13, false)
            .addController(StatCollector.translateToLocal("vmd.siphon.structure.controller"))
            .addCasing("184", StatCollector.translateToLocal("vmd.siphon.structure.siphon_casing"), false)
            .addCasing("93", StatCollector.translateToLocal("vmd.siphon.structure.frame"), false)
            .addCasing("12", StatCollector.translateToLocal("GT5U.MBTT.Tiers.Coil"), true)
            .addCasing("6", StatCollector.translateToLocal("vmd.siphon.structure.rebolted_casing"), false)
            .addEnergyHatch("1", StatCollector.translateToLocal("vmd.siphon.structure.any_siphon_casing"), 1)
            .addMaintenanceHatch("1", StatCollector.translateToLocal("vmd.siphon.structure.any_siphon_casing"), 1)
            .addInputBus("1", StatCollector.translateToLocal("vmd.siphon.structure.input_bus"), 1)
            .addOutputHatch("1", StatCollector.translateToLocal("vmd.siphon.structure.output_hatch"), 1)
            .addStructureInfo("")
            .addSubChannel(GTStructureChannels.HEATING_COIL)
            .toolTipFinisher();
        return tt;
    }
}
