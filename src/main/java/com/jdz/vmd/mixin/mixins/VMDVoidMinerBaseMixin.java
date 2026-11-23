package com.jdz.vmd.mixin.mixins;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.jdz.vmd.api.IVoidMinerOverride;
import com.jdz.vmd.common.tileentities.ExVoidMinerFilter;
import com.jdz.vmd.common.tileentities.ExVoidMinerFilter.Operation;
import com.jdz.vmd.common.tileentities.ExVoidMinerFilter.View;
import com.jdz.vmd.common.tileentities.MTEVoidMinerEXBase;
import com.jdz.vmd.common.tileentities.VoidOreTableHelper;

import bwcrossmod.galacticgreg.MTEVoidMinerBase;
import bwcrossmod.galacticgreg.VoidMinerUtility;
import bwcrossmod.galacticgreg.VoidMinerUtility.DropMap;
import galacticgreg.api.ModDimensionDef;
import galacticgreg.api.enums.DimensionDef;
import gregtech.api.interfaces.IOreMaterial;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTUtility;
import gregtech.common.ores.OreManager;
import gtneioreplugin.plugin.item.ItemDimensionDisplay;
import gtneioreplugin.util.DimensionHelper;

/**
 * 控制器槽内的 GTNEI 维度显示方块可覆写矿表，即使机器位于没有矿表的私人维度。
 * 逻辑移植自 Void-Miner-Tweak-Mod。独立版 vmtweak 存在时由 {@code VmdMixinPlugin} 让位，
 * 已取消的注入回调不重复处理。
 */
@SuppressWarnings("rawtypes")
@Mixin(MTEVoidMinerBase.class)
public abstract class VMDVoidMinerBaseMixin extends MTEEnhancedMultiBlockBase implements IVoidMinerOverride {

    // region MTEVoidMinerBase 的 Shadow 字段

    @Shadow(remap = false)
    private float totalWeight;

    @Shadow(remap = false)
    public VoidMinerUtility.DropMap dropMap;

    @Shadow(remap = false)
    public VoidMinerUtility.DropMap extraDropMap;

    @Shadow(remap = false)
    private ModDimensionDef dimensionDef;

    @Shadow(remap = false)
    private boolean canVoidMine;

    @Shadow(remap = false)
    public ItemStackHandler selected;

    @Shadow(remap = false)
    public boolean blacklist;

    // endregion

    // region 维度覆写的独有状态

    /** 记录读取维度覆写槽的异常。 */
    @Unique
    private static final Logger VMD$LOG = LogManager.getLogger("CheaperVoidMiners");

    /** 覆写失败时在 GUI 显示的警告消息（lang 键原文）。 */
    @Unique
    private String vmd$warning = "";

    /** 上一次覆写的维度缩写（如 "Ow"、"Ne"）；"None" 表示无覆写。 */
    @Unique
    private String vmd$lastDimOverride = "None";

    /** 维度覆写每次变化时自增的单调计数器，供 GUI 检测变化刷新过滤面板。 */
    @Unique
    private int vmd$dimChangeVersion = 0;

    /** The EX table revision changes on every resolution, including removal or invalid markers. */
    @Unique
    private long vmd$exRevision;

    /** Indexes only the current server distribution; persisted selections stay in GT's selected handler. */
    @Unique
    private ExVoidMinerFilter vmd$exFilter;

    /** The current output batch's filter; EX projects the saved slots onto the current ore table. */
    @Unique
    private List<ItemStack> vmd$batchSelections;

    /** Whether the filter contains a selected stack; computed once for GT's empty-filter check. */
    @Unique
    private boolean vmd$batchSelectionEmpty;

    /** The last raw EX marker; a blank abbreviation means a present but invalid marker. */
    @Unique
    private String vmd$exMarker;

    // endregion

    protected VMDVoidMinerBaseMixin(String aName) {
        super(aName);
    }

    // region calculateDropMap 覆写

