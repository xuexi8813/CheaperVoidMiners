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
 * 木制、无电力虚空机器（木制虚空流体泵、木制虚空采矿机、木制虚空挖掘机）的共享基类，
 * 构建在 GT 标准小型机器家族之上（{@link MTEBasicMachine}，与青铜机器相同的基类，但不含蒸汽部分）。
 * <p>
 * 设计动机：木制系列机器完全不需要能源，以纯粹的生产周期运行，因此使用 tier 0 / 电流 0 的标准
 * 机器基类及其配方循环，从而获得经典的机器 GUI（带进度条的输入槽与输出槽）、扳手旋转及标准的
 * 正面/输出面分离、红stone 工作控制以及 14 面贴图系统。生产逻辑本身由每台机器在
 * {@link #checkRecipe()} 中实现：它选择产出（电路配置或按权重随机），检查内部输出是否有空间，
 * 并让标准循环在周期结束时把产出放入内部输出槽 / 输出储罐。不会向相邻方块推送任何东西——
 * 产物会一直留在机器内部直到被取出（管道、传送带、手动），内部输出满时机器会明显停滞。
 * <p>
 * 槽位布局遵循 {@link MTEBasicMachine} 的约定：第一个输入槽兼任编程电路槽（真实的电路物品，
 * 非幽灵槽），木制挖掘机额外增加第二个输入槽用于放置维度显示方块。储罐基类的内部储罐
 * 保留作为泵的流体输出储罐。
 */
public abstract class MTEVoidWoodenBase extends MTEBasicMachine {

    /** 两次生产尝试之间的间隔 tick 数。 */
    public static final int PRODUCTION_CYCLE_TICKS = 200;
    /** 每个成功生产周期产出的流体量，单位 mB。 */
    public static final int PRODUCTION_AMOUNT_MB = 100;
    /** 内部流体输出储罐容量，单位 mB。 */
    public static final int TANK_CAPACITY_MB = 8000;

    private static final Logger LOG = LogManager.getLogger("CheaperVoidMiners");

    public MTEVoidWoodenBase(int aID, String aName, String aNameRegional, String aDescription, int aInputSlotCount,
        int aOutputSlotCount) {
        super(aID, aName, aNameRegional, 0, 0, aDescription, aInputSlotCount, aOutputSlotCount);
    }

    public MTEVoidWoodenBase(String aName, String[] aDescription, ITexture[][][] aTextures, int aInputSlotCount,
        int aOutputSlotCount) {
        super(aName, 0, 0, aDescription, aTextures, aInputSlotCount, aOutputSlotCount);
    }

    /**************************************************************************
     * 槽位配置。
     *************************************************************************/

    /**
     * 第一个输入槽兼任编程电路槽，保留此前 GUI 的电路语义（真实的电路物品，而非幽灵槽）。
     */
    @Override
    public int getCircuitSlot() {
        return getInputSlot();
    }

    /**
     * 电路是放入输入槽的真实物品；GUI 中不提供幽灵电路槽。
     */
    @Override
    public boolean allowSelectCircuit() {
        return false;
    }

    /**
     * 维度显示方块的槽位，相对于 {@link #getInputSlot()}；机器没有该槽时为 -1（泵与采矿机）。
     * 挖掘机使用偏移量 1（第二个输入槽）。
     */
    protected int getDimensionDisplaySlotOffset() {
        return -1;
    }

    /** 维度显示方块的绝对槽位，机器没有该槽时为 -1。 */
    public int getDimensionDisplaySlot() {
        final int offset = getDimensionDisplaySlotOffset();
        return offset >= 0 ? getInputSlot() + offset : -1;
    }

    /** 判断物品堆是否为 GTNEI 维度显示方块；用于过滤维度显示槽的放入内容。 */
    protected static boolean isDimensionDisplayItem(@Nullable ItemStack stack) {
        return ItemDimensionDisplay.getDimension(stack) != null;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        // 槽位专属的内容过滤，叠加在标准的朝向/过滤逻辑之上。
        if (aIndex == getCircuitSlot() && !ItemList.Circuit_Integrated.isStackEqual(aStack, true, true)) return false;
        final int dimensionSlot = getDimensionDisplaySlot();
        if (dimensionSlot >= 0 && aIndex == dimensionSlot && !isDimensionDisplayItem(aStack)) return false;
        return super.allowPutStack(aBaseMetaTileEntity, aIndex, side, aStack);
    }

    /**************************************************************************
     * 无电力机器配置（青铜机器模式）。
     *************************************************************************/

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

    /**
     * 产物会留在机器内部，直到被手动取出或由外部管道/传送带取出；任何方向都不自动输出，
     * 因此内部输出满时机器会明显停滞。
     */
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

    /** 无电力机器：配方检查绝不会因缺少能源而被阻塞。 */
    @Override
    public boolean hasEnoughEnergyToCheckRecipe() {
        return true;
    }

    @Override
    protected boolean drainEnergyForProcess(long aEUt) {
        // 木制系列的 mEUt 始终为 0，推进周期不消耗任何能量。
        return true;
    }

    @Override
    public boolean willExplodeInRain() {
        // 木制机器，如它模仿的原版储罐一样防雨。
        return false;
    }

    /**************************************************************************
     * 贴图：所有面均为木制机壳本体，正面叠加机器专属的覆盖层。14 面系统由标准机器基类处理，
     * 这修复了此前正面覆盖层与输出朝向不匹配的问题。
     *************************************************************************/

    /** 具体机器（水泵 / 矿石钻）正面的覆盖层图标。 */
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

    /**************************************************************************
     * GUI（MUI2，与青铜机器同一窗口家族）。
     *************************************************************************/

    /** GUI 中显示的流体输出槽数量；物品机器为 0，泵为 1。 */
    protected int getFluidOutputSlotCount() {
        return 0;
    }

    @Override
    protected BasicUIProperties getUIProperties() {
        // 从零构建，而不是使用配方表前端：NEI 产出配方表使用可滚动的虚空采矿机前端，
        // 其 UI 属性不适合单方块机器窗口。
        return BasicUIProperties.builder()
            .maxItemInputs(mInputSlotCount)
            .maxItemOutputs(mOutputItems.length)
            .maxFluidInputs(0)
            .maxFluidOutputs(getFluidOutputSlotCount())
            .build();
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        return new MTEVoidWoodenBaseGui(this, getUIProperties()).build(guiData, syncManager, uiSettings);
    }

    /**************************************************************************
     * 生产（由标准机器循环驱动）。
     *************************************************************************/

    /**
     * 选择下一个产出并调度一个生产周期；标准机器基类负责运行进度条，
     * 并在周期结束后把产出放入内部输出槽 / 输出储罐。
     *
     * @return {@link MTEBasicMachine} 的 {@code FOUND_*} 常量之一
     */
    @Override
    public abstract int checkRecipe();

    /** 两次生产尝试之间的间隔 tick 数；采矿机的速度是泵的两倍。 */
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

    /** 将单个物品复制为一份全新的单件物品堆，用于输出槽。 */
    protected static ItemStack singleOutput(ItemStack stack) {
        return GTUtility.copyAmount(1, stack);
    }

    /** 本模块的日志门面；木制机器在此报告数据问题，而不是直接崩溃。 */
    protected static Logger log() {
        return LOG;
    }
}
