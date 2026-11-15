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
 * Void Fluid Driller（虚空流体钻机）家族的五个阶段，仅差结构材料、电压、产出系数与并行规则；
 * 工作逻辑见 {@link MTEVoidFluidDrillerBase}。每台具体类须显式携带 {@link IMetaTileEntity.SkipGenerateName}
 * （注解不继承），显示名来自 lang 键。Naquadah/Infinity 无专用机壳，复用高级机壳替代。
 */
public class MTEVoidFluidDrillers {

    /** Iron 阶段：LV，产出系数 x16（数值加强前为 x1）。 */
    @IMetaTileEntity.SkipGenerateName
    public static class VoidFluidDrillerIron extends MTEVoidFluidDrillerBase<VoidFluidDrillerIron> {

        public VoidFluidDrillerIron(int aID, String aName, String aNameRegional) {
            super(aID, aName, aNameRegional);
        }

        public VoidFluidDrillerIron(String aName) {
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
        public int getVoltageFactor() {
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
        public IStructureDefinition<VoidFluidDrillerIron> getStructureDefinition() {
            return buildBaseStructureDefinition(VoidFluidDrillerIron.class);
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
            return new VoidFluidDrillerIron(this.mName);
        }
    }

    /** Steel 阶段：MV，产出系数 x64（数值加强前为 x4）。 */
    @IMetaTileEntity.SkipGenerateName
    public static class VoidFluidDrillerSteel extends MTEVoidFluidDrillerBase<VoidFluidDrillerSteel> {

        public VoidFluidDrillerSteel(int aID, String aName, String aNameRegional) {
            super(aID, aName, aNameRegional);
        }

        public VoidFluidDrillerSteel(String aName) {
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
        public int getVoltageFactor() {
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
        public IStructureDefinition<VoidFluidDrillerSteel> getStructureDefinition() {
            return buildBaseStructureDefinition(VoidFluidDrillerSteel.class);
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
            return new VoidFluidDrillerSteel(this.mName);
        }
    }

    /** Titanium 阶段：HV，产出系数 x256（数值加强前为 x16）。 */
    @IMetaTileEntity.SkipGenerateName
    public static class VoidFluidDrillerTitanium extends MTEVoidFluidDrillerBase<VoidFluidDrillerTitanium> {

        public VoidFluidDrillerTitanium(int aID, String aName, String aNameRegional) {
            super(aID, aName, aNameRegional);
        }

        public VoidFluidDrillerTitanium(String aName) {
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
        public int getVoltageFactor() {
            return 256;
        }

        @Override
        protected int getControllerTextureIndex() {
            return GTUtility.getCasingTextureIndex(
                getCasingBlock().getBlock(),
                getCasingBlock().get(0)
                    .getItemDamage());
        }

        @Override
        public IStructureDefinition<VoidFluidDrillerTitanium> getStructureDefinition() {
            return buildBaseStructureDefinition(VoidFluidDrillerTitanium.class);
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
            return new VoidFluidDrillerTitanium(this.mName);
        }
    }

    /** Naquadah 阶段：LuV，产出系数 x1024（数值加强前为 x64）；机壳以钨钢钻井机壳代替（无专用机壳）。 */
    @IMetaTileEntity.SkipGenerateName
    public static class VoidFluidDrillerNaquadah extends MTEVoidFluidDrillerBase<VoidFluidDrillerNaquadah> {

        public VoidFluidDrillerNaquadah(int aID, String aName, String aNameRegional) {
            super(aID, aName, aNameRegional);
        }

        public VoidFluidDrillerNaquadah(String aName) {
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
        public int getVoltageFactor() {
            return 1024;
        }

        @Override
        protected int getControllerTextureIndex() {
            return GTUtility.getCasingTextureIndex(
                getCasingBlock().getBlock(),
                getCasingBlock().get(0)
                    .getItemDamage());
        }

        @Override
        public IStructureDefinition<VoidFluidDrillerNaquadah> getStructureDefinition() {
            return buildBaseStructureDefinition(VoidFluidDrillerNaquadah.class);
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
            return new VoidFluidDrillerNaquadah(this.mName);
        }
    }

    /**
     * Infinity 阶段：UV，产出系数 x4096（数值加强前为 x256）；机壳以中子素采矿机壳代替（无专用机壳）。
     * 并行随电压提升：parallel = 8^(输入等级 - UV)（UHV=8 … MAX=262144），EU/t 多安培倍增。
     */
    @IMetaTileEntity.SkipGenerateName
    public static class VoidFluidDrillerInfinity extends MTEVoidFluidDrillerBase<VoidFluidDrillerInfinity> {

        public VoidFluidDrillerInfinity(int aID, String aName, String aNameRegional) {
            super(aID, aName, aNameRegional);
        }

        public VoidFluidDrillerInfinity(String aName) {
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
        public int getVoltageFactor() {
            return 4096;
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
        public IStructureDefinition<VoidFluidDrillerInfinity> getStructureDefinition() {
            return buildBaseStructureDefinition(VoidFluidDrillerInfinity.class);
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
            return new VoidFluidDrillerInfinity(this.mName);
        }
    }
}
