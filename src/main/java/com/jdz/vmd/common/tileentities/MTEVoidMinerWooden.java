package com.jdz.vmd.common.tileentities;

import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL;
import static gregtech.api.objects.XSTR.XSTR_INSTANCE;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.github.bsideup.jabel.Desugar;
import com.jdz.vmd.api.recipe.VmdRecipeMaps;

import galacticgreg.api.enums.DimensionDef;
import gregtech.api.enums.StoneType;
import gregtech.api.interfaces.IOreMaterial;
import gregtech.api.interfaces.IStoneType;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.render.TextureFactory;
import gregtech.common.WorldgenGTOreLayer;
import gregtech.common.WorldgenGTOreSmallPieces;
import gtneioreplugin.util.DimensionHelper;

/**
 * Wooden Void Miner: a non-electric singleblock machine that slowly pulls ore items from the Overworld ore
 * generation tables ("from the void", no block breaking).
 * <p>
 * The ore data is rebuilt from the same GT5u sources the void miner uses, see
 * {@code bwcrossmod.galacticgreg.VoidMinerUtility#generateDropMaps}: enabled {@link WorldgenGTOreLayer} ore
 * veins restricted to the Overworld, plus enabled {@link WorldgenGTOreSmallPieces} entries (poor ore drops
 * resolved by {@link VoidOreTableHelper}). Selection follows the shared wooden rules: a programmed circuit
 * (configuration 1..N) picks the N-th vein, N+1 picks the poor (small) ore pool, configuration 0 or no
 * circuit mixes everything into one weighted pool, anything beyond N+1 idles.
 * <p>
 * Determinism: veins are iterated in {@code sList} registration order and the stone type expansion follows
 * {@link DimensionHelper#getStoneTypes}, exactly like the vanilla void miner drop map construction.
 */
// Skips the GT regional-name registration; the display name comes from the standard lang keys
// (the annotation is not inherited, so the concrete class carries it explicitly).
@IMetaTileEntity.SkipGenerateName
public class MTEVoidMinerWooden extends MTEVoidWoodenBase {

    /** Ticks between two production attempts; the wooden miner works twice as fast as the wooden pump. */
    public static final int MINER_PRODUCTION_CYCLE_TICKS = 100;

    /** The Overworld dimension name as used by the ore worldgen tables and the GTNEI helpers. */
    private static final String OVERWORLD_DIM_NAME = DimensionDef.DimNames.OW;

    /** Sporadic and between materials count as one eighth of the vein weight, mirroring the void miner. */
    private static final float SPORADIC_WEIGHT_DIVISOR = 8.0f;

    /** A weighted entry of an ore pool. */
    @Desugar
    public record WeightedOre(ItemStack stack, float weight) {}

    /** One ore vein: the four vein materials expanded over the Overworld stone types. */
    public static final class OreVein {

        public final String name;
        public final List<WeightedOre> drops = new ArrayList<>();

        public OreVein(String name) {
            this.name = name;
        }
    }

    /**
     * The three Overworld ore pools built by {@link #buildOverworldPools()}; shared between machine
     * instances and the NEI output recipe registration, so both sides see identical data.
     */
    public static final class OverworldPools {

        public final List<OreVein> veins = new ArrayList<>();
        public final List<WeightedOre> smallOres = new ArrayList<>();
        public final List<WeightedOre> mixed = new ArrayList<>();
    }

    /** Session wide flag so empty ore tables are reported once instead of every cycle. */
    private static boolean warnedEmptyOverworldPools = false;

    // Ore pools, built lazily once per machine instance (server and client both need them).
    private boolean poolsBuilt = false;
    private List<OreVein> veinPool = new ArrayList<>();
    private List<WeightedOre> smallOrePool = new ArrayList<>();
    private List<WeightedOre> mixedPool = new ArrayList<>();

