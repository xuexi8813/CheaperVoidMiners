package com.jdz.vmd;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraftforge.common.config.Configuration;

/**
 * 模组配置：虚空挖掘机（Void Excavators）各维度的资源表，键为 GTNEI 维度缩写（如 "Ow"、"Mo"），
 * 条目格式为 {@code blockid:meta:weight}（blockid 也可为 "modid:name"），
 * 由 {@link com.jdz.vmd.common.tileentities.VoidExcavatorResourceTable} 解析。
 */
public class Config {

    private static final String CATEGORY_EXCAVATOR_RESOURCES = "excavator_resources";

    // 默认值按 GTNEI 维度缩写排列；缺失模组（如 Galacticraft）的条目解析失败时仅记日志跳过。
    private static final String[] DEFAULT_OW = { "clay:0:20", "sand:0:15", "gravel:0:15", "dirt:0:50" };
    private static final String[] DEFAULT_NE = { "netherrack:0:60", "soulSand:0:40" };
    private static final String[] DEFAULT_ED = { "endStone:0:100" };
    private static final String[] DEFAULT_MO = { "GalacticraftCore:tile.moonBlock:4:60",
        "GalacticraftCore:tile.moonBlock:3:40" };
    private static final String[] DEFAULT_MA = { "GalacticraftMars:tile.mars:9:60", "GalacticraftMars:tile.mars:5:40" };

    /** 按维度缩写索引的资源表，保持配置顺序。 */
    private static final Map<String, String[]> EXCAVATOR_RESOURCE_TABLES = new LinkedHashMap<>();

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        // 写入默认表使其出现在生成配置中；玩家额外添加的键由下方扫描读取。
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

    private static void loadTable(Configuration configuration, String abbr, String[] defaults, String comment) {
        EXCAVATOR_RESOURCE_TABLES
            .put(abbr, configuration.getStringList(abbr, CATEGORY_EXCAVATOR_RESOURCES, defaults, comment));
    }

    /** 已配置资源表的只读快照；条目为原始字符串，由资源表辅助类解析。 */
    public static Map<String, String[]> getExcavatorResourceTables() {
        return Collections.unmodifiableMap(EXCAVATOR_RESOURCE_TABLES);
    }
}
