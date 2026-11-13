package com.jdz.vmd.common.tileentities;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_OIL_DRILL;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_OIL_DRILL_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_OIL_DRILL_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_OIL_DRILL_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.getCasingTextureForId;
import static gregtech.api.objects.XSTR.XSTR_INSTANCE;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofFrame;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.jdz.vmd.api.recipe.VmdRecipeMaps;

import gregtech.GTMod;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.objects.GTUODimension;
import gregtech.api.objects.GTUOFluid;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gtneioreplugin.plugin.item.ItemDimensionDisplay;
import gtneioreplugin.util.DimensionHelper;

/**
 * Base of the Void Fluid Drillers: multiblock controllers that extract underground fluids of any configured
 * dimension without drilling pipes or depleting chunk veins.
 * <p>
 * Motivation: the vanilla {@link gregtech.common.tileentities.machines.multi.MTEOilDrillBase} drains a finite
 * per-chunk vein, this family mines the same data source "from the void" for a flat EU cost, gated by machine
 * stage (casing/frame/drill head material) instead of chunk depletion.
 * <p>
 * The structure shape is a 1:1 replica of {@link gregtech.common.tileentities.machines.multi.MTEDrillerBase}
 * (3x3x7, frame shaft below the controller, three plus-shaped frame/casing layers, 3x3 casing top with the
 * controller at the front bottom center). Planet selection, fluid selection and drill handling are documented
 * in {@link #createTooltip()}.
 *
 * @param <T> self type for the generic multiblock base
 */
