package dev.nostalgiacraft.monitor.gametest;

import dev.nostalgiacraft.monitor.block.ActuatorBlockEntity;
import dev.nostalgiacraft.monitor.block.CentralBlockEntity;
import dev.nostalgiacraft.monitor.block.SensorBlock;
import dev.nostalgiacraft.monitor.block.SensorBlockEntity;
import dev.nostalgiacraft.monitor.data.Channel;
import dev.nostalgiacraft.monitor.data.ChannelKey;
import dev.nostalgiacraft.monitor.data.MonitorNetwork;
import dev.nostalgiacraft.monitor.data.SensorEntry;
import dev.nostalgiacraft.monitor.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.List;
import java.util.UUID;

public final class Scenario {
    public static final String EMPTY = "empty";
    public static final BlockPos CENTER = new BlockPos(1, 2, 1);
    public static final int TIMEOUT = 400;
    private static final double TOLERANCE = 1e-6;

    private final GameTestHelper helper;
    private final ChannelKey channel = new ChannelKey(UUID.randomUUID(), "test");

    public Scenario(GameTestHelper helper) {
        this.helper = helper;
    }

    public ChannelKey channel() {
        return channel;
    }

    public MonitorNetwork network() {
        return MonitorNetwork.get(helper.getLevel());
    }

    public SensorBlockEntity sensorFacing(BlockPos pos, Direction facing) {
        helper.setBlock(pos, ModBlocks.SENSOR.get().defaultBlockState().setValue(SensorBlock.FACING, facing));
        SensorBlockEntity sensor = (SensorBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (sensor == null || !sensor.joinChannel(channel)) {
            throw new GameTestAssertException("sensor did not join the channel");
        }
        return sensor;
    }

    public CentralBlockEntity central(BlockPos pos) {
        return centralOn(pos, channel);
    }

    public CentralBlockEntity centralOn(BlockPos pos, ChannelKey key) {
        helper.setBlock(pos, ModBlocks.CENTRAL.get());
        CentralBlockEntity central = (CentralBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (central == null || !central.joinChannel(key)) {
            throw new GameTestAssertException("central did not join the channel");
        }
        return central;
    }

    public ActuatorBlockEntity actuator(BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ACTUATOR.get());
        ActuatorBlockEntity actuator = (ActuatorBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (actuator == null || !actuator.joinChannel(channel)) {
            throw new GameTestAssertException("actuator did not join the channel");
        }
        return actuator;
    }

    public boolean emitting(BlockPos pos) {
        return helper.getLevel().getSignal(helper.absolutePos(pos), Direction.UP) > 0;
    }

    public void keepCharged(CentralBlockEntity central) {
        helper.onEachTick(() -> central.energy().receiveEnergy(Integer.MAX_VALUE, false));
    }

    public List<String> inbox() {
        return MonitorNetwork.get(helper.getLevel().getServer().overworld()).inbox().peek(channel.owner());
    }

    public boolean powered() {
        Channel joined = network().find(channel);
        return joined != null && joined.powered(helper.getLevel().getGameTime());
    }

    public SensorEntry entry(BlockPos pos) {
        Channel joined = network().find(channel);
        SensorEntry entry = joined == null ? null : joined.sensorAt(helper.absolutePos(pos));
        if (entry == null) {
            throw new GameTestAssertException("no sensor entry at " + pos);
        }
        return entry;
    }

    public void assertReading(BlockPos pos, double expected) {
        assertValue(entry(pos).value(), expected);
    }

    public void assertValue(double actual, double expected) {
        if (Double.isNaN(actual) || Math.abs(actual - expected) > TOLERANCE) {
            throw new GameTestAssertException("expected reading " + expected + " but was " + actual);
        }
    }
}
