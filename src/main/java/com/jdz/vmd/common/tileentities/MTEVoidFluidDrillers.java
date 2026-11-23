package com.jdz.vmd.common.tileentities;

import static gregtech.api.enums.VoltageIndex.HV;
import static gregtech.api.enums.VoltageIndex.LV;
import static gregtech.api.enums.VoltageIndex.LuV;
import static gregtech.api.enums.VoltageIndex.MV;
import static gregtech.api.enums.VoltageIndex.UV;

import gregtech.api.casing.Casings;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 虚空流体钻机的五档机型。结构材料与机器档位由各机型指定，产量由 {@link MTEVoidFluidDrillerBase} 计算。
 * {@link IMetaTileEntity.SkipGenerateName} 不继承，各机型须显式标注才能使用 lang 显示名。
 */
public class MTEVoidFluidDrillers {

    /** Iron 阶段：LV 机器档，机器档项 16^0=1；EU/t 32。 */
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
        protected int getControllerTextureIndex() {
            // 投影仪要求 casingIndex > 0，ULV 贴图 ID 为 0，故提示用 LV 贴图；结构仍按 ULV 校验。
            return Casings.LVMachineCasing.getTextureId();
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
            return new VoidFluidDrillerIron(this.mName);
        }
    }

    /** Steel 阶段：MV 机器档，机器档项 16^1=16；EU/t 128。 */
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
        protected int getControllerTextureIndex() {
            return Casings.SolidSteelMachineCasing.getTextureId();
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
            return new VoidFluidDrillerSteel(this.mName);
        }
    }

    /** Titanium 阶段：HV 机器档，机器档项 16^2=256；EU/t 512。 */
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
        protected int getControllerTextureIndex() {
            return Casings.StableTitaniumMachineCasing.getTextureId();
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
            return new VoidFluidDrillerTitanium(this.mName);
        }
    }

    /** Naquadah 阶段：LuV 机器档，机器档项 16^3=4096；EU/t 32768。机壳以坚固钨钢机器机壳代替（无专用机壳）。 */
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
        protected int getControllerTextureIndex() {
            return Casings.RobustTungstenSteelMachineCasing.getTextureId();
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
            return new VoidFluidDrillerNaquadah(this.mName);
        }
    }

    /** Infinity 阶段：UV 机器档，机器档项 16^4=65536，电压项基数换 16（见基类产量公式）；EU/t 524288。机壳以中子素采矿机壳代替（无专用机壳）。 */
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
        protected int getControllerTextureIndex() {
            return Casings.MiningNeutroniumCasing.getTextureId();
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
            return new VoidFluidDrillerInfinity(this.mName);
        }
    }
}
