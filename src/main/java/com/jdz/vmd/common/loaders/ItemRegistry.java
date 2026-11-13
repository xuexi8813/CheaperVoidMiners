package com.jdz.vmd.common.loaders;

import static com.jdz.vmd.api.enums.MetaTileEntityIDs.VoidMinerEX1Controller;
import static com.jdz.vmd.api.enums.MetaTileEntityIDs.VoidMinerEX2Controller;
import static com.jdz.vmd.api.enums.MetaTileEntityIDs.VoidMinerEX3Controller;

import net.minecraft.item.ItemStack;

import com.jdz.vmd.api.enums.MetaTileEntityIDs;
import com.jdz.vmd.common.tileentities.MTEVoidFluidDrillers;
import com.jdz.vmd.common.tileentities.MTEVoidFluidPumpWooden;
import com.jdz.vmd.common.tileentities.MTEVoidMiners;

public class ItemRegistry {

    public static ItemStack VoidMinerEX1;
    public static ItemStack VoidMinerEX2;
    public static ItemStack VoidMinerEX3;

    public static ItemStack VoidFluidDrillerIron;
    public static ItemStack VoidFluidDrillerSteel;
    public static ItemStack VoidFluidDrillerTitanium;
    public static ItemStack VoidFluidDrillerNaquadah;
    public static ItemStack VoidFluidDrillerInfinity;
    public static ItemStack VoidFluidPumpWooden;

    public static void run() {
        ItemRegistry.VoidMinerEX1 = new MTEVoidMiners.VMLV(VoidMinerEX1Controller.ID, "VoidMinerEX1", "Void Miner EX I")
            .getStackForm(1L);
        ItemRegistry.VoidMinerEX2 = new MTEVoidMiners.VMHV(
            VoidMinerEX2Controller.ID,
            "VoidMinerEX2",
            "Void Miner EX II").getStackForm(1L);
        ItemRegistry.VoidMinerEX3 = new MTEVoidMiners.VMIV(
            VoidMinerEX3Controller.ID,
            "VoidMinerEX3",
            "Void Miner EX III").getStackForm(1L);

        ItemRegistry.VoidFluidDrillerIron = new MTEVoidFluidDrillers.VoidFluidDrillerIron(
            MetaTileEntityIDs.VoidFluidDrillerIron.ID,
            "VoidFluidDrillerIron",
            "Void Fluid Driller (Iron)").getStackForm(1L);
        ItemRegistry.VoidFluidDrillerSteel = new MTEVoidFluidDrillers.VoidFluidDrillerSteel(
            MetaTileEntityIDs.VoidFluidDrillerSteel.ID,
            "VoidFluidDrillerSteel",
            "Void Fluid Driller (Steel)").getStackForm(1L);
        ItemRegistry.VoidFluidDrillerTitanium = new MTEVoidFluidDrillers.VoidFluidDrillerTitanium(
            MetaTileEntityIDs.VoidFluidDrillerTitanium.ID,
            "VoidFluidDrillerTitanium",
            "Void Fluid Driller (Titanium)").getStackForm(1L);
        ItemRegistry.VoidFluidDrillerNaquadah = new MTEVoidFluidDrillers.VoidFluidDrillerNaquadah(
            MetaTileEntityIDs.VoidFluidDrillerNaquadah.ID,
            "VoidFluidDrillerNaquadah",
            "Void Fluid Driller (Naquadah)").getStackForm(1L);
        ItemRegistry.VoidFluidDrillerInfinity = new MTEVoidFluidDrillers.VoidFluidDrillerInfinity(
            MetaTileEntityIDs.VoidFluidDrillerInfinity.ID,
            "VoidFluidDrillerInfinity",
            "Void Fluid Driller (Infinity)").getStackForm(1L);

        ItemRegistry.VoidFluidPumpWooden = new MTEVoidFluidPumpWooden(
            MetaTileEntityIDs.VoidFluidPumpWooden.ID,
            "VoidFluidPumpWooden",
            "Wooden Void Fluid Pump").getStackForm(1L);
    }
}
