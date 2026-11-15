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

    /** 物品输出上限（9 行 × 8 列 = 72，与 GT5u 5.09.55.x 的 VoidMinerFrontend 一致）。 */
    public static final int MAX_ITEM_OUTPUTS = 72;

    /** 流体输出上限；GTNH 地下流体表远低于此值，条目更多的维度在 NEI 中无法完整展示。 */
    public static final int MAX_FLUID_OUTPUTS = 32;

    /** 虚空流体钻机与木制虚空流体泵的产出配方：某个维度的流体。 */
    public static final RecipeMap<RecipeMapBackend> voidFluidDrillerRecipes = RecipeMapBuilder
        .of("vmd.recipe.voidfluiddriller")
        .maxIO(1, 0, 0, MAX_FLUID_OUTPUTS)
        .minInputs(1, 0)
        .neiHandlerInfo(
            builder -> builder.setHeight(240)
                .setMultipleWidgetsAllowed(false))
        .build();

    /**
     * 虚空挖掘机与木制虚空挖掘机的产出配方：配置表中的资源。
     * 不配置 NEI 进度条（progressBar/progressBarPos）：GT5u 5.09.54.264 的 NEI 渲染路径下
     * 反复抛 Missing progressbar texture ERROR（hammer.png 资产存在但运行时未命中，具体根因
     * 未定位），故省略以由 RecipeMapBuilder 的默认回退进度条渲染。
     */
    public static final RecipeMap<RecipeMapBackend> voidExcavatorRecipes = RecipeMapBuilder
        .of("vmd.recipe.voidexcavator")
        .maxIO(1, MAX_ITEM_OUTPUTS, 0, 0)
        .minInputs(1, 0)
        .neiHandlerInfo(
            builder -> builder.setHeight(240)
                .setMultipleWidgetsAllowed(false))
        .build();

    /**
     * 木制虚空采矿机的产出配方：主世界的完整混合奖池（矿脉 + 贫矿）。
     * 与 voidExcavatorRecipes 相同，不配置 NEI 进度条以避免贴图解析失败刷屏。
     */
    public static final RecipeMap<RecipeMapBackend> woodenMinerRecipes = RecipeMapBuilder.of("vmd.recipe.woodenminer")
        .maxIO(1, MAX_ITEM_OUTPUTS, 0, 0)
        .minInputs(1, 0)
        .neiHandlerInfo(
            builder -> builder.setHeight(240)
                .setMultipleWidgetsAllowed(false))
        .build();

    private VmdRecipeMaps() {}

    public static void register() {}
}
