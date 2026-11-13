package com.jdz.vmd;

import com.jdz.vmd.common.loaders.ItemRegistry;
import com.jdz.vmd.common.loaders.RecipeLoader;
import com.jdz.vmd.common.tileentities.VoidOreTableHelper;

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
    }

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        RecipeLoader.run();
    }

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        // Runs after GT generated the void miner drop maps (BartWorks builds them on FMLLoadCompleteEvent)
        // and before any void miner machine computes its distribution cache on its first tick, so the
        // appended poor ores survive; the append itself is idempotent across repeated server starts.
        VoidOreTableHelper.appendPoorOresToVoidMinerDropMaps();
    }
}