    /**
     * 首 tick 安全重算矿表，避免私人维度的空矿表触发原版 NPE。
     * 远程客户端没有 serverStarting，须在此追加贫矿：晚于 BartWorks 建表和 RFB 类加载限制期，
     * 早于重算矿表和过滤弹窗缓存。追加是幂等的，且必须先于取消检查，不随 vmtweak 让位跳过。
     */
    @Inject(method = "onFirstTick", at = @At("HEAD"), require = 1, remap = false, cancellable = true)
    private void vmd$onFirstTick(IGregTechTileEntity aBaseMetaTileEntity, CallbackInfo ci) {
        VoidOreTableHelper.appendPoorOresToVoidMinerDropMaps();
        if (ci.isCancelled()) return;
        ci.cancel();
        // 复刻 super.onFirstTick 行为
        super.onFirstTick(aBaseMetaTileEntity);
        // 以维度覆写逻辑重算矿表
        vmd$recalculateDropMap();
        // 只有普通 GT 面板仍将 selected 当作按行槽位。
        if (!vmd$isEx()) vmd$resizeSelected();
    }

    /** 替换原 calculateDropMap，支持槽位维度方块覆写目标矿表。 */
    @Inject(method = "calculateDropMap", at = @At("HEAD"), require = 1, remap = false, cancellable = true)
    private void vmd$onCalculateDropMap(CallbackInfo ci) {
        if (ci.isCancelled()) return;
        ci.cancel();
        vmd$recalculateDropMap();
    }

    @Unique
    private void vmd$recalculateDropMap() {
        if (vmd$isEx()) {
            if (getBaseMetaTileEntity().isServerSide()) vmd$resolveExDropMap(vmd$readExMarker());
            return;
        }
        // 重置状态
        this.dropMap = null;
        this.extraDropMap = null;
        this.totalWeight = 0;
        this.canVoidMine = false;
        this.vmd$warning = "";

        // 确定机器实际所在维度的定义
        if (getBaseMetaTileEntity() != null) {
            this.dimensionDef = DimensionDef.getDefForWorld(getBaseMetaTileEntity().getWorld());
        }

        // 读取槽位中的维度覆写
        String overrideAbbr = vmd$readOverrideSlot();
        boolean hasOverride = !"None".equals(overrideAbbr);

        // 尝试按覆写维度取矿表
        if (hasOverride) {
            String targetDimName = DimensionHelper.ABBR_TO_INTERNAL.get(overrideAbbr);
            if (targetDimName != null && VoidMinerUtility.dropMapsByDimName.containsKey(targetDimName)) {
                this.canVoidMine = true;
                this.dropMap = VoidMinerUtility.dropMapsByDimName.get(targetDimName);
                this.extraDropMap = VoidMinerUtility.extraDropsByDimName
                    .getOrDefault(targetDimName, new VoidMinerUtility.DropMap());
                this.dropMap.isDistributionCached(this.extraDropMap);
                this.totalWeight = dropMap.getTotalWeight() + extraDropMap.getTotalWeight();
                vmd$resizeSelected();
                return;
            }
            // 已尝试覆写但目标维度无矿表数据
            this.vmd$warning = StatCollector.translateToLocal("vmd.gui.override.error");
        }

        // 回退到机器实际所在维度
        if (this.dimensionDef == null || !this.dimensionDef.canBeVoidMined()) {
            // GalacticGreg 未注册的私人维度：放置空矿表，保证 GUI 中过滤齿轮按钮仍然可见。
            this.dropMap = new VoidMinerUtility.DropMap();
            this.extraDropMap = new VoidMinerUtility.DropMap();
            vmd$resizeSelected();
            return;
        }

        this.canVoidMine = true;
        String actualDimName = this.dimensionDef.getDimensionName();
        this.dropMap = VoidMinerUtility.dropMapsByDimName.getOrDefault(actualDimName, new VoidMinerUtility.DropMap());
        this.extraDropMap = VoidMinerUtility.extraDropsByDimName
            .getOrDefault(actualDimName, new VoidMinerUtility.DropMap());
        this.dropMap.isDistributionCached(this.extraDropMap);
        this.totalWeight = dropMap.getTotalWeight() + extraDropMap.getTotalWeight();

        if (hasOverride) {
            // 覆写已尝试但失败：按结果给出对应警告
            if (this.totalWeight > 0) {
                this.vmd$warning = StatCollector.translateToLocal("vmd.gui.override.failed");
            } else {
                this.vmd$warning = StatCollector.translateToLocal("vmd.gui.override.error");
            }
        }
        vmd$resizeSelected();
    }

