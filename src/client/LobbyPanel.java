package client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import model.Player;

public final class LobbyPanel extends JPanel {
    private final Consumer<String> invite;
    private String currentUsername, selectedOpponent;
    private boolean connected;
    private final Map<String, Player> online = new LinkedHashMap<>();
    private final Map<String, PlayerCard> cards = new LinkedHashMap<>();
    private final JLabel profileName = label("--", 18, GameTheme.TEXT);
    private final JLabel profileRank = label("--", 24, GameTheme.GOLD);
    private final JLabel profileWins = label("--", 18, GameTheme.TEXT);
    private final JLabel profileTreasure = label("--", 18, GameTheme.TEXT);
    private final JLabel profileMatches = label("--", 18, GameTheme.TEXT);
    private final JLabel profileStatus = label("CH\u1edc C\u1eacP NH\u1eacT", 13, GameTheme.TEXT);
    private final JLabel profileAvatar = new JLabel();
    private final JLabel opponentCount = label("0", 12, GameTheme.GOLD);
    private final JLabel hostLabel = label("Server IP: --", 12, GameTheme.TEXT);
    private final JLabel portLabel = label("Port: --", 12, GameTheme.TEXT);
    private final JLabel connectionLabel = label("\u25cf Ch\u01b0a k\u1ebft n\u1ed1i", 12, GameTheme.TEXT);
    private final JLabel onlineCount = label("Ng\u01b0\u1eddi online: 0", 12, GameTheme.TEXT);
    private final GameButton inviteButton = action("M\u1edcI THI \u0110\u1ea4U", true);
    private final GameButton rankingButton = action("B\u1ea2NG X\u1ebeP H\u1ea0NG", false);
    private final GameButton historyButton = action("L\u1ecaCH S\u1eec TR\u1eacN \u0110\u1ea4U", false);
    private final GameButton logoutButton = new GameButton("\u0110\u0102NG XU\u1ea4T", GameButton.Variant.RED);
    private final PlayerList playerList = new PlayerList();

    public LobbyPanel(Consumer<String> invite, Runnable refresh, Runnable ranking, Runnable history, Runnable logout) {
        this(invite, refresh, ranking, history, logout, null);
    }

