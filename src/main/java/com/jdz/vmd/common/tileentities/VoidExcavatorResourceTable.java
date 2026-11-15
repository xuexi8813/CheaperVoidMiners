package com.jdz.vmd.common.tileentities;

import static gregtech.api.objects.XSTR.XSTR_INSTANCE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.bsideup.jabel.Desugar;
import com.jdz.vmd.Config;

import cpw.mods.fml.common.registry.GameRegistry;
import gtneioreplugin.plugin.item.ItemDimensionDisplay;
import gtneioreplugin.util.DimensionHelper;

/**
 * Void Excavator（虚空挖掘机）家族共享的资源表，由配置驱动（见 {@link Config}），键为 GTNEI 维度缩写。
 * 配置惰性解析一次；损坏条目（未知方块、格式错误）记错误日志跳过，
 * 前提模组缺失只会让表变小，而不会让服务器崩溃。
 */
public final class VoidExcavatorResourceTable {

    private static final Logger LOG = LogManager.getLogger("CheaperVoidMiners");

    /**
     * GC 方块名别名：GTNH Galacticraft 的方块注册名带 "tile." 前缀（注册时传入
     * getUnlocalizedName() 全文），而早期默认配置使用无前缀短名；解析失败时经此表重试一次，
     * 使已按旧名生成过配置文件的用户无需手动修改。值为 GC 实际注册名（含 "tile." 前缀、
     * 不含 modid），重试时 modid 沿用配置条目首段。
     */
    private static final Map<String, String> MOD_BLOCK_ALIASES = new HashMap<>();

    static {
        MOD_BLOCK_ALIASES.put("galacticraftcore:moonblock", "tile.moonBlock");
        MOD_BLOCK_ALIASES.put("galacticraftmars:mars", "tile.mars");
    }

    /**
     * 原版方块的直接引用映射（键小写以容忍配置的大小写写法）。1.7.10 的原版方块不走
     * GameRegistry.registerBlock 注册，{@code GameRegistry.findBlock("minecraft", name)} 恒为 null，
     * 因此配置中不带 modid 的条目必须经由此表解析。
     */
    private static final Map<String, Block> VANILLA_BLOCKS = new HashMap<>();

    static {
        VANILLA_BLOCKS.put("dirt", Blocks.dirt);
        VANILLA_BLOCKS.put("grass", Blocks.grass);
        VANILLA_BLOCKS.put("stone", Blocks.stone);
        VANILLA_BLOCKS.put("cobblestone", Blocks.cobblestone);
        VANILLA_BLOCKS.put("sand", Blocks.sand);
        VANILLA_BLOCKS.put("gravel", Blocks.gravel);
        VANILLA_BLOCKS.put("clay", Blocks.clay);
        VANILLA_BLOCKS.put("netherrack", Blocks.netherrack);
        VANILLA_BLOCKS.put("soulsand", Blocks.soul_sand);
        VANILLA_BLOCKS.put("endstone", Blocks.end_stone);
        VANILLA_BLOCKS.put("snow", Blocks.snow);
        VANILLA_BLOCKS.put("mycelium", Blocks.mycelium);
        VANILLA_BLOCKS.put("hardenedclay", Blocks.hardened_clay);
    }

    /** 未放维度显示方块时默认使用的维度缩写。 */
    public static final String DEFAULT_DIMENSION = "Ow";

    @Desugar
    public record WeightedResource(ItemStack stack, float weight) {}

    private static final Map<String, List<WeightedResource>> TABLES = new HashMap<>();

    private static boolean parsed = false;

    private VoidExcavatorResourceTable() {}

    /** 维度资源列表，保持配置顺序；无表时为空列表，首次调用触发一次性配置解析。 */
    public static List<WeightedResource> getResources(@Nullable String dimensionAbbr) {
        if (!parsed) parseConfig();
        if (dimensionAbbr == null) return new ArrayList<>();
        return TABLES.getOrDefault(dimensionAbbr, new ArrayList<>());
    }