    /** Whether this machine uses the EX-only, server-owned ore filter. */
    @Unique
    private boolean vmd$isEx() {
        return (Object) this instanceof MTEVoidMinerEXBase;
    }

    /** Separates an absent override from an invalid marker so both transitions invalidate stale UI actions. */
    @Unique
    private String vmd$readExMarker() {
        ItemStack slot = this.mInventory[1];
        if (slot == null || !(slot.getItem() instanceof ItemDimensionDisplay)) return null;
        try {
            String marker = ItemDimensionDisplay.getDimension(slot);
            return marker == null ? "" : marker;
        } catch (RuntimeException e) {
            VMD$LOG.warn("Failed to read the EX dimension override marker", e);
            return "";
        }
    }

    /** Resolves the map used by GT output; the EX panel never consults a client's independent drop maps. */
    @Unique
    private void vmd$resolveExDropMap(String marker) {
        dimensionDef = DimensionDef.getDefForWorld(getBaseMetaTileEntity().getWorld());
        dropMap = null;
        extraDropMap = null;
        totalWeight = 0;

        String target = marker == null ? null : DimensionHelper.ABBR_TO_INTERNAL.get(marker);
        boolean validOverride = target != null && vmd$tryApplyExDropMap(target);
        boolean canMineActualDimension = dimensionDef != null && dimensionDef.canBeVoidMined();
        canVoidMine = validOverride || canMineActualDimension;
        if (!validOverride && canMineActualDimension) vmd$tryApplyExDropMap(dimensionDef.getDimensionName());

        vmd$exMarker = marker;
        vmd$lastDimOverride = validOverride ? marker : "None";
        vmd$warning = marker == null || validOverride ? ""
            : dropMap != null ? "vmd.gui.override.failed" : "vmd.gui.override.error";
        vmd$exFilter = new ExVoidMinerFilter(++vmd$exRevision, dropMap == null ? null : dropMap.getOres());
    }

    /** Applies a mineable distribution; an extra-only dimension uses a local base without registering it in GT. */
    @Unique
    private boolean vmd$tryApplyExDropMap(String dimensionName) {
        DropMap base = VoidMinerUtility.dropMapsByDimName.get(dimensionName);
        DropMap extra = VoidMinerUtility.extraDropsByDimName.get(dimensionName);
        if (base == null) {
            if (extra == null) return false;
            base = new DropMap();
        }
        base.isDistributionCached(extra);
        GTUtility.ItemId[] ores = base.getOres();
        float weight = base.getTotalWeight();
        if (ores == null || ores.length == 0 || !(weight > 0)) return false;

        dropMap = base;
        extraDropMap = extra;
        totalWeight = weight;
        return true;
    }

    /** Updates on initial use or controller-slot change even when checkProcessing is not running. */
    @Unique
    private void vmd$refreshExDropMap() {
        String marker = vmd$readExMarker();
        if (vmd$exFilter == null || !Objects.equals(vmd$exMarker, marker)) vmd$resolveExDropMap(marker);
    }

    /**
     * 读取控制器槽位中维度方块的维度缩写；变化时更新 {@link #vmd$lastDimOverride} 并清零 totalWeight。
     */
    @Unique
    private String vmd$readOverrideSlot() {
        String dimAbbr;
        try {
            dimAbbr = Optional.ofNullable(this.mInventory[1])
                .filter(s -> s.getItem() instanceof ItemDimensionDisplay)
                .map(ItemDimensionDisplay::getDimension)
                .orElse("None");
        } catch (Exception e) {
            // 1:1 移植 vmtweak 的回退语义：读取槽位失败视为无覆写（可恢复异常），不中断机器运行，
            // 仅记录 WARN 便于排查异常的槽位数据。
            VMD$LOG.warn("Failed to read the dimension override slot, falling back to no override", e);
            dimAbbr = "None";
        }

        if (!Objects.equals(dimAbbr, vmd$lastDimOverride)) {
            vmd$lastDimOverride = dimAbbr;
            this.totalWeight = 0;
        }
        return dimAbbr;
    }

