package com.jdz.vmd.common.tileentities;

import static gregtech.api.objects.XSTR.XSTR_INSTANCE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.BiMap;

import gregtech.GTMod;
import gregtech.api.objects.GTUODimension;
import gregtech.api.objects.GTUOFluid;
import gtneioreplugin.util.DimensionHelper;

/**
 * GT5u 地下流体表（{@code GTMod.proxy.mUndergroundOil}）的共享访问层：
 * 维度键映射、流体候选提取与加权随机抽取，供流体钻机与木制泵共用。
 */
public final class VoidFluidTableHelper {

    /** 地下流体维度列表中主世界条目的键（对应 GTNEI 的 "Ow"）。 */
    public static final String OVERWORLD_DIMENSION_KEY = "0";

    private static final Logger LOG = LogManager.getLogger("CheaperVoidMiners");

    private VoidFluidTableHelper() {}

    /** 地下流体维度列表的根访问入口，以维度 id 或 provider 名称片段为键。 */
    public static BiMap<String, GTUODimension> getDimensionList() {
        return GTMod.proxy.mUndergroundOil.getDimensionList();
    }

    /** 主世界地下流体表；GT 未生成条目时为 null（调用方视为无流体数据）。 */
    @Nullable
    public static GTUODimension getOverworldDimension() {
        return getDimensionList().get(OVERWORLD_DIMENSION_KEY);
    }

    /** 按 GTNEI 维度缩写查找地下流体维度条目；不存在时为 null。 */
    @Nullable
    public static GTUODimension findDimensionByAbbr(String abbr) {
        for (Map.Entry<String, GTUODimension> entry : getDimensionList().entrySet()) {
            if (abbr.equals(getAbbrForUoKey(entry.getKey()))) return entry.getValue();
        }
        return null;
    }

    /** 反向查找：已解析维度条目对应的缩写，无 GTNEI 条目时为 null。 */
    @Nullable
    public static String getAbbrOfDimension(GTUODimension dimension) {
        for (Map.Entry<String, GTUODimension> entry : getDimensionList().entrySet()) {
            if (entry.getValue() == dimension) return getAbbrForUoKey(entry.getKey());
        }
        return null;
    }

    /**
     * 地下流体维度键（数字维度 id 或世界 provider 类名片段）映射为 GTNEI 缩写；
     * 复刻 {@code gtneioreplugin.util.GT5UndergroundFluidHelper#init} 的映射逻辑。
     */
    @Nullable
    public static String getAbbrForUoKey(String rawDimension) {
        try {
            final int id = Integer.parseInt(rawDimension);
            return id == 0 ? "Ow" : null;
        } catch (NumberFormatException e) {
            // 非数字键，继续走下方按名称片段匹配的逻辑。
            LOG.debug("Skipping non-numeric underground fluid dimension key '{}'", rawDimension, e);
        }
        switch (rawDimension) {
            case "aCentauriBb" -> {
                return "CB";
            }
            case "BarnardaC" -> {
                return "BC";
            }
            case "BarnardaE" -> {
                return "BE";
            }
            case "BarnardaF" -> {
                return "BF";
            }
            case "TCetiE" -> {
                return "TE";
            }
        }
        for (DimensionHelper.Dimension record : DimensionHelper.getAllDim()) {
            if (record.trimmedName()
                .equalsIgnoreCase(rawDimension)) return record.abbr();
        }
        return null;
    }

    /** 维度可抽取的流体列表，保持表内顺序，跳过 FluidRegistry 未收录的流体；无数据时为空列表。 */
    public static List<GTUOFluid> getFluidCandidates(@Nullable GTUODimension dimension) {
        final List<GTUOFluid> candidates = new ArrayList<>();
        if (dimension == null) return candidates;
        for (GTUOFluid fluid : dimension.getFluids()
            .values()) {
            if (fluid.getFluid() != null) candidates.add(fluid);
        }
        return candidates;
    }

    /** 以生成概率（Chance）为权重加权随机抽取；末尾 return 不可达，仅为满足编译器。 */
    public static GTUOFluid weightedRandom(List<GTUOFluid> candidates) {
        long total = 0;
        for (GTUOFluid fluid : candidates) {
            total += Math.max(0, fluid.Chance);
        }
        if (total <= 0) return candidates.get(XSTR_INSTANCE.nextInt(candidates.size()));

        int roll = XSTR_INSTANCE.nextInt((int) Math.min(total, Integer.MAX_VALUE));
        for (GTUOFluid fluid : candidates) {
            roll -= Math.max(0, fluid.Chance);
            if (roll < 0) return fluid;
        }
        return candidates.get(candidates.size() - 1);
    }
}
