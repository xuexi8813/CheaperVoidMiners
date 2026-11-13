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
 * The five stages of the Void Fluid Driller family. Each stage only changes the structure materials, the
 * voltage tier (EU/t), the output factor and the parallel rule; the working logic lives entirely in
 * {@link MTEVoidFluidDrillerBase}.
 * <p>
 * The Naquadah and Infinity stages reuse existing high tier casings because GT does not ship dedicated
 * Naquadah/Infinity machine casings (see the module report for the substitution rationale).
 * Naming: every concrete machine class carries {@link IMetaTileEntity.SkipGenerateName} so the
 * display name comes from the standard lang keys instead of the GT regional-name registration.
 */
public class MTEVoidFluidDrillers {

    /** Iron stage: LV machine, x1 output factor. */
    // Skips the GT regional-name registration (see the class javadoc); the annotation is not inherited,
    // so every concrete class carries it explicitly.
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
        public IStructureDefinition<VoidFluidDrillerIron> getStructureDefinition() {
            return buildBaseStructureDefinition(VoidFluidDrillerIron.class);
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
            return new VoidFluidDrillerIron(this.mName);
        }
    }

    /** Steel stage: MV machine, x4 output factor. */
    // Skips the GT regional-name registration (see the class javadoc); the annotation is not inherited,
    // so every concrete class carries it explicitly.
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
        public IStructureDefinition<VoidFluidDrillerSteel> getStructureDefinition() {
            return buildBaseStructureDefinition(VoidFluidDrillerSteel.class);
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
            return new VoidFluidDrillerSteel(this.mName);
        }
    }

    /** Titanium stage: HV machine, x16 output factor. */
    // Skips the GT regional-name registration (see the class javadoc); the annotation is not inherited,
    // so every concrete class carries it explicitly.
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
        public IStructureDefinition<VoidFluidDrillerTitanium> getStructureDefinition() {
            return buildBaseStructureDefinition(VoidFluidDrillerTitanium.class);
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
            return new VoidFluidDrillerTitanium(this.mName);
        }
    }

    /**
     * Naquadah stage: LuV machine, x64 output factor. Uses the tungstensteel drilling casing as a stand-in
     * because no Naquadah machine casing exists; the frame stays Naquadah.
     */
    // Skips the GT regional-name registration (see the class javadoc); the annotation is not inherited,
    // so every concrete class carries it explicitly.
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
        public IStructureDefinition<VoidFluidDrillerNaquadah> getStructureDefinition() {
            return buildBaseStructureDefinition(VoidFluidDrillerNaquadah.class);
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
            return new VoidFluidDrillerNaquadah(this.mName);
        }
    }

    /**
     * Infinity stage: UV machine, x256 output factor. Uses the neutronium mining casing as a stand-in because
     * no Infinity machine casing exists; the frame stays Infinity.
     * <p>
     * Unlike the other stages this machine scales its parallel count with the supplied voltage:
     * parallel = 8^(input tier - UV), so UHV runs 8 cycles, UEV 64, ... and MAX tier 262144 cycles at once,
     * with the EU/t draw multiplied accordingly (multi-amp drain).
     */
    // Skips the GT regional-name registration (see the class javadoc); the annotation is not inherited,
    // so every concrete class carries it explicitly.
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
        public IStructureDefinition<VoidFluidDrillerInfinity> getStructureDefinition() {
            return buildBaseStructureDefinition(VoidFluidDrillerInfinity.class);
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
            return new VoidFluidDrillerInfinity(this.mName);
        }
    }
}
