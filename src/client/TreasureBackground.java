package client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;

public final class TreasureBackground {
    private TreasureBackground() { }

    public static void paint(Graphics2D g, int width, int height) {
        GameTheme.smooth(g);
        g.setPaint(new GradientPaint(0, 0, new Color(72, 171, 224), 0, height, new Color(190, 232, 243)));
        g.fillRect(0, 0, width, height);
        for (int i = 0; i < 7; i++) { cloud(g, i * width / 6 - 70, 30 + (i % 3) * 70, 1 + (i % 2) * .3); }
        island(g, (int) (width * .13), (int) (height * .37), .85);
        island(g, (int) (width * .87), (int) (height * .43), 1.05);
        island(g, (int) (width * .02), (int) (height * .72), 1.2);
        island(g, (int) (width * .98), (int) (height * .82), .85);
        g.setColor(new Color(46, 122, 154, 95));
        g.fillPolygon(new int[] {0, width / 8, width / 3, width / 2, width},
                new int[] {height, height - 65, height - 26, height - 50, height}, 5);
        chest(g, (int) (width * .14), height - 128, .9);
        compass(g, (int) (width * .86), height - 102, 43);
    }

    private static void cloud(Graphics2D original, int x, int y, double scale) {
        Graphics2D g = (Graphics2D) original.create(); g.translate(x, y); g.scale(scale, scale);
        g.setColor(new Color(249, 253, 255, 175));
        g.fillOval(0, 19, 95, 27); g.fillOval(20, 0, 48, 43); g.fillOval(60, 13, 46, 30); g.dispose();
    }

    private static void island(Graphics2D original, int x, int y, double scale) {
        Graphics2D g = (Graphics2D) original.create(); g.translate(x, y); g.scale(scale, scale);
        g.setColor(new Color(66, 106, 132));
        g.fillPolygon(new int[] {-90, 86, 48, 15, -17, -55}, new int[] {0, 0, 48, 120, 87, 60}, 6);
        g.setColor(new Color(105, 145, 162));
        g.fillPolygon(new int[] {-90, -10, 15, -17, -55}, new int[] {0, 0, 120, 87, 60}, 5);
        g.setColor(new Color(44, 132, 99)); g.fillOval(-95, -17, 188, 38);
        g.setColor(new Color(107, 183, 96)); g.fillOval(-87, -17, 175, 21);
        g.setColor(new Color(202, 216, 207)); g.fillRect(-18, -91, 39, 74);
        g.setColor(new Color(149, 179, 182)); g.fillRect(13, -91, 12, 75);
        for (int i = 0; i < 3; i++) { g.fillRect(-24 + i * 20, -99, 12, 14); }
        g.setColor(new Color(38, 94, 122)); g.fillRoundRect(-6, -63, 13, 24, 8, 8);
        g.setColor(new Color(120, 214, 238, 180)); g.fillRect(38, 9, 15, 81);
        g.setColor(new Color(234, 252, 255, 170)); g.fillRect(42, 9, 4, 78);
        for (int p : new int[] {-60, 58}) {
            g.setColor(new Color(83, 78, 44)); g.fillRect(p, -38, 5, 24);
            g.setColor(new Color(34, 129, 93));
            g.fillPolygon(new int[] {p - 18, p + 3, p + 22}, new int[] {-27, -66, -27}, 3);
            g.setColor(new Color(117, 187, 93));
            g.fillPolygon(new int[] {p - 18, p + 3, p + 3}, new int[] {-27, -66, -27}, 3);
        }
        g.dispose();
    }

    private static void chest(Graphics2D original, int x, int y, double scale) {
        Graphics2D g = (Graphics2D) original.create(); g.translate(x, y); g.scale(scale, scale); g.rotate(-.1);
        g.setColor(new Color(19, 70, 90, 65)); g.fillOval(-12, 73, 135, 25);
        g.setColor(new Color(92, 55, 31)); g.fillRoundRect(0, 0, 111, 84, 8, 8);
        g.setColor(new Color(176, 107, 47)); g.fillRoundRect(4, 2, 103, 35, 8, 8);
        g.setColor(new Color(130, 77, 36)); g.fillRect(4, 38, 103, 42);
        g.setColor(GameTheme.GOLD); g.setStroke(new BasicStroke(4f)); g.drawRoundRect(1, 1, 109, 81, 8, 8);
        g.drawLine(3, 36, 108, 36);
        for (int p : new int[] {17, 88}) { g.fillRect(p, 3, 8, 77); }
        g.fillRoundRect(47, 28, 20, 24, 4, 4);
        g.setColor(new Color(77, 53, 24)); g.fillOval(53, 33, 8, 8); g.fillRect(56, 38, 3, 7);
        g.setColor(new Color(255, 237, 155));
        for (int p : new int[] {16, 38, 76}) { g.fillOval(p, -12 - p % 8, 15, 8); }
        g.dispose();
    }

    private static void compass(Graphics2D g, int x, int y, int r) {
        g.setColor(new Color(234, 212, 148)); g.fillOval(x - r, y - r, r * 2, r * 2);
        g.setColor(new Color(139, 88, 45)); g.setStroke(new BasicStroke(3f)); g.drawOval(x - r, y - r, r * 2, r * 2);
        g.setColor(new Color(20, 94, 136));
        g.fill(new Polygon(new int[] {x, x - 11, x, x + 11}, new int[] {y - r + 9, y, y + r - 9, y}, 4));
        g.setColor(new Color(200, 79, 68));
        g.fillPolygon(new int[] {x, x - 11, x + 11}, new int[] {y - r + 9, y, y}, 3);
        g.setColor(GameTheme.GOLD); g.fillOval(x - 4, y - 4, 8, 8);
    }
}
