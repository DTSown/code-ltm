package client;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.Icon;

public final class PlayerAvatar implements Icon {
    private final int size;
    private final Color accent;

    public PlayerAvatar(String username, int size) {
        this(username, size, username != null && (username.hashCode() & 1) == 1
                ? new Color(203, 79, 116) : new Color(40, 145, 202));
    }

    public PlayerAvatar(String username, int size, Color accent) {
        this.size = size;
        this.accent = accent;
    }

    @Override public int getIconWidth() { return size; }
    @Override public int getIconHeight() { return size; }
    @Override public void paintIcon(Component c, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create(); GameTheme.smooth(g);
        g.translate(x, y); g.scale(size / 80.0, size / 80.0);
        g.setColor(new Color(8, 35, 51)); g.fillRoundRect(1, 1, 78, 78, 8, 8);
        g.setColor(accent); g.fillRoundRect(4, 4, 72, 72, 8, 8);
        g.setColor(accent.brighter()); g.fillPolygon(new int[] {5, 66, 5}, new int[] {5, 5, 64}, 3);
        g.setColor(new Color(16, 50, 68)); g.fillOval(16, 53, 50, 37);
        g.setColor(new Color(251, 204, 158)); g.fillOval(22, 21, 39, 43);
        g.setColor(new Color(94, 57, 41));
        g.fillArc(16, 12, 51, 40, 0, 180);
        g.fillPolygon(new int[] {20, 28, 39, 46, 61, 57, 45, 35, 24},
                new int[] {31, 15, 14, 17, 28, 35, 25, 33, 28}, 9);
        g.setColor(new Color(19, 50, 70)); g.fillOval(30, 38, 6, 8); g.fillOval(48, 38, 6, 8);
        g.setColor(Color.WHITE); g.fillOval(31, 39, 2, 3); g.fillOval(49, 39, 2, 3);
        g.setColor(new Color(166, 86, 76)); g.drawArc(36, 46, 12, 10, 190, 145);
        g.setColor(new Color(89, 58, 32)); g.fillRoundRect(18, 14, 49, 13, 6, 6);
        g.setColor(GameTheme.GOLD); g.drawOval(26, 14, 14, 12); g.drawOval(46, 14, 14, 12);
        g.setColor(new Color(85, 211, 233)); g.fillOval(29, 17, 8, 6); g.fillOval(49, 17, 8, 6);
        g.setColor(GameTheme.GOLD); g.setStroke(new java.awt.BasicStroke(2f)); g.drawRoundRect(1, 1, 77, 77, 8, 8);
        g.dispose();
    }
}
