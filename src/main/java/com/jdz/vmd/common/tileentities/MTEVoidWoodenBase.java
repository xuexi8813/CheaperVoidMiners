package com.jdz.vmd.common.tileentities;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.jdz.vmd.common.gui.MTEVoidWoodenBaseGui;

import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.recipe.BasicUIProperties;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gtneioreplugin.plugin.item.ItemDimensionDisplay;

/**
 * 木制、无电力虚空机器（泵/采矿机/挖掘机）的共享基类，构建在 {@link MTEBasicMachine}（tier 0 / 电流 1）
 * 之上以获得标准机器 GUI 与配方循环；产出由各机器在 {@link #checkRecipe()} 中选择。
 * 产物留在机器内部直到被取出，不自动输出。
 * <p>
 * 槽位约定：编程电路使用 GT5u 标准幽灵电路槽（GUI 右下角点选配置，槽内影子堆叠 stackSize=0），
 * 位于输入槽之前的专用 OTHER 槽位（5.09.54.x 默认布局）；原"第一个输入槽兼电路槽"方案在幽灵模式下
 * 不可行——基类 onPostTick 在每次配方成功后会清空输入槽范围内 stackSize<=0 的堆叠，幽灵电路会被误清，
 * 故电路槽必须独立于输入槽范围，且原首输入槽对 GUI 与自动化一并封闭。
 * 挖掘机另有输入槽 1（偏移 1）放维度显示方块。
 */
public abstract class MTEVoidWoodenBase extends MTEBasicMachine {

    public static final int PRODUCTION_CYCLE_TICKS = 200;
    public static final int PRODUCTION_AMOUNT_MB = 100;
    public static final int TANK_CAPACITY_MB = 8000;

    /**
     * 物品类木制虚空机器（采矿机/挖掘机）的输出缓冲槽数：6 槽缓冲，落槽规则为
     * "同物品可堆叠优先、其次空槽放入"，全部输出槽无法容纳产出时机器待机。
     */
    public static final int OUTPUT_SLOT_COUNT = 6;

    private static final Logger LOG = LogManager.getLogger("CheaperVoidMiners");

    public MTEVoidWoodenBase(int aID, String aName, String aNameRegional, String aDescription, int aInputSlotCount,
        int aOutputSlotCount) {
        // amperage 必须为 1：GT5u 5.09.54.x 的 EUNoOverclockDescriber 校验 amperage >= 1（mEUt 恒 0，传 1 可安全通过）。
        super(aID, aName, aNameRegional, 0, 1, aDescription, aInputSlotCount, aOutputSlotCount);
    }

    public MTEVoidWoodenBase(String aName, String[] aDescription, ITexture[][][] aTextures, int aInputSlotCount,
        int aOutputSlotCount) {
        super(aName, 0, 1, aDescription, aTextures, aInputSlotCount, aOutputSlotCount);
    }

    // ==== 槽位配置 ====

    /**
     * 电路槽 = 输入槽前的专用 OTHER 槽位（getInputSlot() - 1，与 GT5u 5.09.54.x 默认电路槽布局一致），
     * 本机器的该槽位原本空闲（充电/放电槽数为 0，特殊槽为 3）。
     * 动机：幽灵电路堆叠 stackSize 恒为 0（GhostCircuitItemStackHandler 与
     * MetaTileEntity.setInventorySlotContents 均强制归零），而 MTEBasicMachine.onPostTick 在每次
     * 配方调度成功后会清空 [getInputSlot(), getInputSlot()+mInputSlotCount) 内 stackSize<=0 的堆叠；
     * 电路槽必须位于输入槽范围之外，幽灵配置才能跨生产周期保留。
     */
    @Override
    public int getCircuitSlot() {
        return getInputSlot() - 1;
    }

    /**
     * 启用 GT5u 2.9 标准幽灵电路槽，GUI 中可直接点选配置：MTETieredMachineBlockBaseGui 在右下角
     * 自动渲染 GhostCircuitSlotWidget（本方法为 true 时生效），左键/滚轮步进 1..24，Shift+右键清除，
     * Shift+左键打开选择面板；手持真实编程电路点击幽灵槽等效写入对应配置（槽内为 stackSize=0 的
     * 影子堆叠，真实物品不消耗）。基类默认 false，此处覆写为 true。
     */
    @Override
    public boolean allowSelectCircuit() {
        return true;
    }