    public LobbyPanel(Consumer<String> invite, Runnable refresh, Runnable ranking, Runnable history, Runnable logout, SoundManager sound) {
        super(new BorderLayout()); this.invite = invite; setName("lobby-screen");
        JPanel header = createHeader();
        if (sound != null) { header.add(new SoundToggle(sound, "lobby-sound-toggle")); }
        add(header, BorderLayout.NORTH);
        JPanel columns = new JPanel(new BorderLayout(12, 0)); columns.setOpaque(false);
        columns.setName("lobby-columns"); columns.setBorder(BorderFactory.createEmptyBorder(10, 12, 16, 12));
        JScrollPane profileScroll = sideScroll(createProfile(), 210);
        profileScroll.setName("profile-scroll"); columns.add(profileScroll, BorderLayout.WEST);
        JPanel center = new JPanel(new BorderLayout(0, 10)); center.setOpaque(false); center.setName("lobby-center");
        JPanel listHeader = new JPanel(new BorderLayout(8, 0)); listHeader.setBackground(GameTheme.PANEL);
        listHeader.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        listHeader.add(label("NG\u01af\u1edcI CH\u01a0I ONLINE", 12, GameTheme.TEXT), BorderLayout.CENTER);
        opponentCount.setHorizontalAlignment(SwingConstants.CENTER); opponentCount.setPreferredSize(new Dimension(26, 20));
        listHeader.add(opponentCount, BorderLayout.EAST); center.add(listHeader, BorderLayout.NORTH);
        JScrollPane listScroll = new JScrollPane(playerList); GameTheme.styleScrollPane(listScroll);
        listScroll.setName("opponents-scroll"); center.add(listScroll, BorderLayout.CENTER); columns.add(center, BorderLayout.CENTER);
        SideArea actions = new SideArea(228, 488) {
            @Override public void doLayout() {
                for (java.awt.Component c : getComponents()) {
                    int margin = c instanceof GameButton ? 10 : c instanceof JLabel ? 14 : 0;
                    c.setBounds(margin, c.getY(), getWidth() - margin * 2, c.getHeight());
                    if (c instanceof JPanel) { ((JPanel) c).doLayout(); }
                }
            }
        }; actions.setName("lobby-actions");
        JLabel actionsTitle = label("H\u00c0NH \u0110\u1ed8NG", 14, GameTheme.GOLD);
        actionsTitle.setHorizontalAlignment(SwingConstants.CENTER); actionsTitle.setBounds(14, 10, 200, 22); actions.add(actionsTitle);
        GameButton refreshButton = action("L\u00c0M M\u1edaI", false);
        GameButton[] buttons = {inviteButton, refreshButton, rankingButton, historyButton, logoutButton};
        String[] names = {"lobby-invite", "lobby-refresh", "lobby-ranking", "lobby-history", "lobby-logout"};
        int[] positions = {42, 100, 152, 204, 256};
        logoutButton.setFont(GameTheme.BODY.deriveFont(Font.BOLD, 13f));
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setName(names[i]); buttons[i].setBounds(10, positions[i], 208, i == 0 ? 48 : 42); actions.add(buttons[i]);
        }
        inviteButton.addActionListener(event -> { if (canInvite()) { this.invite.accept(selectedOpponent); } });
        refreshButton.addActionListener(event -> refresh.run());
        rankingButton.addActionListener(event -> { if (isSelfFree() && connected) { ranking.run(); } });
        historyButton.addActionListener(event -> { if (isSelfFree() && connected) { history.run(); } });
        logoutButton.addActionListener(event -> { if (isSelfFree() && connected) { logout.run(); } });
        actions.add(createConnectionInfo());
        JScrollPane actionsScroll = sideScroll(actions, 228); actionsScroll.setName("actions-scroll");
        columns.add(actionsScroll, BorderLayout.EAST); add(columns, BorderLayout.CENTER);
        JLabel footer = label("PTIT - L\u1eacP TR\u00ccNH M\u1ea0NG", 12, GameTheme.TEXT);
        footer.setHorizontalAlignment(SwingConstants.CENTER); footer.setOpaque(true); footer.setBackground(new Color(13, 67, 99));
        footer.setPreferredSize(new Dimension(0, 28)); add(footer, BorderLayout.SOUTH);
        profileName.setName("profile-username"); profileRank.setName("profile-rank"); profileWins.setName("profile-wins");
        profileTreasure.setName("profile-treasure"); profileMatches.setName("profile-matches"); profileStatus.setName("profile-status");
        hostLabel.setName("lobby-server-ip"); portLabel.setName("lobby-server-port"); onlineCount.setName("lobby-online-count");
        connectionLabel.setName("lobby-connection"); updateOnlinePlayers(List.of());
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(null) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create(); GameTheme.smooth(g);
                int w = Math.min(600, getWidth() - 32), x = (getWidth() - w) / 2;
                g.setColor(new Color(64, 42, 30, 75)); g.fillRoundRect(x, 20, w, 72, 8, 8);
                g.setPaint(new java.awt.GradientPaint(0, 14, new Color(147, 91, 44), 0, 88, new Color(86, 54, 34)));
                g.fillRoundRect(x, 14, w, 72, 8, 8); g.setColor(GameTheme.GOLD);
                g.drawRoundRect(x, 14, w, 72, 8, 8);
                g.setColor(new Color(200, 138, 70, 100)); g.drawLine(x + 8, 68, x + w - 8, 68);
                g.dispose();
            }
            @Override public void doLayout() {
                for (java.awt.Component c : getComponents()) {
                    if (c instanceof SoundToggle) { c.setBounds(getWidth() - 42, 4, 32, 32); }
                    else { c.setBounds(12, c.getY(), getWidth() - 24, c.getHeight()); }
                }
            }
        };
        header.setOpaque(false); header.setPreferredSize(new Dimension(0, 118));
        JLabel title = label("S\u1ea2NH CH\u1edc", 36, GameTheme.GOLD); title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setBounds(0, 24, 600, 50); header.add(title);
        JLabel subtitle = label("K\u1ebeT N\u1ed0I - TH\u00c1CH \u0110\u1ea4U - S\u0102N KHO B\u00c1U", 12, GameTheme.INK);
        subtitle.setHorizontalAlignment(SwingConstants.CENTER); subtitle.setBounds(0, 92, 600, 20); header.add(subtitle); return header;
    }

    private JPanel createProfile() {
        RoundedPanel profile = new RoundedPanel() {
            @Override public void doLayout() {
                for (java.awt.Component c : getComponents()) {
                    c.setBounds(c == profileAvatar ? (getWidth() - 80) / 2 : 18, c.getY(),
                            c == profileAvatar ? 80 : getWidth() - 36, c.getHeight());
                }
                if (profileName.getToolTipText() != null) { setBoundedText(profileName, profileName.getToolTipText()); }
            }
        }; profile.setName("lobby-profile"); profile.setLayout(null);
        JLabel title = label("H\u1ed2 S\u01a0 C\u1ee6A B\u1ea0N", 13, GameTheme.GOLD);
        title.setHorizontalAlignment(SwingConstants.CENTER); title.setBounds(18, 20, 174, 20); profile.add(title);
        profileAvatar.setBounds(65, 55, 80, 80); profile.add(profileAvatar);
        profileName.setHorizontalAlignment(SwingConstants.CENTER); profileName.setBounds(18, 149, 174, 28); profile.add(profileName);
        String[] labels = {"\u0110I\u1ec2M X\u1ebeP H\u1ea0NG", "S\u1ed1 tr\u1eadn th\u1eafng", "T\u1ed5ng kho b\u00e1u", "T\u1ed5ng tr\u1eadn"};
        JLabel[] values = {profileRank, profileWins, profileTreasure, profileMatches};
        int[] rows = {191, 265, 323, 381};
        for (int i = 0; i < rows.length; i++) {
            JLabel caption = label(labels[i], 12, new Color(183, 216, 232));
            caption.setHorizontalAlignment(SwingConstants.CENTER); caption.setBounds(18, rows[i], 174, 20); profile.add(caption);
            values[i].setHorizontalAlignment(SwingConstants.CENTER); values[i].setBounds(18, rows[i] + 23, 174, i == 0 ? 34 : 28); profile.add(values[i]);
        }
        profileStatus.setHorizontalAlignment(SwingConstants.CENTER); profileStatus.setBounds(18, 450, 174, 24); profile.add(profileStatus);
        SideArea area = new SideArea(210, 488); area.setLayout(new BorderLayout()); area.add(profile); return area;
    }

    private JPanel createConnectionInfo() {
        RoundedPanel info = new RoundedPanel() {
            @Override public void doLayout() {
                for (java.awt.Component c : getComponents()) { c.setBounds(16, c.getY(), getWidth() - 32, c.getHeight()); }
                if (hostLabel.getToolTipText() != null) { setBoundedText(hostLabel, hostLabel.getToolTipText()); }
            }
        }; info.setName("lobby-connection-info"); info.setLayout(null);
        info.setBounds(0, 312, 228, 176);
        JLabel title = label("TH\u00d4NG TIN K\u1ebeT N\u1ed0I", 12, GameTheme.GOLD);
        title.setHorizontalAlignment(SwingConstants.CENTER); title.setBounds(14, 18, 200, 20); info.add(title);
        JLabel[] labels = {hostLabel, portLabel, connectionLabel, onlineCount};
        int[] rows = {48, 76, 103, 136};
        for (int i = 0; i < labels.length; i++) { labels[i].setBounds(16, rows[i], 196, 20); info.add(labels[i]); }
        return info;
    }

    private static JScrollPane sideScroll(JPanel content, int width) {
        JScrollPane scroll = new JScrollPane(content); GameTheme.styleScrollPane(scroll);
        scroll.setPreferredSize(new Dimension(width, 0)); return scroll;
    }

    private static GameButton action(String text, boolean gold) {
        GameButton button = new GameButton(text, gold); button.setFont(GameTheme.BODY.deriveFont(Font.BOLD, 13f)); return button;
    }
    private static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text); label.setFont(new Font("SansSerif", Font.BOLD, size)); label.setForeground(color); return label;
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create(); TreasureBackground.paint(g, getWidth(), getHeight()); g.dispose();
    }

    public void setCurrentUsername(String username) {
        currentUsername = username; selectedOpponent = null;
        setBoundedText(profileName, username == null ? "--" : username);
        profileAvatar.setIcon(new PlayerAvatar(username, 80));
        profileRank.setText("--"); profileWins.setText("--"); profileTreasure.setText("--"); profileMatches.setText("--");
        updateOnlinePlayers(List.of());
    }

    public void updateStatistics(List<Player> ranking) {
        for (Player p : ranking) {
            if (p.getUsername().equals(currentUsername)) {
                profileRank.setText(Integer.toString(p.getTotalRankPoint())); profileWins.setText(Integer.toString(p.getWins()));
                profileTreasure.setText(Integer.toString(p.getTotalTreasureCollected())); profileMatches.setText(Integer.toString(p.getTotalMatches()));
                return;
            }
        }
    }

    public void updateOnlinePlayers(List<Player> players) {
        online.clear();
        for (Player p : players) {
            Player snapshot = new Player(); snapshot.setUsername(p.getUsername()); snapshot.setTotalRankPoint(p.getTotalRankPoint());
            snapshot.setWins(p.getWins()); snapshot.setStatus(p.getStatus()); online.put(snapshot.getUsername(), snapshot);
        }
        Player self = online.get(currentUsername);
        if (self != null) {
            profileRank.setText(Integer.toString(self.getTotalRankPoint())); profileWins.setText(Integer.toString(self.getWins()));
        }
        profileStatus.setText(self == null ? "CH\u1edc C\u1eacP NH\u1eacT" : self.getStatus() == Player.Status.FREE ? "\u25cf \u0110ANG R\u1ea2NH" : "\u25cf \u0110ANG CH\u01a0I");
        profileStatus.setForeground(self == null ? GameTheme.TEXT : self.getStatus() == Player.Status.FREE
                ? new Color(115, 226, 150) : new Color(252, 133, 148));
        if (!canInvite()) { selectedOpponent = null; }
        cards.clear(); playerList.removeAll();
        List<Player> opponents = new ArrayList<>(online.values()); opponents.sort(Comparator.comparing(Player::getUsername));
        for (Player p : opponents) {
            if (p.getUsername().equals(currentUsername)) { continue; }
            PlayerCard card = new PlayerCard(p, () -> selectOpponent(p.getUsername()));
            cards.put(p.getUsername(), card); playerList.add(card);
        }
        if (cards.isEmpty()) {
            JLabel empty = label("Ch\u01b0a c\u00f3 \u0111\u1ed1i th\u1ee7 online", 12, GameTheme.TEXT);
            empty.setName("lobby-empty"); empty.setHorizontalAlignment(SwingConstants.CENTER);
            empty.setOpaque(true); empty.setBackground(GameTheme.PANEL); playerList.add(empty);
        }
        opponentCount.setText(Integer.toString(cards.size())); onlineCount.setText("Ng\u01b0\u1eddi online: " + online.size());
        updateActions(); playerList.revalidate(); playerList.repaint();
    }

    public void setConnection(String host, int port, boolean connected) {
        this.connected = connected;
        setBoundedText(hostLabel, "Server IP: " + host); portLabel.setText("Port: " + port);
        connectionLabel.setText(connected ? "\u25cf \u0110\u00e3 k\u1ebft n\u1ed1i" : "\u25cf Ch\u01b0a k\u1ebft n\u1ed1i");
        connectionLabel.setForeground(connected ? new Color(115, 226, 150) : new Color(252, 133, 148));
        if (!connected) { selectedOpponent = null; }
        updateActions();
    }

    private static void setBoundedText(JLabel label, String text) {
        label.setToolTipText(text); String shortened = text;
        while (!shortened.isEmpty() && label.getFontMetrics(label.getFont()).stringWidth(shortened) > label.getWidth()) {
            int end = shortened.endsWith("...") ? shortened.length() - 3 : shortened.length();
            if (end == 0) { break; }
            shortened = shortened.substring(0, shortened.offsetByCodePoints(end, -1)) + "...";
        }
        label.setText(shortened);
    }

    private boolean isSelfFree() {
        Player self = online.get(currentUsername); return self != null && self.getStatus() == Player.Status.FREE;
    }
    public boolean canInvite() {
        Player other = online.get(selectedOpponent);
        return connected && isSelfFree() && other != null && other.getStatus() == Player.Status.FREE
                && !other.getUsername().equals(currentUsername);
    }
    public String getSelectedOpponent() { return selectedOpponent; }
    public boolean selectOpponent(String username) {
        Player p = online.get(username);
        if (!connected || !isSelfFree() || p == null || p.getStatus() != Player.Status.FREE || username.equals(currentUsername)) { return false; }
        selectedOpponent = username; updateActions(); return true;
    }
    private void updateActions() {
        inviteButton.setEnabled(canInvite()); boolean enabled = connected && isSelfFree();
        rankingButton.setEnabled(enabled); historyButton.setEnabled(enabled); logoutButton.setEnabled(enabled);
        for (PlayerCard card : cards.values()) {
            card.setEnabled(enabled && card.isFree()); card.setOpponentSelected(card.getUsername().equals(selectedOpponent));
        }
    }

    private static class SideArea extends JPanel implements Scrollable {
        private final Dimension size;
        SideArea(int width, int height) { super(null); size = new Dimension(width, height); setOpaque(false); }
        @Override public Dimension getPreferredSize() { return new Dimension(size); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(24, r.height - 24); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
    private static final class PlayerList extends JPanel implements Scrollable {
        PlayerList() {
            super(null); setOpaque(false);
            addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override public void componentResized(java.awt.event.ComponentEvent e) { revalidate(); }
            });
        }
        private int columns() { return getWidth() >= 470 ? 2 : 1; }
        @Override public void doLayout() {
            int columns = columns(), gap = 10, width = (getWidth() - 16 - (columns - 1) * gap) / columns;
            for (int i = 0; i < getComponentCount(); i++) {
                getComponent(i).setBounds(8 + i % columns * (width + gap), 8 + i / columns * 134, width, 124);
            }
        }
        @Override public Dimension getPreferredSize() { return new Dimension(250, Math.max(150, (getComponentCount() + columns() - 1) / columns() * 134 + 16)); }
        @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(250, 400); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(24, r.height - 24); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
