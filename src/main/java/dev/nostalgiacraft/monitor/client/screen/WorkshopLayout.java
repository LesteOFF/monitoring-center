package dev.nostalgiacraft.monitor.client.screen;

/** Shared bounds in Minecraft GUI pixels. Content never crosses the fixed footer. */
public record WorkshopLayout(int left, int top, int width, int height) {
    public static WorkshopLayout fit(int viewportWidth, int viewportHeight) {
        int width = Math.min(420, viewportWidth - 16);
        int height = Math.min(286, viewportHeight - 12);
        return new WorkshopLayout((viewportWidth - width) / 2, (viewportHeight - height) / 2, width, height);
    }

    public int x() { return left + 12; }
    public int y() { return top + 62; }
    public int contentWidth() { return width - 24; }
    public int footerY() { return top + height - 30; }
    public int contentHeight() { return footerY() - y() - 8; }
    public int listCapacity() { return Math.max(1, (contentHeight() - 52) / 24); }
    public int rowCapacity() { return Math.max(1, (contentHeight() - 76) / 24); }
    public int choiceCapacity() { return Math.max(1, (contentHeight() - 40) / 24); }
    public int pageCount(int entries) { return Math.max(1, (entries + listCapacity() - 1) / listCapacity()); }
    public int clampPage(int page, int entries) { return Math.max(0, Math.min(page, pageCount(entries) - 1)); }
}
