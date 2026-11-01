package client;

import java.awt.Color;
import java.awt.Font;
import javax.swing.JLabel;

final class ResultPlayerCard extends RoundedPanel {
    private final Color accent;
    private final int number;
    private final JLabel title, avatar = new JLabel(), username = FantasyScreen.label("--", 18, GameTheme.TEXT);
    private final JLabel scoreCaption = FantasyScreen.label("\u0110i\u1ec3m kho b\u00e1u", 12, GameTheme.TEXT);
    private final JLabel score = FantasyScreen.label("0", 30, GameTheme.GOLD);
    private final JLabel collected = FantasyScreen.label("S\u1ed1 r\u01b0\u01a1ng: 0", 14, GameTheme.TEXT);
    private final JLabel outcome = FantasyScreen.label("", 15, GameTheme.TEXT), rank = FantasyScreen.label("", 14, GameTheme.GOLD);
    private String fullName = "--";

    ResultPlayerCard(int number, Color accent) {
        this.accent = accent; this.number = number; setName("result-player-" + number); setLayout(null);
        title = FantasyScreen.label("PLAYER " + number, 12, accent.brighter());
        username.setName("result-name-" + number); score.setName("result-score-" + number);
        collected.setName("result-collected-" + number); outcome.setName("result-player-outcome-" + number); rank.setName("result-player-rank-" + number);
        for (JLabel item : new JLabel[] {title, avatar, username, scoreCaption, score, collected, outcome, rank}) { add(item); }
    }

    void setData(String name, int points, int chests, String result, int gain, boolean own) {
        fullName = name; username.setToolTipText(name); title.setText("PLAYER " + number + (own ? " / B\u1ea0N" : ""));
        score.setText(Integer.toString(points)); collected.setText("S\u1ed1 r\u01b0\u01a1ng: " + chests); outcome.setText(result);
        outcome.setForeground("TH\u1eaeNG".equals(result) ? FantasyTable.WIN : "THUA".equals(result) ? FantasyTable.LOSE : FantasyTable.DRAW);
        rank.setText("Rank: +" + gain); doLayout(); repaint();
    }

    @Override public void doLayout() {
        boolean compact = getHeight() < 300;
        int width = Math.max(0, getWidth() - 24), size = compact ? 40 : getWidth() < 200 ? 64 : 80;
        title.setBounds(12, compact ? 7 : 16, width, 20); avatar.setBounds((getWidth() - size) / 2, compact ? 30 : 45, size, size);
        avatar.setIcon(new PlayerAvatar(fullName, size, accent));
        username.setFont(GameTheme.BODY.deriveFont(Font.BOLD, compact ? 14f : 18f));
        username.setBounds(12, compact ? 74 : 136, width, compact ? 22 : 27); fit(username, fullName, width, true);
        scoreCaption.setBounds(12, compact ? 98 : 176, width, 20);
        score.setBounds(12, compact ? 120 : 198, width, compact ? 28 : 44);
        score.setFont(GameTheme.BODY.deriveFont(Font.BOLD, compact ? 20f : 30f)); fit(score, score.getText(), width, false);
        collected.setBounds(12, compact ? 150 : 257, width, compact ? 19 : 23);
        collected.setFont(GameTheme.BODY.deriveFont(Font.BOLD, compact ? 12f : 14f)); fit(collected, collected.getText(), width, false);
        outcome.setFont(GameTheme.BODY.deriveFont(Font.BOLD, compact ? 12f : 15f));
        outcome.setBounds(12, compact ? 172 : 294, width, compact ? 18 : 24);
        rank.setFont(GameTheme.BODY.deriveFont(Font.BOLD, compact ? 11f : 14f)); rank.setBounds(12, compact ? 192 : 330, width, compact ? 17 : 23);
    }

    private static void fit(JLabel label, String text, int width, boolean ellipsis) {
        if (ellipsis) {
            String shortened = text;
            while (!shortened.isEmpty() && label.getFontMetrics(label.getFont()).stringWidth(shortened + "...") > width) {
                shortened = shortened.substring(0, shortened.offsetByCodePoints(shortened.length(), -1));
            }
            label.setText(label.getFontMetrics(label.getFont()).stringWidth(text) <= width ? text : shortened + "...");
        } else {
            while (label.getFont().getSize() > 10 && label.getFontMetrics(label.getFont()).stringWidth(text) > width) {
                label.setFont(label.getFont().deriveFont((float) label.getFont().getSize() - 1));
            }
        }
    }
}
