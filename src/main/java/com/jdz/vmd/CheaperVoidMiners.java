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
    // preInit "最先运行。读取配置，创建方块、物品等，并将其注册到 GameRegistry。"（如不需要可移除）
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    // load "进行模组初始化设置。构建需要的数据结构，注册配方。"（如不需要可移除）
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    // postInit "处理与其他模组的交互，并在此基础上完成初始化设置。"（如不需要可移除）
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    // serverStarting：后期数据钩子（贫矿追加、NEI 产出配方），必须在 BartWorks 构建虚空采矿机掉落表之后运行，
    // 该阶段能在 RFB/HMCL 整合包环境中安全地加载游戏玩法类。
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
