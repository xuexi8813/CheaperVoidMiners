package com.jdz.vmd.api.recipe;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import com.gtnewhorizons.modularui.api.math.Pos2d;

import gregtech.api.recipe.BasicUIPropertiesBuilder;
import gregtech.api.recipe.NEIRecipePropertiesBuilder;
import gregtech.api.recipe.RecipeMapFrontend;
import gregtech.api.util.MethodsReturnNonnullByDefault;

/**
 * 本模组产出配方表（虚空流体钻机 / 木制虚空采矿机）的自定义 NEI 输出布局前端。
 * <p>
 * 动机：GT5u 5.09.54.264 的默认前端 {@code UIHelper.getFluidOutputPositions} 将全部流体输出槽
 * 排成 y=62 固定、x 每槽 +18 的单行且无换行；GT 原版配方最多 1-2 个输出故从未暴露问题，而本模组
 * 每条配方有多个输出（流体钻机每维度 ≤5 种流体，木矿机矿脉条目含大矿与归属贫矿），第 4-5 个槽位
 * 起被 NEI 页面右边缘裁切。物品输出默认网格在超出常规数量时同样无法保证不出界。
 * <p>
 * 方案：仿照 GT 自带的 {@code DistillationTowerFrontend}（3 列向上堆叠）改为 <b>3 列 × N 行网格、
 * 向上堆叠</b>——3 列是因为 NEI 页面在 x=106 之后约可容纳 3 列（第 4 列起出界）；向上堆叠则
 * 占用页面上部空白，避免与页面下方（背景之外）由 {@code RecipeDisplayInfo} 绘制的能量/时长文本重叠。
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class VmdFrontend extends RecipeMapFrontend {

    /**
     * 网格列数。NEI 页面背景右缘约在 x=168-175，输出区从 x=106 起，3 列（末列槽位 x=142..160）
     * 恰好容纳；第 4 列起点 x=160 起将出界被裁切。
     */
    private static final int GRID_COLUMNS = 3;

    /** 首个输出槽的基准 x 坐标，与 GT 默认前端（UIHelper）的输出区起点一致。 */
    private static final int OUTPUT_BASE_X = 106;

    /**
     * 首个输出槽的基准 y 坐标（槽位左上角）。GT 默认前端的输出行固定在此高度，向上堆叠时
     * 以 18（槽位含边框尺寸）为步长递减。
     */
    private static final int OUTPUT_BASE_Y = 62;

    /** 单个槽位（含 1px 边框）的尺寸，NEI 网格步长。 */
    private static final int SLOT_STRIDE = 18;

    public VmdFrontend(BasicUIPropertiesBuilder uiPropertiesBuilder, NEIRecipePropertiesBuilder neiPropertiesBuilder) {
        super(uiPropertiesBuilder, neiPropertiesBuilder);
    }

    /**
     * 物品输出槽位置：3 列 × N 行网格、向上堆叠。
     * <p>
     * i 从 1 开始计数：x = 106 + ((i - 1) % 4) * 18，y = 62 - ((i - 1) / 4) * 18。
     * 木制采矿机矿脉条目数不定，3 列网格按行向上扩展容纳。
     */
    @Override
    public List<Pos2d> getItemOutputPositions(int itemOutputCount) {
        return getUpwardGridPositions(itemOutputCount);
    }

    /**
     * 流体输出槽位置：与物品输出同一网格规则。
     * <p>
     * 本模组每条配方最多 5 种流体，同样在两行以内容纳，不再出现默认单行布局的右缘裁切。
     */
    @Override
    public List<Pos2d> getFluidOutputPositions(int fluidOutputCount) {
        return getUpwardGridPositions(fluidOutputCount);
    }

    /**
     * 计算 3 列 × N 行、向上堆叠的槽位坐标序列。
     * <p>
     * 行序与 {@code DistillationTowerFrontend} 一致：第 1 行占满后再向上一行（y 递减 18），
     * 保证数量少时仍贴近默认输出行 y=62，视觉上与 GT 原版配方页面保持一致；
     * count 为 0 时循环不执行，自然返回空列表。
     */
    private static List<Pos2d> getUpwardGridPositions(int outputCount) {
        List<Pos2d> results = new ArrayList<>();
        for (int i = 1; i < outputCount + 1; i++) {
            int x = OUTPUT_BASE_X + ((i - 1) % GRID_COLUMNS) * SLOT_STRIDE;
            int y = OUTPUT_BASE_Y - ((i - 1) / GRID_COLUMNS) * SLOT_STRIDE;
            results.add(new Pos2d(x, y));
        }
        return results;
    }
}
