package dev.nostalgiacraft.monitor.block;

import dev.nostalgiacraft.monitor.data.Channel;
import dev.nostalgiacraft.monitor.data.ChannelKey;
import dev.nostalgiacraft.monitor.data.MonitorNetwork;
import dev.nostalgiacraft.monitor.data.SensorEntry;
import dev.nostalgiacraft.monitor.panel.PanelConfig;
import dev.nostalgiacraft.monitor.panel.PanelLayout;
import dev.nostalgiacraft.monitor.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ScreenBlockEntity extends BlockEntity implements ChannelMember {
    private static final String WIDTH = "width";
    private static final String HEIGHT = "height";
    private static final String CONFIG = "config";
    private static final String LINKS = "links";
    private static final String PAGE = "page";
    private static final int MAX_LINK_LENGTH = 64;

    private ChannelKey channel;
    private PanelConfig config = PanelConfig.DEFAULT;
    private int width = 1;
    private int height = 1;
    private int page;
    private List<String> linkLines = List.of();
    private AABB bounds;
    private Vec3 center;

    public ScreenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SCREEN_ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (channel != null && level instanceof ServerLevel server) {
            MonitorNetwork.get(server).attachPanel(channel, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) {
            MonitorNetwork.get(server).detachPanel(channel, worldPosition);
        }
    }

    @Override
    @Nullable
    public ChannelKey channel() {
        return channel;
    }

    @Override
    public boolean joinChannel(ChannelKey newChannel) {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        MonitorNetwork network = MonitorNetwork.get(server);
        network.detachPanel(channel, worldPosition);
        channel = newChannel;
        network.attachPanel(channel, worldPosition);
        changed();
        return true;
    }

    public void adopt(@Nullable ChannelKey donorChannel, PanelConfig donorConfig, boolean replace) {
        if (donorChannel != null && (channel == null || replace && !(donorChannel.equals(channel) && donorConfig.equals(config)))) {
            config = donorConfig;
            joinChannel(donorChannel);
        }
    }

    public void resize(int newWidth, int newHeight) {
        width = newWidth;
        height = newHeight;
        bounds = null;
        changed();
    }

    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        bounds = null;
    }

    public void setConfig(PanelConfig newConfig) {
        config = newConfig;
        page = 0;
        changed();
    }

    public void paintRow(double clickedY, int color) {
        PanelLayout.Size size = size();
        List<PanelConfig.Row> rows = new ArrayList<>(config.rows().isEmpty() ? automaticRows() : config.rows());
        int pageSize = PanelLayout.pageSize(PanelConfig.Mode.LIST, size, itemCount());
        int row = PanelLayout.rowAt(size, pixelFromTop(clickedY));
        int index = page * pageSize + row;
        if (row >= 0 && row < pageSize && index < rows.size()) {
            rows.set(index, rows.get(index).withColor(color));
            setConfig(config.withRows(rows));
        }
    }

    public void acceptLinkLines(int firstLine, List<String> lines) {
        List<String> updated = new ArrayList<>(linkLines);
        for (int offset = 0; offset < lines.size() && firstLine + offset < PanelLayout.MAX_LINES; offset++) {
            while (updated.size() <= firstLine + offset) {
                updated.add("");
            }
            String line = lines.get(offset);
            updated.set(firstLine + offset, line.length() > MAX_LINK_LENGTH ? line.substring(0, MAX_LINK_LENGTH) : line);
        }
        if (!updated.equals(linkLines)) {
            linkLines = updated;
            changed();
        }
    }

    public void clearLinkLines() {
        linkLines = List.of();
    }

    public String linkLine(int index) {
        return index < linkLines.size() ? linkLines.get(index) : "";
    }

    public List<String> linkLines() {
        return linkLines;
    }

    public boolean touch(Vec3 hit) {
        PanelLayout.Size size = size();
        int items = itemCount();
        int step = PanelLayout.footerStep(PanelLayout.footer(config.mode(), size, items), pixelFromLeft(hit), pixelFromTop(hit.y));
        if (step == 0) {
            return false;
        }
        page = Math.floorMod(page + step, PanelLayout.pageCount(items, PanelLayout.pageSize(config.mode(), size, items)));
        changed();
        return true;
    }

    public int page() {
        return page;
    }

    public int scale() {
        return PanelLayout.scale(width, height, config.scale());
    }

    public PanelLayout.Size size() {
        return PanelLayout.size(width, height, config.scale());
    }

    private int pixelFromTop(double worldY) {
        return PanelLayout.pixel(worldPosition.getY() + height - worldY, scale());
    }

    private int pixelFromLeft(Vec3 hit) {
        Direction right = ScreenMultiblock.right(facing());
        Direction.Axis axis = right.getAxis();
        int step = right.getAxisDirection().getStep();
        double leftEdge = worldPosition.get(axis) + (step > 0 ? 0 : 1);
        return PanelLayout.pixel((hit.get(axis) - leftEdge) * step, scale());
    }

    private int itemCount() {
        Channel joined = level instanceof ServerLevel server ? MonitorNetwork.get(server).find(channel) : null;
        if (joined == null) {
            return 0;
        }
        return switch (config.mode()) {
            case ALARMS -> joined.alarms().lines().size();
            case LIST -> Math.max(linkLines.size(), config.rows().isEmpty() ? joined.entries().size() : config.rows().size());
            case CARDS, GAUGE -> config.rows().isEmpty() ? joined.entries().size() : config.rows().size();
            case GRAPH, HIGHLIGHT -> 0;
        };
    }

    public boolean customized() {
        return channel != null && !config.equals(PanelConfig.DEFAULT);
    }

    public CompoundTag keepsake() {
        CompoundTag tag = new CompoundTag();
        channel.save(tag);
        tag.put(CONFIG, config.toTag());
        return tag;
    }

    public PanelConfig config() {
        return config;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Direction facing() {
        return getBlockState().getValue(ScreenBlock.FACING);
    }

    public AABB panelBounds() {
        if (bounds == null) {
            Direction right = ScreenMultiblock.right(facing());
            bounds = AABB.encapsulatingFullBlocks(worldPosition, worldPosition.relative(right, width - 1).above(height - 1));
            center = bounds.getCenter();
        }
        return bounds;
    }

    public AABB panelShape() {
        double empty = 1 - ScreenBlock.THICKNESS;
        return panelBounds().contract(facing().getStepX() * empty, 0, facing().getStepZ() * empty);
    }

    public Vec3 panelCenter() {
        panelBounds();
        return center;
    }

    private List<PanelConfig.Row> automaticRows() {
        List<PanelConfig.Row> rows = new ArrayList<>();
        Channel joined = level instanceof ServerLevel server ? MonitorNetwork.get(server).find(channel) : null;
        if (joined != null) {
            for (SensorEntry entry : joined.entries()) {
                rows.add(PanelConfig.Row.of(entry.id()));
            }
        }
        return rows;
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (channel != null) {
            channel.save(tag);
        }
        tag.putInt(WIDTH, width);
        tag.putInt(HEIGHT, height);
        tag.putInt(PAGE, page);
        tag.put(CONFIG, config.toTag());
        ListTag links = new ListTag();
        linkLines.forEach(line -> links.add(StringTag.valueOf(line)));
        tag.put(LINKS, links);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        channel = ChannelKey.load(tag);
        width = Mth.clamp(tag.getInt(WIDTH), 1, PanelLayout.MAX_WIDTH);
        height = Mth.clamp(tag.getInt(HEIGHT), 1, PanelLayout.MAX_HEIGHT);
        config = PanelConfig.fromTag(tag.getCompound(CONFIG));
        page = Math.max(0, tag.getInt(PAGE));
        linkLines = tag.getList(LINKS, Tag.TAG_STRING).stream().limit(PanelLayout.MAX_LINES).map(Tag::getAsString).toList();
        bounds = null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
