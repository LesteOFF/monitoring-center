package dev.nostalgiacraft.monitor.client.screen;

import dev.nostalgiacraft.monitor.client.ClientChannels;
import dev.nostalgiacraft.monitor.panel.ValueFormat;
import dev.nostalgiacraft.monitor.series.DerivedMetric.Kind;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

final class ConditionFields {
    private ConditionFields() {
    }

    static Component sensorName(List<ClientChannels.Sensor> sensors, int id) {
        return find(sensors, id).<Component>map(sensor -> Component.literal(sensor.name()))
                .orElse(id == 0 ? WorkshopScreen.text("choose.sensor") : WorkshopScreen.text("sensor.unavailable", id));
    }

    static List<ChoiceScreen.Entry<Integer>> sensorChoices(List<ClientChannels.Sensor> sensors) {
        return sensors.stream().map(sensor -> new ChoiceScreen.Entry<>(sensor.id(), (Component) Component.literal(sensor.name()))).toList();
    }

    static String unit(List<ClientChannels.Sensor> sensors, int id, Kind derived) {
        return ValueFormat.unit(derived, find(sensors, id).map(ClientChannels.Sensor::unit).orElse(""));
    }

    static Component limitLabel(List<ClientChannels.Sensor> sensors, int id, Kind derived) {
        String unit = unit(sensors, id, derived);
        return unit.isBlank() ? WorkshopScreen.text("threshold") : WorkshopScreen.text("threshold.unit", unit);
    }

    static Optional<ClientChannels.Sensor> find(List<ClientChannels.Sensor> sensors, int id) {
        return sensors.stream().filter(sensor -> sensor.id() == id).findFirst();
    }
}
