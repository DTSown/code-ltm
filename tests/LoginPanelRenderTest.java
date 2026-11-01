import client.AuthFeedback;
import client.GameTheme;
import client.LoginPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

public class LoginPanelRenderTest {
    static void check(boolean ok, String message) { if (!ok) { throw new AssertionError(message); } }
    static Component named(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) { return child; }
            if (child instanceof Container) { Component result = named((Container) child, name); if (result != null) { return result; } }
        }
        return null;
    }
    static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) { if (child instanceof Container) { layout((Container) child); } }
    }
    static void textFits(Container parent) {
        for (Component child : parent.getComponents()) {
            if (!child.isVisible()) { continue; }
            if (child instanceof JLabel) {
                JLabel label = (JLabel) child;
                check(label.getFontMetrics(label.getFont()).stringWidth(label.getText()) <= label.getWidth(), "Label fits: " + label.getText());
                check(label.getFontMetrics(label.getFont()).getHeight() <= label.getHeight(), "Label height fits: " + label.getText());
            } else if (child instanceof JButton) {
                JButton button = (JButton) child;
                if (!button.getText().isEmpty()) {
                    check(button.getFontMetrics(button.getFont()).stringWidth(button.getText()) + 20 <= button.getWidth(), "Button text fits");
                }
            }
            if (child instanceof Container) { textFits((Container) child); }
        }
    }
    static BufferedImage render(JPanel panel, Path file, int width, int height) throws Exception {
        panel.setSize(width, height); layout(panel); textFits(panel);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); panel.paint(g); g.dispose();
        ImageIO.write(image, "png", file.toFile()); return image;
    }
    static void controls() {
        AtomicInteger logins = new AtomicInteger(), registrations = new AtomicInteger();
        LoginPanel panel = new LoginPanel(logins::incrementAndGet, registrations::incrementAndGet);
        JTextField user = (JTextField) named(panel, "auth-username");
        JPasswordField pass = (JPasswordField) named(panel, "auth-password");
        JPasswordField confirm = (JPasswordField) named(panel, "auth-confirm");
        JTextField host = (JTextField) named(panel, "auth-host"), port = (JTextField) named(panel, "auth-port");
        JButton submit = (JButton) named(panel, "auth-submit"), secondary = (JButton) named(panel, "auth-secondary");
        JToggleButton show = (JToggleButton) named(panel, "auth-show-password");
        check("5000".equals(panel.getPortText()) && !panel.isRegistrationMode(), "Initial port and login mode");
        submit.doClick(0); check(logins.get() == 0 && panel.validateInput() != null, "Empty fields do not submit");
        user.setText(" Alex "); pass.setText("secret"); host.setText("192.168.1.20"); port.setText("5000");
        char echo = pass.getEchoChar(); show.doClick(0); check(pass.getEchoChar() == 0 && show.isSelected(), "Show password");
        show.doClick(0); check(pass.getEchoChar() == echo && echo != 0, "Hide password again");
        pass.postActionEvent(); check(logins.get() == 1 && "Alex".equals(panel.getUsername())
                && "192.168.1.20".equals(panel.getServerIp()), "Enter submits and retains user-entered IP");
        port.setText("65536"); submit.doClick(0); check(logins.get() == 1, "Invalid port blocks request");
        port.setText("5000"); host.setText(""); submit.doClick(0); check(logins.get() == 1, "Empty IP blocks request");
        host.setText("192.168.1.20"); secondary.doClick(0);
        check(panel.isRegistrationMode() && confirm.isVisible() && !host.isVisible() && registrations.get() == 0, "Register opens form without sending");
        submit.doClick(0); check(registrations.get() == 0, "Empty confirmation blocked");
        confirm.setText("wrong"); confirm.postActionEvent(); check(registrations.get() == 0, "Mismatching confirmation blocked");
        confirm.setText("secret"); confirm.postActionEvent(); check(registrations.get() == 1, "Matching registration submits");
        panel.setBusy(true); submit.doClick(0); secondary.doClick(0); pass.postActionEvent();
        panel.setRegistrationMode(false);
        check(panel.isRegistrationMode() && panel.isBusy() && registrations.get() == 1 && !user.isEnabled()
                && !pass.isEnabled() && !show.isEnabled() && !submit.isEnabled() && !secondary.isEnabled(), "Busy blocks clicks, Enter and mode changes");
        panel.setBusy(false); secondary.doClick(0);
        check(!panel.isRegistrationMode() && "192.168.1.20".equals(panel.getServerIp()) && "5000".equals(panel.getPortText()), "Back preserves connection settings");
        panel.setConnectionStatus(true);
        check(((JLabel) named(panel, "auth-connection")).getText().contains("\u0110\u00e3 k\u1ebft n\u1ed1i"), "Connected display");
        panel.clearPasswords(); check(pass.getPassword().length == 0 && confirm.getPassword().length == 0 && pass.getEchoChar() != 0, "Passwords cleared and hidden");
        check(GameTheme.TEXT.equals(pass.getForeground()) && !GameTheme.TEXT.equals(pass.getBackground()), "High-contrast field colors");
        System.out.println("PASS: Login/Register validation, Enter, password toggle, busy guards, status and retained IP/Port");
    }
    static void screenshots(Path dir) throws Exception {
        for (int[] size : new int[][] {{1100, 700}, {900, 600}, {700, 450}, {1400, 900}}) {
            for (boolean register : new boolean[] {false, true}) {
                LoginPanel panel = new LoginPanel(() -> { }, () -> { });
                panel.setRegistrationMode(register);
                String mode = register ? "register" : "login";
                BufferedImage image = render(panel, dir.resolve(mode + "-" + size[0] + "x" + size[1] + ".png"), size[0], size[1]);
                Set<Integer> colors = new HashSet<>();
                for (int y = 0; y < size[1]; y += 8) { for (int x = 0; x < size[0]; x += 8) { colors.add(image.getRGB(x, y)); } }
                check(colors.size() > 200, "Nonblank scene, wood/form/sky art painted");
                Container form = (Container) named(panel, "auth-form");
                List<Rectangle> bounds = new ArrayList<>();
                for (Component child : form.getComponents()) {
                    if (!child.isVisible()) { continue; }
                    check(new Rectangle(0, 0, form.getWidth(), form.getHeight()).contains(child.getBounds()), "Form control stays inside panel");
                    for (Rectangle other : bounds) { check(!other.intersects(child.getBounds()), "No overlapping form controls"); }
                    bounds.add(child.getBounds());
                }
                JScrollPane scroll = (JScrollPane) named(panel, "auth-scroll");
                check(!scroll.getHorizontalScrollBar().isVisible(), "No horizontal scrolling on supported sizes");
                check(scroll.getVerticalScrollBar().isVisible() == (size[1] < 694), "Short windows scroll rather than clip controls");
                if (scroll.getVerticalScrollBar().isVisible()) {
                    scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum());
                    layout(panel);
                    Component button = named(panel, "auth-secondary");
                    Point p = SwingUtilities.convertPoint(button, 0, 0, scroll.getViewport());
                    check(new Rectangle(0, 0, scroll.getViewport().getWidth(), scroll.getViewport().getHeight())
                            .contains(new Rectangle(p.x, p.y, button.getWidth(), button.getHeight())), "Bottom action reachable by scrolling");
                    render(panel, dir.resolve(mode + "-bottom-" + size[0] + "x" + size[1] + ".png"), size[0], size[1]);
                }
            }
        }
        AtomicInteger closed = new AtomicInteger();
        JPanel dialog = AuthFeedback.createContent("\u0110\u0103ng nh\u1eadp th\u1ea5t b\u1ea1i",
                "T\u00e0i kho\u1ea3n ho\u1eb7c m\u1eadt kh\u1ea9u kh\u00f4ng \u0111\u00fang. Vui l\u00f2ng ki\u1ec3m tra th\u00f4ng tin v\u00e0 th\u1eed l\u1ea1i.", true, closed::incrementAndGet);
        render(dialog, dir.resolve("login-error-dialog.png"), 430, 280);
        ((JButton) named(dialog, "auth-dialog-close")).doClick(0); check(closed.get() == 1, "Feedback close action");
        System.out.println("PASS: Login/Register render at four sizes, text fit, no overlap, pixel checks, scroll reachability and styled feedback");
    }
    public static void main(String[] args) throws Exception {
        Path dir = Path.of("out", "test-screenshots"); Files.createDirectories(dir);
        SwingUtilities.invokeAndWait(() -> { try { controls(); screenshots(dir); } catch (Exception e) { throw new RuntimeException(e); } });
        System.out.println("ALL LOGIN RENDER TESTS PASSED");
    }
}
