package com.jdz.vmd.api.recipe;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;

/**
 * 仅供 NEI 展示的产出配方表。GT 在构建 {@link RecipeMap} 时注册类别和处理器，
 * 因此须在 NEI 于 postInit 构建处理器列表前调用 {@link #register()} 触发类加载。
 */
public final class VmdRecipeMaps {

    /**
     * NEI 标准背景只容纳 3 列 × 3 行物品槽。GT 5.09.54.x 没有新版的大面板，预建 72 槽会溢出。
     * {@code maxIO} 只决定模板槽数；超出的实际输出由 NEI 补画。
     */
    public static final int MAX_ITEM_OUTPUTS = 9;

    /**
     * GTNH 2.9.0 每维度至多 5 种流体，12 槽为自定义配置留余量。
     * {@link VmdFrontend} 用 3 列向上排布，避免槽位被 NEI 页面裁切。
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
     * 木制虚空采矿机每条主世界矿脉一条 NEI 配方，展示对应电路及归属该矿脉的贫矿。
     * 未归属的贫矿只在机器奖池中；机器自行选择奖池，电路输入槽只供 NEI 展示。
     * 不配置 NEI 进度条，避免贴图解析失败。
     */
    public static final RecipeMap<RecipeMapBackend> woodenMinerRecipes = RecipeMapBuilder.of("vmd.recipe.woodenminer")
        .maxIO(2, MAX_ITEM_OUTPUTS, 0, 0)
        .minInputs(1, 0)
        .frontend(VmdFrontend::new)
        .neiHandlerInfo(
            builder -> builder.setHeight(240)
                .setMultipleWidgetsAllowed(false))
        .build();

    /**
     * 地表行星气体钻机每种气体一条 NEI 配方，展示电路编号、常备采矿管和折算后的周期基础产量。
     * 机器直接按电路查全局气体表，不用本表匹配配方。
     */
    public static final RecipeMap<RecipeMapBackend> surfaceGasSiphonRecipes = RecipeMapBuilder
        .of("vmd.recipe.surfacegassiphon")
        .maxIO(2, 0, 0, 1)
        .minInputs(1, 0)
        .frontend(VmdFrontend::new)
        .neiHandlerInfo(
            builder -> builder.setHeight(240)
                .setMultipleWidgetsAllowed(false))
        .build();

    private VmdRecipeMaps() {}

    public static void register() {}
}
