package client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JPanel;

public class RoundedPanel extends JPanel {
    public RoundedPanel() { setOpaque(false); }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create(); GameTheme.smooth(g);
        int w = getWidth(), h = getHeight();
        g.setColor(new Color(0, 30, 47, 70)); g.fillRoundRect(3, 7, w - 6, h - 8, 8, 8);
        g.setPaint(new GradientPaint(0, 0, new Color(17, 63, 88), 0, h, GameTheme.INK));
        g.fillRoundRect(2, 2, w - 5, h - 9, 8, 8);
        g.setStroke(new BasicStroke(2f)); g.setColor(GameTheme.GOLD);
        g.drawRoundRect(2, 2, w - 5, h - 9, 8, 8);
        g.setColor(new Color(60, 116, 139)); g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(7, 7, w - 15, h - 19, 6, 6);
        for (int x : new int[] {12, w - 14}) {
            for (int y : new int[] {12, h - 17}) {
                g.setColor(GameTheme.GOLD); g.fillOval(x - 2, y - 2, 4, 4);
            }
        }
        g.dispose(); super.paintComponent(graphics);
    }
}
