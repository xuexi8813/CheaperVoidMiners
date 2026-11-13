package com.jdz.vmd.common.tileentities;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.getCasingTextureForId;
import static gregtech.api.objects.XSTR.XSTR_INSTANCE;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofFrame;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

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

import galacticgreg.api.ModDimensionDef;
import galacticgreg.api.enums.DimensionDef;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gtneioreplugin.util.DimensionHelper;

/**
 * Base of the Void Excavators: multiblock controllers that dig plain (non ore) resource blocks of a chosen
 * dimension out of the void, driven by the shared config resource table ({@link VoidExcavatorResourceTable}).
 * <p>
 * Motivation: this family is the electric counterpart of the wooden excavator. It deliberately does
 * <b>not</b> extend {@code bwcrossmod.galacticgreg.MTEVoidMinerBase} to stay fully decoupled from the
 * upstream GT ore/miner logic (VMT compatibility principle); instead it reuses the generic
 * {@link MTEEnhancedMultiBlockBase} machinery and the same shape as the Void Fluid Drillers, whose
 * structure is a 1:1 replica of {@code gregtech.common.tileentities.machines.multi.MTEDrillerBase} (3x3x7,
 * frame shaft below the controller, three plus-shaped frame/casing layers, 3x3 casing top with the
 * controller at the front bottom center).
 * <p>
 * The drill head handling (shared ladder, highest bonus wins, 0.1% per cycle consumption) follows the
 * implementation pattern of {@link MTEVoidFluidDrillerBase}, which itself cannot be refactored into a
 * shared helper because it is already shipped and frozen behavior of this mod.
 *
 * @param <T> self type for the generic multiblock base
 */
