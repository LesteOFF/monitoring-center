package dev.nostalgiacraft.monitor.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nostalgiacraft.monitor.client.ClientChannels;
import dev.nostalgiacraft.monitor.data.ChannelKey;
import dev.nostalgiacraft.monitor.panel.PanelConfig;
import dev.nostalgiacraft.monitor.panel.PanelLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PanelPreview {
    private static final int FRAME = 0xFF8E8E8E;
    private static final int GLASS = 0xFF0D0F12;

    private PanelPreview() {
    }

    public static void draw(GuiGraphics graphics, int x, int y, int width, int height, PanelConfig config, int widthBlocks, int heightBlocks,
                            @Nullable ChannelKey channel) {
        float pixel = Math.min(width / (float) (widthBlocks * PanelLayout.PIXELS_PER_BLOCK), height / (float) (heightBlocks * PanelLayout.PIXELS_PER_BLOCK));
        int shownWidth = Math.round(widthBlocks * PanelLayout.PIXELS_PER_BLOCK * pixel);
        int shownHeight = Math.round(heightBlocks * PanelLayout.PIXELS_PER_BLOCK * pixel);
        int left = x + (width - shownWidth) / 2;
        int top = y + (height - shownHeight) / 2;
        graphics.fill(left - 1, top - 1, left + shownWidth + 1, top + shownHeight + 1, FRAME);
        graphics.fill(left, top, left + shownWidth, top + shownHeight, GLASS);
        PanelPainter.Scene scene = new PanelPainter.Scene(config, PanelLayout.size(widthBlocks, heightBlocks, config.scale()), 0, List.of(), channel,
                PanelPainter.clockMinute(Minecraft.getInstance().level));
        float scale = pixel * PanelLayout.scale(widthBlocks, heightBlocks, config.scale());
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(left + PanelLayout.MARGIN * pixel, top + PanelLayout.MARGIN * pixel, 0f);
        pose.scale(scale, scale, 1f);
        PanelPainter.paint(scene, ClientChannels.get(channel)).emit(pose, graphics.bufferSource());
        graphics.flush();
        pose.popPose();
    }
}
