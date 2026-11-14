package com.jdz.vmd;

import org.apache.logging.log4j.Level;

import com.jdz.vmd.api.recipe.VmdRecipeMaps;
import com.jdz.vmd.common.loaders.ItemRegistry;
import com.jdz.vmd.common.loaders.RecipeLoader;
import com.jdz.vmd.common.loaders.VmdOutputRecipeLoader;
import com.jdz.vmd.common.tileentities.VoidOreTableHelper;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
    }

    public void init(FMLInitializationEvent event) {
        ItemRegistry.run();
        // 尽早强制类加载：NEI 构建处理器列表可能早于服务器启动（postInit 期间）。
        VmdRecipeMaps.register();
    }

    public void postInit(FMLPostInitializationEvent event) {
        RecipeLoader.run();
    }

    /**
     * 服务器启动钩子：须在 BartWorks 生成虚空矿机掉落表之后运行（追加贫矿 + 注册 NEI 产出配方）。
     * 不用 FMLLoadCompleteEvent——initializeMods 时段首次加载游戏逻辑类会在 RFB 环境触发类加载崩溃。
     */
    public void serverStarting(FMLServerStartingEvent event) {
        FMLLog.log("CheaperVoidMiners", Level.INFO, "ServerStarting: appending poor ores to the void miner drop maps");
        VoidOreTableHelper.appendPoorOresToVoidMinerDropMaps();
        VmdOutputRecipeLoader.register();
        FMLLog.log(
            "CheaperVoidMiners",
            Level.INFO,
            "ServerStarting: poor ores appended and NEI output recipes registered");
    }
}