    /**
     * 封闭首个输入槽（getInputSlot()）：电路迁移至专用幽灵槽后，三台木制机器的该槽位均无物品用途
     * （挖掘机的维度显示方块在输入槽 1）。若不封闭，isValidSlot 返回 true 会让管道经
     * canInsertItem（以 isValidSlot 为门）把杂物无声塞进 GUI 已不再渲染的死槽位。
     */
    @Override
    public boolean isValidSlot(int aIndex) {
        if (aIndex == getInputSlot()) return false;
        return super.isValidSlot(aIndex);
    }

    /** 维度显示方块槽位相对 {@link #getInputSlot()} 的偏移；无该槽为 -1（挖掘机为 1）。 */
    protected int getDimensionDisplaySlotOffset() {
        return -1;
    }

    public int getDimensionDisplaySlot() {
        final int offset = getDimensionDisplaySlotOffset();
        return offset >= 0 ? getInputSlot() + offset : -1;
    }

    protected static boolean isDimensionDisplayItem(@Nullable ItemStack stack) {
        return ItemDimensionDisplay.getDimension(stack) != null;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        // 槽位专属内容过滤，叠加在标准朝向/过滤逻辑之上。电路槽（isValidSlot 已封闭）与
        // 首个输入槽（同被封闭）不会进入此路径，仅维度显示方块槽需要类型过滤。
        final int dimensionSlot = getDimensionDisplaySlot();
        if (dimensionSlot >= 0 && aIndex == dimensionSlot && !isDimensionDisplayItem(aStack)) return false;
        return super.allowPutStack(aBaseMetaTileEntity, aIndex, side, aStack);
    }

    // ==== 无电力机器配置（青铜机器模式） ====

    @Override
    public boolean isElectric() {
        return false;
    }

    @Override
    public boolean isEnetInput() {
        return false;
    }

    @Override
    public boolean isInputFacing(ForgeDirection side) {
        return false;
    }

    @Override
    public long maxEUStore() {
        return 0;
    }

    @Override
    public long maxEUInput() {
        return 0;
    }

    @Override
    public int rechargerSlotCount() {
        return 0;
    }

    @Override
    public int dechargerSlotCount() {
        return 0;
    }

    /** 产物留在机器内直到取出；不自动输出，内部输出满时停滞。 */
    @Override
    public boolean doesAutoOutput() {
        return false;
    }

    @Override
    public boolean doesAutoOutputFluids() {
        return false;
    }

    @Override
    public boolean canHaveInsufficientEnergy() {
        return false;
    }

    /** 无电力机器：配方检查不受能源阻塞。 */
    @Override
    public boolean hasEnoughEnergyToCheckRecipe() {
        return true;
    }

    @Override
    protected boolean drainEnergyForProcess(long aEUt) {
        // mEUt 恒 0，推进周期不消耗能量。
        return true;
    }

    @Override
    public boolean willExplodeInRain() {
        // 与原版储罐一样防雨。
        return false;
    }

    // ==== 贴图：木制机壳本体 + 正面覆盖层，14 面系统由标准基类处理 ====

    protected abstract ITexture getFrontOverlay();

    private ITexture[] getBodyTexture() {
        return new ITexture[] { TextureFactory.of(woodenCasingBlock(), woodenCasingMeta()) };
    }

    private ITexture[] getFrontTexture() {
        return new ITexture[] { TextureFactory.of(woodenCasingBlock(), woodenCasingMeta()), getFrontOverlay() };
    }

    private static net.minecraft.block.Block woodenCasingBlock() {
        return ItemList.WoodenCasing.getBlock();
    }

    private static int woodenCasingMeta() {
        return ItemList.WoodenCasing.get(0)
            .getItemDamage();
    }

    @Override
    public ITexture[] getSideFacingActive(byte aColor) {
        return getBodyTexture();
    }

    @Override
    public ITexture[] getSideFacingInactive(byte aColor) {
        return getBodyTexture();
    }

