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

    // load "进行模组初始化设置。构建需要的数据结构，注册配方。"（如不需要可移除）
    public void init(FMLInitializationEvent event) {
        ItemRegistry.run();
        // 尽早加载 NEI 产出配方表：NEI 在处理自身配置加载时会基于所有已注册的配方类别构建处理器列表，
        // 而该过程可能发生在 postInit 期间——早于服务器启动才会首次触达该类的时机。
        VmdRecipeMaps.register();
    }

    // postInit "处理与其他模组的交互，并在此基础上完成初始化设置。"（如不需要可移除）
    public void postInit(FMLPostInitializationEvent event) {
        RecipeLoader.run();
    }

    /**
     * 服务器启动：后期数据钩子，必须在所有模组完成自身加载后运行。BartWorks 在其 load-complete 阶段生成
     * 虚空采矿机掉落表（{@code VoidMinerUtility.generateDropMaps()}），该阶段总是先于首次服务器启动，
     * 因此在此处追加贫矿是安全的，且紧接着注册的 NEI 产出配方会包含这些贫矿。
     * <p>
     * 刻意不在 FMLLoadCompleteEvent 中执行：在 initializeMods 期间首次加载游戏玩法类（木制采矿机奖池、
     * 流体表、资源表）会导致 RFB/HMCL 整合包环境崩溃，报
     * {@code ClassNotFoundException: Class bytes are null for net.minecraft.inventory.ISidedInventory}
     * （已通过用户整合包 23:06/23:08 的崩溃报告验证）；而服务器启动阶段可以安全地加载这些类，已在游戏中验证。
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
