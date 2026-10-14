package dev.nostalgiacraft.monitor.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

public final class NetworkSetup {
    public static final String VERSION = "2";

    private NetworkSetup() {
    }

    public static void forget(UUID player) {
        ServerHandlers.forget(player);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(Payloads.OpenSensor.TYPE, Payloads.OpenSensor.CODEC, (message, context) -> ClientHandlers.openSensor(message));
        registrar.playToClient(Payloads.OpenPanel.TYPE, Payloads.OpenPanel.CODEC, (message, context) -> ClientHandlers.openPanel(message));
        registrar.playToClient(Payloads.ChannelSnapshot.TYPE, Payloads.ChannelSnapshot.CODEC,
                (message, context) -> ClientHandlers.snapshot(message));
        registrar.playToClient(Payloads.ChannelDelta.TYPE, Payloads.ChannelDelta.CODEC, (message, context) -> ClientHandlers.delta(message));
        registrar.playToClient(Payloads.ChannelClosed.TYPE, Payloads.ChannelClosed.CODEC,
                (message, context) -> ClientHandlers.closed(message));
        registrar.playToClient(Payloads.History.TYPE, Payloads.History.CODEC, (message, context) -> ClientHandlers.history(message));
        registrar.playToClient(Payloads.OpenCentral.TYPE, Payloads.OpenCentral.CODEC, (message, context) -> ClientHandlers.openCentral(message));
        registrar.playToServer(Payloads.RequestHistory.TYPE, Payloads.RequestHistory.CODEC, ServerHandlers.limited(ServerHandlers::requestHistory));
        registrar.playToServer(Payloads.ConfigureCentral.TYPE, Payloads.ConfigureCentral.CODEC, ServerHandlers.limited(ServerHandlers::configureCentral));
        registrar.playToServer(Payloads.ConfigurePanel.TYPE, Payloads.ConfigurePanel.CODEC, ServerHandlers.limited(ServerHandlers::configurePanel));
        registrar.playToServer(Payloads.ConfigureSensor.TYPE, Payloads.ConfigureSensor.CODEC, ServerHandlers.limited(ServerHandlers::configureSensor));
        registrar.playToServer(Payloads.SetSensorFilterTag.TYPE, Payloads.SetSensorFilterTag.CODEC,
                ServerHandlers.limited(ServerHandlers::setSensorFilterTag));
        registrar.playToServer(Payloads.SetSensorFilter.TYPE, Payloads.SetSensorFilter.CODEC, ServerHandlers.limited(ServerHandlers::setSensorFilter));
    }
}
