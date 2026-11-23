package com.jdz.vmd.common.tileentities;

import java.util.List;

import net.minecraft.item.ItemStack;

import gregtech.api.enums.ItemList;

/** 多方块机器共用的普通集成电路配置读取。 */
final class IntegratedCircuitInputs {

    private IntegratedCircuitInputs() {}

    /**
     * 按非 null 输入列表的顺序，返回首个普通集成电路的配置；没有匹配项时为 0。
     * 沿用 GT 的通配元数据、忽略 NBT 的匹配规则：跳过 null 槽位，数量为 0 的幽灵电路仍可匹配。
     */
    static int readFirstConfig(List<ItemStack> inputs) {
        for (ItemStack stack : inputs) {
            if (ItemList.Circuit_Integrated.isStackEqual(stack, true, true)) {
                return stack.getItemDamage();
            }
        }
        return 0;
    }
}
