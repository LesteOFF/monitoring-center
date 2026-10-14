package dev.nostalgiacraft.monitor.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nostalgiacraft.monitor.MonitoringCenter;
import dev.nostalgiacraft.monitor.client.render.PanelMesh.Quad;
import dev.nostalgiacraft.monitor.panel.PanelLayout.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import org.joml.Matrix4f;

import java.util.List;

public final class PanelIcons {
    public static final List<String> GLYPHS = List.of(
            "bolt", "drop", "box", "redstone", "gear", "gauge", "thermometer", "radiation",
            "clock", "heart", "person", "sun", "cloud", "leaf", "skull", "pile",
            "cpu", "network", "flask", "star", "server", "pulse", "warning", "bell",
            "battery", "flame", "up", "down", "flat", "sigma", "moon", "question");
    private static final int CELL = 16;
    static final ResourceLocation ATLAS = MonitoringCenter.id("textures/panel/icons.png");
    private static final String ITEM_PREFIX = "item:";
    private static final String FLUID_PREFIX = "fluid:";
    private static final int COLUMNS = 8;
    private static final int ROWS = 4;
    private static final int UNKNOWN = GLYPHS.indexOf("question");
    private static final int OPAQUE = 0xFF000000;
    private static final float FLAT = 0.01f;

    private PanelIcons() {
    }

    static void draw(PanelCanvas canvas, String icon, Rect area, int color) {
        if (icon.startsWith(ITEM_PREFIX) || icon.startsWith(FLUID_PREFIX)) {
            canvas.model(icon, area);
        } else {
            drawGlyph(canvas, icon, area, color);
        }
    }

    static void drawGlyph(PanelCanvas canvas, String name, Rect area, int color) {
        int index = GLYPHS.indexOf(name);
        int cell = index < 0 ? UNKNOWN : index;
        float u = cell % COLUMNS / (float) COLUMNS;
        float v = cell / COLUMNS / (float) ROWS;
        canvas.icon(area, u, v, u + 1f / COLUMNS, v + 1f / ROWS, color);
    }

    public static void blitGlyph(GuiGraphics graphics, String name, int x, int y, int color) {
        int index = GLYPHS.indexOf(name);
        int cell = index < 0 ? UNKNOWN : index;
        graphics.setColor((color >> 16 & 0xFF) / 255f, (color >> 8 & 0xFF) / 255f, (color & 0xFF) / 255f, 1f);
        graphics.blit(ATLAS, x, y, cell % COLUMNS * CELL, cell / COLUMNS * CELL, CELL, CELL, COLUMNS * CELL, ROWS * CELL);
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    static void emitModel(PoseStack pose, MultiBufferSource buffers, PanelMesh.Model model) {
        boolean item = model.icon().startsWith(ITEM_PREFIX);
        ResourceLocation id = ResourceLocation.tryParse(model.icon().substring((item ? ITEM_PREFIX : FLUID_PREFIX).length()));
        if (id == null) {
            return;
        }
        if (item) {
            emitItem(pose, buffers, id, model.area());
        } else {
            emitFluid(pose, buffers, id, model.area());
        }
    }

    private static void emitItem(PoseStack pose, MultiBufferSource buffers, ResourceLocation id, Rect area) {
        Minecraft minecraft = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(area.x() + area.width() / 2f, area.y() + area.height() / 2f, PanelMesh.LAYER + area.width() * FLAT);
        pose.mulPose(new Matrix4f().scale(1f, 1f, FLAT));
        pose.scale(area.width(), -area.height(), area.width());
        minecraft.getItemRenderer().renderStatic(new ItemStack(BuiltInRegistries.ITEM.get(id)), ItemDisplayContext.GUI,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers, minecraft.level, 0);
        pose.popPose();
    }

    private static void emitFluid(PoseStack pose, MultiBufferSource buffers, ResourceLocation id, Rect area) {
        IClientFluidTypeExtensions look = IClientFluidTypeExtensions.of(BuiltInRegistries.FLUID.get(id));
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(look.getStillTexture());
        Quad quad = new Quad(area.x(), area.y(), area.right(), area.bottom(), sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(),
                look.getTintColor() | OPAQUE);
        PanelMesh.quads(pose, buffers.getBuffer(RenderType.text(InventoryMenu.BLOCK_ATLAS)), List.of(quad), PanelMesh.LAYER, true);
    }
}
