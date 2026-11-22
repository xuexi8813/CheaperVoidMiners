package com.jdz.vmd;

import org.apache.logging.log4j.Level;

import com.jdz.vmd.api.recipe.VmdRecipeMaps;
import com.jdz.vmd.common.loaders.ItemRegistry;
import com.jdz.vmd.common.loaders.RecipeLoader;
import com.jdz.vmd.common.loaders.VmdOutputRecipeLoader;
import com.jdz.vmd.common.tileentities.VoidOreTableHelper;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import gregtech.api.GregTechAPI;

public class CommonProxy {

    public void init(FMLInitializationEvent event) {
        ItemRegistry.run();
        // 尽早强制类加载：NEI 构建处理器列表可能早于服务器启动（postInit 期间）。
        VmdRecipeMaps.register();
        // NEI 产出配方注册挂到 GT postInit 末尾的官方扩展点 sAfterGTPostload：
        // 1. FML 对 init/postInit 阶段的 mod 分发顺序不保证 required-after 语义（dedicated server 实测
        // 本 mod postInit 先于 GT PostLoad-Phase 执行），直挂本 mod postInit 时 GT 世界生成表
        // （WorldgenGTOreLayer/SmallPieces.sList，GT postInit 经 GTWorldgenloader 填充）尚未就绪，
        // 注册会得到空奖池；sAfterGTPostload 在 GTWorldgenloader 与 StoneType.init() 之后执行，必然就绪。
        // 2. 客户端同样执行 GT postInit，纯客户端连远程服（不触发 FMLServerStartingEvent）也能注册。
        // 3. 该时刻仍处于 FML postInit 阶段内，不违反 RFB 环境对 FMLLoadCompleteEvent 的类加载限制。
        // 贫矿追加（须在 BartWorks 于 FMLLoadCompleteEvent 生成掉落表之后）与此时序无关，仍留 serverStarting。
        GregTechAPI.sAfterGTPostload.add(this::registerVmdOutputRecipes);
    }

    public void postInit(FMLPostInitializationEvent event) {
        RecipeLoader.run();
        // 地表行星气体钻机的 NEI 产出配方主注册点：数据源为 GT5u 原版气体清单
        // （GasSiphonRecipes.RECIPES），由 gtnhintergalactic 的 postInit 填充。本 mod 已声明
        // after:gtnhintergalactic 使此处晚于其 postInit；若 FML 实际分发顺序未保证这一点
        // （本项目实测 FML 对 init/postInit 分发顺序不严格遵循依赖语义），该方法会检测到清单
        // 仍为空表、不缓存空数据并交由 registerVmdOutputRecipes（GT-PostLoad 兜底）重试——
        // GT5u 2.9 的 postInit 经调度排在所有普通 mod 之后，届时清单必然就绪。
        VmdOutputRecipeLoader.registerSurfaceGasSiphonRecipes();
    }

    /** NEI 产出配方注册入口（GT postInit 末尾执行），日志如实标注挂载点。 */
    private void registerVmdOutputRecipes() {
        FMLLog.log("CheaperVoidMiners", Level.INFO, "GT-PostLoad: registering NEI output recipes");
        VmdOutputRecipeLoader.register();
        // 地表行星气体钻机的 NEI 配方兜底注册：主注册点为本 mod postInit（数据源 GasSiphonRecipes
        // 由 gtnhintergalactic 的 postInit 填充）；若 postInit 分发顺序异常导致本 mod postInit 先于
        // gtnhintergalactic 执行，此处（GT5u 2.9 的 postInit 调度排在普通 mod 之后）重试补注册。
        // 已注册时该调用经内部标记直接返回。
        VmdOutputRecipeLoader.registerSurfaceGasSiphonRecipes();
        FMLLog.log("CheaperVoidMiners", Level.INFO, "GT-PostLoad: NEI output recipes registered");
    }

    /**
     * 服务器启动钩子：仅向 GT 虚空采矿机掉落表追加贫矿。须在 BartWorks 生成虚空矿机掉落表
     * （{@code VoidMinerUtility.generateDropMaps}，BartWorks 于 FMLLoadCompleteEvent 执行）之后运行，
     * 否则追加内容会被掉落表重建覆盖。
     * 不用 FMLLoadCompleteEvent——initializeMods 时段首次加载游戏逻辑类会在 RFB 环境触发类加载崩溃。
     * NEI 产出配方注册与此处时序无关（其数据源为 GT 世界生成表，经 GregTechAPI.sAfterGTPostload 注册）。
     */
    public void serverStarting(FMLServerStartingEvent event) {
        FMLLog.log("CheaperVoidMiners", Level.INFO, "ServerStarting: appending poor ores to the void miner drop maps");
        VoidOreTableHelper.appendPoorOresToVoidMinerDropMaps();
        FMLLog.log("CheaperVoidMiners", Level.INFO, "ServerStarting: poor ores appended to the void miner drop maps");
    }
}
