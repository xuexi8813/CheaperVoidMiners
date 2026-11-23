package com.jdz.vmd.common.tileentities;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
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
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.recipe.BasicUIProperties;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;

/**
 * 无电力木制虚空机器的基类。泵和采矿机复用 GT 的 GUI 与生产循环，
 * 在 {@link #checkRecipe()} 选择产出；产物留在内部，不自动输出。
 */
public abstract class MTEVoidWoodenBase extends MTEBasicMachine {

    public static final int PRODUCTION_CYCLE_TICKS = 200;
    public static final int PRODUCTION_AMOUNT_MB = 100;
    public static final int TANK_CAPACITY_MB = 8000;

    /**
     * 物品类木制虚空机器（采矿机）的输出缓冲槽数：6 槽缓冲，落槽规则为
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
     * 电路使用输入槽前的专用 OTHER 槽。GT 配方调度后会清理输入槽的零数量堆叠，
     * 独立槽可防止幽灵电路随之清空。
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
     * 封闭首个输入槽（getInputSlot()）：电路迁移至专用幽灵槽后，两台木制机器的该槽位均无物品用途。
     * 若不封闭，isValidSlot 返回 true 会让管道经 canInsertItem（以 isValidSlot 为门）把杂物
     * 无声塞进 GUI 已不再渲染的死槽位。
     */
    @Override
    public boolean isValidSlot(int aIndex) {
        if (aIndex == getInputSlot()) return false;
        return super.isValidSlot(aIndex);
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

    private static Block woodenCasingBlock() {
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
     * GT 基类默认走 MUI1，不会调用 {@link #buildUI}。启用 MUI2 让客户端与服务端构建同一套面板。
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

    // 5.09.54.x（钉定 275）契约：CommonMetaTileEntity.buildUI(PosGuiData, PanelSyncManager, UISettings) 在 useMui2 为 true
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
     * 单格上限沿用 GT 的 64。javac 生成以本类为 owner 的调用；若仅继承，发布重映射会留下 MCP 名，
     * 在 SRG 运行环境触发 {@code NoSuchMethodError}。显式覆写使声明和调用点一同重映射。
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
     * @param output 本周期将产出的物品堆叠（{@code null} 视为无产出，恒有空间，与基类一致）
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
     * 预订产出，由 GT 在周期结束时落槽。直接写槽会提前产出，并绕过矿物词典统一和堆叠上限。
     * GT 在周期结束或配方失败时清除预订；若仍有旧产出，宁可待机也不覆盖。
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

    /** 日志门面：数据问题在此报告而不崩溃。 */
    protected static Logger log() {
        return LOG;
    }
}
