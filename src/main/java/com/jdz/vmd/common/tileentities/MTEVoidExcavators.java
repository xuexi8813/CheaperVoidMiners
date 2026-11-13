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
 * The five stages of the Void Excavator family. Each stage only changes the structure materials, the
 * voltage tier (EU/t), the resource block output factor and the parallel rule; the working logic lives
 * entirely in {@link MTEVoidExcavatorBase}.
 * <p>
 * The Naquadah and Infinity stages reuse existing high tier casings because GT does not ship dedicated
 * Naquadah/Infinity machine casings (same substitution as the Void Fluid Driller family).
 * Naming: every concrete machine class carries {@link IMetaTileEntity.SkipGenerateName} so the
 * display name comes from the standard lang keys instead of the GT regional-name registration.
 */
public class MTEVoidExcavators {

    /** Iron stage: LV machine, 1 resource block per cycle. */
    // Skips the GT regional-name registration (see the class javadoc); the annotation is not inherited,
    // so every concrete class carries it explicitly.
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

    /** Steel stage: MV machine, 4 resource blocks per cycle. */
    // Skips the GT regional-name registration (see the class javadoc); the annotation is not inherited,
    // so every concrete class carries it explicitly.
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

    /** Titanium stage: HV machine, 16 resource blocks per cycle. */
    // Skips the GT regional-name registration (see the class javadoc); the annotation is not inherited,
    // so every concrete class carries it explicitly.
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
     * Naquadah stage: LuV machine, 64 resource blocks per cycle. Uses the tungstensteel drilling casing as a
     * stand-in because no Naquadah machine casing exists; the frame stays Naquadah.
     */
    // Skips the GT regional-name registration (see the class javadoc); the annotation is not inherited,
    // so every concrete class carries it explicitly.
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
     * Infinity stage: UV machine, 256 resource blocks per cycle. Uses the neutronium mining casing as a
     * stand-in because no Infinity machine casing exists; the frame stays Infinity.
     * <p>
     * Unlike the other stages this machine scales its parallel count with the supplied voltage:
     * parallel = 8^(input tier - UV), so UHV runs 8 cycles, UEV 64, ... and MAX tier 262144 cycles at once,
     * with the EU/t draw multiplied accordingly (multi-amp drain).
     */
    // Skips the GT regional-name registration (see the class javadoc); the annotation is not inherited,
    // so every concrete class carries it explicitly.
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
            // 8^(tier - UV), clamped to the GT tier ladder (tiers above MAX are error tiers).
            // GTUtility.powInt returns double; the result is exact for every reachable exponent.
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
