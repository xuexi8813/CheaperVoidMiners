package com.jdz.vmd.common.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.IDynamicSyncNotifiable;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.jdz.vmd.api.IVoidMinerOverride;
import com.jdz.vmd.common.tileentities.ExVoidMinerFilter.Operation;
import com.jdz.vmd.common.tileentities.ExVoidMinerFilter.View;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.util.GTUtility.ItemId;

/** EX content for GT's existing filter popup. Only its client-side ore grid is rebuilt when the table changes. */
public final class ExVoidMinerFilterPanel {

    /** One stable registration in the GT subpanel, regardless of the current number of rows. */
    private static final String SYNC_KEY = "vmd$exFilter";
    /** Match GT's ten-column grid, with at most eight rows visible at once. */
    private static final int COLUMNS = 10;
    private static final int VISIBLE_ROWS = 8;
    private static final int CELL_SIZE = 18;

    private ExVoidMinerFilterPanel() {}

    /** Builds the shared panel shell; the server never builds ore widgets or reads client display names. */
    public static ModularPanel create(PanelSyncManager syncManager, IVoidMinerOverride miner) {
        StringValue search = new StringValue("");
        FilterSync sync = new FilterSync(miner, search);
        syncManager.syncValue(SYNC_KEY, sync);
        syncManager.onServerTick(sync::publishChanges);
        return new ModularPanel("gt:vm:filter").child(ButtonWidget.panelCloseButton())
            .child(
                Flow.column()
                    .child(
                        IKey.lang("GT5U.gui.text.vm.title")
                            .asWidget())
                    .child(
                        Flow.row()
                            .child(
                                new DynamicSyncedWidget<>().syncHandler(SYNC_KEY)
                                    .initialChild(createOreGrid(sync, search, View.EMPTY))
                                    .coverChildren())
                            .child(createControls(sync))
                            .childPadding(3)
                            .crossAxisAlignment(Alignment.CrossAxis.START)
                            .coverChildren())
                    .child(
                        new TextFieldWidget().value(search)
                            .hintText(StatCollector.translateToLocal("GT5U.gui.text.vm.searchhint"))
                            .autoUpdateOnChange(true)
                            .anchorLeft(0)
                            .width(100))
                    .childPadding(3)
                    .margin(8)
                    .coverChildren())
            .coverChildren();
    }

    /** Displays an empty result or builds the rows for a particular revision without adding any sync registrations. */
    private static IWidget createOreGrid(FilterSync sync, StringValue search, View table) {
        if (table.rows.isEmpty()) {
            return IKey.lang("vmd.gui.filter.no_ores")
                .color(Color.WHITE.main)
                .asWidget()
                .width(CELL_SIZE * COLUMNS);
        }
        int rowCount = (table.rows.size() + COLUMNS - 1) / COLUMNS;
        return new ListWidget<>().child(
            Flow.row()
                .children(table.rows.size(), row -> createOreButton(sync, search, table, row))
                .wrap()
                .crossAxisAlignment(Alignment.CrossAxis.START)
                .width(CELL_SIZE * COLUMNS)
                .coverChildrenHeight()
                .collapseDisabledChild())
            .coverChildrenWidth()
            .height(CELL_SIZE * Math.min(rowCount, VISIBLE_ROWS));
    }