    @Override
    public ITexture[] getFrontFacingActive(byte aColor) {
        return getFrontTexture();
    }

    @Override
    public ITexture[] getFrontFacingInactive(byte aColor) {
        return getFrontTexture();
    }

    @Override
    public ITexture[] getTopFacingActive(byte aColor) {
        return getBodyTexture();
    }

    @Override
    public ITexture[] getTopFacingInactive(byte aColor) {
        return getBodyTexture();
    }

    @Override
    public ITexture[] getBottomFacingActive(byte aColor) {
        return getBodyTexture();
    }

    @Override
    public ITexture[] getBottomFacingInactive(byte aColor) {
        return getBodyTexture();
    }

    @Override
    public ITexture[] getBottomFacingPipeActive(byte aColor) {
        return getBodyTexture();
    }

    @Override
    public ITexture[] getBottomFacingPipeInactive(byte aColor) {
        return getBodyTexture();
    }

    @Override
    public ITexture[] getTopFacingPipeActive(byte aColor) {
        return getBodyTexture();
    }

    @Override
    public ITexture[] getTopFacingPipeInactive(byte aColor) {
        return getBodyTexture();
    }

    @Override
    public ITexture[] getSideFacingPipeActive(byte aColor) {
        return getBodyTexture();
    }

    @Override
    public ITexture[] getSideFacingPipeInactive(byte aColor) {
        return getBodyTexture();
    }

    // ==== GUI（MUI2，与青铜机器同一窗口家族） ====

    /**
     * GT5u 5.09.54.x 的 {@link MTEBasicMachine} 默认返回 false，即右键 {@code openGui} 走 MUI1 旧
     * GUI 体系（{@code addUIWidgets} 通道）；而本类 GUI 由 MUI2 构建（{@link #buildUI} +
     * {@link MTEVoidWoodenBaseGui}），MUI1 路径不会调用 MUI2 面板，导致 GUI 打不开。
     * 54.264 中 {@code GTGuis.GLOBAL_SWITCH_MUI2} 默认为 true，覆写后 openGui 进入
     * {@code MetaTileEntityGuiHandler.open} 的 MUI2 分支并调用 {@link #buildUI}。
     */
    @Override
    protected boolean useMui2() {
        return true;
    }

    /** GUI 中显示的流体输出槽数量；物品机器为 0，泵为 1。 */
    protected int getFluidOutputSlotCount() {
        return 0;
    }

    @Override
    protected BasicUIProperties getUIProperties() {
        // 不用配方表前端：NEI 的虚空采矿机前端 UI 属性不适合单方块机器窗口。
        return BasicUIProperties.builder()
            .maxItemInputs(mInputSlotCount)
            .maxItemOutputs(mOutputItems.length)
            .maxFluidInputs(0)
            .maxFluidOutputs(getFluidOutputSlotCount())
            .build();
    }

