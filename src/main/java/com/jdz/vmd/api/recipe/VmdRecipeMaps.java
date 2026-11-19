package com.jdz.vmd.api.recipe;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;

/**
 * 本模组的 NEI 产出配方表，模式与 GT 虚空采矿机（{@code RecipeMaps.voidMinerRecipes}）一致：
 * 维度显示方块（堆叠数 0）作输入，可产出条目作带权重输出。构造 {@link RecipeMap} 会自动向 GT
 * 注册配方类别并生成 NEI 处理器，无需额外 NEI 插件代码。
 * {@link #register()} 仅为强制类加载——NEI 可能在 postInit 期间构建处理器列表，须早于此。
 */
public final class VmdRecipeMaps {

    /**
     * 物品输出槽容量（NEI 模板槽位数）。{@code RecipeMapBuilder#maxIO} 不限制配方注册的输出数，
     * 只决定 NEI 页面预建的幻影槽位数；基类标准物品输出网格（{@code UIHelper.getItemOutputPositions}）
     * 在超过 6 个输出时按 3 列、自 y=6 起向下排布，170x82 的 NEI 背景恰好容纳 3 行（y=6/24/42，
     * 第 4 行起越出背景下沿），故安全容量为 3 列 × 3 行 = 9——与 GT 5.09.54.265 全部配方表的
     * 最大物品输出数一致（如电弧炉 {@code maxIO(2, 9, 2, 1)}）。5.09.55 的 VoidMinerFrontend 取 72
     * 是因其自带更大的 NEI 页面，本项目钉定的 265 没有该前端，取 72 会让模板按 3 列预建 24 行槽位、
     * 从 GUI 顶溢出（本次修复的 bug）。实际每条矿脉配方的物品输出数为 4 种矿脉材料 + 归属贫矿
     * （各 1 条，主世界仅 Stone 一种石类型），理论上限 8、实测 6；即便超出容量，超出部分仍会经
     * GTNEIDefaultHandler 的"超出常规数量"路径在背景区内补画（无槽位贴图），NEI 展示不会截断。
     */
    public static final int MAX_ITEM_OUTPUTS = 9;

    /**
     * 流体输出上限：GTNH 2.9.0 的 UndergroundFluids.cfg 每维度最多 5 种流体（23 个维度），
     * 取 12 留出自定义余量；更大的值会让 NEI 默认前端按 32 槽分配网格，流体槽被页面边缘裁切
     * （该裁切问题已由 {@link VmdFrontend} 的 3 列向上网格布局修复）。实际每条配方的流体输出数
     * 为该维度流体条目数（≤5 种），3 列 × N 行网格足够容纳。
     */
    public static final int MAX_FLUID_OUTPUTS = 12;

    /** 虚空流体钻机与木制虚空流体泵的产出配方：某个维度的流体。 */
    public static final RecipeMap<RecipeMapBackend> voidFluidDrillerRecipes = RecipeMapBuilder
        .of("vmd.recipe.voidfluiddriller")
        .maxIO(1, 0, 0, MAX_FLUID_OUTPUTS)
        .minInputs(1, 0)
        .frontend(VmdFrontend::new)
        .neiHandlerInfo(
            builder -> builder.setHeight(240)
                .setMultipleWidgetsAllowed(false))
        .build();

    /**
     * 木制虚空采矿机的产出配方：与机器编程电路选择语义对应的条目——每条主世界矿脉一条
     * （输入附配置 i 的编程电路）。
     * 贫矿按材料归属并入对应矿脉条目（机器矿脉模式同样产出归属贫矿），归属数据由
     * {@code MTEVoidMinerWooden#buildOverworldPools} 统一构建，未被归属的贫矿仅保留在机器侧的
     * 贫矿池与混合奖池中，不单独成 NEI 条目。
     * 物品输入上限为 2：编程电路作为第二输入；机器 {@code checkRecipe} 自行选择奖池、不走该表的配方匹配，
     * 输入上限仅决定 NEI 模板槽位数、不约束配方注册（见 {@link #MAX_ITEM_OUTPUTS} 说明），
     * 故无运行时副作用，minInputs 保持 (1, 0)。
     * 与 voidFluidDrillerRecipes 相同，不配置 NEI 进度条以避免贴图解析失败刷屏。
     */
    public static final RecipeMap<RecipeMapBackend> woodenMinerRecipes = RecipeMapBuilder.of("vmd.recipe.woodenminer")
        .maxIO(2, MAX_ITEM_OUTPUTS, 0, 0)
        .minInputs(1, 0)
        .frontend(VmdFrontend::new)
        .neiHandlerInfo(
            builder -> builder.setHeight(240)
                .setMultipleWidgetsAllowed(false))
        .build();

    private VmdRecipeMaps() {}

    public static void register() {}
}
