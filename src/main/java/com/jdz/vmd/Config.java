package com.jdz.vmd;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraftforge.common.config.Configuration;

/**
 * Mod configuration, currently holding the per dimension resource tables of the Void Excavators.
 * <p>
 * Every table is keyed by the dimension abbreviation of the GTNEI dimension display blocks (e.g. "Ow",
 * "Mo"); the former "greeting" sample entry was removed together with this extension because nothing
 * referenced it anymore.
 * <p>
 * Table entry format: {@code blockid:meta:weight}, blockid being either a vanilla registry name ("clay")
 * or a "modid:name" pair ("GalacticraftCore:moonBlock"). The strings are resolved against the GameRegistry
 * at use time (see {@link com.jdz.vmd.common.tileentities.VoidExcavatorResourceTable}), so the defaults
 * below are the only place where these identifiers appear.
 */
public class Config {

    /** Config category of the per dimension excavator resource tables. */
    private static final String CATEGORY_EXCAVATOR_RESOURCES = "excavator_resources";

    // Defaults of the documented dimensions, in the shorthand order of the GTNEI progression tiers.
    // Vanilla entries are always resolvable; the Galacticraft entries degrade to an error log entry when
    // that mod is absent, without breaking the rest of the table.
    private static final String[] DEFAULT_OW = { "clay:0:20", "sand:0:15", "gravel:0:15", "dirt:0:50" };
    private static final String[] DEFAULT_NE = { "netherrack:0:60", "soulSand:0:40" };
    private static final String[] DEFAULT_ED = { "endStone:0:100" };
    private static final String[] DEFAULT_MO = { "GalacticraftCore:moonBlock:4:60", "GalacticraftCore:moonBlock:3:40" };
    private static final String[] DEFAULT_MA = { "GalacticraftMars:mars:9:60", "GalacticraftMars:mars:5:40" };

    /** Resource tables by dimension abbreviation, in config file order. */
    private static final Map<String, String[]> EXCAVATOR_RESOURCE_TABLES = new LinkedHashMap<>();

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        // The documented dimensions are seeded with their default tables so they show up in the generated
        // config file; every additional key the player adds to the category is picked up by the scan below.
        loadTable(configuration, "Ow", DEFAULT_OW, "Overworld: clay, sand, gravel, dirt");
        loadTable(configuration, "Ne", DEFAULT_NE, "Nether: netherrack, soul sand");
        loadTable(configuration, "ED", DEFAULT_ED, "The End: endstone");
        loadTable(configuration, "Mo", DEFAULT_MO, "Moon: moon rock (meta 4), moon dirt (meta 3), Galacticraft");
        loadTable(
            configuration,
            "Ma",
            DEFAULT_MA,
            "Mars: mars stone (meta 9), mars surface rock (meta 5), Galacticraft");

        for (String key : configuration.getCategory(CATEGORY_EXCAVATOR_RESOURCES)
            .getValues()
            .keySet()) {
            if (!EXCAVATOR_RESOURCE_TABLES.containsKey(key)) {
                loadTable(configuration, key, new String[0], "User defined resource table");
            }
        }

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    /** Reads one dimension table from the config, storing the defaults into the file when it is new. */
    private static void loadTable(Configuration configuration, String abbr, String[] defaults, String comment) {
        EXCAVATOR_RESOURCE_TABLES
            .put(abbr, configuration.getStringList(abbr, CATEGORY_EXCAVATOR_RESOURCES, defaults, comment));
    }

    /** Snapshot of the configured tables; entries are raw config strings, parsed by the resource table helper. */
    public static Map<String, String[]> getExcavatorResourceTables() {
        return Collections.unmodifiableMap(EXCAVATOR_RESOURCE_TABLES);
    }
}
