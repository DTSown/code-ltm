import client.ClientConnection;
import client.GamePanel;
import client.MainFrame;
import client.ResultPanel;
import common.GameResultCodec;
import common.GameStateCodec;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.StringWriter;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/** Test-only agent: invokes real Swing controls; never changes a GameState. */
public class JarGuiAgent {
    private static ClientConnection observedConnection;
    private static final java.util.Map<String, String> initialStates = new java.util.HashMap<>();
    public static void premain(String argument, Instrumentation unused) {
        Thread thread = new Thread(() -> run(Integer.parseInt(argument)), "JarGuiTestAgent");
        thread.setDaemon(true); thread.start();
    }

    private static void run(int port) {
        try (Socket socket = new Socket("127.0.0.1", port);
                DataInputStream input = new DataInputStream(socket.getInputStream());
                DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
            output.writeUTF("TreasureGuiAgent"); output.flush();
            while (true) {
                String[] command = input.readUTF().split("\t", -1);
                Properties response = new Properties();
                AtomicReference<Throwable> error = new AtomicReference<>();
                if ("CLICK".equals(command[0]) || "CLOSE".equals(command[0])) {
                    // Clicking can open a modal dialog: acknowledge before entering its nested EDT loop.
                    SwingUtilities.invokeLater(() -> {
                        try { execute(command, response); }
                        catch (Exception e) { e.printStackTrace(); }
                    });
                } else {
                    SwingUtilities.invokeAndWait(() -> {
                        try { execute(command, response); }
                        catch (Throwable e) { error.set(e); }
                    });
                }
                if (error.get() != null) { response.setProperty("error", error.get().toString()); }
                StringWriter text = new StringWriter(); response.store(text, null); output.writeUTF(text.toString()); output.flush();
            }
        } catch (Exception ignored) { }
    }

    private static MainFrame frame() {
        for (Window window : Window.getWindows()) { if (window instanceof MainFrame && window.isDisplayable()) { return (MainFrame) window; } }
        return null;
    }

    private static Object field(MainFrame frame, String name) throws Exception {
        Field field = MainFrame.class.getDeclaredField(name); field.setAccessible(true); return field.get(frame);
    }

    private static Component named(Container container, String name) {
        for (Component child : container.getComponents()) {
            if (name.equals(child.getName())) { return child; }
            if (child instanceof Container) { Component result = named((Container) child, name); if (result != null) { return result; } }
        }
        return null;
    }

    private static Component control(String name) {
        for (Window window : Window.getWindows()) {
            if (window.isVisible() && window instanceof JDialog) { Component result = named(window, name); if (result != null) { return result; } }
        }
        MainFrame frame = frame(); return frame == null ? null : named(frame, name);
    }

