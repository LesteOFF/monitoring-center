package dev.nostalgiacraft.monitor;

import com.mojang.logging.LogUtils;
import dev.nostalgiacraft.monitor.alarm.Notifier;
import dev.nostalgiacraft.monitor.compat.Integrations;
import dev.nostalgiacraft.monitor.config.ClientConfig;
import dev.nostalgiacraft.monitor.config.ServerConfig;
import dev.nostalgiacraft.monitor.data.MonitorNetwork;
import dev.nostalgiacraft.monitor.gametest.IntegrationGameTests;
import dev.nostalgiacraft.monitor.metric.Metrics;
import dev.nostalgiacraft.monitor.network.NetworkSetup;
import dev.nostalgiacraft.monitor.registry.ModBlocks;
import dev.nostalgiacraft.monitor.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;

@Mod(MonitoringCenter.MODID)
public final class MonitoringCenter {
    public static final String MODID = "monitoringcenter";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MonitoringCenter(IEventBus modBus, ModContainer container) {
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modBus.addListener(NetworkSetup::register);
        modBus.addListener(IntegrationGameTests::register);
        Metrics.registerCore();
        Integrations.register();
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                Notifier.deliver(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> NetworkSetup.forget(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(MonitoringCenter::onLevelTick);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            MonitorNetwork.get(level).tick(level);
        }
    }
}
