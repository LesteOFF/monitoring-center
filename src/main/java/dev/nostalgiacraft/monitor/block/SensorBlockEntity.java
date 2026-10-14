package dev.nostalgiacraft.monitor.block;

import dev.nostalgiacraft.monitor.config.ServerConfig;
import dev.nostalgiacraft.monitor.data.Channel;
import dev.nostalgiacraft.monitor.data.ChannelKey;
import dev.nostalgiacraft.monitor.data.MonitorNetwork;
import dev.nostalgiacraft.monitor.data.SensorEntry;
import dev.nostalgiacraft.monitor.metric.ItemFlow;
import dev.nostalgiacraft.monitor.metric.Metric;
import dev.nostalgiacraft.monitor.metric.Metrics;
import dev.nostalgiacraft.monitor.metric.Sample;
import dev.nostalgiacraft.monitor.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

public final class SensorBlockEntity extends BlockEntity implements ChannelMember {
    public static final int MAX_NAME_LENGTH = 32;
    private static final String NAME = "name";
    private static final String METRIC = "metric";
    private static final String FILTER = "filter";
    private static final String FILTER_TAG = "filter_tag";
    private static final String TANK = "tank";
    private static final String RADIUS = "radius";
    private static final String ICON = "icon";
    private static final int DEFAULT_RADIUS = 4;
    private static final int MAX_TANK = 15;

    private final ItemFlow<Item> flow = new ItemFlow<>();
    private ChannelKey channel;
    private String name = "";
    private String metricId = "";
    private String customIcon = "";
    private String observedIcon = "";
    private ItemStack filter = ItemStack.EMPTY;
    private TagKey<Item> filterTag;
    private int tank;
    private int radius = DEFAULT_RADIUS;

    public SensorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SENSOR_ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (channel != null && level instanceof ServerLevel server) {
            MonitorNetwork.get(server).attach(this, server);
        }
    }

    @Override
    public boolean joinChannel(ChannelKey newChannel) {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        MonitorNetwork network = MonitorNetwork.get(server);
        if (newChannel.equals(channel)) {
            return network.attach(this, server);
        }
        if (!network.canAccept(newChannel)) {
            return false;
        }
        network.detach(channel, worldPosition);
        channel = newChannel;
        network.attach(this, server);
        changed();
        return true;
    }

    public void configure(String newName, String newMetricId, int newTank, int newRadius) {
        name = newName.length() > MAX_NAME_LENGTH ? newName.substring(0, MAX_NAME_LENGTH) : newName;
        metricId = Metrics.get(newMetricId) == null ? "" : newMetricId;
        tank = Mth.clamp(newTank, 0, MAX_TANK);
        radius = Mth.clamp(newRadius, 1, ServerConfig.AREA_MAX_RADIUS.get());
        observedIcon = "";
        flow.reset();
        changed();
    }

    public void setFilter(ItemStack stack) {
        filter = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        filterTag = null;
        flow.reset();
        changed();
    }

    public void setFilterTag(String tagId) {
        TagKey<Item> wanted = tagOf(tagId);
        filterTag = wanted != null && filter.is(wanted) ? wanted : null;
        flow.reset();
        changed();
    }

    public boolean accepts(ItemStack stack) {
        if (filter.isEmpty()) {
            return true;
        }
        return filterTag != null ? stack.is(filterTag) : ItemStack.isSameItemSameComponents(stack, filter);
    }

    @Nullable
    public TagKey<Item> filterTag() {
        return filterTag;
    }

    @Nullable
    private static TagKey<Item> tagOf(String tagId) {
        ResourceLocation id = tagId.isEmpty() ? null : ResourceLocation.tryParse(tagId);
        return id == null ? null : TagKey.create(Registries.ITEM, id);
    }

    @Nullable
    public Sample read(ServerLevel server) {
        Metric metric = settleMetric(server);
        return metric.area() || server.isLoaded(targetPos()) ? metric.reader().read(server, this) : null;
    }

    public Metric metric(ServerLevel server) {
        Metric chosen = Metrics.get(metricId);
        if (chosen != null) {
            return chosen;
        }
        return server.isLoaded(targetPos()) ? Metrics.detect(server, this) : Metrics.get(Metrics.REDSTONE);
    }

    private Metric settleMetric(ServerLevel server) {
        Metric metric = metric(server);
        if (!metric.id().equals(metricId) && !metric.id().equals(Metrics.REDSTONE)) {
            metricId = metric.id();
            setChanged();
        }
        return metric;
    }

    public void describe(SensorEntry entry, ServerLevel server) {
        entry.describe(name, metric(server), icon(server));
    }

    @Nullable
    public <T> T neighbor(BlockCapability<T, Direction> capability) {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }
        T inside = server.getCapability(capability, targetPos(), null);
        return inside != null ? inside : server.getCapability(capability, targetPos(), facing().getOpposite());
    }

    public ItemFlow<Item> flow() {
        return flow;
    }

    public void serverTick() {
        if (Metrics.tracksItemFlow(metricId)) {
            Metrics.trackItemFlow(this);
        }
    }

    public void observeIcon(String metric, String icon) {
        if (metric.equals(metricId)) {
            observedIcon = icon;
        }
    }

    public Direction facing() {
        return getBlockState().getValue(SensorBlock.FACING);
    }

    public BlockPos targetPos() {
        return worldPosition.relative(facing());
    }

    @Override
    @Nullable
    public ChannelKey channel() {
        return channel;
    }

    public String name() {
        return name;
    }

    public String metricId() {
        return metricId;
    }

    public ItemStack filter() {
        return filter;
    }

    public int tank() {
        return tank;
    }

    public int radius() {
        return radius;
    }

    private String icon(ServerLevel server) {
        if (!customIcon.isEmpty()) {
            return customIcon;
        }
        if (!filter.isEmpty()) {
            return "item:" + BuiltInRegistries.ITEM.getKey(filter.getItem());
        }
        return observedIcon.isEmpty() ? metric(server).icon() : observedIcon;
    }

    private void changed() {
        setChanged();
        if (level instanceof ServerLevel server) {
            Channel joined = MonitorNetwork.get(server).find(channel);
            SensorEntry entry = joined == null ? null : joined.sensorAt(worldPosition);
            if (entry != null) {
                describe(entry, server);
            }
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (channel != null) {
            channel.save(tag);
        }
        tag.putString(NAME, name);
        tag.putString(METRIC, metricId);
        tag.putString(ICON, customIcon);
        tag.putInt(TANK, tank);
        tag.putInt(RADIUS, radius);
        if (!filter.isEmpty()) {
            tag.put(FILTER, filter.save(registries));
        }
        if (filterTag != null) {
            tag.putString(FILTER_TAG, filterTag.location().toString());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        channel = ChannelKey.load(tag);
        name = tag.getString(NAME);
        metricId = tag.getString(METRIC);
        customIcon = tag.getString(ICON);
        tank = tag.getInt(TANK);
        radius = tag.contains(RADIUS) ? tag.getInt(RADIUS) : DEFAULT_RADIUS;
        filter = tag.contains(FILTER) ? ItemStack.parseOptional(registries, tag.getCompound(FILTER)) : ItemStack.EMPTY;
        filterTag = filter.isEmpty() ? null : tagOf(tag.getString(FILTER_TAG));
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