@IMetaTileEntity.SkipGenerateDescription
public abstract class MTEVoidFluidDrillerBase<T extends MTEVoidFluidDrillerBase<T>> extends MTEEnhancedMultiBlockBase<T>
    implements ISurvivalConstructable {

    /** Length of one production cycle in ticks. */
    public static final int CYCLE_TICKS = 100;
    /** Base fluid output per cycle before voltage factor, drill bonus and parallelism, in mB. */
    public static final int BASE_OUTPUT_MB = 1000;
    /** Chance that one drill head is consumed at the end of a completed cycle. */
    public static final double DRILL_CONSUMPTION_CHANCE = 0.001;

    protected static final String STRUCTURE_PIECE_MAIN = "main";

    /** A drill head tier: which material prefix stack gives which output multiplier. */
    protected static final class DrillTier {

        public final Materials material;
        public final double bonus;

        DrillTier(Materials material, double bonus) {
            this.material = material;
            this.bonus = bonus;
        }
    }

    /**
     * Drill head ladder shared by every stage of the family; all five stacks are valid in all five machines.
     * Order matters only for display, selection picks the highest bonus present in the input bus.
     */
    private static final ImmutableList<DrillTier> DRILL_TIERS = ImmutableList.of(
        new DrillTier(Materials.Iron, 1.25),
        new DrillTier(Materials.Steel, 1.5),
        new DrillTier(Materials.Titanium, 1.75),
        new DrillTier(Materials.Naquadah, 2.0),
        new DrillTier(Materials.Infinity, 3.0));

    /** Resolved best drill head for the current cycle. */
    private static final class DrillSelection {

        public final ItemStack stack;
        public final double bonus;
        public final String materialName;

        DrillSelection(ItemStack stack, double bonus, String materialName) {
            this.stack = stack;
            this.bonus = bonus;
            this.materialName = materialName;
        }
    }

    // Server-side per-cycle state, recomputed in checkProcessing().
    private int cachedParallel = 1;
    private double cachedDrillBonus = 1.0;
    @Nullable
    private Fluid cachedFluid = null;
    private boolean selectedByCircuit = false;
    @Nullable
    private String selectedDimensionAbbr = null;

    // GUI display state, synced through FakeSyncWidgets in drawTexts().
    private String displayDimensionAbbr = "";
    private String displayFluidKey = "";
    private boolean displayFluidSelectedByCircuit = false;
    private String displayDrillMaterial = "";
    private int displayParallel = 1;

    protected int casingTextureIndex;

    public MTEVoidFluidDrillerBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        casingTextureIndex = getControllerTextureIndex();
    }

    public MTEVoidFluidDrillerBase(String aName) {
        super(aName);
        casingTextureIndex = getControllerTextureIndex();
    }

    /**************************************************************************
     * Stage configuration, implemented by each of the five concrete machines.
     *************************************************************************/

    /** Machine casing block of this stage, used for the casing slots of the structure. */
    public abstract ItemList getCasingBlock();

    /** Frame material of this stage, used for the frameGt slots of the structure. */
    public abstract Materials getFrameMaterial();

    /** Lowest allowed energy hatch tier; also sets the EU/t draw and the output voltage factor step. */
    protected abstract int getMinTier();

    /**
     * Voltage factor of this stage, multiplied onto the base output. Follows the stage ladder
     * (LV=1, MV=4, HV=16, LuV=64, UV=256), kept as a constant so stages stay independently tunable.
     */
    public abstract int getVoltageFactor();

    /** Texture index used for the controller front and as base casing texture of the hatch slots. */
    protected abstract int getControllerTextureIndex();

    /**
     * Parallel count for a given input voltage tier. Defaults to 1; the Infinity stage overrides this with an
     * exponential rule (see {@code MTEVoidFluidDrillers.VoidFluidDrillerInf}).
     */
    protected int calculateParallel(int inputTier) {
        return 1;
    }

    /**************************************************************************
     * Structure: 1:1 replica of MTEDrillerBase with stage-specific casing/frame.
     *************************************************************************/

    protected IStructureDefinition<T> buildBaseStructureDefinition(Class<T> clazz) {
        return StructureDefinition.<T>builder()
            .addShape(
                STRUCTURE_PIECE_MAIN,
                transpose(
                    new String[][] { { "   ", " f ", "   " }, { "   ", " f ", "   " }, { "   ", " f ", "   " },
                        { " f ", "fcf", " f " }, { " f ", "fcf", " f " }, { " f ", "fcf", " f " },
                        { "b~b", "bbb", "bbb" } }))
            .addElement('f', ofFrame(getFrameMaterial()))
            .addElement(
                'c',
                ofBlock(
                    getCasingBlock().getBlock(),
                    getCasingBlock().get(0)
                        .getItemDamage()))
            .addElement(
                'b',
                buildHatchAdder(clazz).atLeastList(getAllowedHatches())
                    .hint(1)
                    .casingIndex(casingTextureIndex)
                    .buildAndChain(
                        getCasingBlock().getBlock(),
                        getCasingBlock().get(0)
                            .getItemDamage()))
            .build();
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, 1, 6, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(STRUCTURE_PIECE_MAIN, stackSize, 1, 6, 0, elementBudget, env, false, true);
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        if (!checkPiece(STRUCTURE_PIECE_MAIN, 1, 6, 0, errors)) return;
        checkHatches(errors);
        if (GTUtility.getTier(getMaxInputVoltage()) < getMinTier()) {
            errors.add(StructureErrors.energyHatchTierTooLow(getMinTier()));
        }
    }

    /** Hatch requirements: energy + maintenance are mandatory, output hatch (fluids) and input bus are mandatory. */
    protected void checkHatches(List<StructureError> errors) {
        checkHasEnergyHatch(errors);
        checkOneMaintenanceHatch(errors);
        checkHasOutputHatch(errors);
        checkHasInputBus(errors);
    }

    /** Hatch elements accepted in the 'b' slots; consumed by the structure definition builder. */
    protected List<IHatchElement<? super T>> getAllowedHatches() {
        return ImmutableList.of(InputHatch, OutputHatch, InputBus, OutputBus, Maintenance, Energy);
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        // Drills cannot be rotated and must not face up or down, matching MTEDrillerBase.
        return (d, r, f) -> (d.flag & (ForgeDirection.UP.flag | ForgeDirection.DOWN.flag)) == 0 && r.isNotRotated()
            && !f.isVerticallyFliped();
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        if (side == aFacing) {
            if (aActive) return new ITexture[] { getCasingTextureForId(casingTextureIndex), TextureFactory.builder()
                .addIcon(OVERLAY_FRONT_OIL_DRILL_ACTIVE)
                .extFacing()
                .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_OIL_DRILL_ACTIVE_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
            return new ITexture[] { getCasingTextureForId(casingTextureIndex), TextureFactory.builder()
                .addIcon(OVERLAY_FRONT_OIL_DRILL)
                .extFacing()
                .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_OIL_DRILL_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
        }
        return new ITexture[] { getCasingTextureForId(casingTextureIndex) };
    }

    /**************************************************************************
     * Recipe logic.
     *************************************************************************/

    @Override
    public @NotNull CheckRecipeResult checkProcessing() {
        final int inputTier = GTUtility.getTier(getMaxInputVoltage());
        cachedParallel = calculateParallel(inputTier);

        // 1. Resolve the target dimension and its extractable fluids.
        final GTUODimension dimension = resolveTargetDimension();
        final List<GTUOFluid> candidates = VoidFluidTableHelper.getFluidCandidates(dimension);
        if (candidates.isEmpty()) {
            mEUt = 0;
            mMaxProgresstime = 0;
            stopMachine(ShutDownReasonRegistry.NONE);
            return SimpleCheckRecipeResult.ofFailurePersistOnShutdown("vmd_no_fluid_data");
        }

        // 2. Resolve the fluid: circuit config 1..N picks the N-th entry, anything else is weighted random.
        final int circuitConfig = readCircuitConfig();
        final GTUOFluid selectedFluid;
        if (circuitConfig >= 1) {
            if (circuitConfig > candidates.size()) {
                mEUt = 0;
                mMaxProgresstime = 0;
                return SimpleCheckRecipeResult.ofFailure("vmd_circuit_out_of_range");
            }
            selectedFluid = candidates.get(circuitConfig - 1);
            selectedByCircuit = true;
        } else {
            selectedFluid = VoidFluidTableHelper.weightedRandom(candidates);
            selectedByCircuit = false;
        }
        final Fluid fluid = selectedFluid.getFluid();
        cachedFluid = fluid;

        // 3. Resolve the best drill head present in the input bus (optional, bonus 1.0 when absent).
        final DrillSelection drill = findBestDrill();
        cachedDrillBonus = drill == null ? 1.0 : drill.bonus;

        // 4. Compute the output and check output space when void protection is enabled.
        final long outputAmount = computeOutputAmount(cachedParallel, cachedDrillBonus);
        final FluidStack output = new FluidStack(fluid, (int) Math.min(outputAmount, Integer.MAX_VALUE));
        if (protectsExcessFluid() && !canOutputAll(new FluidStack[] { output })) {
            mEUt = 0;
            mMaxProgresstime = 0;
            return SimpleCheckRecipeResult.ofFailure("vmd_output_full");
        }

        // 5. Apply the cycle stats: one flat voltage of the stage per parallel, 100 tick cycles.
        // GT 2.9 multiblocks keep mEUt as an int, so the multi-amp draw saturates at Integer.MAX_VALUE
        // (reached beyond UIV tier on the Infinity stage); output and parallel count stay unclamped.
        mOutputFluids = new FluidStack[] { output };
        final long totalEUt = GTValues.V[getMinTier()] * (long) cachedParallel;
        mEUt = (int) -Math.min(totalEUt, Integer.MAX_VALUE);
        mMaxProgresstime = CYCLE_TICKS;
        mEfficiency = getCurrentEfficiency(null);
        mEfficiencyIncrease = 10000;

        // 6. Update the GUI display state.
        selectedDimensionAbbr = resolveDisplayedAbbr(dimension);
        displayDimensionAbbr = selectedDimensionAbbr == null ? "" : selectedDimensionAbbr;
        displayFluidKey = fluid.getUnlocalizedName();
        displayFluidSelectedByCircuit = selectedByCircuit;
        displayDrillMaterial = drill == null ? "" : drill.materialName;
        displayParallel = cachedParallel;

        return SimpleCheckRecipeResult.ofSuccess("vmd_drilling");
    }

    /**
     * Total output of one cycle in mB: base output times voltage factor times parallel count times drill bonus.
     * Computed in long space to avoid overflow before the final clamp into int range.
     */
    protected long computeOutputAmount(int parallel, double drillBonus) {
        return (long) ((long) BASE_OUTPUT_MB * getVoltageFactor() * parallel * drillBonus);
    }

    /** Consumes one drill head with a fixed chance at the end of every completed cycle. */
    @Override
    protected void outputAfterRecipe() {
        final DrillSelection drill = findBestDrill();
        if (drill == null) return;
        if (XSTR_INSTANCE.nextFloat() >= DRILL_CONSUMPTION_CHANCE) return;

        // The stacks returned by getStoredInputs() are live references into the input bus inventories.
        ItemStack stack = drill.stack;
        if (stack.stackSize <= 1) {
            stack.stackSize = 0;
        } else {
            stack.stackSize--;
        }
        updateSlots();
    }

    /**************************************************************************
     * Dimension, fluid and drill resolution helpers.
     *************************************************************************/

    /**
     * Resolves the dimension whose underground fluid table this drill taps.
     * <p>
     * A dimension display block in the controller slot selects the dimension explicitly; an invalid item is
     * ignored and the machine falls back to the dimension it is built in.
     */
    @Nullable
    protected GTUODimension resolveTargetDimension() {
        final ItemStack planet = getControllerSlot();
        if (planet != null) {
            final String abbr = ItemDimensionDisplay.getDimension(planet);
            if (abbr != null) return VoidFluidTableHelper.findDimensionByAbbr(abbr);
        }
        return GTMod.proxy.mUndergroundOil.GetDimension(getBaseMetaTileEntity().getWorld().provider.dimensionId);
    }

    /** Abbreviation to display for a resolved dimension, or null when it has no GTNEI entry. */
    @Nullable
    private String resolveDisplayedAbbr(@Nullable GTUODimension dimension) {
        return VoidFluidTableHelper.getAbbrOfDimension(dimension);
    }

    /** Configuration value of the first integrated circuit found in the input buses, or 0 when none is present. */
    private int readCircuitConfig() {
        for (ItemStack stack : getStoredInputs()) {
            if (ItemList.Circuit_Integrated.isStackEqual(stack, true, true)) {
                return stack.getItemDamage();
            }
        }
        return 0;
    }

    /** Highest bonus drill head of the shared ladder currently stored in the input buses, or null. */
    @Nullable
    private DrillSelection findBestDrill() {
        DrillSelection best = null;
        for (ItemStack input : getStoredInputs()) {
            if (input == null) continue;
            for (DrillTier tier : DRILL_TIERS) {
                final ItemStack drillStack = GTOreDictUnificator.get(OrePrefixes.toolHeadDrill, tier.material, 1);
                // Null cannot happen in a correct build: all five stacks are validated at recipe registration
                // time (RecipeLoader). Skip instead of crashing the server thread should GT data be broken.
                if (drillStack == null) continue;
                if (input.isItemEqual(drillStack) && (best == null || tier.bonus > best.bonus)) {
                    best = new DrillSelection(input, tier.bonus, tier.material.mName);
                }
            }
        }
        return best;
    }

    /**************************************************************************
     * GUI and tooltip.
     *************************************************************************/

    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);

        screenElements
            .widget(
                TextWidget
                    .dynamicString(
                        () -> StatCollector.translateToLocalFormatted("vmd.gui.planet", resolvePlanetDisplayName()))
                    .setTextAlignment(Alignment.CenterLeft)
                    .setDefaultColor(COLOR_TEXT_WHITE.get()))
            .widget(new FakeSyncWidget.StringSyncer(() -> displayDimensionAbbr, val -> displayDimensionAbbr = val));
        screenElements
            .widget(
                TextWidget
                    .dynamicString(
                        () -> StatCollector.translateToLocalFormatted("vmd.gui.fluid", resolveFluidDisplayName()))
                    .setTextAlignment(Alignment.CenterLeft)
                    .setDefaultColor(COLOR_TEXT_WHITE.get()))
            .widget(new FakeSyncWidget.StringSyncer(() -> displayFluidKey, val -> displayFluidKey = val))
            .widget(
                new FakeSyncWidget.BooleanSyncer(
                    () -> displayFluidSelectedByCircuit,
                    val -> displayFluidSelectedByCircuit = val));
        screenElements.widget(
            TextWidget.dynamicString(() -> StatCollector.translateToLocalFormatted("vmd.gui.parallel", displayParallel))
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get()))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> displayParallel, val -> displayParallel = val));
        screenElements
            .widget(
                TextWidget
                    .dynamicString(
                        () -> StatCollector.translateToLocalFormatted("vmd.gui.drill", resolveDrillDisplayName()))
                    .setTextAlignment(Alignment.CenterLeft)
                    .setDefaultColor(COLOR_TEXT_WHITE.get()))
            .widget(new FakeSyncWidget.StringSyncer(() -> displayDrillMaterial, val -> displayDrillMaterial = val));
    }

    /** Planet line: localized name of the selected dimension, or the dimension the machine stands in. */
    private String resolvePlanetDisplayName() {
        if (!displayDimensionAbbr.isEmpty()) {
            final String internal = DimensionHelper.ABBR_TO_INTERNAL.get(displayDimensionAbbr);
            if (internal != null) return DimensionHelper.getDimLocalizedName(internal);
        }
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().getWorld() != null) {
            return getBaseMetaTileEntity().getWorld().provider.getDimensionName();
        }
        return StatCollector.translateToLocal("vmd.gui.planet.unknown");
    }

    /** Fluid line: localized name of the circuit-selected fluid, or the weighted random label. */
    private String resolveFluidDisplayName() {
        if (!displayFluidSelectedByCircuit || displayFluidKey.isEmpty()) {
            return StatCollector.translateToLocal("vmd.gui.weighted_random");
        }
        return StatCollector.translateToLocal(displayFluidKey);
    }

    /** Drill line: name and bonus of the best drill head in the bus, or the none label. */
    private String resolveDrillDisplayName() {
        if (displayDrillMaterial.isEmpty()) return StatCollector.translateToLocal("vmd.gui.drill.none");
        for (DrillTier tier : DRILL_TIERS) {
            if (tier.material.mName.equals(displayDrillMaterial)) {
                return tier.material.mDefaultLocalName + " (x" + tier.bonus + ")";
            }
        }
        return displayDrillMaterial;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("vmd.mbtt.machine_type"))
            .addInfo(StatCollector.translateToLocal("vmd.mbtt.info.1"))
            .addInfo(StatCollector.translateToLocal("vmd.mbtt.info.2"))
            .addInfo(StatCollector.translateToLocal("vmd.mbtt.info.3"))
            .addInfo(StatCollector.translateToLocal("vmd.mbtt.info.4"))
            .addInfo(StatCollector.translateToLocal("vmd.mbtt.info.5"))
            .addInfo(StatCollector.translateToLocal("vmd.mbtt.info.6"))
            .addInfo(StatCollector.translateToLocal("vmd.mbtt.info.7"))
            .beginStructureBlock(3, 7, 3, false)
            .addController(StatCollector.translateToLocal("vmd.mbtt.structure.front_bottom_center"))
            .addCasing("3", StatCollector.translateToLocal("vmd.mbtt.structure.center_shaft_frame"), false)
            .addCasing(
                "12",
                StatCollector.translateToLocalFormatted(
                    "vmd.mbtt.structure.frame",
                    GTOreDictUnificator.get(OrePrefixes.frameGt, getFrameMaterial(), 1)
                        .getDisplayName()),
                false)
            .addCasing(
                "7-8",
                getCasingBlock().get(0)
                    .getDisplayName(),
                false)
            .addEnergyHatch(
                "1+",
                StatCollector.translateToLocalFormatted(
                    "vmd.mbtt.structure.any_bottom_casing_min_tier",
                    GTValues.VN[getMinTier()]),
                1)
            .addMaintenanceHatch(StatCollector.translateToLocal("vmd.mbtt.structure.any_bottom_casing"))
            .addInputBus(StatCollector.translateToLocal("vmd.mbtt.structure.input_bus"))
            .addInputHatch(StatCollector.translateToLocal("vmd.mbtt.structure.input_hatch"))
            .addOutputBus(StatCollector.translateToLocal("vmd.mbtt.structure.output_bus"))
            .addOutputHatch(StatCollector.translateToLocal("vmd.mbtt.structure.output_hatch"))
            .toolTipFinisher();
        return tt;
    }

    /**************************************************************************
     * Boilerplate required by the multiblock base.
     *************************************************************************/

    @Override
    public RecipeMap<?> getRecipeMap() {
        return VmdRecipeMaps.voidFluidDrillerRecipes;
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    @Override
    public boolean isCorrectMachinePart(ItemStack aStack) {
        return true;
    }

    @Override
    public int getDamageToComponent(ItemStack aStack) {
        return 0;
    }

    @Override
    public boolean explodesOnComponentBreak(ItemStack aStack) {
        return false;
    }

    @Override
    public boolean supportsBatchMode() {
        // Batch mode multiplies outputs through the recipe logic; this machine scales through parallels
        // instead, so the toggle would be a no-op and is disabled.
        return false;
    }

    @Override
    public boolean supportsSingleRecipeLocking() {
        return false;
    }
}
