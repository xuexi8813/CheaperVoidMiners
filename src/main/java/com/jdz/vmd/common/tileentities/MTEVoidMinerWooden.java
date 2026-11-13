package com.jdz.vmd.common.tileentities;

import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL;
import static gregtech.api.objects.XSTR.XSTR_INSTANCE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.bsideup.jabel.Desugar;

import bartworks.common.configs.Configuration;
import cpw.mods.fml.common.registry.GameRegistry;
import galacticgreg.api.enums.DimensionDef;
import gregtech.api.enums.StoneType;
import gregtech.api.interfaces.IOreMaterial;
import gregtech.api.interfaces.IStoneType;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gregtech.common.WorldgenGTOreLayer;
import gregtech.common.WorldgenGTOreSmallPieces;
import gregtech.common.ores.OreInfo;
import gregtech.common.ores.OreManager;
import gtneioreplugin.util.DimensionHelper;

/**
 * Wooden Void Miner: a non-electric singleblock machine that slowly pulls ore items from the Overworld ore
 * generation tables ("from the void", no block breaking).
 * <p>
 * The ore data is rebuilt from the same GT5u sources the void miner uses, see
 * {@code bwcrossmod.galacticgreg.VoidMinerUtility#generateDropMaps}: enabled {@link WorldgenGTOreLayer} ore
 * veins restricted to the Overworld, plus enabled {@link WorldgenGTOreSmallPieces} entries. Selection
 * follows the shared wooden rules: a programmed circuit (configuration 1..N) picks the N-th vein, N+1
 * picks the poor (small) ore pool, configuration 0 or no circuit mixes everything into one weighted pool,
 * anything beyond N+1 idles.
 * <p>
 * Determinism: veins are iterated in {@code sList} registration order and the stone type expansion follows
 * {@link DimensionHelper#getStoneTypes}, exactly like the vanilla void miner drop map construction.
 */
public class MTEVoidMinerWooden extends MTEVoidWoodenBase {

    /** Ticks between two production attempts; the wooden miner works twice as fast as the wooden pump. */
    public static final int MINER_PRODUCTION_CYCLE_TICKS = 100;

    /** The Overworld dimension name as used by the ore worldgen tables and the GTNEI helpers. */
    private static final String OVERWORLD_DIM_NAME = DimensionDef.DimNames.OW;

    /** Sporadic and between materials count as one eighth of the vein weight, mirroring the void miner. */
    private static final float SPORADIC_WEIGHT_DIVISOR = 8.0f;

    /** A weighted entry of an ore pool. */
    @Desugar
    private record WeightedOre(ItemStack stack, float weight) {}

    /** One ore vein: the four vein materials expanded over the Overworld stone types. */
    private static final class OreVein {

        private final String name;
        private final List<WeightedOre> drops = new ArrayList<>();

        private OreVein(String name) {
            this.name = name;
        }
    }

    /** Session wide flag so empty ore tables are reported once instead of every cycle. */
    private static boolean warnedEmptyOverworldPools = false;

    // Ore pools, built lazily once per machine instance (server and client both need them).
    private boolean poolsBuilt = false;
    private final List<OreVein> veinPool = new ArrayList<>();
    private final List<WeightedOre> smallOrePool = new ArrayList<>();
    private final List<WeightedOre> mixedPool = new ArrayList<>();

