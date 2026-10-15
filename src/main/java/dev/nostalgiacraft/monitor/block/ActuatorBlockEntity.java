package dev.nostalgiacraft.monitor.block;

import dev.nostalgiacraft.monitor.alarm.AlarmState;
import dev.nostalgiacraft.monitor.data.ActuatorSettings;
import dev.nostalgiacraft.monitor.data.AlarmBoard;
import dev.nostalgiacraft.monitor.data.Channel;
import dev.nostalgiacraft.monitor.data.ChannelKey;
import dev.nostalgiacraft.monitor.data.MonitorNetwork;
import dev.nostalgiacraft.monitor.data.SensorEntry;
import dev.nostalgiacraft.monitor.data.SensorScheduler;
import dev.nostalgiacraft.monitor.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class ActuatorBlockEntity extends BlockEntity implements ChannelMember {
    private static final String SETTINGS = "actuator";
    private static final String CONDITION_ACTIVE = "condition_active";

    private final AlarmState condition = new AlarmState();
    private ChannelKey channel;
    private ActuatorSettings settings = ActuatorSettings.DEFAULT;

    public ActuatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ACTUATOR_ENTITY.get(), pos, state);
    }

    public void serverTick(ServerLevel server) {
        long tick = server.getGameTime();
        if (tick % SensorScheduler.ACTIVE_PERIOD != 0) {
            return;
        }
        Channel joined = MonitorNetwork.get(server).find(channel);
        boolean powered = joined != null && joined.powered(tick);
        emit(server, powered && settings.output(conditionMet(joined)));
    }

    private boolean conditionMet(Channel joined) {
        return switch (settings.source()) {
            case RULE -> joined.alarms().active(settings.ruleId());
            case SENSOR -> sensorConditionMet(joined);
        };
    }

    private boolean sensorConditionMet(Channel joined) {
        SensorEntry entry = joined.sensor(settings.condition().sensorId());
        double value = entry == null ? Double.NaN : AlarmBoard.observed(settings.condition(), entry);
        condition.update(settings.condition(), value, MonitorNetwork.hysteresis());
        return condition.active();
    }

    private void emit(ServerLevel server, boolean output) {
        BlockState state = getBlockState();
        if (state.getValue(ActuatorBlock.POWERED) != output) {
            server.setBlock(worldPosition, state.setValue(ActuatorBlock.POWERED, output), Block.UPDATE_ALL);
            setChanged();
        }
    }

    public void configure(ActuatorSettings newSettings) {
        settings = newSettings;
        condition.restore(false);
        setChanged();
    }

    public ActuatorSettings settings() {
        return settings;
    }

    @Override
    @Nullable
    public ChannelKey channel() {
        return channel;
    }

    @Override
    public boolean joinChannel(ChannelKey newChannel) {
        if (!newChannel.equals(channel)) {
            channel = newChannel;
            condition.restore(false);
            setChanged();
        }
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (channel != null) {
            channel.save(tag);
        }
        tag.put(SETTINGS, settings.toTag());
        tag.putBoolean(CONDITION_ACTIVE, condition.active());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        channel = ChannelKey.load(tag);
        settings = ActuatorSettings.fromTag(tag.getCompound(SETTINGS));
        condition.restore(tag.getBoolean(CONDITION_ACTIVE));
    }
}
