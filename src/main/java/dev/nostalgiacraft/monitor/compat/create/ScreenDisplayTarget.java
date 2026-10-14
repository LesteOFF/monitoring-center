package dev.nostalgiacraft.monitor.compat.create;

import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import dev.nostalgiacraft.monitor.block.ScreenBlockEntity;
import dev.nostalgiacraft.monitor.block.ScreenMultiblock;
import dev.nostalgiacraft.monitor.panel.PanelLayout;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

public final class ScreenDisplayTarget extends DisplayTarget {
    private static final int CHARACTER_PIXELS = 6;

    @Override
    public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext context) {
        ScreenBlockEntity screen = ScreenMultiblock.controller(context.level(), context.getTargetPos());
        if (screen != null) {
            screen.acceptLinkLines(line, text.stream().map(Component::getString).toList());
        }
    }

    @Override
    public DisplayTargetStats provideStats(DisplayLinkContext context) {
        ScreenBlockEntity screen = ScreenMultiblock.controller(context.level(), context.getTargetPos());
        PanelLayout.Size size = screen == null ? PanelLayout.size(1, 1, 1) : screen.size();
        int rows = PanelLayout.list(size, 0).rows().size();
        int columns = size.width() / CHARACTER_PIXELS;
        return new DisplayTargetStats(rows, columns, this);
    }
}
