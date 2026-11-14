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

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        ItemRegistry.run();
        // Load the NEI output recipe maps early: NEI builds its handler list from every registered recipe
        // category while it processes its config load, which can happen during postInit - before a server
        // start would first touch the class.
        VmdRecipeMaps.register();
    }

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        RecipeLoader.run();
    }

    /**
     * Server start: late data hooks that must run after every mod finished its own loading. BartWorks generates
     * the void miner drop maps ({@code VoidMinerUtility.generateDropMaps()}) during its load-complete phase,
     * which always precedes the first server start, so appending the poor ores here is safe and the NEI output
     * recipes registered right after include them.
     * <p>
     * Deliberately NOT done in FMLLoadCompleteEvent: first-loading gameplay classes (wooden miner pools, fluid
     * tables, resource tables) during initializeMods crashes RFB/HMCL pack environments with
     * {@code ClassNotFoundException: Class bytes are null for net.minecraft.inventory.ISidedInventory} (verified
     * in user pack crash reports 23:06/23:08); the server-start phase loads classes safely, as verified in game.
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
