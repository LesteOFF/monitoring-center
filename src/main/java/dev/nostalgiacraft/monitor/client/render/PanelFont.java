package dev.nostalgiacraft.monitor.client.render;

import dev.nostalgiacraft.monitor.MonitoringCenter;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class PanelFont {
    static final ResourceLocation TEXTURE = MonitoringCenter.id("textures/font/panel.png");
    private static final ResourceLocation DEFINITION = MonitoringCenter.id("font/panel.json");
    private static PanelFontAtlas atlas;

    private PanelFont() {
    }

    static PanelFontAtlas atlas() {
        if (atlas == null) {
            ResourceManager resources = Minecraft.getInstance().getResourceManager();
            try (InputStream definition = resources.open(DEFINITION); InputStream image = resources.open(TEXTURE)) {
                atlas = PanelFontAtlas.read(new InputStreamReader(definition, StandardCharsets.UTF_8), ImageIO.read(image));
            } catch (IOException exception) {
                throw new IllegalStateException("Could not load the monitoring panel bitmap font", exception);
            }
        }
        return atlas;
    }

    public static void clear() {
        atlas = null;
    }
}
