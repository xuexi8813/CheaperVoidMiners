package com.jdz.vmd.mixin.mixins;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;

import java.lang.reflect.Method;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.PanelSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.jdz.vmd.api.IVoidMinerOverride;

import bwcrossmod.galacticgreg.MTEVoidMinerBase;
import bwcrossmod.galacticgreg.VoidMinerUtility;
import gregtech.common.gui.modularui.multiblock.MTEVoidMinerBaseGui;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gtneioreplugin.plugin.item.ItemDimensionDisplay;
import gtneioreplugin.util.DimensionHelper;

/**
 * {@link MTEVoidMinerBaseGui} 的维度覆写 mixin：在终端区域追加覆写状态显示，
 * 并在覆写维度变化时对过滤弹窗做"关闭-丢弃缓存-重开"的实时刷新。
 * 逻辑 1:1 移植自 Void-Miner-Tweak-Mod（reobf.vmtweak），前缀与键名统一改为 vmd 命名空间。
 *
 * <p>
 * 独立版 vmtweak 在场时由 {@code com.jdz.vmd.mixin.VmdMixinPlugin} 让位（GUI 类
 * 只会被一方注入）；两个 @Inject 注入器内的 {@code isCancelled} 检查是第二道防线。
 */
@SuppressWarnings("rawtypes")
@Mixin(value = MTEVoidMinerBaseGui.class, remap = false)
public abstract class VMDVoidMinerBaseGuiMixin extends MTEMultiBlockBaseGui<MTEVoidMinerBase> {

    /**
     * 本 mixin 自身的日志器。静态字段不参与 @Shadow/@Unique 合并到目标类的成员注入，
     * 仅存在于 mixin 类内供日志使用。
     */
    @Unique
    private static final Logger LOG = LogManager.getLogger("CheaperVoidMiners");

    @SuppressWarnings("DataFlowIssue")
    private VMDVoidMinerBaseGuiMixin() {
        super(null);
    }

    /** 上一次观测到的维度变化版本号，用于检测覆写是否变化。 */
    @Unique
    private int vmd$lastSeenVersion = -1;

    /**
     * 过滤弹窗实时刷新的状态机：
     * 0 = IDLE（监视变化）
     * 1 = CLOSING（已发送关闭，等待客户端往返确认）
     * 2 = DISPOSED（已丢弃缓存，下一 tick 重开）
     */
    @Unique
    private int vmd$refreshState = 0;

    /**
     * 覆写 createTerminalTextWidget：在终端/屏幕区域既有状态行下方追加维度覆写状态显示。
     */
    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        ListWidget<IWidget, ?> list = super.createTerminalTextWidget(syncManager, parent);

        MTEVoidMinerBase mb = this.multiblock;

        // 将覆写显示文本同步到客户端
        StringSyncValue overrideTextSyncer = new StringSyncValue(
            () -> ((IVoidMinerOverride) mb).vmd$getOverrideDisplayText());
        syncManager.syncValue("vmd$overrideText", overrideTextSyncer);

        // 同步警告文本
        StringSyncValue warningSyncer = new StringSyncValue(() -> ((IVoidMinerOverride) mb).vmd$getWarning());
        syncManager.syncValue("vmd$warning", warningSyncer);

        // 在既有状态行下方追加覆写状态标签
        // 同步值为服务端原始数据：""（无覆写）、"!警告lang键"（覆写失败）、或维度缩写（"Ow"、"Ne"、"EA"）。
        // 客户端 lambda 使用客户端安全 API 完成本地化。
        list.child(IKey.dynamic(() -> {
            String raw = overrideTextSyncer.getValue();
            if (raw == null || raw.isEmpty()) return "";
            // "!" 前缀表示警告 lang 键
            if (raw.startsWith("!")) {
                return StatCollector.translateToLocal(raw.substring(1));
            }
            // 否则是维度缩写，在客户端本地化
            String displayName;
            if ("EA".equals(raw)) {
                displayName = StatCollector.translateToLocal("vmd.gui.override.end_asteroids");
            } else {
                String internalName = DimensionHelper.ABBR_TO_INTERNAL.getOrDefault(raw, raw);
                displayName = DimensionHelper.getDimLocalizedName(internalName);
                if (displayName == null || displayName.isEmpty()) {
                    displayName = raw;
                }
            }
            return StatCollector.translateToLocal("vmd.gui.override.description") + displayName;
        })
            .color(Color.WHITE.main)
            .asWidget()
            .setEnabledIf(w -> {
                String raw = overrideTextSyncer.getValue();
                return raw != null && !raw.isEmpty();
            })
            .marginBottom(2)
            .widthRel(1));

