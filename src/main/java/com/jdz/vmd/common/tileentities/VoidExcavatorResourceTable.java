package com.jdz.vmd.common.tileentities;

import static gregtech.api.objects.XSTR.XSTR_INSTANCE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import gregtech.api.enums.Mods;
import gtneioreplugin.plugin.item.ItemDimensionDisplay;
import gtneioreplugin.util.DimensionHelper;

/**
 * Void Excavator（虚空挖掘机）家族共享的资源表，由配置文件驱动（见 {@link Config}）。
 * <p>
 * 动机：木制挖掘机与多方块挖掘机都要从虚空挖掘指定维度的"非矿石资源"，
 * 因此配置解析、方块解析与权重随机抽取只在此处实现一次，避免在每台机器中重复。
 * 资源表以 GTNEI 维度显示方块的维度缩写为键（"Ow"、"Mo" 等）。
 * <p>
 * 配置在每次会话中惰性解析一次；方块 id 通过 GameRegistry 解析，
 * 损坏条目（未知方块、数字格式错误）以错误日志报告并跳过，
 * 因此前提模组缺失或方块损坏只会让表变小，而不是让服务器崩溃。
 */
public final class VoidExcavatorResourceTable {

    private static final Logger LOG = LogManager.getLogger("CheaperVoidMiners");

    /** 未放入维度显示方块时，默认使用的资源表对应维度缩写。 */
    public static final String DEFAULT_DIMENSION = "Ow";

    /** 维度资源表中的一条带权重资源条目。 */
    @Desugar
    public record WeightedResource(ItemStack stack, float weight) {}

    private static final Map<String, List<WeightedResource>> TABLES = new HashMap<>();

    private static boolean parsed = false;

    private VoidExcavatorResourceTable() {}

    /**
     * 某维度的资源列表，保持配置顺序。永不返回 null；该维度没有（有效）资源表时为空列表。
     * 首次调用会触发一次性配置解析。
     */
    public static List<WeightedResource> getResources(@Nullable String dimensionAbbr) {
        if (!parsed) parseConfig();
        if (dimensionAbbr == null) return new ArrayList<>();
        return TABLES.getOrDefault(dimensionAbbr, new ArrayList<>());
    }

    /**
     * 按配置权重对资源表做加权随机抽取；随机数对 [0, total) 的划分方式
     * 与木制家族的其他辅助方法完全一致。
     */
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

    /**
     * 维度显示方块对应的维度缩写，不是该类物品时为 null；
     * 机器用它拒绝显示槽位中的无关物品。
     */
    @Nullable
    public static String getDimensionAbbr(@Nullable ItemStack stack) {
        if (stack == null) return null;
        return ItemDimensionDisplay.getDimension(stack);
    }

    /** 缩写对应的维度本地化名称，用于工具提示与 GUI 行；无对应名称时回退为缩写本身。 */
    public static String getDimensionDisplayName(String dimensionAbbr) {
        final String internal = DimensionHelper.ABBR_TO_INTERNAL.get(dimensionAbbr);
        if (internal != null) return DimensionHelper.getDimLocalizedName(internal);
        return dimensionAbbr;
    }

    /** 一次性解析全部已配置的资源表；格式错误的条目记录日志后跳过。 */
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

    /**
     * 解析一条 {@code blockid:meta:weight} 条目；blockid 可携带 modid
     * （如 {@code GalacticraftCore:moonBlock}），此时条目为四段。无法解析的方块
     * （例如未安装 Galacticraft 时的 Galacticraft 方块）会以错误日志报告，而不是被静默跳过。
     */
    @Nullable
    private static WeightedResource parseResource(String rawEntry) {
        final String[] segments = rawEntry.trim()
            .split(":");
        // 三段："blockid:meta:weight"；四段："modid:blockname:meta:weight"。
        if (segments.length != 3 && segments.length != 4) {
            LOG.error(
                "Invalid excavator resource entry '{}', expected [blockid:meta:weight] or [modid:blockname:meta:weight]",
                rawEntry);
            return null;
        }

        final String blockId = segments.length == 4 ? segments[0] + ":" + segments[1] : segments[0];
        // FML 没有单参数的 findBlock；原版方块位于 "Minecraft" 命名空间。
        final Block block = segments.length == 4 ? GameRegistry.findBlock(segments[0], segments[1])
            : GameRegistry.findBlock(Mods.Minecraft.ID, segments[0]);
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
