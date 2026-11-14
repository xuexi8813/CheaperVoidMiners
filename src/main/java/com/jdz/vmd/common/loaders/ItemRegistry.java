package com.jdz.vmd.common.loaders;

import static com.jdz.vmd.api.enums.MetaTileEntityIDs.VoidMinerEX1Controller;
import static com.jdz.vmd.api.enums.MetaTileEntityIDs.VoidMinerEX2Controller;
import static com.jdz.vmd.api.enums.MetaTileEntityIDs.VoidMinerEX3Controller;

import java.util.function.Supplier;

import net.minecraft.item.ItemStack;

import com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler;
import com.jdz.vmd.api.enums.MetaTileEntityIDs;
import com.jdz.vmd.common.tileentities.MTEVoidExcavatorWooden;
import com.jdz.vmd.common.tileentities.MTEVoidExcavators;
import com.jdz.vmd.common.tileentities.MTEVoidFluidDrillers;
import com.jdz.vmd.common.tileentities.MTEVoidFluidPumpWooden;
import com.jdz.vmd.common.tileentities.MTEVoidMinerWooden;
import com.jdz.vmd.common.tileentities.MTEVoidMiners;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

public class ItemRegistry {

    /** 归属提示行在当前模组容器缺失时的后备显示名称。 */
    private static final String FALLBACK_MOD_DISPLAY_NAME = "CheaperVoidMiners";

    public static ItemStack VoidMinerEX1;
    public static ItemStack VoidMinerEX2;
    public static ItemStack VoidMinerEX3;

    public static ItemStack VoidFluidDrillerIron;
    public static ItemStack VoidFluidDrillerSteel;
    public static ItemStack VoidFluidDrillerTitanium;
    public static ItemStack VoidFluidDrillerNaquadah;
    public static ItemStack VoidFluidDrillerInfinity;
    public static ItemStack VoidFluidPumpWooden;
    public static ItemStack VoidMinerWooden;
    public static ItemStack VoidExcavatorWooden;
    public static ItemStack VoidExcavatorIron;
    public static ItemStack VoidExcavatorSteel;
    public static ItemStack VoidExcavatorTitanium;
    public static ItemStack VoidExcavatorNaquadah;
    public static ItemStack VoidExcavatorInfinity;

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

        ItemRegistry.VoidMinerWooden = new MTEVoidMinerWooden(
            MetaTileEntityIDs.VoidMinerWooden.ID,
            "VoidMinerWooden",
            "Wooden Void Miner").getStackForm(1L);

        ItemRegistry.VoidExcavatorWooden = new MTEVoidExcavatorWooden(
            MetaTileEntityIDs.VoidExcavatorWooden.ID,
            "VoidExcavatorWooden",
            "Wooden Void Excavator").getStackForm(1L);

        ItemRegistry.VoidExcavatorIron = new MTEVoidExcavators.VoidExcavatorIron(
            MetaTileEntityIDs.VoidExcavatorIron.ID,
            "VoidExcavatorIron",
            "Void Excavator (Iron)").getStackForm(1L);
        ItemRegistry.VoidExcavatorSteel = new MTEVoidExcavators.VoidExcavatorSteel(
            MetaTileEntityIDs.VoidExcavatorSteel.ID,
            "VoidExcavatorSteel",
            "Void Excavator (Steel)").getStackForm(1L);
        ItemRegistry.VoidExcavatorTitanium = new MTEVoidExcavators.VoidExcavatorTitanium(
            MetaTileEntityIDs.VoidExcavatorTitanium.ID,
            "VoidExcavatorTitanium",
            "Void Excavator (Titanium)").getStackForm(1L);
        ItemRegistry.VoidExcavatorNaquadah = new MTEVoidExcavators.VoidExcavatorNaquadah(
            MetaTileEntityIDs.VoidExcavatorNaquadah.ID,
            "VoidExcavatorNaquadah",
            "Void Excavator (Naquadah)").getStackForm(1L);
        ItemRegistry.VoidExcavatorInfinity = new MTEVoidExcavators.VoidExcavatorInfinity(
            MetaTileEntityIDs.VoidExcavatorInfinity.ID,
            "VoidExcavatorInfinity",
            "Void Excavator (Infinity)").getStackForm(1L);

        registerOwnershipTooltips();
    }

    /**
     * 为本模组的每台机器的提示信息（tooltip）追加一行 "Added by CheaperVoidMiners"，遵循 Twist Space
     * Technology 的做法，通过 GTNHLib 的 {@link AnimatedTooltipHandler} 追加模组署名行
     * （这些机器注册在 GregTech 的方块上，因此通用的模组归属显示的会是 GregTech）。
     * <p>
     * 显示的名称是当前活跃模组容器的 mcmod.info 名称，在 {@link #run()} 被从 {@code init} 调用时读取，
     * 此时本模组仍是活跃容器。
     */
    private static void registerOwnershipTooltips() {
        final ModContainer container = Loader.instance()
            .activeModContainer();
        final String modDisplayName = container != null ? container.getName() : FALLBACK_MOD_DISPLAY_NAME;
        final Supplier<String> creditLine = AnimatedTooltipHandler
            .translatedText("vmd.credit.added_by", modDisplayName);

        for (ItemStack stack : new ItemStack[] { VoidMinerEX1, VoidMinerEX2, VoidMinerEX3, VoidFluidDrillerIron,
            VoidFluidDrillerSteel, VoidFluidDrillerTitanium, VoidFluidDrillerNaquadah, VoidFluidDrillerInfinity,
            VoidFluidPumpWooden, VoidMinerWooden, VoidExcavatorWooden, VoidExcavatorIron, VoidExcavatorSteel,
            VoidExcavatorTitanium, VoidExcavatorNaquadah, VoidExcavatorInfinity }) {
            AnimatedTooltipHandler.addItemTooltip(
                stack,
                AnimatedTooltipHandler.chain(AnimatedTooltipHandler.text(AnimatedTooltipHandler.BLUE), creditLine));
        }
    }
}
