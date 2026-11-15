package com.jdz.vmd.common.tileentities;

import com.gtnewhorizon.structurelib.structure.IStructureDefinition;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.VoltageIndex;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;

/**
 * 三个 EX 虚空采矿机阶段（EX I/II/III），构建在 GT 虚空采矿机多方块（{@link MTEVoidMinerEXBase}）之上。
 * 构造传入的 TIER_MULTIPLIER（4/8/16）保持上游档位语义；实际产出在该值之上于输出端统一 ×16 加强
 * （等效 32×TIER_MULTIPLIER ores/s，EX1/2/3 = 128/256/512），见 {@link MTEVoidMinerEXBase#addOutputPartial}。
 * 每个具体类须显式携带 {@link IMetaTileEntity.SkipGenerateName}（注解不继承），显示名来自 lang 键。
 */
public class MTEVoidMiners {

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
            return VoltageIndex.LV;
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