    // 54.264 契约：CommonMetaTileEntity.buildUI(PosGuiData, PanelSyncManager, UISettings) 在 useMui2 为 true
    // 时由 MetaTileEntityGuiHandler 调用（服务器与客户端两侧），签名与本覆写一致。
    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        return new MTEVoidWoodenBaseGui(this, getUIProperties()).build(guiData, syncManager, uiSettings);
    }

    // ==== 生产（由标准机器循环驱动） ====

    /**
     * 选择产出并调度生产周期；基类负责进度条并在周期结束后放入内部输出槽/储罐。
     *
     * @return {@link MTEBasicMachine} 的 {@code FOUND_*} 常量之一
     */
    @Override
    public abstract int checkRecipe();

    /**
     * 单格库存上限。显式声明（覆写 {@code CommonMetaTileEntity.getInventoryStackLimit}，其实现恒为 64）
     * 的动机：javac 对 {@code this.getInventoryStackLimit()} 生成 owner 为本类静态类型的符号引用，
     * 发布重映射不会改写无声明 mod 类的调用点，导致发布 jar 中残留 MCP 名；而整合包运行环境
     * （GT 类一律 SRG 名，5.09.54.265 的 {@code BaseMetaTileEntity.func_70297_j_} 亦同名覆写）
     * 解析不到 MCP 名即抛 NoSuchMethodError（crash-2026-11-15_20.44.54）。声明后声明与调用点
     * 一起随发布重映射改名，跨 5.09.54.x/265 运行环境均可命中。返回值与上游实现一致取 64。
     */
    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    /**
     * 输出缓冲空间检查：任一输出槽为空，或已有同物品同 NBT 堆叠且合并后不超过单格上限，即视为有空间。
     * 接受条件与基类周期结束搬运（{@code onPostTick} → {@code BaseMetaTileEntity.addStackToSlot}
     * 依次尝试全部输出槽）完全一致，用于替代 GT 原版 {@code canOutput(ItemStack...)} 的
     * "产出[i] 只配输出槽[i]"位置对应检查——加权随机产出多种资源时，位置对应制会因输出槽 0
     * 恰好堆着不同资源而误判满仓停滞。
     *
     * @param output 本周期将产出的单件物品堆叠（{@code null} 视为无产出，恒有空间，与基类一致）
     * @return 至少一个输出槽能容纳该产出时为 {@code true}；全部输出槽都无法容纳时为 {@code false}
     */
    protected boolean hasRoomFor(@Nullable ItemStack output) {
        if (output == null) return true;
        final int stackLimit = getInventoryStackLimit();
        for (ItemStack slot : getAllOutputs()) {
            if (slot == null) return true;
            if (GTUtility.areStacksEqual(slot, output, false)
                && slot.stackSize + output.stackSize <= Math.min(slot.getMaxStackSize(), stackLimit)) return true;
        }
        return false;
    }

    /**
     * 预订本周期产出：写入 {@code mOutputItems[0]}，实际落槽复用基类周期结束的搬运循环——该循环对
     * 全部输出槽按槽序尝试 {@code addStackToSlot}（同物品可合并的槽优先命中，否则放入遇到的第一个
     * 空槽），天然满足"同物品可堆叠优先、其次空槽放入"的缓冲语义。不直接写输出槽：在配方调度时直接
     * 落槽会让产出在周期开始就出现在输出槽中（破坏"周期结束时落产出"的既有语义），并绕过
     * {@code mOutputItems} 通道上的矿物字典统一化与堆叠数钳制。
     *
     * 不变量：每次 {@code checkRecipe} 进入时 {@code mOutputItems[0]} 必为 {@code null}
     * （周期结束与配方失败两条路径都会清空 mOutputItems）；非 {@code null} 属于不变量破坏，
     * 返回 {@code false} 让机器待机而不是覆盖丢失产出。
     *
     * @param output 本周期将产出的物品堆叠
     * @return 产出已成功进入 mOutputItems 通道时为 {@code true}
     */
    protected boolean tryStoreOutput(ItemStack output) {
        if (mOutputItems[0] != null) return false;
        mOutputItems[0] = output;
        return true;
    }

    /** 生产周期 tick 数；采矿机速度是泵的两倍。 */
    protected int getProductionCycleTicks() {
        return PRODUCTION_CYCLE_TICKS;
    }

    /**
     * 已插入集成电路的配置值：读取电路槽内堆叠的 damage。幽灵电路（GUI 点选写入）与真实编程电路
     * （经幽灵槽点击放置，被 MetaTileEntity.setInventorySlotContents 规范化）落在同一电路槽，
     * 两者均为 stackSize=0 影子堆叠，damage 读取语义天然一致；槽位为空时返回 0——与
     * GhostCircuitItemStackHandler.getCircuitConfig() 的无电路（NO_CONFIG = -1）在机器侧等价
     * （配置 0 = 混合/加权池语义），1..N 选第 N 项、N+1 选贫矿池、超界待机的分支由各机器保持。
     */
    protected int readCircuitConfig() {
        final ItemStack circuit = mInventory[getCircuitSlot()];
        if (circuit != null && ItemList.Circuit_Integrated.isStackEqual(circuit, true, true)) {
            return circuit.getItemDamage();
        }
        return 0;
    }

    protected static ItemStack singleOutput(ItemStack stack) {
        return GTUtility.copyAmount(1, stack);
    }

    /** 日志门面：数据问题在此报告而不崩溃。 */
    protected static Logger log() {
        return LOG;
    }
}