    private static void execute(String[] command, Properties result) throws Exception {
        MainFrame frame = frame(); result.setProperty("ready", Boolean.toString(frame != null));
        if (frame == null) { return; }
        observeInitialState(frame);
        switch (command[0]) {
            case "SET": ((JTextField) control(command[1])).setText(command[2]); break;
            case "CLICK": {
                AbstractButton button = (AbstractButton) control(command[1]);
                if (button == null || !button.isEnabled()) { throw new IllegalStateException("Disabled/missing control " + command[1]); }
                button.doClick(0); break;
            }
            case "KEY": {
                GamePanel game = (GamePanel) field(frame, "gamePanel");
                int key = java.awt.event.KeyEvent.getExtendedKeyCodeForChar(command[1].charAt(0));
                Object action = game.getInputMap(GamePanel.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(key, 0));
                game.getActionMap().get(action).actionPerformed(null); break;
            }
            case "CONFIRM": {
                for (Window window : Window.getWindows()) {
                    if (window instanceof JDialog && window.isVisible()) {
                        JOptionPane pane = optionPane(window);
                        if (pane != null) { pane.setValue(JOptionPane.YES_OPTION); }
                    }
                } break;
            }
            case "SCREENSHOT": {
                java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(frame.getWidth(), frame.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = image.createGraphics(); frame.paint(g); g.dispose();
                javax.imageio.ImageIO.write(image, "png", Path.of(command[1]).toFile()); break;
            }
            case "CLOSE": frame.dispatchEvent(new java.awt.event.WindowEvent(frame, java.awt.event.WindowEvent.WINDOW_CLOSING)); break;
            case "RESIZE": frame.setSize(Integer.parseInt(command[1]), Integer.parseInt(command[2])); frame.validate(); break;
            case "SNAPSHOT": snapshot(frame, result); break;
            default: throw new IllegalArgumentException("Unknown test command");
        }
    }

    private static JOptionPane optionPane(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JOptionPane) { return (JOptionPane) child; }
            if (child instanceof Container) { JOptionPane result = optionPane((Container) child); if (result != null) { return result; } }
        }
        return null;
    }

    private static void snapshot(MainFrame frame, Properties result) throws Exception {
        result.setProperty("title", frame.getTitle());
        result.setProperty("user", String.valueOf(field(frame, "currentUsername")));
        result.setProperty("status", String.valueOf(field(frame, "currentStatus")));
        result.setProperty("connected", Boolean.toString(((ClientConnection) field(frame, "connection")).isConnected()));
        result.setProperty("busy", Boolean.toString(((client.LoginPanel) field(frame, "loginPanel")).isBusy()));
        for (String panel : new String[] {"loginPanel", "lobbyPanel", "gamePanel", "resultPanel", "rankingPanel", "historyPanel"}) {
            if (((Component) field(frame, panel)).isVisible()) { result.setProperty("screen", panel); }
        }
        GamePanel game = (GamePanel) field(frame, "gamePanel");
        result.setProperty("sound-enabled", Boolean.toString(game.isSoundEnabled()));
        client.SoundManager sound = (client.SoundManager) field(frame, "sound");
        result.setProperty("music", String.valueOf(sound.getRequestedMusic()));
        Field gameSound = GamePanel.class.getDeclaredField("sound"); gameSound.setAccessible(true);
        result.setProperty("sound-shared", Boolean.toString(gameSound.get(game) == sound));
        if (game.getGameState() != null) {
            result.setProperty("state", GameStateCodec.encode(game.getGameState()));
            String initial = initialStates.get(game.getGameState().getRoomId());
            if (initial != null) { result.setProperty("initial-state", initial); }
        }
        ResultPanel outcome = (ResultPanel) field(frame, "resultPanel");
        if (outcome.getResult() != null) { result.setProperty("result", GameResultCodec.encode(outcome.getResult())); }
        for (String name : new String[] {"server-time", "profile-rank", "profile-matches", "profile-treasure", "lobby-online-count", "auth-feedback", "treasure-notice"}) {
            Component c = control(name);
            if (c instanceof JLabel) { result.setProperty(name, ((JLabel) c).getText()); }
        }
        for (String name : new String[] {"auth-dialog-message", "invitation-message"}) {
            Component c = control(name); if (c instanceof JTextArea && SwingUtilities.getWindowAncestor(c).isVisible()) { result.setProperty(name, ((JTextArea) c).getText()); }
        }
        for (String name : new String[] {"MOVE_UP", "exit-game", "play-again", "opponent-Alex", "opponent-Nina"}) {
            Component c = control(name); if (c != null) { result.setProperty(name, Boolean.toString(c.isEnabled())); }
        }
        for (String name : new String[] {"ranking-table", "history-table"}) {
            JTable table = (JTable) control(name); StringBuilder rows = new StringBuilder();
            for (int row = 0; row < table.getRowCount(); row++) {
                for (int col = 0; col < table.getModel().getColumnCount(); col++) {
                    if (col > 0) { rows.append('\t'); } rows.append(table.getModel().getValueAt(row, col));
                }
                rows.append('\n');
            }
            result.setProperty(name, rows.toString()); result.setProperty(name + "-rows", Integer.toString(table.getRowCount()));
        }
    }

    private static void observeInitialState(MainFrame frame) throws Exception {
        ClientConnection active = (ClientConnection) field(frame, "connection");
        if (active == observedConnection) { return; }
        Field listenerField = ClientConnection.class.getDeclaredField("messageListener"); listenerField.setAccessible(true);
        client.MessageListener original = (client.MessageListener) listenerField.get(active);
        active.setMessageListener(message -> {
            if (common.Protocol.GAME_STATE.equals(message.getType())) {
                String roomId = GameStateCodec.decode(message.getData()).getRoomId();
                initialStates.putIfAbsent(roomId, message.getData());
            }
            original.onMessage(message);
        });
        observedConnection = active;
    }
}