        // EX 系列实时产能行：非 EX 机型 vmd$getCurrentOutputPerSecond() 返回 0，同步值恒为空串、行隐藏。
        // 同步值拆分口径与覆写行一致：服务端传千分位格式化数字（formatNumber 为配置化 locale 的纯文本
        // 格式化，非本地化语义，双侧可用，且每次调用新建 DecimalFormat，无共享可变状态），
        // 客户端 lambda 完成模板本地化，保证专服下按客户端语言显示双语。
        StringSyncValue outputRateSyncer = new StringSyncValue(() -> {
            long outputPerSecond = ((IVoidMinerOverride) mb).vmd$getCurrentOutputPerSecond();
            // 0 = 非 EX 机型不支持产能显示；正数产能最大 2^45，不会与 0 冲突
            return outputPerSecond > 0 ? formatNumber(outputPerSecond) : "";
        });
        syncManager.syncValue("vmd$outputRate", outputRateSyncer);

        list.child(IKey.dynamic(() -> {
            String raw = outputRateSyncer.getValue();
            if (raw == null || raw.isEmpty()) return "";
            return StatCollector.translateToLocalFormatted("vmd.gui.output_rate", raw);
        })
            .color(Color.WHITE.main)
            .asWidget()
            .setEnabledIf(w -> {
                String raw = outputRateSyncer.getValue();
                return raw != null && !raw.isEmpty();
            })
            .marginBottom(2)
            .widthRel(1));

