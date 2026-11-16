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
        """)
public class CheaperVoidMiners {

    public static final String MODID = "cheapervoidminers";

    @SidedProxy(clientSide = "com.jdz.vmd.ClientProxy", serverSide = "com.jdz.vmd.CommonProxy")
    public static CommonProxy proxy;

    /**
     * FML 握手期网络校验（{@code NetworkModHolder} 在 mod 构造期扫描 mod 主类的该注解方法，
     * 构造 {@code MethodNetworkChecker}，优先于默认 checker）：对端 mod 列表必须包含本 mod。
     * 默认 checker 只实现"客户端必须装服务器上的 mod"，不校验"服务器缺客户端装的 mod"；
     * 本 mod 的物品全部为挂在 GregTech 方块 meta 上的 MTE（不注册独立 Block/Item id），
     * 不会触发 FML 的 id 映射（GameData）拒连，单端缺失也能正常连接，故必须显式双向要求。
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
    // 后期数据钩子（贫矿追加）：须在 BartWorks 生成虚空采矿机掉落表（loadComplete）之后运行；
    // NEI 产出配方不依赖此钩子，已经 GregTechAPI.sAfterGTPostload（GT postInit 末尾）注册。
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
