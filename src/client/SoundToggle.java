package client;

import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.beans.PropertyChangeListener;
import javax.swing.Icon;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

/** Compact controls share the same per-client setting on every screen. */
public final class SoundToggle extends JToggleButton {
    private final SoundManager sound;
    private final PropertyChangeListener listener = event -> refreshLater();

    public SoundToggle(SoundManager sound, String name) {
        this.sound = sound; setName(name); setFocusable(false);
        setPreferredSize(new Dimension(32, 32));
        setMargin(new java.awt.Insets(0, 0, 0, 0)); setBackground(GameTheme.PANEL);
        setIcon(new SpeakerIcon());
        addActionListener(event -> {
            sound.setEnabled(isSelected());
            if (sound.isEnabled()) { sound.play(SoundManager.Effect.CLICK); }
            refresh();
        });
        refresh();
    }

    @Override public void addNotify() { super.addNotify(); sound.addEnabledListener(listener); refresh(); }
    @Override public void removeNotify() { sound.removeEnabledListener(listener); super.removeNotify(); }

    private void refreshLater() {
        if (SwingUtilities.isEventDispatchThread()) { refresh(); }
        else { SwingUtilities.invokeLater(this::refresh); }
    }

    public void refresh() {
        setSelected(sound.isEnabled());
        String text = sound.isEnabled() ? "T\u1eaft \u00e2m thanh" : "B\u1eadt \u00e2m thanh";
        setToolTipText(text); getAccessibleContext().setAccessibleName(text); repaint();
    }

    private final class SpeakerIcon implements Icon {
        public int getIconWidth() { return 22; }
        public int getIconHeight() { return 22; }
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create(); g.translate(x, y); GameTheme.smooth(g);
            g.setColor(GameTheme.GOLD); g.setStroke(new BasicStroke(1.7f));
            g.fillRect(1, 8, 4, 7);
            g.fillPolygon(new int[] {5, 11, 11, 5}, new int[] {8, 3, 20, 15}, 4);
            if (sound.isEnabled()) { g.drawArc(11, 6, 7, 11, -60, 120); g.drawArc(11, 2, 10, 19, -60, 120); }
            else { g.drawLine(14, 7, 21, 16); g.drawLine(21, 7, 14, 16); }
            g.dispose();
        }
    }
}
