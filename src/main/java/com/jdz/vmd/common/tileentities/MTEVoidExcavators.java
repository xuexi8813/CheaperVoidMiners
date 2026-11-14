package com.jdz.vmd.common.tileentities;

import static gregtech.api.enums.VoltageIndex.HV;
import static gregtech.api.enums.VoltageIndex.LV;
import static gregtech.api.enums.VoltageIndex.LuV;
import static gregtech.api.enums.VoltageIndex.MAX;
import static gregtech.api.enums.VoltageIndex.MV;
import static gregtech.api.enums.VoltageIndex.UV;

import com.gtnewhorizon.structurelib.structure.IStructureDefinition;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;

/**
 * Void Excavator（虚空挖掘机）家族的五个阶段。每个阶段仅改变结构材料、电压等级（EU/t）、
 * 资源方块产出系数与并行规则；工作逻辑完全位于 {@link MTEVoidExcavatorBase}。
 * <p>
 * Naquadah 与 Infinity 阶段复用现有的高级机壳，因为 GT 没有提供专用的
 * Naquadah/Infinity 机器机壳（与 Void Fluid Driller 家族的替代方式相同）。
 * 命名：每台具体机器类都携带 {@link IMetaTileEntity.SkipGenerateName}，
 * 使显示名来自标准 lang 键，而不是 GT 的区域名注册。
 */
public class MTEVoidExcavators {

    /** Iron 阶段：LV 机器，每周期产出 1 个资源方块。 */
    // 跳过 GT 的区域名注册（见类 javadoc）；该注解不会被继承，
    // 因此每个具体类都要显式携带。
    @IMetaTileEntity.SkipGenerateName
    public static class VoidExcavatorIron extends MTEVoidExcavatorBase<VoidExcavatorIron> {

        public VoidExcavatorIron(int aID, String aName, String aNameRegional) {
            super(aID, aName, aNameRegional);
        }

        public VoidExcavatorIron(String aName) {
            super(aName);
        }

        @Override
        public ItemList getCasingBlock() {
            return ItemList.Casing_ULV;
        }

        @Override
        public Materials getFrameMaterial() {
            return Materials.Iron;
        }

        @Override
        protected int getMinTier() {
            return LV;
        }

        @Override
        public int getStageMultiplier() {
            return 1;
        }

        @Override
        protected int getControllerTextureIndex() {
            return GTUtility.getCasingTextureIndex(
                getCasingBlock().getBlock(),
                getCasingBlock().get(0)
                    .getItemDamage());
        }

        @Override
        public IStructureDefinition<VoidExcavatorIron> getStructureDefinition() {
            return buildBaseStructureDefinition(VoidExcavatorIron.class);
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
            return new VoidExcavatorIron(this.mName);
        }
    }

    /** Steel 阶段：MV 机器，每周期产出 4 个资源方块。 */
    // 跳过 GT 的区域名注册（见类 javadoc）；该注解不会被继承，
    // 因此每个具体类都要显式携带。
    @IMetaTileEntity.SkipGenerateName
    public static class VoidExcavatorSteel extends MTEVoidExcavatorBase<VoidExcavatorSteel> {

        public VoidExcavatorSteel(int aID, String aName, String aNameRegional) {
            super(aID, aName, aNameRegional);
        }

        public VoidExcavatorSteel(String aName) {
            super(aName);
        }

        @Override
        public ItemList getCasingBlock() {
            return ItemList.Casing_SolidSteel;
        }

        @Override
        public Materials getFrameMaterial() {
            return Materials.Steel;
        }

        @Override
        protected int getMinTier() {
            return MV;
        }

        @Override
        public int getStageMultiplier() {
            return 4;
        }

        @Override
        protected int getControllerTextureIndex() {
            return GTUtility.getCasingTextureIndex(
                getCasingBlock().getBlock(),
                getCasingBlock().get(0)
                    .getItemDamage());
        }

        @Override
        public IStructureDefinition<VoidExcavatorSteel> getStructureDefinition() {
            return buildBaseStructureDefinition(VoidExcavatorSteel.class);
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
            return new VoidExcavatorSteel(this.mName);
        }
    }

    /** Titanium 阶段：HV 机器，每周期产出 16 个资源方块。 */
    // 跳过 GT 的区域名注册（见类 javadoc）；该注解不会被继承，
    // 因此每个具体类都要显式携带。
    @IMetaTileEntity.SkipGenerateName
    public static class VoidExcavatorTitanium extends MTEVoidExcavatorBase<VoidExcavatorTitanium> {

        public VoidExcavatorTitanium(int aID, String aName, String aNameRegional) {
            super(aID, aName, aNameRegional);
        }

