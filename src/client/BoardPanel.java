package client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JPanel;
import javax.swing.Timer;
import model.GameState;
import model.Player;
import model.Position;
import model.Treasure;

/** Paints only the server snapshot; tile decoration is deterministic. */
public final class BoardPanel extends JPanel {
    public static final Color BLUE = new Color(43, 166, 232);
    public static final Color PINK = new Color(228, 75, 120);
    // Bảng vẽ giữ trạng thái đã nhận từ Server; không tự quyết định vị trí, nhặt rương hay cộng điểm.
    private GameState state;
    private String currentUsername;
    private final Map<String, Long> stunDeadlines = new HashMap<>();
    private final Runnable stunChanged;
    private final Timer stunTimer;
    private long animationTime;

    public BoardPanel() { this(() -> { }); }

    public BoardPanel(Runnable stunChanged) {
        this.stunChanged = stunChanged;
        stunTimer = new Timer(33, event -> {
            animationTime = System.nanoTime();
            if (stunDeadlines.values().removeIf(deadline -> animationTime - deadline >= 0)) { stunChanged.run(); }
            if (stunDeadlines.isEmpty()) { ((Timer) event.getSource()).stop(); }
            repaint();
        });
        setName("fantasy-board"); setOpaque(false); setToolTipText("");
        getAccessibleContext().setAccessibleName("B\u1ea3n \u0111\u1ed3 s\u0103n kho b\u00e1u 7 x 7");
    }

    public void showStun(String username, long deadline) {
        stunDeadlines.put(username, deadline); animationTime = System.nanoTime();
        if (!stunTimer.isRunning()) { stunTimer.start(); }
        stunChanged.run(); repaint();
    }

    public boolean isStunned(String username) {
        Long deadline = stunDeadlines.get(username);
        return deadline != null && System.nanoTime() - deadline < 0;
    }

    public void clearStuns() {
        stunTimer.stop(); stunDeadlines.clear(); stunChanged.run(); repaint();
    }

    @Override public void removeNotify() { clearStuns(); super.removeNotify(); }

    // ===== NHẬN SNAPSHOT VÀ YÊU CẦU SWING VẼ LẠI BẢN ĐỒ =====
    public void setSnapshot(GameState state, String username) {
        // Lưu trạng thái và tên người đang dùng Client; repaint() lên lịch vẽ, không xử lý một bước di chuyển.
        this.state = state; currentUsername = username; repaint();
    }

    // ===== ĐỔI TỌA ĐỘ Ô 7x7 THÀNH VÙNG PIXEL TRÊN CỬA SỔ =====
    public Rectangle getTileBounds(int row, int col) {
        if (row < 0 || col < 0 || row >= 7 || col >= 7) { throw new IllegalArgumentException("Outside board"); }
        int side = Math.max(0, Math.min(getWidth(), getHeight()) - 16);
        int x = (getWidth() - side) / 2, y = (getHeight() - side) / 2;
        return new Rectangle(x + col * side / 7, y + row * side / 7,
                (col + 1) * side / 7 - col * side / 7, (row + 1) * side / 7 - row * side / 7);
    }

    @Override public String getToolTipText(MouseEvent event) {
        if (state == null) { return null; }
        for (int row = 0; row < 7; row++) {
            for (int col = 0; col < 7; col++) {
                if (!getTileBounds(row, col).contains(event.getPoint())) { continue; }
                String result = "(" + row + ", " + col + ") " + (state.getMap()[row][col] == 1 ? "Obstacle" : "Road");
                for (Player p : new Player[] {state.getPlayer1(), state.getPlayer2()}) {
                    if (at(p.getPosition(), row, col)) { return result + " - " + p.getUsername(); }
                }
                for (Treasure t : state.getTreasures()) {
                    if (at(t.getPosition(), row, col)) { return result + " - " + t.getId() + ": " + t.getType() + " (+" + t.getValue() + ")"; }
                }
                return result;
            }
        }
        return null;
    }

    private static boolean at(Position position, int row, int col) {
        return position != null && position.getRow() == row && position.getCol() == col;
    }

