package client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JButton;

public class GameButton extends JButton {
    public enum Variant { GOLD, BLUE, GREEN, RED }
    private final boolean gold;
    private final Variant variant;

    public GameButton(String text, boolean gold) {
        this(text, gold ? Variant.GOLD : Variant.BLUE);
    }

    public GameButton(String text, Variant variant) {
        super(text); this.variant = variant; this.gold = variant == Variant.GOLD;
        setFont(new Font("SansSerif", Font.BOLD, 15));
        setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setOpaque(false);
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(300, gold ? 48 : 42));
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                scrollRectToVisible(new java.awt.Rectangle(0, 0, getWidth(), getHeight())); repaint();
            }
            @Override public void focusLost(java.awt.event.FocusEvent e) { repaint(); }
        });
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create(); GameTheme.smooth(g);
        int w = getWidth(), h = getHeight();
        boolean pressed = getModel().isPressed();
        Color top = gold ? new Color(255, 227, 102) : new Color(32, 143, 204);
        Color bottom = gold ? new Color(233, 165, 30) : new Color(10, 90, 145);
        if (variant == Variant.GREEN) { top = new Color(73, 177, 108); bottom = new Color(25, 114, 71); }
        if (variant == Variant.RED) { top = new Color(212, 87, 106); bottom = new Color(142, 43, 69); }
        if (!isEnabled()) { top = new Color(61, 91, 105); bottom = GameTheme.PANEL; }
        else if (pressed) { top = bottom; }
        else if (getModel().isRollover()) { top = top.brighter(); }
        g.setColor(new Color(0, 0, 0, 75)); g.fillRoundRect(2, 4, w - 4, h - 4, 8, 8);
        g.setPaint(new GradientPaint(0, 2, top, 0, h - 4, bottom));
        g.fillRoundRect(2, 1, w - 5, h - 5, 8, 8);
        g.setColor(!isEnabled() ? new Color(84, 117, 133) : gold ? GameTheme.GOLD
                : variant == Variant.GREEN ? new Color(133, 233, 160)
                : variant == Variant.RED ? new Color(255, 162, 169) : new Color(102, 201, 241));
        g.setStroke(new BasicStroke(2f)); g.drawRoundRect(2, 1, w - 5, h - 5, 8, 8);
        g.setColor(new Color(255, 255, 255, 65)); g.drawLine(10, 4, w - 12, 4);
        if (hasFocus()) {
            g.setColor(Color.WHITE); g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(6, 5, w - 13, h - 13, 6, 6);
        }
        g.setFont(getFont());
        g.setColor(!isEnabled() ? new Color(192, 214, 224) : gold ? new Color(68, 43, 9) : Color.WHITE);
        int baseline = (h - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent() - 1 + (pressed ? 1 : 0);
        g.drawString(getText(), (w - g.getFontMetrics().stringWidth(getText())) / 2, baseline);
        g.dispose();
    }
}
