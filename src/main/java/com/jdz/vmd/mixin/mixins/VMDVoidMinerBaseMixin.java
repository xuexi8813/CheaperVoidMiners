package com.jdz.vmd.mixin.mixins;

import java.util.Objects;
import java.util.Optional;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.jdz.vmd.api.IVoidMinerOverride;

import bwcrossmod.galacticgreg.MTEVoidMinerBase;
import bwcrossmod.galacticgreg.VoidMinerUtility;
import galacticgreg.api.ModDimensionDef;
import galacticgreg.api.enums.DimensionDef;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTUtility;
import gtneioreplugin.plugin.item.ItemDimensionDisplay;
import gtneioreplugin.util.DimensionHelper;

/**
 * {@link MTEVoidMinerBase} 的维度覆写 mixin：控制器槽位放入 GTNEI 维度显示方块后，
 * 虚空矿机改挖目标维度的矿表（含本无矿表的私人/个人维度）。逻辑 1:1 移植自
 * Void-Miner-Tweak-Mod（reobf.vmtweak），前缀与键名统一改为 vmd 命名空间。
 *
 * <p>
 * 与独立版 vmtweak 同时在场时由 {@code com.jdz.vmd.mixin.VmdMixinPlugin} 让位；
 * 各注入器内的 {@code isCancelled} 检查是第二道防线：vmtweak 先注入并 cancel 时，
 * 本 mixin 静默跳过，不会重复执行。
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

    // endregion

    // region 维度覆写的独有状态

    /** 覆写失败时在 GUI 显示的警告消息（lang 键原文）。 */
    @Unique
    private String vmd$warning = "";

    /** 上一次覆写的维度缩写（如 "Ow"、"Ne"）；"None" 表示无覆写。 */
    @Unique
    private String vmd$lastDimOverride = "None";

    /** 维度覆写每次变化时自增的单调计数器，供 GUI 检测变化刷新过滤面板。 */
    @Unique
    private int vmd$dimChangeVersion = 0;

    // endregion

    protected VMDVoidMinerBaseMixin(String aName) {
        super(aName);
    }

    // region calculateDropMap 覆写

    /**
     * 替换原 onFirstTick 中 calculateDropMap 之后的逻辑：原码
     * {@code int size = dropMap.getOres().length} 在 getOres() 为 null
     * （私人维度的空 DropMap）时 NPE，此处改为安全重算。
     */
    @Inject(method = "onFirstTick", at = @At("HEAD"), require = 1, remap = false, cancellable = true)
    private void vmd$onFirstTick(IGregTechTileEntity aBaseMetaTileEntity, CallbackInfo ci) {
        if (ci.isCancelled()) return;
        ci.cancel();
        // 复刻 super.onFirstTick 行为
        super.onFirstTick(aBaseMetaTileEntity);
        // 以维度覆写逻辑重算矿表
        vmd$recalculateDropMap();
        // getOres() 空值安全处理
        vmd$resizeSelected();
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
        String prev = vmd$lastDimOverride;
        vmd$readOverrideSlot();
        if (!Objects.equals(prev, vmd$lastDimOverride)) {
            vmd$dimChangeVersion++;
            vmd$recalculateDropMap();
        }
    }

    // endregion

    // region 供 GUI mixin 使用的公开访问器

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
     * @return 发送给客户端的覆写显示原始数据，不本地化（本地化在客户端控件中完成，
     *         避免服务端线程加载 I18n 等客户端专属类）："" 表示无覆写；覆写成功返回
     *         维度缩写（如 "Ow"、"Ne"、"EA"）；覆写失败返回 "!" 前缀的警告 lang 键。
     */
    @Unique
    @Override
    public String vmd$getOverrideDisplayText() {
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