    /**
     * 保证过滤槽 {@link ItemStackHandler} 容量不小于当前矿表的矿物数；只扩不缩，
     * 避免仍引用旧下标的缓存 GUI 控件下标越界。
     */
    @Unique
    private void vmd$resizeSelected() {
        if (this.dropMap == null) return;
        GTUtility.ItemId[] ores = this.dropMap.getOres();
        if (ores == null) return;
        int oreCount = ores.length;
        if (this.selected.getSlots() < oreCount) {
            this.selected.setSize(oreCount);
        }
    }

    // endregion

    // region NBT 持久化

    @Inject(method = "saveNBTData", at = @At("HEAD"), require = 1, remap = false)
    private void vmd$onSaveNBT(NBTTagCompound aNBT, CallbackInfo ci) {
        if (ci.isCancelled()) return;
        aNBT.setString("vmd$lastDimOverride", this.vmd$lastDimOverride);
    }

    @Inject(method = "loadNBTData", at = @At("HEAD"), require = 1, remap = false)
    private void vmd$onLoadNBT(NBTTagCompound aNBT, CallbackInfo ci) {
        if (ci.isCancelled()) return;
        this.vmd$exFilter = null;
        this.vmd$lastDimOverride = aNBT.getString("vmd$lastDimOverride");
        if (this.vmd$lastDimOverride.isEmpty() && aNBT.hasKey("vmtweak$lastDimOverride")) {
            // 用户此前安装过独立版 vmtweak：新键缺失时回退读旧键，存档平滑迁移。
            this.vmd$lastDimOverride = aNBT.getString("vmtweak$lastDimOverride");
        }
        if (this.vmd$lastDimOverride.isEmpty()) {
            this.vmd$lastDimOverride = "None";
        }
    }

    // endregion

    // region 处理周期：检测槽位变化

    @Inject(method = "checkProcessing", at = @At("HEAD"), require = 1, remap = false)
    private void vmd$onCheckProcessing(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (cir.isCancelled()) return;
        if (vmd$isEx()) {
            vmd$refreshExDropMap();
            return;
        }
        String prev = vmd$lastDimOverride;
        vmd$readOverrideSlot();
        if (!Objects.equals(prev, vmd$lastDimOverride)) {
            vmd$dimChangeVersion++;
            vmd$recalculateDropMap();
        }
    }

    // endregion

    // region 产出筛选

    /** Delegates to GT's existing matcher, including its material extension, for non-EX machines. */
    @Shadow(remap = false)
    private static boolean contains(List<ItemStack> list, ItemStack stack) {
        throw new AssertionError();
    }

    /** Uses the EX key for both selected stacks and input ores without entering GT's wider matcher. */
    @Redirect(
        method = "handleOutputs",
        at = @At(
            value = "INVOKE",
            target = "Lbwcrossmod/galacticgreg/MTEVoidMinerBase;contains(Ljava/util/List;Lnet/minecraft/item/ItemStack;)Z"),
        require = 2,
        allow = 2,
        remap = false)
    private boolean vmd$matchesOutputFilter(List<ItemStack> list, ItemStack stack) {
        return vmd$isEx() ? ExVoidMinerFilter.matchesOutput(list, stack) : contains(list, stack);
    }

    /** Includes small/poor ore blocks missing from the item ore dictionary on the EX input path only. */
    @Redirect(
        method = "handleOutputs",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;"),
        require = 1,
        allow = 1,
        remap = false)
    private Stream<ItemStack> vmd$filterInputOres(Stream<ItemStack> inputs, Predicate<ItemStack> isOre) {
        return inputs.filter(vmd$isEx() ? isOre.or(ExVoidMinerFilter::isSmallOre) : isOre);
    }

    /** Captures the selection for both GT checks; MUI2 2.3.92 exposes only the slot API. */
    @Inject(method = "handleOutputs", at = @At("HEAD"), require = 1, remap = false)
    private void vmd$beginOutput(CallbackInfo ci) {
        if (vmd$isEx()) {
            vmd$batchSelections = vmd$exFilter.effectiveSelections(selected);
        } else {
            vmd$batchSelections = new ArrayList<>();
            for (int slot = 0; slot < selected.getSlots(); slot++) {
                ItemStack stack = selected.getStackInSlot(slot);
                if (stack != null) vmd$batchSelections.add(stack);
            }
        }
        vmd$batchSelectionEmpty = vmd$batchSelections.isEmpty();
    }

