package dev.nostalgiacraft.monitor.network;

import dev.nostalgiacraft.monitor.MonitoringCenter;
import dev.nostalgiacraft.monitor.block.SensorBlockEntity;
import dev.nostalgiacraft.monitor.data.ChannelKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

public final class Payloads {
    private static final StreamCodec<ByteBuf, String> ID = ByteBufCodecs.stringUtf8(64);
    private static final StreamCodec<ByteBuf, String> TEXT = ByteBufCodecs.stringUtf8(128);

    private Payloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(MonitoringCenter.id(name));
    }

    private static final int MAX_SAMPLES = 4096;
    private static final StreamCodec<ByteBuf, float[]> SAMPLES = StreamCodec.of(Payloads::writeSamples, Payloads::readSamples);

    private static void writeSamples(ByteBuf buffer, float[] samples) {
        VarInt.write(buffer, samples.length);
        for (float sample : samples) {
            buffer.writeFloat(sample);
        }
    }

    private static float[] readSamples(ByteBuf buffer) {
        float[] samples = new float[Math.min(VarInt.read(buffer), MAX_SAMPLES)];
        for (int index = 0; index < samples.length; index++) {
            samples[index] = buffer.readFloat();
        }
        return samples;
    }

    public record AlarmLine(String message, int severity, boolean active) {
        public static final StreamCodec<ByteBuf, AlarmLine> CODEC = StreamCodec.composite(
                TEXT, AlarmLine::message, ByteBufCodecs.VAR_INT, AlarmLine::severity, ByteBufCodecs.BOOL, AlarmLine::active, AlarmLine::new);
    }

    public record SensorState(int id, String name, String unit, String icon, String metric, double value, double capacity, int alarm,
                              float[] seconds) {
        public static final StreamCodec<ByteBuf, SensorState> CODEC = new StreamCodec<>() {
            @Override
            public SensorState decode(ByteBuf buffer) {
                return new SensorState(VarInt.read(buffer), TEXT.decode(buffer), TEXT.decode(buffer), TEXT.decode(buffer),
                        ID.decode(buffer), buffer.readDouble(), buffer.readDouble(), VarInt.read(buffer), SAMPLES.decode(buffer));
            }

            @Override
            public void encode(ByteBuf buffer, SensorState state) {
                VarInt.write(buffer, state.id());
                TEXT.encode(buffer, state.name());
                TEXT.encode(buffer, state.unit());
                TEXT.encode(buffer, state.icon());
                ID.encode(buffer, state.metric());
                buffer.writeDouble(state.value());
                buffer.writeDouble(state.capacity());
                VarInt.write(buffer, state.alarm());
                SAMPLES.encode(buffer, state.seconds());
            }
        };
    }

    public record Reading(int id, double value, double capacity, int alarm) {
        public static final StreamCodec<ByteBuf, Reading> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Reading::id, ByteBufCodecs.DOUBLE, Reading::value, ByteBufCodecs.DOUBLE, Reading::capacity,
                ByteBufCodecs.VAR_INT, Reading::alarm, Reading::new);
    }

    public record ChannelSnapshot(ChannelKey key, boolean powered, List<SensorState> sensors, List<AlarmLine> alarms)
            implements CustomPacketPayload {
        public static final Type<ChannelSnapshot> TYPE = typeOf("channel_snapshot");
        public static final StreamCodec<ByteBuf, ChannelSnapshot> CODEC = StreamCodec.composite(
                ChannelKey.STREAM_CODEC, ChannelSnapshot::key, ByteBufCodecs.BOOL, ChannelSnapshot::powered,
                SensorState.CODEC.apply(ByteBufCodecs.list()), ChannelSnapshot::sensors,
                AlarmLine.CODEC.apply(ByteBufCodecs.list()), ChannelSnapshot::alarms, ChannelSnapshot::new);

        public Type<ChannelSnapshot> type() {
            return TYPE;
        }
    }

    public record ChannelDelta(ChannelKey key, boolean powered, List<Reading> readings, boolean alarmsChanged, List<AlarmLine> alarms)
            implements CustomPacketPayload {
        public static final Type<ChannelDelta> TYPE = typeOf("channel_delta");
        public static final StreamCodec<ByteBuf, ChannelDelta> CODEC = StreamCodec.composite(
                ChannelKey.STREAM_CODEC, ChannelDelta::key, ByteBufCodecs.BOOL, ChannelDelta::powered,
                Reading.CODEC.apply(ByteBufCodecs.list()), ChannelDelta::readings, ByteBufCodecs.BOOL, ChannelDelta::alarmsChanged,
                AlarmLine.CODEC.apply(ByteBufCodecs.list()), ChannelDelta::alarms, ChannelDelta::new);

        public Type<ChannelDelta> type() {
            return TYPE;
        }
    }

    public record ChannelClosed(ChannelKey key) implements CustomPacketPayload {
        public static final Type<ChannelClosed> TYPE = typeOf("channel_closed");
        public static final StreamCodec<ByteBuf, ChannelClosed> CODEC = StreamCodec.composite(
                ChannelKey.STREAM_CODEC, ChannelClosed::key, ChannelClosed::new);

        public Type<ChannelClosed> type() {
            return TYPE;
        }
    }

    public record RequestHistory(ChannelKey key, int sensorId) implements CustomPacketPayload {
        public static final Type<RequestHistory> TYPE = typeOf("request_history");
        public static final StreamCodec<ByteBuf, RequestHistory> CODEC = StreamCodec.composite(
                ChannelKey.STREAM_CODEC, RequestHistory::key, ByteBufCodecs.VAR_INT, RequestHistory::sensorId, RequestHistory::new);

        public Type<RequestHistory> type() {
            return TYPE;
        }
    }

    public record History(ChannelKey key, int sensorId, List<float[]> tiers) implements CustomPacketPayload {
        public static final Type<History> TYPE = typeOf("history");
        public static final StreamCodec<ByteBuf, History> CODEC = StreamCodec.composite(
                ChannelKey.STREAM_CODEC, History::key, ByteBufCodecs.VAR_INT, History::sensorId,
                SAMPLES.apply(ByteBufCodecs.list()), History::tiers, History::new);

        public Type<History> type() {
            return TYPE;
        }
    }

    public record OpenCentral(BlockPos pos, ChannelKey key, CompoundTag config) implements CustomPacketPayload {
        public static final Type<OpenCentral> TYPE = typeOf("open_central");
        public static final StreamCodec<ByteBuf, OpenCentral> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenCentral::pos, ChannelKey.STREAM_CODEC, OpenCentral::key,
                ByteBufCodecs.COMPOUND_TAG, OpenCentral::config, OpenCentral::new);

        public Type<OpenCentral> type() {
            return TYPE;
        }
    }

    public record ConfigureCentral(BlockPos pos, String channel, CompoundTag config) implements CustomPacketPayload {
        public static final Type<ConfigureCentral> TYPE = typeOf("configure_central");
        public static final StreamCodec<ByteBuf, ConfigureCentral> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ConfigureCentral::pos, ByteBufCodecs.stringUtf8(ChannelKey.MAX_NAME_LENGTH), ConfigureCentral::channel,
                ByteBufCodecs.COMPOUND_TAG, ConfigureCentral::config, ConfigureCentral::new);

        public Type<ConfigureCentral> type() {
            return TYPE;
        }
    }

    public record OpenPanel(BlockPos pos) implements CustomPacketPayload {
        public static final Type<OpenPanel> TYPE = typeOf("open_panel");
        public static final StreamCodec<ByteBuf, OpenPanel> CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, OpenPanel::pos, OpenPanel::new);

        public Type<OpenPanel> type() {
            return TYPE;
        }
    }

    public record ConfigurePanel(BlockPos pos, String channel, CompoundTag config) implements CustomPacketPayload {
        public static final Type<ConfigurePanel> TYPE = typeOf("configure_panel");
        public static final StreamCodec<ByteBuf, ConfigurePanel> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ConfigurePanel::pos, ByteBufCodecs.stringUtf8(ChannelKey.MAX_NAME_LENGTH), ConfigurePanel::channel,
                ByteBufCodecs.COMPOUND_TAG, ConfigurePanel::config, ConfigurePanel::new);

        public Type<ConfigurePanel> type() {
            return TYPE;
        }
    }

    public record OpenSensor(BlockPos pos, List<String> metrics) implements CustomPacketPayload {
        public static final Type<OpenSensor> TYPE = typeOf("open_sensor");
        public static final StreamCodec<ByteBuf, OpenSensor> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenSensor::pos, ID.apply(ByteBufCodecs.list()), OpenSensor::metrics, OpenSensor::new);

        public Type<OpenSensor> type() {
            return TYPE;
        }
    }

    public record SetSensorFilter(BlockPos pos, boolean fromHand) implements CustomPacketPayload {
        public static final Type<SetSensorFilter> TYPE = typeOf("set_sensor_filter");
        public static final StreamCodec<ByteBuf, SetSensorFilter> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetSensorFilter::pos, ByteBufCodecs.BOOL, SetSensorFilter::fromHand, SetSensorFilter::new);

        public Type<SetSensorFilter> type() {
            return TYPE;
        }
    }

    public record SetSensorFilterTag(BlockPos pos, String tag) implements CustomPacketPayload {
        public static final Type<SetSensorFilterTag> TYPE = typeOf("set_sensor_filter_tag");
        public static final StreamCodec<ByteBuf, SetSensorFilterTag> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetSensorFilterTag::pos, TEXT, SetSensorFilterTag::tag, SetSensorFilterTag::new);

        public Type<SetSensorFilterTag> type() {
            return TYPE;
        }
    }

    public record ConfigureSensor(BlockPos pos, String name, String channel, String metric, int tank, int radius)
            implements CustomPacketPayload {
        public static final Type<ConfigureSensor> TYPE = typeOf("configure_sensor");
        public static final StreamCodec<ByteBuf, ConfigureSensor> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ConfigureSensor::pos,
                ByteBufCodecs.stringUtf8(SensorBlockEntity.MAX_NAME_LENGTH), ConfigureSensor::name,
                ByteBufCodecs.stringUtf8(ChannelKey.MAX_NAME_LENGTH), ConfigureSensor::channel,
                ID, ConfigureSensor::metric,
                ByteBufCodecs.VAR_INT, ConfigureSensor::tank,
                ByteBufCodecs.VAR_INT, ConfigureSensor::radius, ConfigureSensor::new);

        public Type<ConfigureSensor> type() {
            return TYPE;
        }
    }
}
