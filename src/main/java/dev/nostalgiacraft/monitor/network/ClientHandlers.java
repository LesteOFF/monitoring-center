package dev.nostalgiacraft.monitor.network;

import dev.nostalgiacraft.monitor.block.ScreenBlockEntity;
import dev.nostalgiacraft.monitor.block.SensorBlockEntity;
import dev.nostalgiacraft.monitor.client.ClientChannels;
import dev.nostalgiacraft.monitor.client.screen.CentralScreen;
import dev.nostalgiacraft.monitor.client.screen.PanelScreen;
import dev.nostalgiacraft.monitor.client.screen.SensorScreen;
import net.minecraft.client.Minecraft;

final class ClientHandlers {
    private ClientHandlers() {
    }

    static void openPanel(Payloads.OpenPanel message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getBlockEntity(message.pos()) instanceof ScreenBlockEntity screen) {
            minecraft.setScreen(new PanelScreen(screen));
        }
    }

    static void snapshot(Payloads.ChannelSnapshot message) {
        ClientChannels.snapshot(message);
    }

    static void delta(Payloads.ChannelDelta message) {
        ClientChannels.delta(message);
    }

    static void closed(Payloads.ChannelClosed message) {
        ClientChannels.close(message.key());
    }

    static void history(Payloads.History message) {
        ClientChannels.history(message);
    }

    static void openCentral(Payloads.OpenCentral message) {
        Minecraft.getInstance().setScreen(new CentralScreen(message.pos(), message.key(), message.config()));
    }

    static void openSensor(Payloads.OpenSensor message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getBlockEntity(message.pos()) instanceof SensorBlockEntity sensor) {
            minecraft.setScreen(new SensorScreen(sensor, message.metrics()));
        }
    }
}