    public MTEVoidMinerWooden(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, "", 1, 1);
    }

    public MTEVoidMinerWooden(String aName, ITexture[][][] aTextures) {
        super(aName, new String[] { "" }, aTextures, 1, 1);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MTEVoidMinerWooden(this.mName, this.mTextures);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return VmdRecipeMaps.woodenMinerRecipes;
    }

    @Override
    protected int getProductionCycleTicks() {
        return MINER_PRODUCTION_CYCLE_TICKS;
    }

    /**************************************************************************
     * Production.
     *************************************************************************/

    @Override
    public int checkRecipe() {
        if (!poolsBuilt) buildOrePools();
        if (mixedPool.isEmpty()) {
            if (!warnedEmptyOverworldPools) {
                warnedEmptyOverworldPools = true;
                log().warn("Overworld ore vein and small ore tables are empty, Wooden Void Miner idles");
            }
            return DID_NOT_FIND_RECIPE;
        }

        final int config = readCircuitConfig();
        final List<WeightedOre> pool;
        if (config == 0) {
            // No circuit or configuration 0: everything mixed into one weighted pool.
            pool = mixedPool;
        } else if (config <= veinPool.size()) {
            pool = veinPool.get(config - 1).drops;
        } else if (config == veinPool.size() + 1) {
            pool = smallOrePool;
        } else {
            return DID_NOT_FIND_RECIPE; // Beyond the pool count: idle, mirroring the wooden machine family.
        }
        if (pool.isEmpty()) return DID_NOT_FIND_RECIPE;

        final ItemStack output = singleOutput(weightedPick(pool).stack());
        // A full output slot or a different ore still inside stalls the machine until the slot drains.
        if (!canOutput(output)) return DID_NOT_FIND_RECIPE;

        mOutputItems[0] = output;
        mMaxProgresstime = getProductionCycleTicks();
        return FOUND_AND_SUCCESSFULLY_USED_RECIPE;
    }

    /** Weighted random pick over a pool; the roll partitions [0, total) exactly like the fluid helper. */
    private WeightedOre weightedPick(List<WeightedOre> pool) {
        float total = 0;
        for (WeightedOre entry : pool) {
            total += Math.max(0, entry.weight());
        }
        if (total <= 0) return pool.get(XSTR_INSTANCE.nextInt(pool.size()));

        float roll = XSTR_INSTANCE.nextFloat() * total;
        for (WeightedOre entry : pool) {
            roll -= Math.max(0, entry.weight());
            if (roll < 0) return entry;
        }
        return pool.get(pool.size() - 1);
    }

    /**************************************************************************
     * Ore pool construction, mirroring VoidMinerUtility.generateDropMaps for the Overworld.
     *************************************************************************/

    private void buildOrePools() {
        poolsBuilt = true;
        final OverworldPools pools = buildOverworldPools();
        this.veinPool = pools.veins;
        this.smallOrePool = pools.smallOres;
        this.mixedPool = pools.mixed;
    }

    /**
     * Builds the three Overworld ore pools from the GT5u worldgen tables; the same data source, expansion
     * rules and ordering the machine itself uses, exposed for the NEI output recipe registration.
     */
    public static OverworldPools buildOverworldPools() {
        final OverworldPools pools = new OverworldPools();

        final List<StoneType> stones = DimensionHelper.getStoneTypes(OVERWORLD_DIM_NAME);

        // Registration order of sList is deterministic, so is the resulting vein indexing.
        for (WorldgenGTOreLayer layer : WorldgenGTOreLayer.sList) {
            if (!layer.mEnabled) continue;
            if (!layer.getAllowedDimensions()
                .contains(OVERWORLD_DIM_NAME)) continue;

            final OreVein vein = new OreVein(layer.mWorldGenName);
            addMaterialDrops(vein, layer.mPrimary, stones, layer.mWeight);
            addMaterialDrops(vein, layer.mSecondary, stones, layer.mWeight);
            addMaterialDrops(vein, layer.mSporadic, stones, layer.mWeight / SPORADIC_WEIGHT_DIVISOR);
            addMaterialDrops(vein, layer.mBetween, stones, layer.mWeight / SPORADIC_WEIGHT_DIVISOR);
            if (vein.drops.isEmpty()) continue;
            pools.veins.add(vein);
            pools.mixed.addAll(vein.drops);
        }

        for (WorldgenGTOreSmallPieces piece : WorldgenGTOreSmallPieces.sList) {
            if (!piece.mEnabled) continue;
            if (!piece.getAllowedDimensions()
                .contains(OVERWORLD_DIM_NAME)) continue;

            VoidOreTableHelper.forEachSmallOreDrop(piece, stones, (stack, weight) -> {
                final WeightedOre drop = new WeightedOre(stack, weight);
                pools.smallOres.add(drop);
                pools.mixed.add(drop);
            });
        }
        return pools;
    }

    /** Expands one vein material over the stone types; a missing adapter stops the expansion early. */
    private static void addMaterialDrops(OreVein vein, IOreMaterial material, List<StoneType> stones, float weight) {
        if (material == null) return;
        for (IStoneType stone : stones) {
            final ItemStack stack = VoidOreTableHelper.resolveOreStack(material, stone, false);
            if (stack == null) {
                // Mirrors VoidMinerUtility: an unresolvable (material, stone) pair stops the expansion.
                break;
            }
            if (VoidOreTableHelper.isVoidMinerBlacklisted(stack)) continue;
            vein.drops.add(new WeightedOre(stack, weight));
        }
    }

    /**************************************************************************
     * Textures: wooden casing body with the GT ore drill overlay on the front face.
     *************************************************************************/

    @Override
    protected ITexture getFrontOverlay() {
        return TextureFactory.builder()
            .addIcon(OVERLAY_FRONT_ORE_DRILL)
            .extFacing()
            .build();
    }

    /**************************************************************************
     * Tooltip: static rules plus a live status line for the current selection.
     *************************************************************************/

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("vmd.miner.desc.1"),
            StatCollector.translateToLocal("vmd.miner.desc.2"), StatCollector.translateToLocal("vmd.miner.desc.3"),
            StatCollector.translateToLocal("vmd.miner.desc.4"), StatCollector.translateToLocal("vmd.miner.desc.5"),
            StatCollector.translateToLocalFormatted("vmd.miner.desc.selection", describeCurrentSelection()) };
    }

    /** Human readable name of what the inserted circuit currently selects. */
    protected String describeCurrentSelection() {
        if (!poolsBuilt) buildOrePools();

        final int config = readCircuitConfig();
        if (config >= 1 && config <= veinPool.size()) {
            return veinPool.get(config - 1).name;
        }
        if (config == veinPool.size() + 1) {
            return StatCollector.translateToLocal("vmd.miner.selection.poor");
        }
        if (config == 0) {
            return StatCollector.translateToLocal("vmd.miner.selection.mixed");
        }
        return StatCollector.translateToLocal("vmd.pump.selection.invalid");
    }
}
