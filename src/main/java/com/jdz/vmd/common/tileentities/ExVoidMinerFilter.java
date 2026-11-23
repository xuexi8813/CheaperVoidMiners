package com.jdz.vmd.common.tileentities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.github.bsideup.jabel.Desugar;

import gregtech.api.interfaces.IOreMaterial;
import gregtech.api.util.GTUtility.ItemId;
import gregtech.common.ores.OreInfo;
import gregtech.common.ores.OreManager;

/** Indexes one effective EX ore table. GT's persisted stacks remain the only stored selection. */
public final class ExVoidMinerFilter {

    /** Supported requests; the wire protocol validates the operation and uses -1 for non-row requests. */
    public enum Operation {
        SELECT,
        DESELECT,
        SELECT_ALL,
        DESELECT_ALL,
        WHITELIST,
        BLACKLIST
    }

    /** Read-only panel state. Row order, revision and marks travel together, never as a writable inventory. */
    public static final class View {

        /** The initial client state, before the server sends the first view. */
        public static final View EMPTY = new View(0, Collections.emptyList(), new BitSet(), false);

        /** The ore table revision to send back with actions based on these rows. */
        public final long revision;
        /** The server's ordered ore identities; their indices are only valid at this revision. */
        public final List<ItemId> rows;
        /** The current GT whitelist/blacklist mode. */
        public final boolean blacklist;
        /** Selected row positions; keep this owned by the read-only view. */
        private final BitSet selected;

        private View(long revision, List<ItemId> rows, BitSet selected, boolean blacklist) {
            this.revision = revision;
            this.rows = rows;
            this.selected = selected;
            this.blacklist = blacklist;
        }

        /** Builds a received view without trusting its data as a source of server selections. */
        public static View received(long revision, List<ItemId> rows, BitSet selected, boolean blacklist) {
            return new View(
                revision,
                Collections.unmodifiableList(new ArrayList<>(rows)),
                (BitSet) selected.clone(),
                blacklist);
        }