    /** 按配置权重加权随机抽取；掷点对 [0, total) 的划分与木制家族其他辅助方法一致。 */
    public static WeightedResource weightedRandom(List<WeightedResource> resources) {
        float total = 0;
        for (WeightedResource resource : resources) {
            total += Math.max(0, resource.weight());
        }
        if (total <= 0) return resources.get(XSTR_INSTANCE.nextInt(resources.size()));

        float roll = XSTR_INSTANCE.nextFloat() * total;
        for (WeightedResource resource : resources) {
            roll -= Math.max(0, resource.weight());
            if (roll < 0) return resource;
        }
        return resources.get(resources.size() - 1);
    }

    /** 维度显示方块对应的缩写，非该类物品为 null；用于拒绝显示槽中的无关物品。 */
    @Nullable
    public static String getDimensionAbbr(@Nullable ItemStack stack) {
        if (stack == null) return null;
        return ItemDimensionDisplay.getDimension(stack);
    }

    /** 缩写对应的维度本地化名称；无条目时回退为缩写本身。 */
    public static String getDimensionDisplayName(String dimensionAbbr) {
        final String internal = DimensionHelper.ABBR_TO_INTERNAL.get(dimensionAbbr);
        if (internal != null) return DimensionHelper.getDimLocalizedName(internal);
        return dimensionAbbr;
    }

    private static void parseConfig() {
        parsed = true;
        for (Map.Entry<String, String[]> entry : Config.getExcavatorResourceTables()
            .entrySet()) {
            final List<WeightedResource> resources = new ArrayList<>();
            for (String rawEntry : entry.getValue()) {
                final WeightedResource resource = parseResource(rawEntry);
                if (resource != null) resources.add(resource);
            }
            TABLES.put(entry.getKey(), resources);
        }
    }

    /** 解析 {@code blockid:meta:weight} 条目（blockid 可带 modid，共四段）；无法解析时记错误日志。 */
    @Nullable
    private static WeightedResource parseResource(String rawEntry) {
        final String[] segments = rawEntry.trim()
            .split(":");
        if (segments.length != 3 && segments.length != 4) {
            LOG.error(
                "Invalid excavator resource entry '{}', expected [blockid:meta:weight] or [modid:blockname:meta:weight]",
                rawEntry);
            return null;
        }

        final String blockId = segments.length == 4 ? segments[0] + ":" + segments[1] : segments[0];
        final Block block;
        if (segments.length == 4) {
            // 带 modid 的条目走 GameRegistry（GalacticraftCore:tile.moonBlock 等）。
            Block resolved = GameRegistry.findBlock(segments[0], segments[1]);
            if (resolved == null || resolved == Blocks.air) {
                // 旧版默认配置的短名别名重试（GC 注册名带 "tile." 前缀）；modid 沿用条目首段。
                final String alias = MOD_BLOCK_ALIASES
                    .get((segments[0] + ":" + segments[1]).toLowerCase(Locale.ENGLISH));
                if (alias != null) {
                    resolved = GameRegistry.findBlock(segments[0], alias);
                }
            }
            block = resolved;
        } else {
            // 原版方块不在 GameRegistry 的 mod 容器中，必须走直接映射表。
            block = VANILLA_BLOCKS.get(segments[0].toLowerCase(Locale.ENGLISH));
        }
        if (block == null || block == Blocks.air) {
            LOG.error("Excavator resource block '{}' not found, entry '{}' skipped", blockId, rawEntry);
            return null;
        }

        final int meta;
        final float weight;
        try {
            meta = Integer.parseInt(segments[segments.length - 2]);
            weight = Float.parseFloat(segments[segments.length - 1]);
        } catch (NumberFormatException e) {
            LOG.error("Invalid meta or weight in excavator resource entry '{}'", rawEntry, e);
            return null;
        }
        if (weight <= 0) {
            LOG.error("Non positive weight in excavator resource entry '{}'", rawEntry);
            return null;
        }
        return new WeightedResource(new ItemStack(block, 1, meta), weight);
    }
}
