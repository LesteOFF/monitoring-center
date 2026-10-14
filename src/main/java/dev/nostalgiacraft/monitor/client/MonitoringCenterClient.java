package dev.nostalgiacraft.monitor.client;

import dev.nostalgiacraft.monitor.MonitoringCenter;
import dev.nostalgiacraft.monitor.block.ScreenBlockEntity;
import dev.nostalgiacraft.monitor.block.ScreenMultiblock;
import dev.nostalgiacraft.monitor.client.render.PanelRenderer;
import dev.nostalgiacraft.monitor.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

@Mod(value = MonitoringCenter.MODID, dist = Dist.CLIENT)
public final class MonitoringCenterClient {
    private static final float OUTLINE_ALPHA = 0.4f;

    public MonitoringCenterClient(IEventBus modBus) {
        modBus.addListener(MonitoringCenterClient::registerRenderers);
        modBus.addListener((RegisterClientReloadListenersEvent event) ->
                event.registerReloadListener((ResourceManagerReloadListener) resources -> PanelRenderer.clear()));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ClientChannels.tick());
        NeoForge.EVENT_BUS.addListener(MonitoringCenterClient::onLogout);
        NeoForge.EVENT_BUS.addListener(MonitoringCenterClient::outlineWholePanel);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlocks.SCREEN_ENTITY.get(), PanelRenderer::new);
        if (ModList.get().isLoaded("create")) {
            try {
                Class.forName("dev.nostalgiacraft.monitor.compat.create.CreateCompatClient")
                        .getMethod("registerRenderers", EntityRenderersEvent.RegisterRenderers.class).invoke(null, event);
            } catch (ReflectiveOperationException | LinkageError failure) {
                MonitoringCenter.LOGGER.warn("Create coupler rendering disabled: incompatible installed version", failure);
            }
        }
    }

    private static void outlineWholePanel(RenderHighlightEvent.Block event) {
        Level level = Minecraft.getInstance().level;
        ScreenBlockEntity panel = level == null ? null : ScreenMultiblock.controller(level, event.getTarget().getBlockPos());
        if (panel == null || panel.width() * panel.height() == 1) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        LevelRenderer.renderLineBox(event.getPoseStack(), event.getMultiBufferSource().getBuffer(RenderType.lines()),
                panel.panelShape().move(-camera.x, -camera.y, -camera.z), 0f, 0f, 0f, OUTLINE_ALPHA);
        event.setCanceled(true);
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientChannels.clear();
        PanelRenderer.clear();
    }
}
