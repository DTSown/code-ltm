package client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

abstract class FantasyScreen extends JPanel {
    FantasyScreen(String title) {
        super(new BorderLayout(12, 12)); setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(12, 16, 16, 16));
        JPanel header = new JPanel(null) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create(); GameTheme.smooth(g);
                int w = Math.min(730, getWidth() - 12), x = (getWidth() - w) / 2;
                g.setColor(new Color(38, 45, 37, 80)); g.fillRoundRect(x + 2, 10, w, 82, 8, 8);
                g.setPaint(new GradientPaint(0, 4, new Color(153, 99, 47), 0, 86, new Color(91, 54, 31)));
                g.fillRoundRect(x, 4, w, 82, 8, 8); g.setColor(GameTheme.GOLD); g.drawRoundRect(x, 4, w, 82, 8, 8);
                g.setColor(new Color(227, 162, 85, 90)); g.drawLine(x + 12, 56, x + w - 12, 56); g.dispose();
            }
            @Override public void doLayout() {
                for (java.awt.Component c : getComponents()) { c.setBounds(8, c.getY(), getWidth() - 16, c.getHeight()); }
            }
        };
        header.setOpaque(false); header.setPreferredSize(new Dimension(0, 100));
        JLabel heading = label(title, 30, GameTheme.GOLD); heading.setName("screen-heading"); heading.setBounds(8, 12, 600, 42); header.add(heading);
        JLabel subtitle = label("\u0110UA S\u0102N KHO B\u00c1U ONLINE", 12, new Color(255, 237, 200));
        subtitle.setBounds(8, 63, 600, 20); header.add(subtitle); add(header, BorderLayout.NORTH);
    }

    protected static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text, JLabel.CENTER); label.setFont(GameTheme.BODY.deriveFont(Font.BOLD, (float) size));
        label.setForeground(color); return label;
    }

    protected void addFooter(GameButton first, GameButton second) {
        JPanel area = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0)); area.setOpaque(false);
        JPanel buttons = new JPanel(new GridLayout(1, 2, 14, 0)); buttons.setOpaque(false);
        buttons.setPreferredSize(new Dimension(450, 48)); buttons.add(first); buttons.add(second); area.add(buttons);
        add(area, BorderLayout.SOUTH);
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create(); TreasureBackground.paint(g, getWidth(), getHeight()); g.dispose();
    }
}