    // ===== VẼ NỀN, VẬT CẢN, RƯƠNG VÀ HAI NHÂN VẬT THEO SNAPSHOT =====
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create(); GameTheme.smooth(g);
        Rectangle first = getTileBounds(0, 0), last = getTileBounds(6, 6);
        int side = last.x + last.width - first.x;
        g.setColor(new Color(9, 45, 48, 110)); g.fillRoundRect(first.x - 7, first.y - 3, side + 14, side + 14, 8, 8);
        g.setColor(new Color(97, 111, 88)); g.fillRoundRect(first.x - 6, first.y - 6, side + 12, side + 12, 8, 8);
        g.setColor(GameTheme.GOLD); g.drawRoundRect(first.x - 6, first.y - 6, side + 12, side + 12, 8, 8);
        // Mỗi ô được vẽ trên hệ tọa độ mẫu 100x100 rồi scale theo kích thước ô thực tế.
        for (int row = 0; row < 7; row++) {
            for (int col = 0; col < 7; col++) {
                Rectangle tile = getTileBounds(row, col);
                Graphics2D t = (Graphics2D) g.create(tile.x, tile.y, tile.width, tile.height);
                t.scale(tile.width / 100.0, tile.height / 100.0);
                grass(t, row, col);
                // Giá trị 1 là tường/vật cản; mẫu trang trí dựa vào tọa độ nên hai Client không tự sinh map khác nhau.
                if (state != null && state.getMap()[row][col] == 1) { obstacle(t, (row * 7 + col) % 3); }
                t.dispose();
            }
        }
        if (state != null) {
            // Chỉ vẽ những rương còn trong snapshot; Server bỏ rương cũ thì lần vẽ này tự bỏ hình của nó.
            for (Treasure treasure : state.getTreasures()) {
                Rectangle tile = getTileBounds(treasure.getPosition().getRow(), treasure.getPosition().getCol());
                Graphics2D t = (Graphics2D) g.create(tile.x, tile.y, tile.width, tile.height);
                t.scale(tile.width / 100.0, tile.height / 100.0); chest(t, treasure.getType()); t.dispose();
                g.setFont(new Font("SansSerif", Font.BOLD, 10)); g.setColor(new Color(44, 58, 35));
                String value = "+" + treasure.getValue();
                g.drawString(value, tile.x + (tile.width - g.getFontMetrics().stringWidth(value)) / 2, tile.y + tile.height - 3);
            }
            // Hai người dùng hai màu cố định; vị trí hiển thị lấy trực tiếp từ Player trong snapshot.
            paintPlayer(g, state.getPlayer1(), BLUE); paintPlayer(g, state.getPlayer2(), PINK);
            // Effects use board coordinates so adjacent tiles never clip the orbit.
            paintStun(g, state.getPlayer1()); paintStun(g, state.getPlayer2());
        } else {
            g.setFont(GameTheme.BODY.deriveFont(Font.BOLD, 14f)); g.setColor(GameTheme.INK);
            String waiting = "\u0110ang ch\u1edd b\u1ea3n \u0111\u1ed3...";
            g.drawString(waiting, (getWidth() - g.getFontMetrics().stringWidth(waiting)) / 2, getHeight() / 2);
        }
        g.dispose();
    }

    private void paintStun(Graphics2D g, Player player) {
        if (!isStunned(player.getUsername()) || player.getPosition() == null) { return; }
        Rectangle tile = getTileBounds(player.getPosition().getRow(), player.getPosition().getCol());
        double radius = Math.max(3.2, Math.min(5, tile.width * .085));
        double orbitX = tile.width * .31, orbitY = radius * 1.1;
        double centerX = Math.max(orbitX + radius + 1,
                Math.min(getWidth() - orbitX - radius - 1, tile.getCenterX()));
        double centerY = Math.max(orbitY + radius + 1, tile.y - 1);
        double phase = (animationTime % 2_000_000_000L) / 2_000_000_000.0 * Math.PI * 2;
        for (int star = 0; star < 4; star++) {
            double angle = phase + star * Math.PI / 2;
            double x = centerX + Math.cos(angle) * orbitX, y = centerY + Math.sin(angle) * orbitY;
            Path2D shape = new Path2D.Double();
            for (int point = 0; point < 10; point++) {
                double a = -Math.PI / 2 + point * Math.PI / 5 + phase;
                double r = (point % 2 == 0 ? radius : radius * .43);
                if (point == 0) { shape.moveTo(x + Math.cos(a) * r, y + Math.sin(a) * r); }
                else { shape.lineTo(x + Math.cos(a) * r, y + Math.sin(a) * r); }
            }
            shape.closePath(); g.setStroke(new BasicStroke(1f));
            g.setColor(new Color(106, 71, 12)); g.draw(shape);
            g.setColor(new Color(255, 235, 40)); g.fill(shape);
        }
    }

    private static void grass(Graphics2D g, int row, int col) {
        int variant = (row * 13 + col * 7) % 4;
        g.setColor(new Color(147 + variant * 6, 190 + variant * 3, 88 + variant * 4)); g.fillRect(0, 0, 100, 100);
        g.setColor(new Color(210 + variant * 3, 204 + variant * 2, 128 + variant * 4)); g.fillRoundRect(6, 6, 88, 88, 8, 8);
        g.setColor(new Color(169, 178, 97, 100)); g.drawLine(12, 68, 55, 68); g.drawLine(69, 31, 90, 31);
        g.setColor(new Color(73, 141, 65));
        for (int x : new int[] {2, 92}) { g.drawLine(x, 32, x + 3, 21); g.drawLine(x + 3, 32, x + 7, 26); }
        g.setColor(new Color(251, 236, 161)); g.fillOval(88, 83, 4, 4); g.fillOval(4, 10, 4, 4);
    }

    private static void obstacle(Graphics2D g, int variant) {
        g.setColor(new Color(36, 71, 46, 65)); g.fillOval(12, 72, 77, 19);
        if (variant == 0) {
            g.setColor(new Color(101, 113, 111));
            g.fillPolygon(new int[] {12, 19, 42, 72, 91, 83, 42}, new int[] {71, 30, 13, 20, 53, 82, 91}, 7);
            g.setColor(new Color(164, 174, 157));
            g.fillPolygon(new int[] {19, 42, 72, 49}, new int[] {30, 13, 20, 55}, 4);
            g.setColor(new Color(76, 88, 86)); g.drawLine(49, 55, 42, 88); g.drawLine(49, 55, 84, 64);
        } else if (variant == 1) {
            g.setColor(new Color(114, 80, 48)); g.fillRect(44, 55, 14, 34);
            g.setColor(new Color(33, 112, 68)); g.fillPolygon(new int[] {8, 51, 93}, new int[] {69, 18, 69}, 3);
            g.setColor(new Color(46, 146, 77)); g.fillPolygon(new int[] {17, 51, 85}, new int[] {48, 4, 48}, 3);
            g.setColor(new Color(77, 164, 89)); g.fillPolygon(new int[] {30, 51, 70}, new int[] {24, 4, 24}, 3);
        } else {
            g.setColor(new Color(47, 124, 72)); g.fillOval(10, 30, 56, 51); g.fillOval(45, 28, 45, 56);
            g.setColor(new Color(78, 156, 74)); g.fillOval(27, 15, 49, 58); g.fillOval(15, 29, 28, 28);
            g.setColor(new Color(233, 123, 133)); g.fillOval(33, 45, 6, 6); g.fillOval(65, 57, 6, 6);
        }
    }

    private static void chest(Graphics2D g, String type) {
        boolean gold = Treasure.GOLD.equals(type), silver = Treasure.SILVER.equals(type);
        Color body = gold ? new Color(215, 141, 26) : silver ? new Color(119, 147, 164) : new Color(136, 76, 41);
        Color lid = gold ? new Color(255, 218, 80) : silver ? new Color(218, 235, 238) : new Color(196, 125, 64);
        Color trim = gold ? new Color(255, 236, 122) : silver ? new Color(239, 250, 250) : new Color(230, 177, 91);
        if (gold) { g.setColor(new Color(255, 247, 153, 95)); g.fillOval(5, 10, 90, 77); }
        g.setColor(new Color(33, 52, 37, 65)); g.fillOval(13, 70, 77, 17);
        g.setColor(body); g.fillRoundRect(19, 42, 63, 37, 5, 5);
        g.setColor(lid); g.fillRoundRect(19, 20, 63, 33, 8, 8);
        g.setColor(new Color(82, 63, 42)); g.setStroke(new BasicStroke(3)); g.drawRoundRect(19, 20, 63, 59, 6, 6);
        g.drawLine(20, 51, 81, 51);
        g.setColor(trim); g.fillRect(27, 24, 7, 51); g.fillRect(66, 24, 7, 51);
        g.fillRoundRect(43, 43, 14, 20, 3, 3); g.setColor(new Color(72, 59, 41));
        g.fillOval(48, 47, 5, 5); g.fillRect(49, 51, 3, 6);
        g.setColor(new Color(255, 255, 255, 160)); g.drawLine(39, 27, 60, 27);
    }

    // ===== VẼ NHÂN VẬT VÀ TÊN TRÊN Ô ĐÃ ĐƯỢC SERVER XÁC NHẬN =====
    private void paintPlayer(Graphics2D g, Player player, Color accent) {
        Position position = player.getPosition(); if (position == null) { return; }
        Rectangle tile = getTileBounds(position.getRow(), position.getCol());
        Graphics2D p = (Graphics2D) g.create(tile.x, tile.y, tile.width, tile.height);
        p.scale(tile.width / 100.0, tile.height / 100.0);
        boolean own = player.getUsername().equals(currentUsername);
        p.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 85)); p.fillOval(9, 21, 82, 77);
        p.setColor(own ? GameTheme.GOLD : accent.brighter()); p.setStroke(new BasicStroke(own ? 5 : 2)); p.drawOval(13, 24, 74, 69);
        p.setColor(new Color(39, 60, 61)); p.fillOval(25, 78, 50, 14);
        p.setColor(accent); p.fillRoundRect(27, 49, 47, 35, 12, 12);
        p.setColor(new Color(247, 206, 165)); p.fillOval(25, 22, 50, 46);
        p.setColor(new Color(92, 53, 37)); p.fillArc(21, 16, 58, 44, 0, 180);
        p.fillPolygon(new int[] {25, 39, 50, 72, 64, 45, 31}, new int[] {36, 20, 22, 35, 39, 30, 42}, 7);
        p.setColor(new Color(33, 58, 73)); p.fillOval(36, 41, 6, 8); p.fillOval(57, 41, 6, 8);
        p.setColor(Color.WHITE); p.fillOval(37, 42, 2, 3); p.fillOval(58, 42, 2, 3);
        p.setColor(new Color(169, 82, 71)); p.drawArc(43, 47, 14, 10, 190, 145);
        p.setColor(GameTheme.GOLD); p.setStroke(new BasicStroke(3)); p.drawOval(32, 20, 14, 11); p.drawOval(53, 20, 14, 11);
        p.dispose();
        g.setFont(new Font("SansSerif", Font.BOLD, tile.width < 40 ? 9 : 10));
        String name = fitName(g, player.getUsername(), tile.width - 4);
        int width = g.getFontMetrics().stringWidth(name), x = tile.x + (tile.width - width) / 2;
        int nameY = isStunned(player.getUsername()) ? tile.y + tile.height - 14 : tile.y + 1;
        g.setColor(own ? new Color(83, 61, 17) : accent.darker()); g.fillRoundRect(x - 3, nameY, width + 6, 13, 4, 4);
        g.setColor(Color.WHITE); g.drawString(name, x, nameY + 10);
    }

    private static String fitName(Graphics2D g, String text, int width) {
        if (g.getFontMetrics().stringWidth(text) <= width) { return text; }
        String shortName = text;
        while (!shortName.isEmpty() && g.getFontMetrics().stringWidth(shortName + "...") > width) {
            shortName = shortName.substring(0, shortName.offsetByCodePoints(shortName.length(), -1));
        }
        return shortName + "...";
    }
}