    /** Capture the row's table revision, so a widget can never rebind its index to a different table. */
    private static ToggleButton createOreButton(FilterSync sync, StringValue search, View table, int row) {
        ItemStack stack = table.rows.get(row)
            .getItemStack();
        String oreName = stack.getDisplayName()
            .toLowerCase(Locale.ROOT);
        return new ToggleButton()
            .value(
                new BoolValue.Dynamic(
                    () -> sync.view.isSelected(row),
                    selected -> sync.request(table.revision, row, selected ? Operation.SELECT : Operation.DESELECT)))
            .overlay(
                new ItemDrawable(stack).asIcon()
                    .size(16))
            .background(true, GTGuiTextures.BUTTON_STANDARD_PRESSED.withColorOverride(Color.GREY.main))
            .background(false, GTGuiTextures.BUTTON_STANDARD)
            .tooltipBuilder(false, tooltip -> describeOre(tooltip, stack, sync.view.blacklist, false))
            .tooltipBuilder(true, tooltip -> describeOre(tooltip, stack, sync.view.blacklist, true))
            .setEnabledIf(
                widget -> oreName.contains(
                    search.getStringValue()
                        .toLowerCase(Locale.ROOT)));
    }

    /** Retains the GT void/no-void item tooltip, based on the most recently received mode. */
    private static void describeOre(RichTooltip tooltip, ItemStack stack, boolean blacklist, boolean selected) {
        tooltip.addFromItem(stack);
        tooltip.addLine(
            StatCollector.translateToLocal(
                blacklist == selected ? "GT5U.gui.button.vm.tt.void" : "GT5U.gui.button.vm.tt.novoid"));
        tooltip.setAutoUpdate(true);
    }

    /** Creates GT's existing mode and bulk buttons; their only side effect is sending an action request. */
    private static Flow createControls(FilterSync sync) {
        return Flow.column()
            .child(
                new ToggleButton().value(
                    new BoolValue.Dynamic(
                        () -> sync.view.blacklist,
                        blacklist -> sync
                            .request(sync.view.revision, -1, blacklist ? Operation.BLACKLIST : Operation.WHITELIST)))
                    .tooltip(
                        false,
                        tooltip -> tooltip.add(StatCollector.translateToLocal("GT5U.gui.button.vm.whitelist")))
                    .tooltip(
                        true,
                        tooltip -> tooltip.add(StatCollector.translateToLocal("GT5U.gui.button.vm.blacklist")))
                    .overlay(
                        false,
                        GTGuiTextures.OVERLAY_BUTTON_WHITELIST.asIcon()
                            .size(16))
                    .overlay(
                        true,
                        GTGuiTextures.OVERLAY_BUTTON_BLACKLIST.asIcon()
                            .size(16)))
            .child(new ButtonWidget<>().onMousePressed(button -> {
                sync.request(sync.view.revision, -1, Operation.SELECT_ALL);
                return true;
            })
                .tooltip(tooltip -> tooltip.add(StatCollector.translateToLocal("GT5U.gui.button.vm.select")))
                .overlay(
                    GTGuiTextures.OVERLAY_BUTTON_CHECKMARK.asIcon()
                        .size(16)))
            .child(new ButtonWidget<>().onMousePressed(button -> {
                sync.request(sync.view.revision, -1, Operation.DESELECT_ALL);
                return true;
            })
                .tooltip(tooltip -> tooltip.add(StatCollector.translateToLocal("GT5U.gui.button.vm.deselect")))
                .overlay(
                    GTGuiTextures.OVERLAY_BUTTON_CROSS.asIcon()
                        .size(16)))
            .crossAxisAlignment(Alignment.CrossAxis.START)
            .childPadding(3)
            .coverChildren();
    }

    /** Transfers authoritative snapshots in one direction and revision-tagged actions in the other. */
    private static final class FilterSync extends SyncHandler<FilterSync> implements IDynamicSyncNotifiable {

        /** Packet discriminators prevent either side from accepting the other side's message shape. */
        private static final int SNAPSHOT = 0;
        private static final int EDIT = 1;
        private static final Operation[] OPERATIONS = Operation.values();

        /** Server authority for resolving the current revision and validating all filter changes. */
        private final IVoidMinerOverride miner;
        /** Client-only text; reused when a new ore grid replaces the old one. */
        private final StringValue search;
        /** Last complete client snapshot, never mutated by a widget. */
        private View view = View.EMPTY;
        /** Last snapshot sent on this panel opening; null ensures a fresh snapshot on re-open. */
        private View sent;
        /** MUI callback that swaps and initializes the grid subtree in the existing panel. */
        private Consumer<IWidget> updateGrid;

