package dev.nostalgiacraft.monitor.client.screen;

import dev.nostalgiacraft.monitor.block.SensorBlockEntity;
import dev.nostalgiacraft.monitor.config.ServerConfig;
import dev.nostalgiacraft.monitor.data.ChannelKey;
import dev.nostalgiacraft.monitor.metric.Metric;
import dev.nostalgiacraft.monitor.metric.Metrics;
import dev.nostalgiacraft.monitor.network.Payloads;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public final class SensorScreen extends WorkshopScreen {
    private final SensorBlockEntity sensor;
    private final List<String> metrics = new ArrayList<>();
    private String nameText;
    private String channelText;
    private String metricId;
    private int tank;
    private int radius;
    private String shownTag = "";
    private ItemStack shownFilter = ItemStack.EMPTY;

    public SensorScreen(SensorBlockEntity sensor, List<String> applicable) {
        super(text("sensor"), text("sensor.help"));
        this.sensor = sensor;
        metrics.add("");
        metrics.addAll(applicable);
        metricId = sensor.metricId();
        if (!metrics.contains(metricId)) metrics.add(metricId);
        nameText = sensor.name();
        channelText = sensor.channel() == null ? ChannelKey.DEFAULT_NAME : sensor.channel().name();
        tank = sensor.tank();
        radius = sensor.radius();
    }

    @Override
    protected void init() {
        super.init();
        int x = layout.x(), y = layout.y(), w = layout.contentWidth(), half = (w - 6) / 2;
        // Two identity fields share a row; the long choice receives the full panel width.
        field(text("name"), x, y, half, nameText, SensorBlockEntity.MAX_NAME_LENGTH, value -> nameText = value)
                .setTooltip(Tooltip.create(text("name.help")));
        field(text("channel"), x + half + 6, y, w - half - 6, channelText, ChannelKey.MAX_NAME_LENGTH, value -> channelText = value)
                .setTooltip(Tooltip.create(text("channel.help")));
        choice(text("sensor.measure"), metricName(metricId), x, y + 40, w, () -> {
            List<ChoiceScreen.Entry<String>> choices = metrics.stream().map(id -> new ChoiceScreen.Entry<>(id, metricName(id))).toList();
            choose(text("sensor.measure"), choices, value -> metricId = value);
        });
        Metric metric = Metrics.get(metricId);
        if (metric != null && Metrics.usesTank(metric.id())) {
            choice(text("label.tank"), text("tank.number", tank + 1), x, y + 80, w, () -> {
                choose(text("label.tank"), IntStream.range(0, 16).mapToObj(value -> new ChoiceScreen.Entry<>(value, text("tank.number", value + 1))).toList(),
                        value -> tank = value);
            }).setTooltip(Tooltip.create(text("tank.help")));
        } else if (metric != null && metric.area()) {
            choice(text("label.radius"), text("radius.blocks", radius), x, y + 80, w, () -> {
                choose(text("label.radius"), IntStream.rangeClosed(1, ServerConfig.AREA_MAX_RADIUS.get())
                        .mapToObj(value -> new ChoiceScreen.Entry<>(value, text("radius.blocks", value))).toList(), value -> radius = value);
            }).setTooltip(Tooltip.create(text("radius.help")));
        } else if (Metrics.usesFilter(metricId)) {
            filter(x, y + 80, w, half);
        } else {
            paragraph(text(metricId.isEmpty() ? "sensor.auto.help" : "sensor.direct.help"), x, y + 84, w);
        }
        footer(this::confirm);
    }

    private void filter(int x, int y, int w, int half) {
        shownFilter = sensor.filter().copy();
        ItemStack held = minecraft.player.getMainHandItem();
        shownTag = tagId(sensor);
        int third = (w - 12) / 3;
        Component current = shownFilter.isEmpty() ? text("filter.none") : shownTag.isEmpty() ? shownFilter.getHoverName() : Component.literal("#" + shownTag);
        label(text("filter.current", current), x, y, w);
        button(text("filter.hand"), x, y + 12, third, () -> sendFilter(true)).active = !held.isEmpty();
        button(text("filter.tag"), x + third + 6, y + 12, third, this::chooseTag).active = !shownFilter.isEmpty();
        button(text("filter.clear"), x + 2 * (third + 6), y + 12, w - 2 * (third + 6), () -> sendFilter(false)).active = !shownFilter.isEmpty();
        paragraph(text("sensor.filter.help"), x, y + 40, w);
    }

    private static String tagId(SensorBlockEntity sensor) {
        return sensor.filterTag() == null ? "" : sensor.filterTag().location().toString();
    }

    private void chooseTag() {
        List<ChoiceScreen.Entry<String>> entries = new ArrayList<>();
        entries.add(new ChoiceScreen.Entry<>("", text("filter.exact")));
        shownFilter.getTags().map(tag -> tag.location().toString()).sorted()
                .forEach(tag -> entries.add(new ChoiceScreen.Entry<>(tag, Component.literal("#" + tag))));
        choose(text("filter.tag"), entries, tag -> PacketDistributor.sendToServer(new Payloads.SetSensorFilterTag(sensor.getBlockPos(), tag)));
    }

    private void sendFilter(boolean fromHand) {
        PacketDistributor.sendToServer(new Payloads.SetSensorFilter(sensor.getBlockPos(), fromHand));
    }

    @Override
    public void tick() {
        super.tick();
        boolean changed = !ItemStack.matches(shownFilter, sensor.filter()) || !shownTag.equals(tagId(sensor));
        if (Metrics.usesFilter(metricId) && changed) {
            rebuildWidgets();
        }
    }

    private static Component metricName(String id) {
        return Component.translatable(id.isEmpty() ? "metric.monitoringcenter.auto" : "metric.monitoringcenter." + id);
    }

    private void confirm() {
        PacketDistributor.sendToServer(new Payloads.ConfigureSensor(sensor.getBlockPos(), nameText, channelText, metricId, tank, radius));
        onClose();
    }
}
