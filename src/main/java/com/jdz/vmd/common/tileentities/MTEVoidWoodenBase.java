package com.jdz.vmd.common.tileentities;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.IConfigurationCircuitSupport;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.RecipeMapWorkable;
import gregtech.api.metatileentity.implementations.MTEBasicTank;
import gregtech.api.objects.GTUODimension;
import gregtech.api.objects.GTUOFluid;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTItemTransfer;
import gregtech.api.util.GTUtility;

/**
 * Shared base of the wooden, non-electric void machines (currently the Wooden Void Fluid Pump; upcoming
 * wooden miners will extend this as well).
 * <p>
 * Motivation: the wooden family runs without any energy hatch and works on a pure tick timer, so
 * {@link MTEBasicTank} (tank plumbing + standard singleblock ModularUI, no power logic) is the closest GT5u
 * base. This class adds the dedicated programmed-circuit slot, the production cycle and the fluid auto
 * output; concrete machines only implement the per-cycle production.
 * <p>
 * GUI: the standard MTEBasicTank GUI is reused; the tank input slot doubles as the circuit slot (it only
 * accepts integrated circuits from automation and the machine only reads circuits from it), the fluid slot
 * shows the internal buffer.
 */
public abstract class MTEVoidWoodenBase extends MTEBasicTank
    implements IConfigurationCircuitSupport, RecipeMapWorkable {

    /** Ticks between two production attempts. */
    public static final int PRODUCTION_CYCLE_TICKS = 200;
    /** Fluid produced per successful production cycle, in mB. */
    public static final int PRODUCTION_AMOUNT_MB = 100;
    /** Ticks between two auto output attempts against the front neighbor. */
    public static final int AUTO_OUTPUT_INTERVAL_TICKS = 20;
    /** Internal fluid buffer capacity, in mB. */
    public static final int TANK_CAPACITY_MB = 8000;

    private static final Logger LOG = LogManager.getLogger("CheaperVoidMiners");

    /** Session wide flag so a broken Overworld table is reported once instead of every cycle. */
    private static boolean warnedNoOverworldTable = false;

    public MTEVoidWoodenBase(int aID, String aName, String aNameRegional, String aDescription) {
        super(aID, aName, aNameRegional, 0, 3, aDescription);
    }

    public MTEVoidWoodenBase(String aName, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, 0, 3, aDescription, aTextures);
    }

    /**************************************************************************
     * Tank and slot configuration.
     *************************************************************************/

    @Override
    public boolean doesFillContainers() {
        return false;
    }

    @Override
    public boolean doesEmptyContainers() {
        return false;
    }

    @Override
    public boolean canTankBeFilled() {
        // Output-only buffer: neighbors may drain it, nothing may push into it.
        return false;
    }

    @Override
    public boolean canTankBeEmptied() {
        return true;
    }

    @Override
    public int getCapacity() {
        return TANK_CAPACITY_MB;
    }

    @Override
    public boolean willExplodeInRain() {
        // Wooden non-steam machine, rain proof like the vanilla tanks it imitates.
        return false;
    }

    /** The tank input slot doubles as the programmed circuit slot. */
    @Override
    public int getInputSlot() {
        return 0;
    }

    @Override
    public int getCircuitSlot() {
        return getInputSlot();
    }

    @Override
    public boolean allowSelectCircuit() {
        return true;
    }

    @Override
    public int getCircuitSlotX() {
        return 79;
    }

    @Override
    public int getCircuitSlotY() {
        return 16;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return aIndex == getCircuitSlot() && ItemList.Circuit_Integrated.isStackEqual(aStack, true, true);
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return aIndex == getCircuitSlot() || aIndex == getOutputSlot();
    }

    /**************************************************************************
     * Tick driven production and output.
     *************************************************************************/

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;

        if (aTick % getProductionCycleTicks() == 0) tryProduce();
        if (aTick % AUTO_OUTPUT_INTERVAL_TICKS == 0) {
            tryAutoOutput();
            tryAutoOutputItems();
        }
    }

    /**
     * Ticks between two production attempts. Overridable so machines of the family can run at their own
     * pace; the default matches the wooden pump.
     */
    protected int getProductionCycleTicks() {
        return PRODUCTION_CYCLE_TICKS;
    }

    /**
     * Produces the next batch of fluid into the internal buffer. The dimension table is fixed to the
     * Overworld entry; a circuit configuration selects the table entry, anything else is weighted random.
     */
    protected void tryProduce() {
        if (mFluid != null && mFluid.amount >= getCapacity()) return;

        final GTUODimension overworld = VoidFluidTableHelper.getOverworldDimension();
        final List<GTUOFluid> candidates = VoidFluidTableHelper.getFluidCandidates(overworld);
        if (candidates.isEmpty()) {
            if (!warnedNoOverworldTable) {
                warnedNoOverworldTable = true;
                LOG.warn("Underground fluid table has no usable Overworld entry, wooden void machines idle");
            }
            return;
        }

        final int config = readCircuitConfig();
        final GTUOFluid pick;
        if (config >= 1) {
            if (config > candidates.size()) return; // Out of range configuration: idle, do not randomize.
            pick = candidates.get(config - 1);
        } else {
            pick = VoidFluidTableHelper.weightedRandom(candidates);
        }
        final Fluid fluid = pick.getFluid();
        if (fluid == null) return;

        // Never mix fluids in the buffer; wait until it is drained before switching fluids.
        if (mFluid != null && !mFluid.isFluidEqual(new FluidStack(fluid, 1))) return;
        if (mFluid == null) mFluid = new FluidStack(fluid, 0);

        mFluid.amount += Math.min(PRODUCTION_AMOUNT_MB, getCapacity() - mFluid.amount);
        markDirty();
    }

    /**
     * Pushes buffer contents into the IFluidHandler neighbor at the front face, following the GT singleblock
     * output convention ({@code MTEHatchOutput}).
     */
    protected void tryAutoOutput() {
        if (mFluid == null || mFluid.amount <= 0) return;
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        final ForgeDirection side = base.getFrontFacing();
        final IFluidHandler target = base.getITankContainerAtSide(side);
        if (target == null) return;
        GTUtility.moveFluid(this, target, side, mFluid.amount, null);
    }

    /**
     * Pushes the content of the item output slot into the inventory neighbor at the front face, following
     * the GT singleblock output convention ({@code MTEHatchOutputBus}). The circuit slot is excluded from
     * the transfer and from the cleanup below (the configured circuit is a zero-size stack), and the wooden
     * fluid pump never fills the output slot, so this is a cheap emptiness check there.
     */
    protected void tryAutoOutputItems() {
        if (mInventory[getOutputSlot()] == null) return;
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        final GTItemTransfer transfer = new GTItemTransfer();
        transfer.push(base, base.getFrontFacing());
        transfer.setSourceSlots(getOutputSlot());
        if (transfer.transfer() > 0) {
            GTUtility.cleanInventory(this, getOutputSlot(), getOutputSlot() + 1);
        }
    }

    /** Configuration value of the inserted integrated circuit, or 0 when the slot holds none. */
    protected int readCircuitConfig() {
        final ItemStack circuit = mInventory[getCircuitSlot()];
        if (circuit != null && ItemList.Circuit_Integrated.isStackEqual(circuit, true, true)) {
            return circuit.getItemDamage();
        }
        return 0;
    }

    /**
     * NEI output recipe map of this machine; every wooden family member reports the map its outputs are
     * documented in (see {@link RecipeMapWorkable} for the NEI catalyst registration contract).
     */
    @Override
    public abstract RecipeMap<?> getRecipeMap();

    /**
     * Localized description of the fluid the machine would currently select, or the weighted random label;
     * used by subclasses for status lines in tooltips and GUI.
     */
    protected String describeCurrentSelection() {
        final int config = readCircuitConfig();
        if (config >= 1) {
            final List<GTUOFluid> candidates = VoidFluidTableHelper
                .getFluidCandidates(VoidFluidTableHelper.getOverworldDimension());
            if (config <= candidates.size()) {
                final Fluid fluid = candidates.get(config - 1)
                    .getFluid();
                if (fluid != null) return StatCollector.translateToLocal(fluid.getUnlocalizedName());
            }
            return StatCollector.translateToLocal("vmd.pump.selection.invalid");
        }
        return StatCollector.translateToLocal("vmd.gui.weighted_random");
    }

    /** Logger facade of the module; the wooden machines report data problems here instead of crashing. */
    protected static Logger log() {
        return LOG;
    }
}
