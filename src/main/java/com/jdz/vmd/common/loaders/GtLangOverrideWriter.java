package com.jdz.vmd.common.loaders;

import java.io.File;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import org.apache.logging.log4j.Level;

import com.jdz.vmd.CheaperVoidMiners;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;

/**
 * 客户端 GT 语言链中文覆盖写入器。
 * <p>
 * 动机：GT5u 2.9 的机器名不走 vanilla 语言通道，而是 {@code GTLanguageManager} 自维护的
 * GT 语言链（WAILA 等读取该链），其键为全小写（GT 构造器强制 {@code mName.toLowerCase()}），
 * 非英文语言时从游戏根目录的 {@code GregTech_(语言码).lang} 读取（注意不是 config 子目录，
 * GT5u 经 GTPreLoad.initLocalization 以 config 的父目录为语言文件目录）。本模组 vanilla 语言
 * 副本（assets/.../lang/zh_CN.lang）只对 vanilla 通道生效，WAILA 显示名必须写入上述覆盖
 * 文件才能汉化。
 * <p>
 * 需求：仅客户端且当前语言为 zh_CN 时，幂等写入 10 台机器的中文显示名；dedicated server
 * 与其他语言直接跳过，不创建文件。GT5u 在下一次语言重载/启动时读取该文件生效。
 * 写入时机为 postInit：此时写文件不会触发新的 GT 类加载（LoadComplete 之后写入会在 RFB
 * 环境触发类加载崩溃，勿迁移时机）。
 */
public final class GtLangOverrideWriter {

    /** 覆盖文件名：GTLanguageManager.reloadLanguage 按 "GregTech_" + 当前语言码 + ".lang" 拼接。 */
    private static final String L10N_FILE_NAME = "GregTech_zh_CN.lang";

    /** GTLanguageManager 读写翻译条目使用的 Configuration category。 */
    private static final String LANGUAGE_CATEGORY = "LanguageFile";

    /** 目标语言码；客户端当前语言不匹配时不写文件。 */
    private static final String TARGET_LANGUAGE = "zh_CN";

    /**
     * 10 台机器的语言条目，每行依次为：GT 语言键、GT 英文占位值、中文显示名。
     * 键 = "gt.blockmachines." + mName（注册名，GT 构造器强制全小写）+ ".name"；
     * 英文占位值为注册时传入的 aRegionalName——zh_CN 覆盖文件先于机器键存在时，GT 会把该
     * 英文占位写进文件，检测到现值等于占位值则覆盖回中文；其余非中文值视为用户自定义，
     * 保持不动以保证幂等。
     */
    private static final String[][] MACHINE_NAMES = {
        { "gt.blockmachines.voidminerex1.name", "Void Miner EX I", "虚空矿机 EX I" },
        { "gt.blockmachines.voidminerex2.name", "Void Miner EX II", "虚空矿机 EX II" },
        { "gt.blockmachines.voidminerex3.name", "Void Miner EX III", "虚空矿机 EX III" },
        { "gt.blockmachines.voidfluiddrilleriron.name", "Void Fluid Driller (Iron)", "虚空流体钻机（铁）" },
        { "gt.blockmachines.voidfluiddrillersteel.name", "Void Fluid Driller (Steel)", "虚空流体钻机（钢）" },
        { "gt.blockmachines.voidfluiddrillertitanium.name", "Void Fluid Driller (Titanium)", "虚空流体钻机（钛）" },
        { "gt.blockmachines.voidfluiddrillernaquadah.name", "Void Fluid Driller (Naquadah)", "虚空流体钻机（硅岩）" },
        { "gt.blockmachines.voidfluiddrillerinfinity.name", "Void Fluid Driller (Infinity)", "虚空流体钻机（无尽）" },
        { "gt.blockmachines.voidfluidpumpwooden.name", "Wooden Void Fluid Pump", "木质虚空流体泵" },
        { "gt.blockmachines.voidminerwooden.name", "Wooden Void Miner", "木质虚空矿机" } };

    private GtLangOverrideWriter() {}

    /**
     * 向 GT 的 zh_CN 覆盖文件写入机器中文显示名；非客户端、非 zh_CN 语言时直接返回。
     * 只写 zh_CN 覆盖文件，不触碰 GT 自管理的 GregTech.lang（英文主文件）。
     * 写入异常仅记录 ERROR 日志，不中断启动流程。
     */
    public static void writeChineseOverrides() {
        // dedicated server 走 CommonProxy 不会调用本方法，此判断作为双重保险。
        if (FMLCommonHandler.instance()
            .getSide() != Side.CLIENT) {
            return;
        }
        final String languageCode = Minecraft.getMinecraft()
            .getLanguageManager()
            .getCurrentLanguage()
            .getLanguageCode();
        if (!TARGET_LANGUAGE.equalsIgnoreCase(languageCode)) {
            return;
        }
        // 与 GT5u 同目录：游戏根目录（config 的父目录），即 GTPreLoad.initLocalization 传入的
        // languageDir；整合包中就是版本目录（如 versions/GT New Horizons 2.9.0/GregTech_zh_CN.lang）。
        final File langFile = new File(
            Loader.instance()
                .getConfigDir()
                .getParentFile(),
            L10N_FILE_NAME);
        try {
            final Configuration config = new Configuration(langFile);
            int writtenCount = 0;
            boolean fileChanged = false;
            for (final String[] entry : MACHINE_NAMES) {
                final String key = entry[0];
                final String englishPlaceholder = entry[1];
                final String chineseName = entry[2];
                final Property property = config.get(LANGUAGE_CATEGORY, key, chineseName);
                if (!property.wasRead()) {
                    // 文件中不存在该键：get 已将中文默认值写入 Property，save 后落盘。
                    writtenCount++;
                    fileChanged = true;
                } else if (englishPlaceholder.equals(property.getString())) {
                    // GT 把英文占位写进了覆盖文件（文件先建、机器键后注册的场景），覆盖回中文。
                    property.set(chineseName);
                    writtenCount++;
                    fileChanged = true;
                }
                // 已是中文（或用户自定义）的键保持原样。
            }
            if (fileChanged) {
                config.save();
                FMLLog.log(
                    CheaperVoidMiners.MODID,
                    Level.INFO,
                    "Wrote %d Chinese name overrides to %s (GT applies them on next language reload/startup)",
                    writtenCount,
                    langFile.getAbsolutePath());
            }
        } catch (final Exception e) {
            FMLLog.log(
                CheaperVoidMiners.MODID,
                Level.ERROR,
                e,
                "Failed to write GT language overrides to %s",
                langFile.getAbsolutePath());
        }
    }
}
