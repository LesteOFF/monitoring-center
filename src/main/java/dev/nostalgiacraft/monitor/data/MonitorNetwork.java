package dev.nostalgiacraft.monitor.data;

import dev.nostalgiacraft.monitor.MonitoringCenter;
import dev.nostalgiacraft.monitor.alarm.Inbox;
import dev.nostalgiacraft.monitor.alarm.Notifier;
import dev.nostalgiacraft.monitor.block.CentralBlockEntity;
import dev.nostalgiacraft.monitor.block.SensorBlockEntity;
import dev.nostalgiacraft.monitor.config.ServerConfig;
import dev.nostalgiacraft.monitor.metric.Sample;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MonitorNetwork extends SavedData {
    private static final String CHANNELS = "channels";
    private static final String INBOX = "inbox";
    private static final String PLAYER = "player";
    private static final String NOTES = "notes";
    private static final String TIME = "time";
    private static final long NOTE_LIFETIME_MILLIS = Duration.ofDays(30).toMillis();
    private static final double PERCENT = 100.0;
    private static final int SAVE_INTERVAL_TICKS = 1200;
    private static final Factory<MonitorNetwork> FACTORY = new Factory<>(MonitorNetwork::new, MonitorNetwork::load);

    private final Map<ChannelKey, Channel> channels = new HashMap<>();
    private final List<SensorEntry> slots = new ArrayList<>();
    private final List<SensorEntry> orphans = new ArrayList<>();
    private final Inbox inbox = new Inbox();

    public static MonitorNetwork get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, MonitoringCenter.MODID);
    }

    public Channel channel(ChannelKey key) {
        return channels.computeIfAbsent(key, Channel::new);
    }

    @Nullable
    public Channel find(@Nullable ChannelKey key) {
        return key == null ? null : channels.get(key);
    }

    public Collection<Channel> channels() {
        return channels.values();
    }

    public boolean attach(SensorBlockEntity sensor, ServerLevel level) {
        Channel channel = find(sensor.channel());
        boolean known = channel != null && channel.sensorAt(sensor.getBlockPos()) != null;
        if (!known && !canAccept(sensor.channel())) {
            return false;
        }
        sensor.describe(channel(sensor.channel()).add(sensor.getBlockPos()), level);
        if (!known) {
            rebuildSlots();
        }
        return true;
    }

    public boolean canAccept(ChannelKey key) {
        Channel channel = find(key);
        return channel == null || channel.sensors().size() < ServerConfig.MAX_SENSORS_PER_CHANNEL.get();
    }

    public void detach(@Nullable ChannelKey key, BlockPos pos) {
        Channel channel = find(key);
        if (channel != null) {
            channel.remove(pos);
            discardIfUnused(channel);
            rebuildSlots();
        }
    }

    public boolean attachCentral(ChannelKey key, BlockPos pos) {
        Channel channel = channel(key);
        if (channel.central() != null && !channel.central().equals(pos)) {
            return false;
        }
        channel.setCentral(pos);
        setDirty();
        return true;
    }

    public void detachCentral(@Nullable ChannelKey key, BlockPos pos) {
        Channel channel = find(key);
        if (channel != null && pos.equals(channel.central())) {
            channel.setCentral(null);
            discardIfUnused(channel);
            setDirty();
        }
    }

    public void attachPanel(ChannelKey key, BlockPos pos) {
        channel(key).panels().add(pos.immutable());
    }

    public void detachPanel(@Nullable ChannelKey key, BlockPos pos) {
        Channel channel = find(key);
        if (channel != null && channel.panels().remove(pos)) {
            discardIfUnused(channel);
        }
    }

    public void tick(ServerLevel level) {
        if (channels.isEmpty()) {
            return;
        }
        long tick = level.getGameTime();
        SensorScheduler.run(tick, slots, entry -> entry.record(read(level, entry), SensorScheduler.secondsPerSample(entry)));
        dropOrphans();
        long noticeCooldown = ServerConfig.NOTICE_COOLDOWN_SECONDS.get() * (long) SensorScheduler.ACTIVE_PERIOD;
        for (Channel channel : channels.values()) {
            if (channel.powerLost(tick, noticeCooldown) && channel.central() != null && level.isLoaded(channel.central())) {
                Notifier.centralOff(level.getServer(), channel);
            }
        }
        if (tick % SensorScheduler.ACTIVE_PERIOD == 0) {
            dropMissingCentrals(level);
            evaluate(level, tick, noticeCooldown);
            PanelSync.run(level, channels.values());
        }
        if (tick % SAVE_INTERVAL_TICKS == 0) {
            setDirty();
        }
    }

    public void discardIfUnused(Channel channel) {
        if (channel.unused()) {
            channels.remove(channel.key());
        }
    }

    @Nullable
    private Sample read(ServerLevel level, SensorEntry entry) {
        if (!level.isLoaded(entry.pos())) {
            return null;
        }
        if (!(level.getBlockEntity(entry.pos()) instanceof SensorBlockEntity sensor) || !entry.channel().key().equals(sensor.channel())) {
            orphans.add(entry);
            return null;
        }
        if (!entry.channel().powered(level.getGameTime())) {
            return null;
        }
        Sample sample = sensor.read(level);
        sensor.describe(entry, level);
        return sample;
    }

    private void evaluate(ServerLevel level, long tick, long noticeCooldown) {
        double hysteresis = hysteresis();
        for (Channel channel : channels.values()) {
            channel.sampleSums();
            if (channel.powered(tick)) {
                List<AlarmBoard.Notice> notices = channel.evaluateAlarms(tick, hysteresis, noticeCooldown);
                if (!notices.isEmpty()) {
                    Notifier.alarms(level.getServer(), channel, notices);
                }
            }
        }
    }

    public static double hysteresis() {
        return ServerConfig.ALARM_HYSTERESIS_PERCENT.get() / PERCENT;
    }

    public Inbox inbox() {
        return inbox;
    }

    private void dropMissingCentrals(ServerLevel level) {
        for (Channel channel : List.copyOf(channels.values())) {
            BlockPos central = channel.central();
            if (central != null && level.isLoaded(central) && !(level.getBlockEntity(central) instanceof CentralBlockEntity found
                    && channel.key().equals(found.channel()))) {
                detachCentral(channel.key(), central);
            }
        }
    }

    private void dropOrphans() {
        if (orphans.isEmpty()) {
            return;
        }
        orphans.forEach(entry -> detach(entry.channel().key(), entry.pos()));
        orphans.clear();
    }

    private void rebuildSlots() {
        slots.clear();
        channels.values().forEach(channel -> slots.addAll(channel.sensors()));
        setDirty();
    }

    private ListTag savedInbox() {
        ListTag owners = new ListTag();
        for (UUID player : inbox.players()) {
            CompoundTag owner = new CompoundTag();
            ListTag notes = new ListTag();
            inbox.peek(player).forEach(note -> notes.add(StringTag.valueOf(note)));
            owner.putUUID(PLAYER, player);
            owner.putLong(TIME, inbox.lastAdded(player));
            owner.put(NOTES, notes);
            owners.add(owner);
        }
        return owners;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        channels.values().forEach(channel -> list.add(channel.save()));
        tag.put(CHANNELS, list);
        tag.put(INBOX, savedInbox());
        return tag;
    }

    private static MonitorNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
        MonitorNetwork network = new MonitorNetwork();
        for (Tag element : tag.getList(CHANNELS, Tag.TAG_COMPOUND)) {
            Channel channel = Channel.load((CompoundTag) element);
            if (!channel.unused()) {
                network.channels.put(channel.key(), channel);
            }
        }
        long now = System.currentTimeMillis();
        for (Tag element : tag.getList(INBOX, Tag.TAG_COMPOUND)) {
            CompoundTag owner = (CompoundTag) element;
            long time = owner.contains(TIME) ? owner.getLong(TIME) : now;
            owner.getList(NOTES, Tag.TAG_STRING).forEach(note -> network.inbox.add(owner.getUUID(PLAYER), note.getAsString(), time));
        }
        network.inbox.dropOlderThan(now - NOTE_LIFETIME_MILLIS);
        network.rebuildSlots();
        return network;
    }
}
