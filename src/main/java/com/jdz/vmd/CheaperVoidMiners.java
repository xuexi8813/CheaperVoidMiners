package com.jdz.vmd;

import java.util.Map;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.relauncher.Side;

@Mod(
    modid = CheaperVoidMiners.MODID,
    version = Tags.VERSION,
    name = "CheaperVoidMiners",
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = """
        required-after:gregtech;
        after:bartworks;
        after:gtnhintergalactic;
        """)
public class CheaperVoidMiners {

    public static final String MODID = "cheapervoidminers";

    @SidedProxy(clientSide = "com.jdz.vmd.ClientProxy", serverSide = "com.jdz.vmd.CommonProxy")
    public static CommonProxy proxy;

    /**
     * FML 默认握手不拒绝只在客户端安装的模组。本模组的 MTE 复用 GT 方块 ID，
     * 也不会触发 ID 映射拒连，因此显式要求两端安装。
     */
    @NetworkCheckHandler
    public boolean checkModList(Map<String, String> remoteModList, Side side) {
        return remoteModList.containsKey(MODID);
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
    // BartWorks 在 loadComplete 建表；贫矿追加须更晚，不影响 GT-PostLoad 注册的 NEI 配方。
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