        public VoidExcavatorTitanium(String aName) {
            super(aName);
        }

        @Override
        public ItemList getCasingBlock() {
            return ItemList.Casing_StableTitanium;
        }

        @Override
        public Materials getFrameMaterial() {
            return Materials.Titanium;
        }

        @Override
        protected int getMinTier() {
            return HV;
        }

        @Override
        public int getStageMultiplier() {
            return 16;
        }

        @Override
        protected int getControllerTextureIndex() {
            return GTUtility.getCasingTextureIndex(
                getCasingBlock().getBlock(),
                getCasingBlock().get(0)
                    .getItemDamage());
        }

        @Override
        public IStructureDefinition<VoidExcavatorTitanium> getStructureDefinition() {
            return buildBaseStructureDefinition(VoidExcavatorTitanium.class);
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
            return new VoidExcavatorTitanium(this.mName);
        }
    }

    /**
     * Naquadah 阶段：LuV 机器，每周期产出 64 个资源方块。因不存在 Naquadah 机器机壳，
     * 以钨钢钻井机壳代替；框架保持为 Naquadah。
     */
    // 跳过 GT 的区域名注册（见类 javadoc）；该注解不会被继承，
    // 因此每个具体类都要显式携带。
    @IMetaTileEntity.SkipGenerateName
    public static class VoidExcavatorNaquadah extends MTEVoidExcavatorBase<VoidExcavatorNaquadah> {

        public VoidExcavatorNaquadah(int aID, String aName, String aNameRegional) {
            super(aID, aName, aNameRegional);
        }

        public VoidExcavatorNaquadah(String aName) {
            super(aName);
        }

        @Override
        public ItemList getCasingBlock() {
            return ItemList.Casing_RobustTungstenSteel;
        }

        @Override
        public Materials getFrameMaterial() {
            return Materials.Naquadah;
        }

        @Override
        protected int getMinTier() {
            return LuV;
        }

        @Override
        public int getStageMultiplier() {
            return 64;
        }

        @Override
        protected int getControllerTextureIndex() {
            return GTUtility.getCasingTextureIndex(
                getCasingBlock().getBlock(),
                getCasingBlock().get(0)
                    .getItemDamage());
        }

        @Override
        public IStructureDefinition<VoidExcavatorNaquadah> getStructureDefinition() {
            return buildBaseStructureDefinition(VoidExcavatorNaquadah.class);
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
            return new VoidExcavatorNaquadah(this.mName);
        }
    }

    /**
     * Infinity 阶段：UV 机器，每周期产出 256 个资源方块。因不存在 Infinity 机器机壳，
     * 以中子素采矿机壳代替；框架保持为 Infinity。
     * <p>
     * 与其他阶段不同，本机器的并行数随所供电压提升：
     * parallel = 8^(输入等级 - UV)，即 UHV 一次运行 8 个周期，UEV 64 个……MAX 等级一次运行 262144 个周期，
     * EU/t 消耗相应倍增（多安培抽取）。
     */
    // 跳过 GT 的区域名注册（见类 javadoc）；该注解不会被继承，
    // 因此每个具体类都要显式携带。
    @IMetaTileEntity.SkipGenerateName
    public static class VoidExcavatorInfinity extends MTEVoidExcavatorBase<VoidExcavatorInfinity> {

        public VoidExcavatorInfinity(int aID, String aName, String aNameRegional) {
            super(aID, aName, aNameRegional);
        }

        public VoidExcavatorInfinity(String aName) {
            super(aName);
        }

        @Override
        public ItemList getCasingBlock() {
            return ItemList.Casing_MiningNeutronium;
        }

        @Override
        public Materials getFrameMaterial() {
            return Materials.Infinity;
        }

        @Override
        protected int getMinTier() {
            return UV;
        }

        @Override
        public int getStageMultiplier() {
            return 256;
        }

        @Override
        protected int calculateParallel(int inputTier) {
            // 8^(等级 - UV)，截断到 GT 等级阶梯（MAX 以上的等级属于错误等级）。
            // GTUtility.powInt 返回 double；对每个可达的指数，结果都是精确的。
            final int exponent = Math.min(inputTier, MAX) - UV;
            return (int) GTUtility.powInt(8, exponent);
        }

        @Override
        protected int getControllerTextureIndex() {
            return GTUtility.getCasingTextureIndex(
                getCasingBlock().getBlock(),
                getCasingBlock().get(0)
                    .getItemDamage());
        }

        @Override
        public IStructureDefinition<VoidExcavatorInfinity> getStructureDefinition() {
            return buildBaseStructureDefinition(VoidExcavatorInfinity.class);
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
            return new VoidExcavatorInfinity(this.mName);
        }
    }
}
