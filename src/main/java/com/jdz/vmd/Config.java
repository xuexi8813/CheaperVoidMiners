package com.jdz.vmd;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraftforge.common.config.Configuration;

/**
 * 模组配置，目前保存虚空挖掘机（Void Excavators）各维度的资源表。
 * <p>
 * 每张表以 GTNEI 维度显示方块的维度缩写作为键（例如 "Ow"、"Mo"）；原先的 "greeting" 示例条目
 * 已随本次扩展一并移除，因为不再有任何代码引用它。
 * <p>
 * 表条目格式：{@code blockid:meta:weight}，其中 blockid 既可以是原版注册名（"clay"），
 * 也可以是 "modid:name" 形式（"GalacticraftCore:moonBlock"）。这些字符串会在使用时通过 GameRegistry
 * 解析（参见 {@link com.jdz.vmd.common.tileentities.VoidExcavatorResourceTable}），因此下面的默认值
 * 是这些标识符出现的唯一位置。
 */
public class Config {

    /** 各维度挖掘机资源表的配置分类。 */
    private static final String CATEGORY_EXCAVATOR_RESOURCES = "excavator_resources";

    // 已收录维度的默认值，按 GTNEI 进度阶段缩写的顺序排列。
    // 原版条目总是可以解析；当 Galacticraft 模组缺失时，其条目会降级为一条错误日志，
    // 而不会破坏表中的其余内容。
    private static final String[] DEFAULT_OW = { "clay:0:20", "sand:0:15", "gravel:0:15", "dirt:0:50" };
    private static final String[] DEFAULT_NE = { "netherrack:0:60", "soulSand:0:40" };
    private static final String[] DEFAULT_ED = { "endStone:0:100" };
    private static final String[] DEFAULT_MO = { "GalacticraftCore:moonBlock:4:60", "GalacticraftCore:moonBlock:3:40" };
    private static final String[] DEFAULT_MA = { "GalacticraftMars:mars:9:60", "GalacticraftMars:mars:5:40" };

    /** 按维度缩写索引的资源表，保持配置文件中的顺序。 */
    private static final Map<String, String[]> EXCAVATOR_RESOURCE_TABLES = new LinkedHashMap<>();

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        // 为已收录维度写入默认资源表，使其出现在生成的配置文件中；玩家在该分类下额外添加的
        // 任意键都会被下方的扫描逻辑读取。
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

    /** 从配置中读取一张维度表；若为新建条目，则将默认值写入配置文件。 */
    private static void loadTable(Configuration configuration, String abbr, String[] defaults, String comment) {
        EXCAVATOR_RESOURCE_TABLES
            .put(abbr, configuration.getStringList(abbr, CATEGORY_EXCAVATOR_RESOURCES, defaults, comment));
    }

    /** 已配置资源表的快照；条目为原始配置字符串，由资源表辅助类负责解析。 */
    public static Map<String, String[]> getExcavatorResourceTables() {
        return Collections.unmodifiableMap(EXCAVATOR_RESOURCE_TABLES);
    }
}