@IMetaTileEntity.SkipGenerateDescription
public abstract class MTEVoidExcavatorBase<T extends MTEVoidExcavatorBase<T>> extends MTEEnhancedMultiBlockBase<T>
    implements ISurvivalConstructable {

    /** Length of one production cycle in ticks. */
    public static final int CYCLE_TICKS = 100;
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
    private boolean selectedByCircuit = false;

    // GUI display state, synced through FakeSyncWidgets in drawTexts().
    private String displayDimensionAbbr = "";
    private String displayResourceName = "";
    private boolean displayResourceSelectedByCircuit = false;
    private String displayDrillMaterial = "";
    private int displayParallel = 1;

    protected int casingTextureIndex;

    public MTEVoidExcavatorBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        casingTextureIndex = getControllerTextureIndex();
    }

    public MTEVoidExcavatorBase(String aName) {
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

    /** Lowest allowed energy hatch tier; also sets the EU/t draw of every cycle. */
    protected abstract int getMinTier();

    /**
     * Resource blocks produced per cycle before parallelism and drill bonus, kept as a constant so stages
     * stay independently tunable.
     */
    public abstract int getStageMultiplier();

    /** Texture index used for the controller front and as base casing texture of the hatch slots. */
    protected abstract int getControllerTextureIndex();

    /**
     * Parallel count for a given input voltage tier. Defaults to 1; the Infinity stage overrides this with an
     * exponential rule (see {@code MTEVoidExcavators.VoidExcavatorInfinity}).
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

    /** Hatch requirements: energy + maintenance are mandatory, input bus and output bus are mandatory. */
    protected void checkHatches(List<StructureError> errors) {
        checkHasEnergyHatch(errors);
        checkOneMaintenanceHatch(errors);
        checkHasInputBus(errors);
        checkHasOutputBus(errors);
    }

    /** Hatch elements accepted in the 'b' slots; consumed by the structure definition builder. */
    protected List<IHatchElement<? super T>> getAllowedHatches() {
        return ImmutableList.of(InputBus, OutputBus, Maintenance, Energy);
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        // Excavators cannot be rotated and must not face up or down, matching the driller family shape.
        return (d, r, f) -> (d.flag & (ForgeDirection.UP.flag | ForgeDirection.DOWN.flag)) == 0 && r.isNotRotated()
            && !f.isVerticallyFliped();
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        if (side == aFacing) {
            if (aActive) return new ITexture[] { getCasingTextureForId(casingTextureIndex), TextureFactory.builder()
                .addIcon(OVERLAY_FRONT_ORE_DRILL_ACTIVE)
                .extFacing()
                .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ORE_DRILL_ACTIVE_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
            return new ITexture[] { getCasingTextureForId(casingTextureIndex), TextureFactory.builder()
                .addIcon(OVERLAY_FRONT_ORE_DRILL)
                .extFacing()
                .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ORE_DRILL_GLOW)
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

        // 1. Resolve the target dimension and its configured resource table.
        final String dimensionAbbr = resolveDimensionAbbr();
        final List<VoidExcavatorResourceTable.WeightedResource> resources = VoidExcavatorResourceTable
            .getResources(dimensionAbbr);
        if (resources.isEmpty()) {
            mEUt = 0;
            mMaxProgresstime = 0;
            stopMachine(ShutDownReasonRegistry.NONE);
            return SimpleCheckRecipeResult.ofFailurePersistOnShutdown("vmd_excavator_no_resources");
        }

        // 2. Resolve the resource: circuit config 1..N picks the N-th entry, anything else is weighted random.
        final int circuitConfig = readCircuitConfig();
        final VoidExcavatorResourceTable.WeightedResource selected;
        if (circuitConfig >= 1) {
            if (circuitConfig > resources.size()) {
                mEUt = 0;
                mMaxProgresstime = 0;
                return SimpleCheckRecipeResult.ofFailure("vmd_excavator_circuit_out_of_range");
            }
            selected = resources.get(circuitConfig - 1);
            selectedByCircuit = true;
        } else {
            selected = VoidExcavatorResourceTable.weightedRandom(resources);
            selectedByCircuit = false;
        }
        // 3. Resolve the best drill head present in the input bus (optional, bonus 1.0 when absent).
        final DrillSelection drill = findBestDrill();
        cachedDrillBonus = drill == null ? 1.0 : drill.bonus;

        // 4. Compute the output and check output space before committing the cycle.
        final long amount = computeOutputAmount(cachedParallel, cachedDrillBonus);
        final ItemStack output = GTUtility.copyAmount(
            (int) Math.min(amount, Integer.MAX_VALUE),
            selected.stack()
                .copy());
        if (!canOutputAll(new ItemStack[] { output })) {
            mEUt = 0;
            mMaxProgresstime = 0;
            return SimpleCheckRecipeResult.ofFailure("vmd_excavator_output_full");
        }

        // 5. Apply the cycle stats: one flat voltage of the stage per parallel, 100 tick cycles.
        // GT 2.9 multiblocks keep mEUt as an int, so the multi-amp draw saturates at Integer.MAX_VALUE
        // (reached beyond UIV tier on the Infinity stage); output and parallel count stay unclamped.
        mOutputItems = new ItemStack[] { output };
        final long totalEUt = GTValues.V[getMinTier()] * (long) cachedParallel;
        mEUt = (int) -Math.min(totalEUt, Integer.MAX_VALUE);
        mMaxProgresstime = CYCLE_TICKS;
        mEfficiency = getCurrentEfficiency(null);
        mEfficiencyIncrease = 10000;

        // 6. Update the GUI display state.
        displayDimensionAbbr = dimensionAbbr == null ? "" : dimensionAbbr;
        displayResourceName = selected.stack()
            .getDisplayName();
        displayResourceSelectedByCircuit = selectedByCircuit;
        displayDrillMaterial = drill == null ? "" : drill.materialName;
        displayParallel = cachedParallel;

        return SimpleCheckRecipeResult.ofSuccess("vmd_excavator_digging");
    }

    /**
     * Total resource blocks of one cycle: stage multiplier times parallel count times drill bonus. Computed
     * in long space to avoid overflow before the final clamp into int range.
     */
    protected long computeOutputAmount(int parallel, double drillBonus) {
        return (long) ((long) getStageMultiplier() * parallel * drillBonus);
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
     * Dimension, resource and drill resolution helpers.
     *************************************************************************/

    /**
     * Abbreviation of the dimension whose resource table this excavator digs.
     * <p>
     * A dimension display block in the controller slot selects the dimension explicitly; an invalid item is
     * ignored and the machine falls back to the dimension it is built in. A dimension unknown to the GTNEI
     * registry resolves to null, which matches no table and fails the cycle with "no resources".
     */
    @Nullable
    protected String resolveDimensionAbbr() {
        final ItemStack display = getControllerSlot();
        if (display != null) {
            final String abbr = VoidExcavatorResourceTable.getDimensionAbbr(display);
            if (abbr != null) return abbr;
        }

        final ModDimensionDef dimensionDef = DimensionDef.getDefForWorld(getBaseMetaTileEntity().getWorld());
        if (dimensionDef != null) {
            return DimensionHelper.INTERNAL_TO_ABBR.get(dimensionDef.getDimensionName());
        }
        return null;
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
                        () -> StatCollector.translateToLocalFormatted("vmd.gui.resource", resolveResourceDisplayName()))
                    .setTextAlignment(Alignment.CenterLeft)
                    .setDefaultColor(COLOR_TEXT_WHITE.get()))
            .widget(new FakeSyncWidget.StringSyncer(() -> displayResourceName, val -> displayResourceName = val))
            .widget(
                new FakeSyncWidget.BooleanSyncer(
                    () -> displayResourceSelectedByCircuit,
                    val -> displayResourceSelectedByCircuit = val));
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
            return VoidExcavatorResourceTable.getDimensionDisplayName(displayDimensionAbbr);
        }
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().getWorld() != null) {
            return getBaseMetaTileEntity().getWorld().provider.getDimensionName();
        }
        return StatCollector.translateToLocal("vmd.gui.planet.unknown");
    }

    /** Resource line: name of the circuit-selected resource, or the weighted random label. */
    private String resolveResourceDisplayName() {
        if (!displayResourceSelectedByCircuit || displayResourceName.isEmpty()) {
            return StatCollector.translateToLocal("vmd.gui.weighted_random");
        }
        return displayResourceName;
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
        tt.addMachineType(StatCollector.translateToLocal("vmd.mbe.machine_type"))
            .addInfo(StatCollector.translateToLocal("vmd.mbe.info.1"))
            .addInfo(StatCollector.translateToLocal("vmd.mbe.info.2"))
            .addInfo(StatCollector.translateToLocal("vmd.mbe.info.3"))
            .addInfo(StatCollector.translateToLocal("vmd.mbe.info.4"))
            .addInfo(StatCollector.translateToLocal("vmd.mbe.info.5"))
            .addInfo(StatCollector.translateToLocalFormatted("vmd.mbe.info.6", getStageMultiplier()))
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
            .addInputBus(StatCollector.translateToLocal("vmd.mbe.structure.input_bus"))
            .addOutputBus(StatCollector.translateToLocal("vmd.mbe.structure.output_bus"))
            .toolTipFinisher();
        return tt;
    }

    /**************************************************************************
     * Boilerplate required by the multiblock base.
     *************************************************************************/

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