    public MTEVoidMinerWooden(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, "");
    }

    public MTEVoidMinerWooden(String aName, ITexture[][][] aTextures) {
        super(aName, new String[] { "" }, aTextures);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MTEVoidMinerWooden(this.mName, this.mTextures);
    }

    /**************************************************************************
     * Production.
     *************************************************************************/

    @Override
    protected int getProductionCycleTicks() {
        return MINER_PRODUCTION_CYCLE_TICKS;
    }

    @Override
    protected void tryProduce() {
        if (!poolsBuilt) buildOrePools();
        if (mixedPool.isEmpty()) {
            if (!warnedEmptyOverworldPools) {
                warnedEmptyOverworldPools = true;
                log().warn("Overworld ore vein and small ore tables are empty, Wooden Void Miner idles");
            }
            return;
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
            return; // Beyond the pool count: idle, mirroring the wooden pump behavior.
        }
        if (pool.isEmpty()) return;

        final WeightedOre pick = weightedPick(pool);
        // addStackToSlot merges into a same stack up to its max size; a blocked slot (different ore and no
        // ejection space) simply skips the cycle until the output slot drains through the front face.
        if (getBaseMetaTileEntity().addStackToSlot(getOutputSlot(), GTUtility.copyAmount(1, pick.stack()))) {
            markDirty();
        }
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
            veinPool.add(vein);
            mixedPool.addAll(vein.drops);
        }

        for (WorldgenGTOreSmallPieces piece : WorldgenGTOreSmallPieces.sList) {
            if (!piece.mEnabled) continue;
            if (!piece.getAllowedDimensions()
                .contains(OVERWORLD_DIM_NAME)) continue;

            addSmallOreDrops(piece, stones);
        }
    }

    /** Expands one vein material over the stone types; a missing adapter stops the expansion early. */
    private void addMaterialDrops(OreVein vein, IOreMaterial material, List<StoneType> stones, float weight) {
        if (material == null) return;
        for (IStoneType stone : stones) {
            final ItemStack stack = resolveOreStack(material, stone, false);
            if (stack == null) {
                // Mirrors VoidMinerUtility: an unresolvable (material, stone) pair stops the expansion.
                break;
            }
            if (isVoidMinerBlacklisted(stack)) continue;
            vein.drops.add(new WeightedOre(stack, weight));
        }
    }

    /**
     * Expands the poor ore drop of one small pieces entry over the stone types, weighted by its generation
     * amount; same expansion rules as the vein materials ({@link #addMaterialDrops}).
     */
    private void addSmallOreDrops(WorldgenGTOreSmallPieces piece, List<StoneType> stones) {
        final IOreMaterial material = piece.getMaterial();
        if (material == null) return;
        for (IStoneType stone : stones) {
            final ItemStack stack = resolveOreStack(material, stone, true);
            if (stack == null) {
                // Mirrors VoidMinerUtility: an unresolvable (material, stone) pair stops the expansion.
                break;
            }
            if (isVoidMinerBlacklisted(stack)) continue;
            final WeightedOre drop = new WeightedOre(stack, piece.mAmount);
            smallOrePool.add(drop);
            mixedPool.add(drop);
        }
    }

    private ItemStack resolveOreStack(IOreMaterial material, IStoneType stone, boolean small) {
        try (OreInfo<IOreMaterial> info = OreInfo.getNewInfo()) {
            info.material = material;
            info.stoneType = stone;
            info.isSmall = small;
            final ItemStack stack = OreManager.getStack(info, 1);
            if (stack == null) {
                log().warn("Could not resolve ore stack for {} on stone {}", material, stone);
            }
            return stack;
        }
    }

    /** Same identifier format as the void miner drop map blacklist ({@code VoidMinerUtility.DropMap}). */
    private static boolean isVoidMinerBlacklisted(ItemStack stack) {
        final String identifier = String.format(
            "%s:%d",
            GameRegistry.findUniqueIdentifierFor(Block.getBlockFromItem(stack.getItem()))
                .toString(),
            stack.getItemDamage());
        return Arrays.asList(Configuration.multiblocks.voidMinerBlacklist)
            .contains(identifier);
    }

    /**************************************************************************
     * Textures: wooden casing body with the GT ore drill overlay on the front face.
     *************************************************************************/

    @Override
    public ITexture[][][] getTextureSet(ITexture[] aTextures) {
        final ITexture body = TextureFactory.of(ItemListWoodenCasingBlock(), ItemListWoodenCasingMeta());
        final ITexture front = TextureFactory.builder()
            .addIcon(OVERLAY_FRONT_ORE_DRILL)
            .extFacing()
            .build();

        final ITexture[][][] rTextures = new ITexture[2][17][];
        for (byte c = -1; c < 16; c++) {
            rTextures[0][c + 1] = new ITexture[] { body };
            rTextures[1][c + 1] = new ITexture[] { body, front };
        }
        return rTextures;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        return mTextures[side == aFacing ? 1 : 0][colorIndex + 1];
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return true;
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
    @Override
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

    /** Resolves lazily so the class can be loaded before GT registers its blocks. */
    private static net.minecraft.block.Block ItemListWoodenCasingBlock() {
        return gregtech.api.enums.ItemList.WoodenCasing.getBlock();
    }

    private static int ItemListWoodenCasingMeta() {
        return gregtech.api.enums.ItemList.WoodenCasing.get(0)
            .getItemDamage();
    }
}
