package com.jdz.vmd.common.tileentities;

import static gregtech.api.objects.XSTR.XSTR_INSTANCE;
import static gregtech.api.util.GTOreDictUnificator.get;

import java.util.List;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableList;

import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/**
 * 钻头选择结果与多台机器共享的钻头判定/消耗逻辑（原为虚空流体钻机基类私有实现，因地表行星气体
 * 钻机复用同一套"输入总线钻头阶级加成 + 按概率消耗"机制而顶层化；三处使用场景：
 * {@link MTEVoidFluidDrillerBase}（0.1% 消耗概率）、{@link MTESurfacePlanetaryGasSiphon}（1% 消耗概率））。
 * <p>
 * 消耗语义（两台机器一致，见各自 checkProcessing 说明）：消耗时点 = 周期启动成功时（全部失败返回
 * 分支之后）；必须在配方窗口内（checkRecipe 先以 startRecipeProcessing 开窗、后以 endRecipeProcessing
 * 关窗）扣减——普通输入总线中 {@link #stack} 为库存槽位活跃引用，扣减即生效；ME 输入总线中
 * {@link #stack} 为窗口内提取出的 slot.extracted 活跃引用，扣减后由窗口关闭时的差额结算
 * （extractedAmount − stackSize，{@code MTEHatchInputBusME.endRecipeProcessing}）自动从 AE 网络补扣。
 * 单周期至多消耗 1 个，概率由调用方传入（钻机 0.1%、地表气体钻机 1%，各自独立常量）。
 */
public final class DrillSelection {

    /** 全阶段共享的钻头材质阶梯（列表序 = 钻头阶级 0..4）：钻头项 = 4^(阶级+1)；输入总线取阶级最高者。 */
    public static final ImmutableList<Materials> DRILL_MATERIALS = ImmutableList
        .of(Materials.Iron, Materials.Steel, Materials.Titanium, Materials.Naquadah, Materials.Infinity);

    /** 选中钻头的活跃库存引用（普通总线槽位库存或 ME 总线提取快照），消耗时直接改写。 */
    public final ItemStack stack;
    /** 钻头材质名（仅用于 GUI 显示，不影响选择逻辑）。 */
    public final String materialName;
    /** 钻头阶级（{@link #DRILL_MATERIALS} 列表序 0..4），驱动钻头项 4^(阶级+1)。 */
    public final int rung;

    public DrillSelection(ItemStack stack, String materialName, int rung) {
        this.stack = stack;
        this.materialName = materialName;
        this.rung = rung;
    }

    /** 钻头项：4^(阶级+1)（铁=4、钢=16、钛=64、硅岩=256、无尽=1024），位移实现即 1L&lt;&lt;2(阶级+1)。 */
    public static long bonus(int drillRung) {
        return 1L << (2 * (drillRung + 1));
    }

    /**
     * 输入堆叠中钻头阶级最高者，无则为 null（同阶级先到先得，材质名仅影响 GUI 显示）。
     * 正常构建中钻头物品堆不会为 null（RecipeLoader 已校验）；GT 数据损坏时跳过而不崩溃。
     */
    @Nullable
    public static DrillSelection findDrill(List<ItemStack> inputs) {
        DrillSelection best = null;
        for (ItemStack input : inputs) {
            if (input == null) continue;
            for (int rung = 0; rung < DRILL_MATERIALS.size(); rung++) {
                final Materials material = DRILL_MATERIALS.get(rung);
                final ItemStack drillStack = get(OrePrefixes.toolHeadDrill, material, 1);
                if (drillStack == null) continue;
                if (input.isItemEqual(drillStack) && (best == null || rung > best.rung)) {
                    best = new DrillSelection(input, material.mName, rung);
                }
            }
        }
        return best;
    }

    /**
     * 周期启动成功时调用：以 {@code chance} 概率消耗 1 个选中的钻头（roll 一次 XSTR），
     * 消耗方式为直接改写 {@link #stack} 的活跃引用并刷新机器槽位（见类注释的 ME 总线兼容语义）。
     */
    public void consumeIfRolled(double chance, MTEMultiBlockBase host) {
        if (XSTR_INSTANCE.nextFloat() >= chance) return;
        if (stack.stackSize <= 1) {
            stack.stackSize = 0;
        } else {
            stack.stackSize--;
        }
        host.updateSlots();
    }
}
