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
 * 动机：GT5u 5.09.54.x（钉定 275）的默认前端 {@code UIHelper.getFluidOutputPositions} 将全部流体输出槽
 * 排成 y=62 固定、x 每槽 +18 的单行且无换行；GT 原版配方最多 1-2 个输出故从未暴露问题，而本模组
 * 流体钻机每维度最多 5 种流体，第 4-5 个槽位起被 NEI 页面右边缘裁切。物品输出不自算坐标，直接
 * 沿用基类标准网格（≤6 个居中排布、更多时自 y=6 起向下换行）：NEI 模板槽位数即配方表的 maxIO
 * （{@link VmdRecipeMaps#MAX_ITEM_OUTPUTS}，已与标准网格的 9 槽安全容量对齐），若自算向上网格，
 * 模板会按 maxIO 预建全部槽位并从 GUI 顶溢出（本次修复的 bug）。
 * <p>
 * 方案：流体输出仿照 GT 自带的 {@code DistillationTowerFrontend}（3 列向上堆叠）——3 列是因为
 * NEI 页面在 x=106 之后约可容纳 3 列（第 4 列起出界）；向上堆叠则占用页面上部空白，避免与页面
 * 下方（背景之外）由 {@code RecipeDisplayInfo} 绘制的能量/时长文本重叠。
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class VmdFrontend extends RecipeMapFrontend {

    /**
     * 网格列数。NEI 页面背景右缘约在 x=168-175，流体输出区从 x=106 起，3 列（末列槽位 x=142..160）
     * 恰好容纳；第 4 列起点 x=160 起将出界被裁切。
     */
    private static final int GRID_COLUMNS = 3;

    /** 流体输出首槽的基准 x 坐标，与 GT 默认前端（UIHelper）的输出区起点一致。 */
    private static final int FLUID_BASE_X = 106;

    /**
     * 流体输出首槽的基准 y 坐标（槽位左上角）。GT 默认前端的输出行固定在此高度，向上堆叠时
     * 以 18（槽位含边框尺寸）为步长递减。
     */
    private static final int FLUID_BASE_Y = 62;

    /** 单个槽位（含 1px 边框）的尺寸，NEI 网格步长。 */
    private static final int SLOT_STRIDE = 18;

    public VmdFrontend(BasicUIPropertiesBuilder uiPropertiesBuilder, NEIRecipePropertiesBuilder neiPropertiesBuilder) {
        super(uiPropertiesBuilder, neiPropertiesBuilder);
    }

    /**
     * 流体输出槽位置：3 列 × N 行网格、向上堆叠（物品输出沿用基类标准布局，见类注释）。
     * <p>
     * 本模组每条配方最多 5 种流体，在两行以内容纳，不再出现默认单行布局的右缘裁切；
     * 容量上限 12（{@link VmdRecipeMaps#MAX_FLUID_OUTPUTS}，4 行）与网格安全容量对齐，
     * 全部模板槽位均在 NEI 背景区内。
     */
    @Override
    public List<Pos2d> getFluidOutputPositions(int fluidOutputCount) {
        return getUpwardGridPositions(fluidOutputCount);
    }

    /**
     * 计算 3 列 × N 行、向上堆叠的槽位坐标序列。
     * <p>
     * i 从 1 开始计数：x = 106 + ((i - 1) % 3) * 18，y = 62 - ((i - 1) / 3) * 18。
     * 行序与 {@code DistillationTowerFrontend} 一致：第 1 行占满后再向上一行（y 递减 18），
     * 保证数量少时仍贴近默认输出行 y=62，视觉上与 GT 原版配方页面保持一致；
     * count 为 0 时循环不执行，自然返回空列表。
     */
    private static List<Pos2d> getUpwardGridPositions(int outputCount) {
        List<Pos2d> results = new ArrayList<>();
        for (int i = 1; i < outputCount + 1; i++) {
            int x = FLUID_BASE_X + ((i - 1) % GRID_COLUMNS) * SLOT_STRIDE;
            int y = FLUID_BASE_Y - ((i - 1) / GRID_COLUMNS) * SLOT_STRIDE;
            results.add(new Pos2d(x, y));
        }
        return results;
    }
}
