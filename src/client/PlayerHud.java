package client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JLabel;
import javax.swing.JPanel;
import model.Player;

public final class PlayerHud extends JPanel {
    private final Color accent;
    private final JLabel title, avatar = new JLabel(), username = label("--", 16), score = label("0", 28);
    private final JLabel scoreCaption = label("\u0110I\u1ec2M / pts", 11), collected = label("R\u01b0\u01a1ng: 0", 16);
    private final JLabel ownBadge = label("", 11);
    private boolean own;

    public PlayerHud(int number, Color accent) {
        this.accent = accent; setOpaque(false); setLayout(null); setName("player-hud-" + number);
        title = label("PLAYER " + number, 12); title.setForeground(accent.brighter());
        username.setName("hud-name-" + number); score.setName("hud-score-" + number);
        collected.setName("hud-collected-" + number); ownBadge.setName("hud-own-" + number);
        score.setForeground(GameTheme.GOLD); ownBadge.setForeground(GameTheme.GOLD);
        for (JLabel item : new JLabel[] {title, avatar, username, scoreCaption, score, collected, ownBadge}) { add(item); }
    }

    private static JLabel label(String text, int size) {
        JLabel label = new JLabel(text, JLabel.CENTER); label.setFont(GameTheme.BODY.deriveFont(Font.BOLD, (float) size));
        label.setForeground(GameTheme.TEXT); return label;
    }

    public void setPlayer(Player player, String currentUsername) {
        String name = player == null ? "--" : player.getUsername();
        own = name.equals(currentUsername); username.setToolTipText(name); username.setText(name);
        score.setText(player == null ? "0" : Integer.toString(player.getMatchScore()));
        collected.setText("R\u01b0\u01a1ng: " + (player == null ? 0 : player.getCollectedTreasure()));
        ownBadge.setText(own ? "\u2605 C\u1ee6A B\u1ea0N" : "\u0110\u1ed0I TH\u1ee6");
        avatar.setToolTipText(name); doLayout(); repaint();
    }

    @Override public void doLayout() {
        boolean compact = getHeight() < 320, shortHud = getHeight() < 232;
        int size = shortHud ? 40 : compact ? 48 : 80, width = Math.max(0, getWidth() - 20);
        title.setBounds(10, 12, width, 18); avatar.setBounds((getWidth() - size) / 2, shortHud ? 32 : compact ? 36 : 43, size, size);
        avatar.setIcon(new PlayerAvatar(avatar.getToolTipText(), size, accent));
        username.setBounds(10, shortHud ? 76 : compact ? 89 : 136, width, 24);
        username.setFont(GameTheme.BODY.deriveFont(Font.BOLD, compact ? 14f : 16f));
        String name = username.getToolTipText() == null ? "--" : username.getToolTipText(), fitted = name;
        while (!fitted.isEmpty() && username.getFontMetrics(username.getFont()).stringWidth(fitted + "...") > width) {
            fitted = fitted.substring(0, fitted.offsetByCodePoints(fitted.length(), -1));
        }
        username.setText(username.getFontMetrics(username.getFont()).stringWidth(name) <= width ? name : fitted + "...");
        scoreCaption.setBounds(10, shortHud ? 101 : compact ? 116 : 177, width, 18);
        score.setBounds(10, shortHud ? 122 : compact ? 138 : 199, width, compact ? 36 : 40);
        score.setFont(GameTheme.BODY.deriveFont(Font.BOLD, compact ? 24f : 28f));
        while (score.getFont().getSize() > 12 && score.getFontMetrics(score.getFont()).stringWidth(score.getText()) > width) {
            score.setFont(score.getFont().deriveFont((float) score.getFont().getSize() - 1));
        }
        collected.setBounds(10, shortHud ? 158 : compact ? 178 : 258, width, 24);
        collected.setFont(GameTheme.BODY.deriveFont(Font.BOLD, compact ? 12f : 14f));
        while (collected.getFont().getSize() > 10 && collected.getFontMetrics(collected.getFont()).stringWidth(collected.getText()) > width) {
            collected.setFont(collected.getFont().deriveFont((float) collected.getFont().getSize() - 1));
        }
        ownBadge.setBounds(10, getHeight() - 30, width, 18); ownBadge.setVisible(getHeight() >= (compact ? 232 : 312));
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create(); GameTheme.smooth(g);
        g.setColor(new Color(10, 38, 48, 95)); g.fillRoundRect(3, 6, getWidth() - 6, getHeight() - 7, 8, 8);
        g.setPaint(new GradientPaint(0, 0, new Color(accent.getRed() / 3, accent.getGreen() / 3, accent.getBlue() / 3),
                0, getHeight(), GameTheme.INK));
        g.fillRoundRect(2, 2, getWidth() - 5, getHeight() - 9, 8, 8);
        g.setColor(own ? GameTheme.GOLD : accent); g.setStroke(new BasicStroke(own ? 3f : 2f));
        g.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 9, 8, 8); g.dispose();
    }
}