        private FilterSync(IVoidMinerOverride miner, StringValue search) {
            this.miner = miner;
            this.search = search;
            allowC2S();
        }

        @Override
        public void init(String key, PanelSyncManager syncManager) {
            super.init(key, syncManager);
            sent = null;
        }

        @Override
        public void attachDynamicWidgetListener(Consumer<IWidget> listener) {
            // MUI calls this during widget initialization, before it initializes the existing child.
            updateGrid = listener;
        }

        /** Called once per server tick while this subpanel is open, including during machine downtime. */
        private void publishChanges() {
            View next = miner.vmd$getExFilterView();
            if (next.equals(sent)) return;
            syncToClient(SNAPSHOT, buffer -> {
                buffer.writeLong(next.revision);
                buffer.writeBoolean(next.blacklist);
                buffer.writeVarIntToBuffer(next.rows.size());
                for (int index = 0; index < next.rows.size(); index++) {
                    ItemId ore = next.rows.get(index);
                    buffer.writeVarIntToBuffer(Item.getIdFromItem(ore.item()));
                    buffer.writeInt(ore.metaData());
                    buffer.writeNBTTagCompoundToBuffer(ore.nbt());
                    buffer.writeBoolean(next.isSelected(index));
                }
            });
            sent = next;
        }

        /** Send only fixed-size action data, with no client-supplied item identity or selection state. */
        private void request(long revision, int row, Operation operation) {
            if (revision == View.EMPTY.revision) return;
            sync(EDIT, buffer -> {
                buffer.writeLong(revision);
                buffer.writeInt(row);
                buffer.writeByte(operation.ordinal());
            });
        }

        @Override
        public void readOnClient(int id, PacketBuffer buffer) throws IOException {
            if (id != SNAPSHOT) throw new IOException("Invalid EX filter snapshot packet: " + id);
            long revision = buffer.readLong();
            boolean blacklist = buffer.readBoolean();
            int count = buffer.readVarIntFromBuffer();
            // Each row uses at least one item ID byte, four metadata bytes, two NBT length bytes and one mark byte.
            if (count < 0 || count > buffer.readableBytes() / 8) {
                throw new IOException("Invalid EX filter row count: " + count);
            }
            List<ItemId> rows = new ArrayList<>(count);
            BitSet selected = new BitSet(count);
            for (int index = 0; index < count; index++) {
                int itemId = buffer.readVarIntFromBuffer();
                Item item = Item.getItemById(itemId);
                if (item == null) throw new IOException("Unknown item in EX filter snapshot: " + itemId);
                rows.add(ItemId.createNoCopy(item, buffer.readInt(), buffer.readNBTTagCompoundFromBuffer()));
                if (buffer.readBoolean()) selected.set(index);
            }
            if (buffer.isReadable()) throw new IOException("Trailing data in EX filter snapshot");
            boolean replacedTable = revision != view.revision || !view.rows.equals(rows);
            view = View.received(revision, rows, selected, blacklist);
            if (replacedTable && updateGrid != null) updateGrid.accept(createOreGrid(this, search, view));
        }

        @Override
        public void readOnServer(int id, PacketBuffer buffer) throws IOException {
            if (id != EDIT || buffer.readableBytes() != Long.BYTES + Integer.BYTES + Byte.BYTES) {
                throw new IOException("Invalid EX filter edit packet: " + id);
            }
            long revision = buffer.readLong();
            int row = buffer.readInt();
            int operation = buffer.readUnsignedByte();
            if (operation >= OPERATIONS.length) throw new IOException("Invalid EX filter action: " + operation);
            miner.vmd$applyExFilterAction(revision, row, OPERATIONS[operation]);
        }
    }
}
