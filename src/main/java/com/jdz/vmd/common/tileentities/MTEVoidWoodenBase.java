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
 * Shared base of the wooden, non-electric void machines (Wooden Void Fluid Pump, Wooden Void Miner,
 * Wooden Void Excavator), built on the GT standard small machine family
 * ({@link MTEBasicMachine}, the same base as the bronze machines but without the steam part).
 * <p>
 * Motivation: the wooden family runs without any energy source on a pure production cycle, so it uses the
 * standard machine base with tier 0 / amperage 0 and its recipe cycle, gaining the classic machine GUI
 * (input slot with progress bar and output slot), wrench rotation with the standard main/output facing
 * split, redstone working control and the 14 face texture system. The production itself is implemented per
 * machine in {@link #checkRecipe()}: it selects the output (circuit configuration or weighted random),
 * checks that the internal output has room, and lets the standard cycle place the output into the internal
 * output slot / output tank when the cycle finishes. Nothing is pushed to neighbors - outputs stay inside
 * until taken out (pipes, conveyors, manual) and a full internal output stalls the machine.
 * <p>
 * Slot layout follows the {@link MTEBasicMachine} convention: the first input slot doubles as the
 * programmed circuit slot (a real circuit item, no ghost slot), the wooden excavator adds a second input
 * slot for the dimension display block. The internal tank of the tank base remains available as the pump's
 * fluid output tank.
 */
public abstract class MTEVoidWoodenBase extends MTEBasicMachine {

    /** Ticks between two production attempts. */
    public static final int PRODUCTION_CYCLE_TICKS = 200;
    /** Fluid produced per successful production cycle, in mB. */
    public static final int PRODUCTION_AMOUNT_MB = 100;
    /** Internal fluid output tank capacity, in mB. */
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
     * Slot configuration.
     *************************************************************************/

    /**
     * The first input slot doubles as the programmed circuit slot, keeping the circuit semantics of the
     * previous GUI (a real circuit item, not a ghost slot).
     */
    @Override
    public int getCircuitSlot() {
        return getInputSlot();
    }

    /**
     * The circuit is a real item placed into the input slot; no ghost circuit slot is offered in the GUI.
     */
    @Override
    public boolean allowSelectCircuit() {
        return false;
    }

    /**
     * Slot of the dimension display block, relative to {@link #getInputSlot()}; -1 when the machine has
     * none (pump and miner). The excavator uses offset 1 (second input slot).
     */
    protected int getDimensionDisplaySlotOffset() {
        return -1;
    }

    /** Absolute slot of the dimension display block, or -1 when the machine has none. */
    public int getDimensionDisplaySlot() {
        final int offset = getDimensionDisplaySlotOffset();
        return offset >= 0 ? getInputSlot() + offset : -1;
    }

    /** Whether the stack is a GTNEI dimension display block; used to filter the dimension display slot. */
    protected static boolean isDimensionDisplayItem(@Nullable ItemStack stack) {
        return ItemDimensionDisplay.getDimension(stack) != null;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        // Slot specific content filters, applied on top of the standard facing/filter logic.
        if (aIndex == getCircuitSlot() && !ItemList.Circuit_Integrated.isStackEqual(aStack, true, true)) return false;
        final int dimensionSlot = getDimensionDisplaySlot();
        if (dimensionSlot >= 0 && aIndex == dimensionSlot && !isDimensionDisplayItem(aStack)) return false;
        return super.allowPutStack(aBaseMetaTileEntity, aIndex, side, aStack);
    }

    /**************************************************************************
     * Non-electric machine configuration (bronze machine pattern).
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
     * Outputs stay inside the machine until taken out manually or by external pipes/conveyors; no auto
     * output in any direction, so a full internal output stalls the machine visibly.
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

    /** Non-electric machine: recipe checks are never blocked by a missing energy supply. */
    @Override
    public boolean hasEnoughEnergyToCheckRecipe() {
        return true;
    }

    @Override
    protected boolean drainEnergyForProcess(long aEUt) {
        // mEUt is always 0 for the wooden family, nothing is drained to progress a cycle.
        return true;
    }

    @Override
    public boolean willExplodeInRain() {
        // Wooden machine, rain proof like the vanilla tanks it imitates.
        return false;
    }

    /**************************************************************************
     * Textures: wooden casing body on every face, the machine specific overlay on the front face. The 14
     * face system is handled by the standard machine base, which fixes the previous mismatch between the
     * front overlay and the output facing.
     *************************************************************************/

    /** Front face overlay icon of the concrete machine (water pump / ore drill). */
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
     * GUI (MUI2, same window family as the bronze machines).
     *************************************************************************/

    /** Number of fluid output slots shown in the GUI; 0 for the item machines, 1 for the pump. */
    protected int getFluidOutputSlotCount() {
        return 0;
    }

    @Override
    protected BasicUIProperties getUIProperties() {
        // Built from scratch instead of the recipemap frontend: the NEI output maps use the scrolling
        // void miner frontend whose UI properties do not fit a singleblock machine window.
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
     * Production (driven by the standard machine cycle).
     *************************************************************************/

    /**
     * Selects the next output and schedules one production cycle; the standard machine base runs the
     * progress bar and places the output into the internal output slot / output tank afterwards.
     *
     * @return one of the {@code FOUND_*} constants of {@link MTEBasicMachine}
     */
    @Override
    public abstract int checkRecipe();

    /** Ticks between two production attempts; the miner works twice as fast as the pump. */
    protected int getProductionCycleTicks() {
        return PRODUCTION_CYCLE_TICKS;
    }

    /** Configuration value of the inserted integrated circuit, or 0 when the slot holds none. */
    protected int readCircuitConfig() {
        final ItemStack circuit = mInventory[getCircuitSlot()];
        if (circuit != null && ItemList.Circuit_Integrated.isStackEqual(circuit, true, true)) {
            return circuit.getItemDamage();
        }
        return 0;
    }

    /** Copies one item into a fresh single-item stack for the output slots. */
    protected static ItemStack singleOutput(ItemStack stack) {
        return GTUtility.copyAmount(1, stack);
    }

    /** Logger facade of the module; the wooden machines report data problems here instead of crashing. */
    protected static Logger log() {
        return LOG;
    }
}
