package com.jdz.vmd;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(
    modid = CheaperVoidMiners.MODID,
    version = Tags.VERSION,
    name = "CheaperVoidMiners",
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = """
        required-after:gregtech;
        """)
public class CheaperVoidMiners {

    public static final String MODID = "cheapervoidminers";

    @SidedProxy(clientSide = "com.jdz.vmd.ClientProxy", serverSide = "com.jdz.vmd.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    // 后期数据钩子（贫矿追加 + NEI 产出配方）：须在 BartWorks 生成虚空采矿机掉落表之后运行。
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
