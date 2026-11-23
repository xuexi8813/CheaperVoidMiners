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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.Constants;
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
import com.jdz.vmd.api.recipe.VmdRecipeMaps;

import bartworks.system.material.WerkstoffLoader;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReason;
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
 * <li>产量电压公式：产量 = 本机每周期基础产量 × 16^max(0, 机器电压档 − 该气体行星 Tier)；
 * 机器电压档低于行星 Tier 的气体不可选（失败提示）。EU/t 仍按原版公式（Depth × 4^(行星Tier+2)）
 * 与原版无损超频规则运行，<b>不随 16^ 产量加成放大</b>。</li>
 * <li>钻头加成：与虚空流体钻机同款阶级制（铁×4/钢×16/钛×64/硅岩×256/无尽×1024，取最高阶级），
 * 每周期启动成功 1% 概率消耗 1 个钻头（钻机为 0.1%，独立常量；判定/扣减共用
 * {@link DrillSelection}）。</li>
 * </ul>
 * 未计线圈加速的基准周期为 200t（10 秒），每周期基础产量按旧基准 9t 的实际产速折算
 * （{@link #calculateBaseOutputAmount}）。加热线圈仍为必需，加速沿用原版速度曲线
 * （{@link #calculateProcessTimeTicks}）。
 * 其余机制与原版 1:1：Mining Pipes 需求（Depth × 64 根常备、不消耗）、无损超频（产量 ×2^oc、
 * EUt ×4^oc）。批量时长预算按 200/9 同比放大；完工气体独立保存并分片排出，输出保护下保留未接收的气体。
 * 原版的轨道空间站 chunk loading（IChunkLoader + 焊接枪切换）随空间站限制一并移除——地表多方块随所在区块
 * 正常加载/暂停，GT 标准行为。
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
    private static final double LOG4 = Math.log10(4);

    /**
     * 单位钻井深度的 Mining Pipes 常备量（原版 Siphon 1:1：Depth × 64，仅要求常备、不消耗）。
     * 机器校验（{@link #checkProcessing}）与 NEI 需求展示（{@code VmdOutputRecipeLoader}）共用本
     * 常量，单一数据源防止两处漂移。
     */
    public static final int MINING_PIPES_PER_DEPTH = 64;

    /** 未计线圈加速的基准周期时长（ticks）：200t = 10 秒，供运行时计算与 NEI 时长展示共用。 */
    public static final int BASE_PROCESS_TIME_TICKS = 200;

    /** 旧机器在最低档线圈下的实际周期：原公式 {@code (int) (20 * (1.0f / (2 + 0.10f)))} 得 9t。 */
    private static final int LEGACY_BASE_PROCESS_TIME_TICKS = 9;

    /** 本机钻头消耗概率：每周期启动成功 1%（虚空流体钻机为 0.1%，按需求独立成常量）。 */
    public static final double DRILL_CONSUMPTION_CHANCE = 0.01;

    private static final String IN_FLIGHT_GAS_KEY = "vmdSiphonInFlightGas";
    private static final String PENDING_GAS_KEY = "vmdSiphonPendingGas";

    private HeatingCoilLevel coilLevel;
    private int casingAmount;

    /** 只有正常完成的周期才把在制气体移入待排；GT 停机只取消这一部分。 */
    private SiphonGasOutput inFlightGas;
    /** 正常周期只产生一种气体；队列也能一次接管旧存档里的输出数组。 */
    private final Deque<SiphonGasOutput> pendingGas = new ArrayDeque<>();

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
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        if (inFlightGas != null) tag.setTag(IN_FLIGHT_GAS_KEY, inFlightGas.save());
        else tag.removeTag(IN_FLIGHT_GAS_KEY);
        final NBTTagList pending = new NBTTagList();
        for (SiphonGasOutput output : pendingGas) {
            pending.appendTag(output.save());
        }
        tag.setTag(PENDING_GAS_KEY, pending);
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        inFlightGas = tag.hasKey(IN_FLIGHT_GAS_KEY, Constants.NBT.TAG_COMPOUND)
            ? SiphonGasOutput.load(tag.getCompoundTag(IN_FLIGHT_GAS_KEY))
            : null;
        pendingGas.clear();
        final NBTTagList pending = tag.getTagList(PENDING_GAS_KEY, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < pending.tagCount(); i++) {
            final SiphonGasOutput output = SiphonGasOutput.load(pending.getCompoundTagAt(i));
            if (output != null) pendingGas.addLast(output);
        }
    }

    @Override
    public void stopMachine(@NotNull ShutDownReason reason) {
        inFlightGas = null;
        super.stopMachine(reason);
        markDirty();
    }

    @Override
    protected void runMachine(IGregTechTileEntity tile, long tick) {
        // Retry after the base has revalidated the structure, even if the controller is disabled.
        if (!pendingGas.isEmpty()) {
            drainPendingGas();
            if (pendingGas.isEmpty()) scheduleRecipeCheckImmediate();
        }
        super.runMachine(tile, tick);
    }

    @Override
    protected boolean addFluidOutputs(@NotNull FluidStack[] oldOutputs) {
        // GT calls this at completion, before clearing mOutputFluids and calling outputAfterRecipe.
        for (FluidStack output : oldOutputs) {
            if (output != null && output.amount > 0) {
                enqueueCompletedGas(new SiphonGasOutput(output, output.amount));
            }
        }
        return pendingGas.isEmpty();
    }

    @Override
    protected void outputAfterRecipe() {
        if (inFlightGas != null) {
            enqueueCompletedGas(inFlightGas);
            inFlightGas = null;
        }
        // Try once at completion, so an available output does not add an idle tick to every cycle.
        if (!pendingGas.isEmpty()) drainPendingGas();
    }

    private void enqueueCompletedGas(SiphonGasOutput output) {
        output.finish(protectsExcessFluid());
        pendingGas.addLast(output);
    }

    private void drainPendingGas() {
        final SiphonGasOutput output = pendingGas.getFirst();
        final boolean delivered = output.drainOneSlice(getOutputHatches());
        if (output.remaining() == 0) {
            pendingGas.removeFirst();
        } else if (!delivered) {
            return;
        }
        markDirty();
    }

    @Override
    public @NotNull CheckRecipeResult checkProcessing() {
        if (!pendingGas.isEmpty()) {
            resetMachine();
            return CheckRecipeResultRegistry.FLUID_OUTPUT_FULL;
        }
        final List<PlanetaryGasTableHelper.GasEntry> gasTable = PlanetaryGasTableHelper.getEntries();
        if (gasTable.isEmpty()) {
            // Fail Fast：GT5u 气体清单为空（gtnhintergalactic 未启用或数据异常，构建时已记错误日志），
            // 机器不可用，禁止静默空表。
            resetMachine();
            return SimpleCheckRecipeResult.ofFailure("vmd_siphon_no_gas_data");
        }

        final List<ItemStack> storedInputs = getStoredInputs();

        // 编程电路（配置 1..N）选择全局气体表第 N 项（钻机同款语义）：未插入、配置 0 或超出范围一律不启动。
        final int circuitConfig = IntegratedCircuitInputs.readFirstConfig(storedInputs);
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
        // 与原版的唯一扫描差异：本机扫描范围限输入总线（原版还扫描控制器槽）。
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

        // 只检查真实仓能否接收一点气体，未接收的完工量由控制器暂存，普通小仓也能工作。
        if (protectsExcessFluid() && !SiphonGasOutput.hasSpace(gas.fluid, getOutputHatches())) {
            resetMachine();
            return CheckRecipeResultRegistry.FLUID_OUTPUT_FULL;
        }
        final long baseOutputAmount = calculateBaseOutputAmount(gas.fluid.amount);

        // EUt = 原版公式 Depth × 4^(行星Tier+2)（不随 16^ 电压加成放大），无损超频规则与原版一致：
        // oc = floor(log4(输入电压 / EUt))，oc ≥ 1 时产量 ×2^oc、EUt ×4^oc。
        final int recipeEUt = calculateEUt(gas.depth, gas.planetTier);
        final int ocLevel = MathHelper.floor_double(Math.log10((double) maxInputVoltage / recipeEUt) / LOG4);
        if (ocLevel < 0) {
            resetMachine();
            return CheckRecipeResultRegistry.insufficientPower(recipeEUt);
        }

        // 产量 = 折算后的基础产量 × 16^max(0, 机器档 − 行星Tier) × 钻头项 4^(阶级+1) × 超频产量项 2^oc。
        // 各倍率均为 2 的幂，按位移位数累加（16^x = 1<<4x、4^x = 1<<2x）并经饱和位移防 long 回绕。
        int amountBits = 4 * Math.max(0, machineTier - gas.planetTier);
        if (drill != null) amountBits += 2 * (drill.rung + 1);
        if (ocLevel > 0) amountBits += ocLevel;
        final long outputAmount = saturatingShift(baseOutputAmount, amountBits);

        final int processTime = calculateProcessTimeTicks(getCoilTier());
        final int batchMultiplier = isBatchModeEnabled() ? Math.max(1, getMaxBatchSize() / processTime) : 1;
        inFlightGas = new SiphonGasOutput(gas.fluid, saturatingMultiply(outputAmount, batchMultiplier));

        if (ocLevel == 0) {
            lEUt = -recipeEUt;
        } else {
            // EUt ×4^oc（原版 1:1：oc-- 后 lEUt = -recipeEUt × (4 << 2×(oc-1)) = -recipeEUt × 4^oc）：
            // 位移位数 = 2×oc，饱和防回绕（原版 int 乘法在极端电压下会溢出为负）。
            lEUt = -saturatingShift(recipeEUt, 2 * ocLevel);
        }
        mEfficiency = 10000 - (getIdealStatus() - getRepairStatus()) * 1000;
        mEfficiencyIncrease = 10000;
        mMaxProgresstime = processTime * batchMultiplier;

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
     * 关联 NEI 展示用配方表。基类 {@code getRecipeMap()} 默认返回 null，{@code RecipeMapWorkable#getAvailableRecipeMaps}
     * 由此得空列表，而 GT5u 的 NEI catalyst 索引（{@code NEIGTConfig#generateRecipeCatalystIndex}）只为
     * 可用配方表非空的机器注册 catalyst——缺省则本机不会出现在 {@code surfaceGasSiphonRecipes} 页签的
     * catalyst 列表，NEI 用法页（点本机控制器）无对应条目，且该页签的 HandlerInfo 显示栈
     * （{@code NEIGTConfig#createHandlerInfoBuilderTemplate}）同样取自 catalyst、落为 null，页签整体缺失。
     * 本机配方选择完全由 {@link #checkProcessing()} 自定义逻辑完成、不查该表，故该关联仅服务 NEI 展示，
     * 与虚空流体钻机、木制虚空采矿机的形态一致。
     */
    @Override
    public RecipeMap<?> getRecipeMap() {
        return VmdRecipeMaps.surfaceGasSiphonRecipes;
    }

    /**
     * 饱和左移：base × 2^bits。结果超出 long 范围时按 {@code Long.MAX_VALUE} 饱和，防回绕为负。
     * bits ≥ 64 时 Java 位移位数按 64 取模，必须先于位移显式判饱和。
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

    // ==== Heating Coil（原版 1:1） ====

    public void setCoilLevel(HeatingCoilLevel coilLevel) {
        this.coilLevel = coilLevel;
    }

    public HeatingCoilLevel getCoilLevel() {
        return this.coilLevel;
    }

    /** 线圈档位（HeatingCoilLevel.getTier() + 1，None = -1），驱动周期公式。 */
    public int getCoilTier() {
        return this.coilLevel == null ? 0 : (int) this.coilLevel.getTier() + 1;
    }

    /**
     * 本机每周期基础产量（mB）：按旧基准 9t 的实际产速折算至 200t，不足 1mB 的部分向下取整。
     * 运行时与 NEI 共用此换算，先用 long 计算，再由调用方在写入 FluidStack 时按 int 上限截断。
     *
     * @param legacyAmount 原配方每周期的基础产量（mB）
     * @return 未计档位、钻头、超频及批量的本机每周期产量（mB）
     */
    public static long calculateBaseOutputAmount(int legacyAmount) {
        return (long) legacyAmount * BASE_PROCESS_TIME_TICKS / LEGACY_BASE_PROCESS_TIME_TICKS;
    }

    /**
     * 周期时长（ticks）：保留原版 speedBoost 的加速曲线 {@code 1 / (2 + 0.1 × max(1, coilTier))}，
     * 归一化到最低档的 {@link #BASE_PROCESS_TIME_TICKS}。用整数式 {@code 200 × 21 / (20 + max(1, coilTier))}
     * 向下取整；各档取整后的周期不保证与旧周期等比，例如 {@code coilTier = 4} 时为 175t。
     */
    private static int calculateProcessTimeTicks(int coilTier) {
        return BASE_PROCESS_TIME_TICKS * 21 / (20 + Math.max(1, coilTier));
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        // 不可朝上/朝下、不可旋转、不得垂直翻转（原版 1:1）。
        return (d, r, f) -> d.offsetY == 0 && r.isNotRotated() && !f.isVerticallyFliped();
    }

    @Override
    public boolean supportsVoidProtection() {
        return true;
    }

    @Override
    public boolean supportsBatchMode() {
        return true;
    }

    /** 本机批量时长预算沿用旧版 128t，并按基础周期调整的比例换算（向下取整）。 */
    @Override
    protected int getMaxBatchSize() {
        return super.getMaxBatchSize() * BASE_PROCESS_TIME_TICKS / LEGACY_BASE_PROCESS_TIME_TICKS;
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
