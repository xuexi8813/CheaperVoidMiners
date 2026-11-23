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
 * 共享的钻头选择结果与消耗逻辑。周期成功启动时，在配方处理窗口内尝试消耗。
 * 普通总线直接扣减槽位库存；ME 总线扣减提取引用，由 {@code endRecipeProcessing} 在关窗时向 AE 网络结算差额。
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

    /** 选最高阶钻头，同阶取首个；跳过 GT 未提供的钻头材质，无匹配则返回 null。 */
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
