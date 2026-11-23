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
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import gregtech.api.GregTechAPI;

public class CommonProxy {

    public void init(FMLInitializationEvent event) {
        ItemRegistry.run();
        // NEI 可能在 postInit 构建处理器列表，先强制加载配方表。
        VmdRecipeMaps.register();
        // FML 不保证 postInit 的依赖顺序。GT 的钩子在世界生成表和 StoneType 就绪后执行。
        // 客户端连接远程服也执行此钩子；此时仍在 postInit，避开 RFB 的 loadComplete 类加载限制。
        GregTechAPI.sAfterGTPostload.add(this::registerVmdOutputRecipes);
    }

    public void postInit(FMLPostInitializationEvent event) {
        RecipeLoader.run();
        // 气体清单由 gtnhintergalactic 在 postInit 填充。若尚未就绪，不缓存空表，留待 GT-PostLoad 重试。
        VmdOutputRecipeLoader.registerSurfaceGasSiphonRecipes();
    }

    /** 在 GT postInit 末尾注册 NEI 产出配方。 */
    private void registerVmdOutputRecipes() {
        FMLLog.log("CheaperVoidMiners", Level.INFO, "GT-PostLoad: registering NEI output recipes");
        VmdOutputRecipeLoader.register();
        // GT5u 2.9 的钩子晚于普通模组的 postInit，可重试尚未注册的气体配方。
        VmdOutputRecipeLoader.registerSurfaceGasSiphonRecipes();
        FMLLog.log("CheaperVoidMiners", Level.INFO, "GT-PostLoad: NEI output recipes registered");
    }

    /**
     * 在服务器启动时追加贫矿。BartWorks 在 loadComplete 重建矿表，提前追加会被覆盖。
     * 不直接用 loadComplete：RFB 在 initializeMods 期间首次加载游戏逻辑类会崩溃。
     */
    public void serverStarting(FMLServerStartingEvent event) {
        FMLLog.log("CheaperVoidMiners", Level.INFO, "ServerStarting: appending poor ores to the void miner drop maps");
        VoidOreTableHelper.appendPoorOresToVoidMinerDropMaps();
        FMLLog.log("CheaperVoidMiners", Level.INFO, "ServerStarting: poor ores appended to the void miner drop maps");
    }
}
