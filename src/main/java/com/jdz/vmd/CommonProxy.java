package com.jdz.vmd;

import com.jdz.vmd.api.recipe.VmdRecipeMaps;
import com.jdz.vmd.common.loaders.ItemRegistry;
import com.jdz.vmd.common.loaders.RecipeLoader;
import com.jdz.vmd.common.loaders.VmdOutputRecipeLoader;
import com.jdz.vmd.common.tileentities.VoidOreTableHelper;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        ItemRegistry.run();
        // Load the NEI output recipe maps early: NEI builds its handler list from every registered recipe
        // category while it processes its config load, which can happen during postInit - before
        // LoadComplete would first touch the class.
        VmdRecipeMaps.register();
    }

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        RecipeLoader.run();
    }

    /**
     * End of mod loading. FMLLoadCompleteEvent is dispatched in mod id order, so BartWorks
     * ("bartworks" < "cheapervoidminers") has already run {@code VoidMinerUtility.generateDropMaps()}
     * including its own output recipes when this handler runs. Appending the poor ores afterwards is
     * therefore safe, and the NEI output recipes registered right after include them.
     */
    public void loadComplete(FMLLoadCompleteEvent event) {
        VoidOreTableHelper.appendPoorOresToVoidMinerDropMaps();
        VmdOutputRecipeLoader.register();
    }
}
