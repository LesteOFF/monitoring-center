package dev.nostalgiacraft.monitor.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nostalgiacraft.monitor.panel.PanelLayout.Rect;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

final class PanelMesh {
    static final float LAYER = 0.1f;

    record Quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int color) {
    }

    record Model(String icon, Rect area) {
    }

    final List<Quad> fills = new ArrayList<>();
    final List<Quad> glyphs = new ArrayList<>();
    final List<Quad> icons = new ArrayList<>();
    final List<Model> models = new ArrayList<>();

    void emit(PoseStack pose, MultiBufferSource buffers) {
        models.forEach(model -> PanelIcons.emitModel(pose, buffers, model));
        layer(pose, buffers, RenderType.textBackground(), fills, 0f, false);
        layer(pose, buffers, RenderType.text(PanelFont.TEXTURE), glyphs, LAYER, true);
        layer(pose, buffers, RenderType.text(PanelIcons.ATLAS), icons, LAYER, true);
    }

    private static void layer(PoseStack pose, MultiBufferSource buffers, RenderType type, List<Quad> quads, float depth, boolean textured) {
        if (!quads.isEmpty()) {
            quads(pose, buffers.getBuffer(type), quads, depth, textured);
        }
    }

    static void quads(PoseStack pose, VertexConsumer buffer, List<Quad> quads, float depth, boolean textured) {
        Matrix4f matrix = pose.last().pose();
        for (Quad quad : quads) {
            vertex(buffer, matrix, quad.x0(), quad.y0(), depth, quad.u0(), quad.v0(), quad.color(), textured);
            vertex(buffer, matrix, quad.x0(), quad.y1(), depth, quad.u0(), quad.v1(), quad.color(), textured);
            vertex(buffer, matrix, quad.x1(), quad.y1(), depth, quad.u1(), quad.v1(), quad.color(), textured);
            vertex(buffer, matrix, quad.x1(), quad.y0(), depth, quad.u1(), quad.v0(), quad.color(), textured);
        }
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, float depth, float u, float v, int color,
                               boolean textured) {
        buffer.addVertex(matrix, x, y, depth).setColor(color);
        if (textured) {
            buffer.setUv(u, v);
        }
        buffer.setLight(LightTexture.FULL_BRIGHT);
    }
}
