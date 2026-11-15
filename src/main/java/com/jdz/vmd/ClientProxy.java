package com.jdz.vmd;

import com.jdz.vmd.common.loaders.GtLangOverrideWriter;

import cpw.mods.fml.common.event.FMLPostInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
        // GT 语言链（WAILA 等）独立于 vanilla 语言通道，非英文语言时从
        // config/GregTech/GregTech_zh_CN.lang 读取；postInit 写入该文件不会触发新的 GT
        // 类加载（LoadComplete 之后写入会在 RFB 环境崩溃，勿迁移时机）。
        // GT 在下一次语言重载/启动时读取该文件生效。
        GtLangOverrideWriter.writeChineseOverrides();
    }
}
