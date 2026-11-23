package com.jdz.vmd.mixin.mixins;

import java.lang.reflect.Method;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.utils.serialization.ByteBufAdapters;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.jdz.vmd.api.IVoidMinerOverride;
import com.jdz.vmd.common.gui.ExVoidMinerFilterPanel;
import com.jdz.vmd.common.tileentities.MTEVoidMinerEXBase;

import bwcrossmod.galacticgreg.MTEVoidMinerBase;
import bwcrossmod.galacticgreg.VoidMinerUtility;
import gregtech.common.gui.modularui.multiblock.MTEVoidMinerBaseGui;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gtneioreplugin.plugin.item.ItemDimensionDisplay;
import gtneioreplugin.util.DimensionHelper;

/**
 * 在虚空矿机终端显示维度覆写，并复用 GT 的过滤弹窗入口。普通机型保留旧刷新方式，EX 同步服务端矿表。
 * 独立版 vmtweak 存在时由 {@code VmdMixinPlugin} 让位，已取消的注入回调不重复处理。
 */
@SuppressWarnings("rawtypes")
@Mixin(value = MTEVoidMinerBaseGui.class, remap = false)
public abstract class VMDVoidMinerBaseGuiMixin extends MTEMultiBlockBaseGui<MTEVoidMinerBase> {

    /** 记录过滤面板构建与缓存清理错误。 */
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
     * 在终端既有状态行下方追加维度覆写状态，以及 EX 的最近实际产出。
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

        // 服务端只同步原始值：空串、带 ! 的警告键或维度缩写；由客户端按本地语言显示。
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

        if (mb instanceof MTEVoidMinerEXBase<?>ex) {
            list.child(vmd$createLastOutputRow(syncManager, ex));
        }

        return list;
    }

    /** 两侧以同一键注册只读同步；矿石名称和图标由客户端根据同步到的物品栈显示。 */
    @Unique
    private static Flow vmd$createLastOutputRow(PanelSyncManager syncManager, MTEVoidMinerEXBase<?> ex) {
        GenericSyncValue<ItemStack, ?> lastOutputSyncer = GenericSyncValue.builder(ItemStack.class)
            .getter(syncManager.isClient() ? () -> null : ex::getLastOutputOre)
            .adapter(ByteBufAdapters.ITEM_STACK)
            .copy(ItemStack::copy)
            .nullable()
            .build();
        syncManager.syncValue("vmd$lastOutputOre", lastOutputSyncer);

        ItemDrawable icon = new ItemDrawable(lastOutputSyncer.getValue());
        lastOutputSyncer.setChangeListener(() -> icon.setItem(lastOutputSyncer.getValue()));
        return Flow.row()
            .child(
                icon.asWidget()
                    .setEnabledIf(w -> lastOutputSyncer.getValue() != null))
            .child(IKey.dynamic(() -> {
                ItemStack stack = lastOutputSyncer.getValue();
                return stack == null ? StatCollector.translateToLocal("vmd.gui.last_output.none")
                    : StatCollector.translateToLocalFormatted("vmd.gui.last_output", stack.getDisplayName());
            })
                .color(Color.WHITE.main)
                .asWidget()
                .textAlign(Alignment.CenterLeft)
                .expanded())
            .collapseDisabledChild()
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .childPadding(2)
            .marginBottom(2)
            .fullWidth()
            .coverChildrenHeight(16);
    }

    /** EX 齿轮不依赖本地矿表；仅改 GT 的按钮显示条件，不另注册面板或按钮。 */
    @ModifyArg(
        method = "createRightPanelGapRow",
        at = @At(
            value = "INVOKE",
            target = "Lcom/cleanroommc/modularui/widgets/layout/Flow;childIf(ZLjava/util/function/Supplier;)Lcom/cleanroommc/modularui/api/widget/IParentWidget;"),
        index = 0,
        require = 1,
        remap = false)
    private boolean vmd$showFilterGear(boolean hasDropMap) {
        return hasDropMap || multiblock instanceof MTEVoidMinerEXBase;
    }

    /**
     * 主面板开启期间更新 EX 矿表，即使 EX 停机且过滤子面板未开启。
     * 普通机型保留原有的关闭、丢弃缓存、重开流程。
     */
    @Inject(method = "createRightPanelGapRow", at = @At("RETURN"), require = 1, remap = false)
    private void vmd$onCreateRightPanelGapRow(ModularPanel parent, PanelSyncManager syncManager,
        CallbackInfoReturnable<Flow> cir) {
        if (cir.isCancelled()) return;

        MTEVoidMinerBase mb = this.multiblock;

        if (mb instanceof MTEVoidMinerEXBase) {
            syncManager.onServerTick(((IVoidMinerOverride) mb)::vmd$refreshExFilter);
            return;
        }

        // 查找原方法刚注册的过滤面板 handler
        IPanelHandler filterPanel = syncManager.findPanelHandlerNullable("filter");
        if (filterPanel == null) return;

        // 初始化版本号
        vmd$lastSeenVersion = ((IVoidMinerOverride) mb).vmd$getDimChangeVersion();

        // 等客户端确认关闭（isPanelOpen 为 false）后再丢弃缓存，避免 closeSubPanels() 发生 NPE。
        // 丢弃后的下一 tick 重开。
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
        if (mb instanceof MTEVoidMinerEXBase) {
            cir.setReturnValue(ExVoidMinerFilterPanel.create(syncManager, (IVoidMinerOverride) mb));
            return;
        }

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
     * 丢弃两侧的面板缓存。ModularUI 2.3.92 的 {@code deleteCachedPanel()} 在服务端因
     * {@code openedPanel} 恒为空而直接返回；{@code readOnServer(SYNC_DISPOSE)} 是网络包入口，
     * 直接调用需传空缓冲区，不是公开清理 API。无等价入口，故反射调用私有 {@code disposePanel()}，
     * 再发 {@code SYNC_DISPOSE} 让客户端丢弃缓存。
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