        /** Reports selection for one display row, independent of the GT handler's slot numbers. */
        public boolean isSelected(int row) {
            return selected.get(row);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof View)) return false;
            View view = (View) other;
            return revision == view.revision && blacklist == view.blacklist
                && selected.equals(view.selected)
                && rows.equals(view.rows);
        }

        @Override
        public int hashCode() {
            return Objects.hash(revision, rows, selected, blacklist);
        }
    }

    /** Stable for this resolved table, changes when the server resolves a different override or table. */
    private final long revision;
    /** Stable order for the GUI, including separate regular/small/stone variants. */
    private final List<ItemId> rows;
    /** Material and ore size when known, exact GT item identity otherwise; one key per row. */
    private final List<Object> rowKeys;
    /** One representative stack per available identity, used for bulk selection. */
    private final Map<Object, ItemId> representatives;

    /** Creates the revision's ordered rows and material/size groups from GT's actual merged distribution. */
    public ExVoidMinerFilter(long revision, ItemId[] ores) {
        this.revision = revision;
        List<ItemId> ordered = new ArrayList<>();
        if (ores != null) ordered.addAll(Arrays.asList(ores));
        ordered.sort(
            Comparator.comparingInt(ItemId::metaData)
                .thenComparingInt(ore -> Item.getIdFromItem(ore.item())));
        rows = Collections.unmodifiableList(ordered);
        rowKeys = new ArrayList<>(rows.size());
        representatives = new LinkedHashMap<>();
        for (ItemId ore : rows) {
            Object key = selectionKey(ore.getItemStack());
            rowKeys.add(key);
            representatives.putIfAbsent(key, ore);
        }
    }

    /** Checks availability of the requested revision and row before any request may mutate GT state. */
    public boolean accepts(long revision, int row, Operation operation) {
        if (this.revision != revision) return false;
        if (operation == Operation.SELECT || operation == Operation.DESELECT) return row >= 0 && row < rows.size();
        return row == -1;
    }

    /** Projects what GT should match for this run. Unavailable saved stacks are neither used nor removed. */
    public List<ItemStack> effectiveSelections(ItemStackHandler selected) {
        List<ItemStack> active = new ArrayList<>();
        for (int slot = 0; slot < selected.getSlots(); slot++) {
            ItemStack stack = selected.getStackInSlot(slot);
            if (stack != null && representatives.containsKey(selectionKey(stack))) active.add(stack);
        }
        return active;
    }

    /** Computes all display marks from the same table and the current shared GT selection. */
    public View view(ItemStackHandler selected, boolean blacklist) {
        Set<Object> marked = new HashSet<>();
        for (int slot = 0; slot < selected.getSlots(); slot++) {
            ItemStack stack = selected.getStackInSlot(slot);
            if (stack != null) marked.add(selectionKey(stack));
        }
        BitSet selectedRows = new BitSet(rows.size());
        for (int row = 0; row < rows.size(); row++) {
            if (marked.contains(rowKeys.get(row))) selectedRows.set(row);
        }
        return new View(revision, rows, selectedRows, blacklist);
    }

    /** Applies a validated selection action while preserving unavailable entries and the GT handler's NBT shape. */
    public ItemStackHandler edit(ItemStackHandler selected, long revision, int row, Operation operation) {
        if (!accepts(revision, row, operation)) return selected;
        List<ItemStack> stacks = new ArrayList<>(selected.getSlots());
        for (int slot = 0; slot < selected.getSlots(); slot++) {
            stacks.add(selected.getStackInSlot(slot));
        }
        boolean changed;
        switch (operation) {
            case SELECT:
                changed = addSelections(stacks, Collections.singletonMap(rowKeys.get(row), rows.get(row)));
                break;
            case DESELECT:
                changed = removeSelections(stacks, Collections.singleton(rowKeys.get(row)));
                break;
            case SELECT_ALL:
                changed = addSelections(stacks, representatives);
                break;
            case DESELECT_ALL:
                changed = removeSelections(stacks, representatives.keySet());
                break;
            default:
                return selected;
        }
        return changed ? new ItemStackHandler(stacks) : selected;
    }

    /** Removes only the requested identities, including duplicate slots from older saves. */
    private static boolean removeSelections(List<ItemStack> stacks, Set<Object> keys) {
        boolean changed = false;
        for (int slot = 0; slot < stacks.size(); slot++) {
            ItemStack stack = stacks.get(slot);
            if (stack != null && keys.contains(selectionKey(stack))) {
                stacks.set(slot, null);
                changed = true;
            }
        }
        return changed;
    }

    /** Adds canonical server-side representatives to free slots, growing without clearing saved stacks. */
    private static boolean addSelections(List<ItemStack> stacks, Map<Object, ItemId> additions) {
        Set<Object> savedKeys = new HashSet<>();
        for (ItemStack stack : stacks) {
            if (stack != null) savedKeys.add(selectionKey(stack));
        }
        boolean changed = false;
        int slot = 0;
        for (Map.Entry<Object, ItemId> addition : additions.entrySet()) {
            if (!savedKeys.add(addition.getKey())) continue;
            while (slot < stacks.size() && stacks.get(slot) != null) slot++;
            ItemStack stack = addition.getValue()
                .getItemStack();
            if (slot == stacks.size()) stacks.add(stack);
            else stacks.set(slot, stack);
            slot++;
            changed = true;
        }
        return changed;
    }

    /** Tests an EX output against GUI selections or input ores, using the same identity as the panel. */
    public static boolean matchesOutput(List<ItemStack> selections, ItemStack output) {
        if (selections.isEmpty()) return false;
        Object key = selectionKey(output);
        for (ItemStack stack : selections) {
            if (stack != null && key.equals(selectionKey(stack))) return true;
        }
        return false;
    }

    /** Recognizes small/poor ore blocks even when GT's item ore dictionary test does not. */
    public static boolean isSmallOre(ItemStack stack) {
        MaterialKey key = materialKey(stack);
        return key != null && key.small();
    }

    /** Unresolved stacks match exact item, metadata and NBT, without considering stack size. */
    private static Object selectionKey(ItemStack stack) {
        MaterialKey key = materialKey(stack);
        return key == null ? ItemId.createNoCopy(stack) : key;
    }

    /** Reads concrete ore identities without retaining the pooled info or its stone type. */
    private static MaterialKey materialKey(ItemStack stack) {
        if (Items.feather.getDamage(stack) == OreDictionary.WILDCARD_VALUE) return null;
        try (OreInfo<?> info = OreManager.getOreInfo(stack)) {
            return info == null || info.material == null ? null : new MaterialKey(info.material, info.isSmall);
        }
    }

    /** Shared identity for a material's regular or small/poor ore, independent of surrounding stone. */
    @Desugar
    private record MaterialKey(IOreMaterial material, boolean small) {}
}