    @Redirect(
        method = "handleOutputs",
        at = @At(
            value = "INVOKE",
            target = "Lcom/cleanroommc/modularui/utils/item/ItemStackHandler;getStacks()Ljava/util/List;"),
        require = 1,
        remap = false)
    private List<ItemStack> vmd$effectiveOutputSelections(ItemStackHandler handler) {
        return vmd$batchSelections;
    }

    @Inject(method = "isSelectedEmpty", at = @At("HEAD"), require = 1, remap = false, cancellable = true)
    private void vmd$isOutputSelectionEmpty(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(vmd$batchSelectionEmpty);
    }

    @Inject(method = "handleOutputs", at = @At("RETURN"), require = 1, remap = false)
    private void vmd$endOutput(CallbackInfo ci) {
        vmd$batchSelections = null;
    }

    /** 非 EX 路径保留同材料的围岩和大小矿宽匹配；未匹配时仍走 GT 原版比较。 */
    @Inject(method = "contains", at = @At("HEAD"), require = 1, remap = false, cancellable = true)
    private static void vmd$onContains(List<ItemStack> list, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (cir.isCancelled()) return;
        if (vmd$matchesByMaterial(list, stack)) cir.setReturnValue(true);
    }

    /** 逐条比对列表与目标方块的矿物材料，任一条目与目标同材料即命中。 */
    @Unique
    private static boolean vmd$matchesByMaterial(List<ItemStack> list, ItemStack stack) {
        if (list == null || list.isEmpty() || stack == null) return false;
        final IOreMaterial material = OreManager.getMaterial(stack);
        if (material == null) return false;
        for (ItemStack cursor : list) {
            if (cursor == null) continue;
            if (OreManager.getMaterial(cursor) == material) return true;
        }
        return false;
    }

    // endregion

    // region 供 GUI mixin 使用的公开访问器

    @Unique
    @Override
    public void vmd$refreshExFilter() {
        if (vmd$isEx() && getBaseMetaTileEntity().isServerSide()) vmd$refreshExDropMap();
    }

    @Unique
    @Override
    public View vmd$getExFilterView() {
        if (!vmd$isEx() || !getBaseMetaTileEntity().isServerSide()) return View.EMPTY;
        vmd$refreshExDropMap();
        return vmd$exFilter.view(selected, blacklist);
    }

    @Unique
    @Override
    public void vmd$applyExFilterAction(long revision, int row, Operation operation) {
        if (!vmd$isEx() || !getBaseMetaTileEntity().isServerSide()) return;
        vmd$refreshExDropMap();
        if (!vmd$exFilter.accepts(revision, row, operation)) return;
        if (operation == Operation.WHITELIST || operation == Operation.BLACKLIST) {
            boolean nextMode = operation == Operation.BLACKLIST;
            if (blacklist == nextMode) return;
            blacklist = nextMode;
        } else {
            ItemStackHandler nextSelection = vmd$exFilter.edit(selected, revision, row, operation);
            if (nextSelection == selected) return;
            selected = nextSelection;
        }
        markDirty();
    }

    @Unique
    @Override
    public int vmd$getDimChangeVersion() {
        return vmd$dimChangeVersion;
    }

    /**
     * @return 当前警告消息（lang 键原文），无警告时为空字符串。
     */
    @Unique
    @Override
    public String vmd$getWarning() {
        return vmd$warning;
    }

    /**
     * 客户端负责本地化，避免专用服务器加载客户端类。
     *
     * @return 无覆写为空串，成功为维度缩写，警告为带 {@code !} 前缀的 lang 键
     */
    @Unique
    @Override
    public String vmd$getOverrideDisplayText() {
        if (vmd$isEx() && !vmd$warning.isEmpty()) return "!" + vmd$warning;
        if ("None".equals(vmd$lastDimOverride)) {
            return "";
        }
        if (!vmd$warning.isEmpty() && (this.dropMap == null || this.dropMap.getTotalWeight() <= 0)) {
            // "!" 前缀使客户端能区分警告与维度缩写
            return "!" + vmd$warning;
        }
        // 返回原始缩写，由客户端本地化
        return vmd$lastDimOverride;
    }

    // endregion
}
