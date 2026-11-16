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
 * Void Fluid Driller（虚空流体钻机）家族的五个阶段，仅差结构材料、机器档位与产量公式中的机器档项
 * （16^(机器档位)，见基类 {@link MTEVoidFluidDrillerBase#computeOutputAmount}）；电压项由能源仓实际
 * GT 输入电压档驱动（无尽钻机电压基数换 16），为五档共用逻辑。工作逻辑见
 * {@link MTEVoidFluidDrillerBase}。每台具体类须显式携带 {@link IMetaTileEntity.SkipGenerateName}
 * （注解不继承），显示名来自 lang 键。Naquadah/Infinity 无专用机壳，复用高级机壳替代。
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
            // ULV 机壳贴图 id 为 0，被 casingIndex>0 契约保留为"未设置"（投影仪构建即抛
            // IllegalArgumentException），改用相邻 LV 机壳贴图；仅影响投影全息提示渲染，
            // 结构校验按方块+meta 完全不受影响。
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

    /** Naquadah 阶段：LuV 机器档，机器档项 16^3=4096；EU/t 32768。机壳以钨钢钻井机壳代替（无专用机壳）。 */
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
