package com.jdz.vmd.api.enums;

/**
 * 本 mod 注册的 MetaTileEntity 的 GT 分配 id 段（26763 起）。
 * 历史空缺 id（26766、26774-26779）曾分配给已删除机器（VoidMinerEX4 / 挖土机六台），永久预留、勿复用：
 * 旧存档中的残留 TileEntity 仍会按原 id 反序列化解析，复用空缺 id 会把旧机器方块错误映射为新机器。
 */
public enum MetaTileEntityIDs {

    VoidMinerEX1Controller(26763),
    VoidMinerEX2Controller(26764),
    VoidMinerEX3Controller(26765),
    // 26766 = 原 VoidMinerEX4Controller（已删除），预留勿复用。
    VoidFluidDrillerIron(26767),
    VoidFluidDrillerSteel(26768),
    VoidFluidDrillerTitanium(26769),
    VoidFluidDrillerNaquadah(26770),
    VoidFluidDrillerInfinity(26771),
    VoidFluidPumpWooden(26772),
    VoidMinerWooden(26773),
    // 26774-26779 = 原挖土机六台（VoidExcavatorWooden/Iron/Steel/Titanium/Naquadah/Infinity，已删除），预留勿复用。
    // 下一可用 id 取"现有最大值 + 1"且跳过上述空洞段：26773 + 1 = 26774 落在空洞内，故取 26780。
    SurfacePlanetaryGasSiphonController(26780);

    public final int ID;

    MetaTileEntityIDs(int ID) {
        this.ID = ID;
    }
}
