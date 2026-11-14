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
 * Void Excavator（虚空挖掘机）家族的五个阶段，仅差结构材料、电压、产出系数与并行规则；
 * 工作逻辑见 {@link MTEVoidExcavatorBase}。每台具体类须显式携带 {@link IMetaTileEntity.SkipGenerateName}
 * （注解不继承），显示名来自 lang 键。Naquadah/Infinity 无专用机壳，复用高级机壳替代。
 */
public class MTEVoidExcavators {

    /** Iron 阶段：LV，每周期产出 1 个资源方块。 */
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

    /** Steel 阶段：MV，每周期产出 4 个资源方块。 */
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

    /** Titanium 阶段：HV，每周期产出 16 个资源方块。 */
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

    /** Naquadah 阶段：LuV，每周期产出 64 个资源方块；机壳以钨钢钻井机壳代替（无专用机壳）。 */
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
     * Infinity 阶段：UV，每周期产出 256 个资源方块；机壳以中子素采矿机壳代替（无专用机壳）。
     * 并行随电压提升：parallel = 8^(输入等级 - UV)（UHV=8 … MAX=262144），EU/t 多安培倍增。
     */
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
            // 8^(等级 - UV)，截断到 MAX；GTUtility.powInt 对每个可达指数结果精确。
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
