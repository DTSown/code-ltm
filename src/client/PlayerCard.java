package client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JButton;
import model.Player;

public final class PlayerCard extends JButton {
    private final String username;
    private final int rank, wins;
    private final boolean free;
    private final PlayerAvatar avatar;
    private boolean selected;

    public PlayerCard(Player player, Runnable select) {
        username = player.getUsername(); rank = player.getTotalRankPoint(); wins = player.getWins();
        free = player.getStatus() == Player.Status.FREE;
        avatar = new PlayerAvatar(username, 52);
        setName("opponent-" + username); setToolTipText(username + " - " + player.getStatus());
        getAccessibleContext().setAccessibleName(username + ", rank " + rank + ", wins " + wins + ", " + player.getStatus());
        setPreferredSize(new Dimension(220, 124)); setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false);
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        addActionListener(event -> select.run());
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                scrollRectToVisible(new java.awt.Rectangle(0, 0, getWidth(), getHeight())); repaint();
            }
            @Override public void focusLost(java.awt.event.FocusEvent e) { repaint(); }
        });
    }

    public String getUsername() { return username; }
    public boolean isFree() { return free; }
    public boolean isOpponentSelected() { return selected; }
    public void setOpponentSelected(boolean selected) { this.selected = selected; repaint(); }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create(); GameTheme.smooth(g);
        int w = getWidth(), h = getHeight();
        Color top = selected ? new Color(29, 87, 108) : new Color(17, 63, 87);
        g.setPaint(new GradientPaint(0, 0, top, 0, h, GameTheme.INK)); g.fillRoundRect(1, 1, w - 3, h - 3, 8, 8);
        g.setColor(selected || hasFocus() ? GameTheme.GOLD : free ? new Color(70, 148, 180) : new Color(155, 70, 98));
        g.setStroke(new BasicStroke(selected || hasFocus() ? 2f : 1f)); g.drawRoundRect(1, 1, w - 3, h - 3, 8, 8);
        avatar.paintIcon(this, g, 12, 17);
        g.setFont(GameTheme.BODY.deriveFont(Font.BOLD, 14f)); g.setColor(GameTheme.TEXT);
        g.drawString(fitText(g, username, w - 90), 76, 30);
        g.setFont(GameTheme.BODY.deriveFont(12f)); g.setColor(GameTheme.GOLD);
        g.drawString(fitText(g, "\u0110i\u1ec3m rank: " + rank, w - 90), 76, 50);
        g.setColor(new Color(191, 221, 235)); g.drawString(fitText(g, "Th\u1eafng: " + wins, w - 90), 76, 69);
        g.setColor(free ? new Color(115, 226, 150) : new Color(252, 133, 148));
        g.fillOval(15, 94, 8, 8); g.drawString(free ? "\u0110ang r\u1ea3nh" : "\u0110ang ch\u01a1i", 30, 102);
        if (!isEnabled() && free) {
            g.setColor(new Color(10, 26, 39, 60)); g.fillRoundRect(1, 1, w - 3, h - 3, 8, 8);
        }
        g.dispose();
    }

    static String fitText(Graphics2D g, String text, int width) {
        if (g.getFontMetrics().stringWidth(text) <= width) { return text; }
        String shortened = text;
        while (!shortened.isEmpty() && g.getFontMetrics().stringWidth(shortened + "...") > width) {
            shortened = shortened.substring(0, shortened.offsetByCodePoints(shortened.length(), -1));
        }
        return shortened + "...";
    }
}
