package com.jdz.vmd.common.tileentities;

import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import appeng.items.storage.ItemVoidStorageCell;
import gregtech.api.enums.OutputHatchType;
import gregtech.api.interfaces.IOutputHatch;
import gregtech.api.util.FluidEjectionHelper;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputME;

/** A siphon output with a long amount and a voiding policy fixed when production finishes. */
final class SiphonGasOutput {

    private static final Logger LOG = LogManager.getLogger("CheaperVoidMiners");

    private final FluidStack fluid;
    private long remaining;
    private boolean protectedOutput;

    SiphonGasOutput(FluidStack fluid, long amount) {
        this.fluid = new FluidStack(fluid, 1);
        this.remaining = amount;
    }

    /** Freezes the completed output's policy so later GUI changes cannot void protected gas. */
    void finish(boolean protect) {
        protectedOutput = protect;
    }

    /** Protected runs only need space for the next mB, not the whole cycle in a single hatch. */
    static boolean hasSpace(FluidStack fluid, List<IOutputHatch> hatches) {
        return deliver(new FluidStack(fluid, 1), hatches, true, true) > 0;
    }

    /** Attempts a single int-sized slice, retaining any rejected protected output for a later tick. */
    boolean drainOneSlice(List<IOutputHatch> hatches) {
        int sliceSize = (int) Math.min(remaining, Integer.MAX_VALUE);
        int accepted = deliver(new FluidStack(fluid, sliceSize), hatches, protectedOutput, false);
        if (!protectedOutput && accepted < sliceSize) {
            // The available sinks are exhausted; in voiding mode only, discard the overflow.
            remaining = 0;
        } else {
            remaining -= accepted;
        }
        return accepted > 0;
    }

    long remaining() {
        return remaining;
    }

    NBTTagCompound save() {
        final NBTTagCompound tag = fluid.writeToNBT(new NBTTagCompound());
        tag.setLong("Remaining", remaining);
        tag.setBoolean("Protected", protectedOutput);
        return tag;
    }

    static SiphonGasOutput load(NBTTagCompound tag) {
        final FluidStack fluid = GTUtility.loadFluid(tag);
        final long amount = tag.getLong("Remaining");
        if (fluid == null || amount <= 0) {
            LOG.error("Invalid saved surface siphon gas output: {}", tag);
            return null;
        }
        final SiphonGasOutput output = new SiphonGasOutput(fluid, amount);
        output.protectedOutput = tag.getBoolean("Protected");
        return output;
    }

    private static int deliver(FluidStack stack, List<IOutputHatch> hatches, boolean protect, boolean simulate) {
        final int initial = stack.amount;
        for (IOutputHatch hatch : hatches) {
            // Dedicated void sinks never store gas. In voiding mode the caller discards their share as overflow.
            if (hatch.getHatchType() == OutputHatchType.Void) continue;
            int offered = stack.amount;
            if (hatch instanceof MTEHatchOutputME meHatch) {
                final var provider = meHatch.getProvider();
                final ItemStack cell = meHatch.getCellStack();
                if (cell != null && cell.getItem() instanceof ItemVoidStorageCell) continue;
                if (protect && provider.canVoidOverflow()) continue;
                // A non-recipe ME transaction may accept an entire slice with only 1 mB of physical space.
                final long space = provider.getPhysicalSpace();
                if (space <= 0) continue;
                offered = (int) Math.min(offered, space);
            }
            final FluidStack part = new FluidStack(stack, offered);
            // Even for a voidable run we need the actual transaction delta, not the helper's void-mode result.
            final FluidEjectionHelper transfer = new FluidEjectionHelper(Collections.singletonList(hatch), true);
            final int accepted = transfer.ejectStack(part);
            if (!simulate && accepted > 0) transfer.commit();
            stack.amount -= accepted;
            if (stack.amount == 0) break;
        }
        return initial - stack.amount;
    }
}
