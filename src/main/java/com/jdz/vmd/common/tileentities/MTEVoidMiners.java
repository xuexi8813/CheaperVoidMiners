package com.jdz.vmd.common.tileentities;

import com.gtnewhorizon.structurelib.structure.IStructureDefinition;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.VoltageIndex;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;

/**
 * 三个 EX 虚空采矿机阶段（EX I/II/III），构建在 GT 虚空采矿机多方块结构之上。
 * <p>
 * 命名：每个具体机器类都带有 {@link IMetaTileEntity.SkipGenerateName}，使显示名称来自标准 lang 键，
 * 而不是 GT 的区域名称注册（该注解不会被继承，因此每个具体类都显式携带）。
 */
public class MTEVoidMiners {

    // 跳过 GT 的区域名称注册（参见类 javadoc）；该注解不会被继承，
    // 因此每个具体类都显式携带。
    @IMetaTileEntity.SkipGenerateName
    public static class VMLV extends MTEVoidMinerEXBase<VMLV> {

        public VMLV(int aID, String aName, String aNameRegional) {
            super(aID, aName, aNameRegional, 4);
        }

        public VMLV(String aName, int tier) {
            super(aName, tier);
        }

        @Override
        public ItemList getItemList() {
            return ItemList.Casing_SolidSteel;
        }

        @Override
        public Materials getMaterials() {
            return Materials.Steel;
        }

        @Override
        protected int getMinTier() {
            return VoltageIndex.LV; // LV 电压等级
        }

        @Override
        protected int getControllerTextureIndex() {
            return GTUtility.getCasingTextureIndex(getItemList().getBlock(), 0);
        }

        @Override
        public IStructureDefinition<VMLV> getStructureDefinition() {
            return buildBaseStructureDefinition(VMLV.class);
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
            return new VMLV(this.mName, this.TIER_MULTIPLIER);
        }

    }

    // 跳过 GT 的区域名称注册（参见类 javadoc）；该注解不会被继承，
    // 因此每个具体类都显式携带。
    @IMetaTileEntity.SkipGenerateName
    public static class VMHV extends MTEVoidMinerEXBase<VMHV> {

        public VMHV(int aID, String aName, String aNameRegional) {
            super(aID, aName, aNameRegional, 8);
        }

        public VMHV(String aName, int tier) {
            super(aName, tier);
        }

        @Override
        public ItemList getItemList() {
            return ItemList.Casing_CleanStainlessSteel;
        }

        @Override
        public Materials getMaterials() {
            return Materials.StainlessSteel;
        }

        @Override
        protected int getMinTier() {
            return VoltageIndex.HV;
        }

        @Override
        public IStructureDefinition<VMHV> getStructureDefinition() {
            return buildBaseStructureDefinition(VMHV.class);
        }

        @Override
        protected int getControllerTextureIndex() {
            return GTUtility.getCasingTextureIndex(getItemList().getBlock(), 1);
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
            return new VMHV(this.mName, this.TIER_MULTIPLIER);
        }

    }

    // 跳过 GT 的区域名称注册（参见类 javadoc）；该注解不会被继承，
    // 因此每个具体类都显式携带。
    @IMetaTileEntity.SkipGenerateName
    public static class VMIV extends MTEVoidMinerEXBase<VMIV> {

        public VMIV(int aID, String aName, String aNameRegional) {
            super(aID, aName, aNameRegional, 16);
        }

        public VMIV(String aName, int tier) {
            super(aName, tier);
        }

        @Override
        public ItemList getItemList() {
            return ItemList.Casing_RobustTungstenSteel;
        }

        @Override
        public Materials getMaterials() {
            return Materials.TungstenSteel;
        }

        @Override
        protected int getMinTier() {
            return VoltageIndex.IV;
        }

        @Override
        public IStructureDefinition<VMIV> getStructureDefinition() {
            return buildBaseStructureDefinition(VMIV.class);
        }

        @Override
        protected int getControllerTextureIndex() {
            return GTUtility.getCasingTextureIndex(getItemList().getBlock(), 0);
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
            return new VMIV(this.mName, this.TIER_MULTIPLIER);
        }
    }
}
