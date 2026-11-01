package client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.JTextField;
import javax.swing.JScrollPane;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.border.AbstractBorder;

public final class GameTheme {
    public static final Color INK = new Color(9, 34, 53);
    public static final Color PANEL = new Color(12, 46, 67);
    public static final Color GOLD = new Color(247, 198, 76);
    public static final Color TEXT = new Color(237, 247, 252);
    public static final Font BODY = new Font("SansSerif", Font.PLAIN, 14);
    private GameTheme() { }

    public static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    public static void styleField(JTextField field) {
        field.setFont(BODY);
        field.setForeground(TEXT); field.setCaretColor(GOLD);
        field.setBackground(new Color(14, 57, 80));
        field.setSelectionColor(new Color(39, 113, 149)); field.setSelectedTextColor(Color.WHITE);
        field.setDisabledTextColor(new Color(156, 188, 205));
        field.setBorder(new AbstractBorder() {
            @Override public Insets getBorderInsets(Component c) { return new Insets(8, 12, 8, 12); }
            @Override public Insets getBorderInsets(Component c, Insets insets) {
                insets.set(8, 12, 8, 12); return insets;
            }
            @Override public void paintBorder(Component c, Graphics graphics, int x, int y, int width, int height) {
                Graphics2D g = (Graphics2D) graphics.create(); smooth(g);
                g.setColor(c.hasFocus() ? GOLD : new Color(61, 138, 177));
                g.setStroke(new BasicStroke(c.hasFocus() ? 2f : 1f));
                g.drawRoundRect(x + 1, y + 1, width - 3, height - 3, 8, 8); g.dispose();
            }
        });
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                field.repaint(); field.scrollRectToVisible(new java.awt.Rectangle(0, 0, field.getWidth(), field.getHeight()));
            }
            @Override public void focusLost(FocusEvent e) { field.repaint(); }
        });
    }

    public static void styleScrollPane(JScrollPane scroll) {
        scroll.setBorder(null); scroll.setOpaque(false); scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getVerticalScrollBar().setUI(new BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() { thumbColor = GOLD; trackColor = INK; }
            @Override protected javax.swing.JButton createDecreaseButton(int orientation) { return zeroButton(); }
            @Override protected javax.swing.JButton createIncreaseButton(int orientation) { return zeroButton(); }
            private javax.swing.JButton zeroButton() {
                javax.swing.JButton button = new javax.swing.JButton();
                button.setPreferredSize(new java.awt.Dimension(0, 0)); return button;
            }
        });
    }
}
