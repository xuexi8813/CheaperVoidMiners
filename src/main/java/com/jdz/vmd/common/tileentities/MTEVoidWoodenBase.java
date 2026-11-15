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
 * 产物留在机器内部直到被取出，不自动输出。槽位约定：第一个输入槽兼任编程电路槽（真实物品，非幽灵槽），
 * 挖掘机另有第二个输入槽放维度显示方块。
 */
public abstract class MTEVoidWoodenBase extends MTEBasicMachine {

    public static final int PRODUCTION_CYCLE_TICKS = 200;
    public static final int PRODUCTION_AMOUNT_MB = 100;
    public static final int TANK_CAPACITY_MB = 8000;

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

    /** 电路槽 = 输入槽 0（真实电路物品，非幽灵槽）。 */
    @Override
    public int getCircuitSlot() {
        return getInputSlot();
    }

    /** 电路为放入输入槽的真实物品，GUI 不提供幽灵电路槽。 */
    @Override
    public boolean allowSelectCircuit() {
        return false;
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
        // 槽位专属内容过滤，叠加在标准朝向/过滤逻辑之上。
        if (aIndex == getCircuitSlot() && !ItemList.Circuit_Integrated.isStackEqual(aStack, true, true)) return false;
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

    /** 生产周期 tick 数；采矿机速度是泵的两倍。 */
    protected int getProductionCycleTicks() {
        return PRODUCTION_CYCLE_TICKS;
    }

    /** 已插入集成电路的配置值，槽位中没有电路时为 0。 */
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