        return list;
    }

    /**
     * 在 createRightPanelGapRow 的 RETURN 处注册服务端 tick 处理器，监视维度覆写变化。
     * 过滤弹窗打开时检测到变化，执行"关闭-丢弃缓存-重开"，使新维度的矿物列表即时显示。
     */
    @Inject(method = "createRightPanelGapRow", at = @At("RETURN"), require = 1, remap = false)
    private void vmd$onCreateRightPanelGapRow(ModularPanel parent, PanelSyncManager syncManager,
        CallbackInfoReturnable<Flow> cir) {
        if (cir.isCancelled()) return;

        MTEVoidMinerBase mb = this.multiblock;

        // 查找原方法刚注册的过滤面板 handler
        IPanelHandler filterPanel = syncManager.findPanelHandlerNullable("filter");
        if (filterPanel == null) return;

        // 初始化版本号
        vmd$lastSeenVersion = ((IVoidMinerOverride) mb).vmd$getDimChangeVersion();

        // 注册服务端 tick 处理器，用三阶段状态机实时刷新过滤弹窗：
        // IDLE → 检测到变化 → 关闭面板 → CLOSING
        // → 等待客户端关闭往返（isPanelOpen 变 false）→ 丢弃缓存 → DISPOSED
        // → 重开面板 → IDLE
        // 先等客户端完全关闭面板再丢弃其 syncManager，避免 closeSubPanels() 中 NPE。
        syncManager.onServerTick(() -> {
            switch (vmd$refreshState) {
                case 1: // CLOSING - 等待面板经客户端往返后完全关闭
                    if (!filterPanel.isPanelOpen()) {
                        // 客户端已确认关闭，可安全丢弃两侧的缓存面板
                        vmd$forceDisposePanel((PanelSyncHandler) filterPanel);
                        vmd$refreshState = 2;
                    }
                    return;
                case 2: // DISPOSED - 以全新 UI 重开
                    filterPanel.openPanel();
                    ((PanelSyncHandler) filterPanel).syncToClient(PanelSyncHandler.SYNC_OPEN);
                    vmd$refreshState = 0;
                    return;
                default: // IDLE - 监视变化
                    break;
            }
            int currentVersion = ((IVoidMinerOverride) mb).vmd$getDimChangeVersion();
            if (currentVersion != vmd$lastSeenVersion) {
                vmd$lastSeenVersion = currentVersion;
                if (filterPanel.isPanelOpen()) {
                    // 发送关闭请求；客户端往返 closePanelInternal 后服务端才会看到 isPanelOpen()==false
                    filterPanel.closePanel();
                    vmd$refreshState = 1;
                } else {
                    // 未打开：仅丢弃缓存，下次手动打开即为全新内容
                    vmd$forceDisposePanel((PanelSyncHandler) filterPanel);
                }
            }
        });
    }

    /**
     * 在 createFilterPopup 的 HEAD 处注入，保证客户端与服务端的 dropMap 均正确。
     * 客户端 MTE 的 dropMap 可能过期：calculateDropMap 只在服务端配方处理时运行。
     */
    @Inject(method = "createFilterPopup", at = @At("HEAD"), require = 1, remap = false, cancellable = true)
    private void vmd$onCreateFilterPopup(PanelSyncManager syncManager, IPanelHandler panelHandler,
        CallbackInfoReturnable<ModularPanel> cir) {
        if (cir.isCancelled()) return;

        MTEVoidMinerBase mb = multiblock;

        // 从覆写槽位解析正确的矿表；数据异常（物品/维度解析失败）仅记 DEBUG 日志，
        // 走后续无矿表兜底面板，不让 GUI 构建崩溃。
        try {
            ItemStack slotStack = mb.mInventory[1];
            if (slotStack != null && slotStack.getItem() instanceof ItemDimensionDisplay) {
                String dimAbbr = ItemDimensionDisplay.getDimension(slotStack);
                if (dimAbbr != null && !"None".equals(dimAbbr)) {
                    String targetDimName = DimensionHelper.ABBR_TO_INTERNAL.get(dimAbbr);
                    if (targetDimName != null) {
                        VoidMinerUtility.DropMap resolvedMap = VoidMinerUtility.dropMapsByDimName.get(targetDimName);
                        if (resolvedMap != null) {
                            VoidMinerUtility.DropMap extraMap = VoidMinerUtility.extraDropsByDimName
                                .getOrDefault(targetDimName, new VoidMinerUtility.DropMap());
                            resolvedMap.isDistributionCached(extraMap);
                            mb.dropMap = resolvedMap;
                            mb.extraDropMap = extraMap;
                            // 保证过滤槽容量不小于矿物数
                            if (resolvedMap.getOres() != null
                                && mb.selected.getSlots() < resolvedMap.getOres().length) {
                                mb.selected.setSize(resolvedMap.getOres().length);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("Failed to resolve override drop map from the controller slot", e);
        }

        // 最终兜底：矿表仍为 null 或无矿物时显示"无矿物"面板
        VoidMinerUtility.DropMap dm = mb.dropMap;
        if (dm == null) {
            cir.setReturnValue(vmd$emptyFilterPanel());
            return;
        }
        dm.isDistributionCached(mb.extraDropMap);
        if (dm.getOres() == null || dm.getOres().length == 0) {
            cir.setReturnValue(vmd$emptyFilterPanel());
            return;
        }
        // 保证过滤槽容量不小于矿物数（只扩不缩）
        int oreCount = dm.getOres().length;
        if (mb.selected.getSlots() < oreCount) {
            mb.selected.setSize(oreCount);
        }
    }

    /**
     * 强制丢弃服务端与客户端两侧的缓存面板。
     *
     * <p>
     * 反射不可达原因（ModularUI 2.3.92 源码核实）：{@code PanelSyncHandler#deleteCachedPanel()}
     * 以 {@code openedPanel == null || isPanelOpen()} 提前返回，而服务端 openedPanel 恒为 null
     * （仅在客户端 openPanel 时赋值），该方法在服务端永远无效；唯一能清空缓存的
     * {@code disposePanel()} 为 private，且无公开等价入口（readOnServer(SYNC_DISPOSE) 属网络
     * 包读取接口、需传 null buffer，语义上并非 dispose 的公开 API），因此经反射调用，
     * 再发送 SYNC_DISPOSE 让客户端同步丢弃其缓存面板。
     */
    @Unique
    private static void vmd$forceDisposePanel(PanelSyncHandler psh) {
        try {
            Method disposeMethod = PanelSyncHandler.class.getDeclaredMethod("disposePanel");
            disposeMethod.setAccessible(true);
            disposeMethod.invoke(psh);
            // 通知客户端同样丢弃其缓存面板
            psh.sync(PanelSyncHandler.SYNC_DISPOSE);
        } catch (Exception e) {
            LOG.debug("Failed to dispose the cached filter panel", e);
        }
    }

    @Unique
    private ModularPanel vmd$emptyFilterPanel() {
        return new ModularPanel("gt:vm:filter").child(ButtonWidget.panelCloseButton())
            .child(
                IKey.lang("vmd.gui.filter.no_ores")
                    .color(Color.WHITE.main)
                    .asWidget()
                    .margin(8))
            .coverChildren();
    }
}
