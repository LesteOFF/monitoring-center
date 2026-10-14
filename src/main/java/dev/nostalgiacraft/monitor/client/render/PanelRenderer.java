package dev.nostalgiacraft.monitor.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.nostalgiacraft.monitor.block.ScreenBlock;
import dev.nostalgiacraft.monitor.block.ScreenBlockEntity;
import dev.nostalgiacraft.monitor.block.ScreenMultiblock;
import dev.nostalgiacraft.monitor.client.ClientChannels;
import dev.nostalgiacraft.monitor.config.ClientConfig;
import dev.nostalgiacraft.monitor.data.PanelSync;
import dev.nostalgiacraft.monitor.panel.PanelLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

public final class PanelRenderer implements BlockEntityRenderer<ScreenBlockEntity> {
    private static final double VIEW_DISTANCE_SQUARED = PanelSync.VIEW_DISTANCE * PanelSync.VIEW_DISTANCE;
    private static final int RENDER_DISTANCE = PanelSync.VIEW_DISTANCE + 16;
    private static final float SURFACE = (float) (ScreenBlock.THICKNESS - 0.5 + 0.002);
    private static final float PIXEL = 1f / PanelLayout.PIXELS_PER_BLOCK;
    private static final float INSET = PanelLayout.MARGIN * PIXEL;
    private static final Map<ScreenBlockEntity, Painted> PAINTED = new WeakHashMap<>();

    private record Stamp(@Nullable ClientChannels.View view, int values, int series, PanelPainter.Scene scene, long pulse, boolean plain) {
    }

    private record Painted(Stamp stamp, PanelMesh mesh) {
    }

    public PanelRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void clear() {
        PAINTED.clear();
        PanelFont.clear();
    }

    @Override
    public void render(ScreenBlockEntity screen, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!screen.getBlockState().getValue(ScreenBlock.CONTROLLER) || screen.getLevel() == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        if (camera.distanceToSqr(screen.panelCenter()) > VIEW_DISTANCE_SQUARED
                || !minecraft.levelRenderer.getFrustum().isVisible(screen.panelBounds())) {
            return;
        }
        pose.pushPose();
        alignToContent(pose, screen);
        mesh(screen).emit(pose, buffers);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(ScreenBlockEntity screen) {
        return screen.width() > 1 || screen.height() > 1;
    }

    @Override
    public int getViewDistance() {
        return RENDER_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(ScreenBlockEntity screen) {
        return screen.panelBounds();
    }

    private static PanelMesh mesh(ScreenBlockEntity screen) {
        ClientChannels.View view = ClientChannels.get(screen.channel());
        PanelPainter.Scene scene = PanelPainter.Scene.of(screen);
        boolean plain = ClientConfig.PERFORMANCE_MODE.get();
        boolean pulsing = view != null && view.alarming() && !plain;
        Stamp stamp = new Stamp(view, view == null ? 0 : view.valueVersion(), view == null ? 0 : view.seriesVersion(), scene,
                pulsing ? System.currentTimeMillis() / PanelPainter.PULSE_MILLIS : 0, plain);
        Painted painted = PAINTED.get(screen);
        if (painted == null || !painted.stamp().equals(stamp)) {
            painted = new Painted(stamp, PanelPainter.paint(scene, view));
            PAINTED.put(screen, painted);
        }
        return painted.mesh();
    }

    private static void alignToContent(PoseStack pose, ScreenBlockEntity screen) {
        Direction facing = screen.facing();
        Direction right = ScreenMultiblock.right(facing);
        float along = INSET - 0.5f;
        float pixel = PIXEL * screen.scale();
        pose.translate(0.5f + right.getStepX() * along + facing.getStepX() * SURFACE, screen.height() - INSET,
                0.5f + right.getStepZ() * along + facing.getStepZ() * SURFACE);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.scale(pixel, -pixel, pixel);
    }
}
