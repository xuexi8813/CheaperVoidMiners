package com.jdz.vmd.api.recipe;

import com.gtnewhorizons.modularui.common.widget.ProgressBar;

import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;

/**
 * 本模组的 NEI 产出配方表，遵循 GT 虚空采矿机（{@code RecipeMaps.voidMinerRecipes}）的模式：
 * 每个可开采维度对应一条配方，以维度显示方块（堆叠数为 0）作为输入，所有可产出条目作为
 * 带权重的输出（概率单位为 0.01%）。
 * <p>
 * 这些配方表是普通的静态字段：构造 {@link RecipeMap} 时会自动向 GT 注册其配方类别，
 * GT 的 NEI 插件会为每个已注册类别构建 NEI 处理器，并通过 {@code getAvailableRecipeMaps()}
 * 将每台 {@code RecipeMapWorkable} 机器注册为催化剂；我们这边不需要额外的 NEI 插件代码。
 * <p>
 * {@link #register()} 仅用于强制该类在模组加载阶段尽早加载，使这些类别在 NEI 构建其处理器
 * 列表之前就已存在（该过程可能发生在 postInit 期间，早于 LoadComplete）。
 */
public final class VmdRecipeMaps {

    /**
     * 物品输出上限（9 行 × 8 列 = 72，与 GT5u 5.09.55.x 的 VoidMinerFrontend 一致）。
     * 5.09.54.264 没有滚动输出前端，NEI 的默认处理器会自动对超大配方分页展示。
     */
    public static final int MAX_ITEM_OUTPUTS = 72;

    /**
     * 钻机配方表的流体输出上限；GTNH 的地下流体表远低于此值，条目更多的维度在 NEI 中
     * 将无法完整展示。
     */
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

    /** 虚空挖掘机与木制虚空挖掘机的产出配方：配置表中的资源。 */
    public static final RecipeMap<RecipeMapBackend> voidExcavatorRecipes = RecipeMapBuilder
        .of("vmd.recipe.voidexcavator")
        .maxIO(1, MAX_ITEM_OUTPUTS, 0, 0)
        .minInputs(1, 0)
        .progressBar(GTUITextures.PROGRESSBAR_HAMMER, ProgressBar.Direction.DOWN)
        .progressBarPos(75, 26)
        .neiHandlerInfo(
            builder -> builder.setHeight(240)
                .setMultipleWidgetsAllowed(false))
        .build();

    /** 木制虚空采矿机的产出配方：主世界的完整混合奖池（矿脉 + 贫矿）。 */
    public static final RecipeMap<RecipeMapBackend> woodenMinerRecipes = RecipeMapBuilder.of("vmd.recipe.woodenminer")
        .maxIO(1, MAX_ITEM_OUTPUTS, 0, 0)
        .minInputs(1, 0)
        .progressBar(GTUITextures.PROGRESSBAR_HAMMER, ProgressBar.Direction.DOWN)
        .progressBarPos(75, 26)
        .neiHandlerInfo(
            builder -> builder.setHeight(240)
                .setMultipleWidgetsAllowed(false))
        .build();

    private VmdRecipeMaps() {}

    /** 空操作钩子，仅用于强制该类加载；时机选择的原因见类注释。 */
    public static void register() {}
}
