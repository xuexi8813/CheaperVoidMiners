package com.jdz.vmd.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.launchwrapper.Launch;

import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin 配置插件：独立版 vmtweak（Void-Miner-Tweak-Mod）在场时整体让位。
 *
 * <p>
 * 动机：两个 mod 若同时对 {@code MTEVoidMinerBase} / {@code MTEVoidMinerBaseGui}
 * 注入，会重复 cancel 方法、重复注册同步值与 tick 处理器，mixin 框架对此并不感知。
 * 用户在删除 vmtweak jar 前的过渡期内同时安装两者时，由本插件禁用本组 mixin，
 * 行为与仅装 vmtweak 完全一致。
 *
 * <p>
 * 在场判定：mixin 准备（PREPARE）阶段早于 FML Loader 初始化，
 * {@code Loader.instance().isModLoaded} 会 NPE（冒烟堆栈已验证），故改用类路径资源探测：
 * 不加载类、不触发转换器、不依赖 FML API，探测 vmtweak 发布 jar 的实际主类
 * reobf.vmtweak.main.VMTweak。
 */
public class VmdMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return "mixins.cheapervoidminers.refmap.json";
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // 独立版 vmtweak 在场时禁用本组 mixin，避免对同一目标的重复注入与重复逻辑。
        // PREPARE 阶段早于 FML Loader 初始化，不能调 Loader.instance().isModLoaded（会 NPE），
        // 以类路径资源探测替代：探测对象为 vmtweak 发布 jar 的实际主类。
        boolean present = Launch.classLoader.getResource("reobf/vmtweak/main/VMTweak.class") != null;
        return !present;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @SuppressWarnings("unused")
    @Override
    public List<String> getMixins() {
        final List<String> mixins = new ArrayList<>();
        mixins.add("VMDVoidMinerBaseMixin");
        mixins.add("VMDVoidMinerBaseGuiMixin");
        return mixins;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
