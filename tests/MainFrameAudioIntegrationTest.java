package client;

import common.GameResultCodec;
import common.GameStateCodec;
import common.Message;
import common.Protocol;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javax.sound.sampled.Clip;
import javax.swing.AbstractButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import model.GameResult;
import model.GameState;
import model.Player;
import model.Position;

/** Exercises the actual packaged MainFrame without showing windows or playing audio. */
public final class MainFrameAudioIntegrationTest {
    private static void check(boolean value, String message) {
        if (!value) { throw new AssertionError(message); }
    }

    private static void await(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(5); }
        check(condition.getAsBoolean(), message);
    }

    private static final class Line {
        final String resource;
        final AtomicInteger starts = new AtomicInteger();
        volatile boolean running, closed;
        Line(String resource) { this.resource = resource; }
        Clip clip() {
            return (Clip) Proxy.newProxyInstance(Clip.class.getClassLoader(), new Class<?>[] {Clip.class}, (proxy, method, args) -> {
                check(!SwingUtilities.isEventDispatchThread(), "Audio operations stay off the EDT");
                switch (method.getName()) {
                    case "loop": case "start": running = true; starts.incrementAndGet(); return null;
                    case "stop": running = false; return null;
                    case "close": check(!closed, "Clip is released once"); closed = true; running = false; return null;
                    case "setFramePosition": return null;
                    default: throw new AssertionError("Unexpected Clip operation: " + method.getName());
                }
            });
        }
    }

    private static final class Backend implements Function<String, Clip> {
        final List<Line> lines = new CopyOnWriteArrayList<>();
        @Override public Clip apply(String resource) {
            check(!SwingUtilities.isEventDispatchThread(), "Audio opens off the EDT");
            Line line = new Line(resource); lines.add(line); return line.clip();
        }
        int starts(String resource) { return lines.stream().filter(line -> line.resource.equals(resource)).mapToInt(line -> line.starts.get()).sum(); }
        long liveMusic() {
            return lines.stream().filter(line -> line.running && !line.closed
                    && (line.resource.equals(SoundManager.Music.LOBBY.resource())
                    || line.resource.equals(SoundManager.Music.GAME.resource()))).count();
        }
    }

    private static final class Connection extends ClientConnection {
        volatile boolean connected = true;
        @Override public boolean isConnected() { return connected; }
        @Override public synchronized void sendMessage(Message message) { }
        @Override public void disconnect() { connected = false; }
    }

    private static Object field(Object owner, Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }

    private static AbstractButton control(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) { return (AbstractButton) child; }
            if (child instanceof Container) {
                AbstractButton found = control((Container) child, name); if (found != null) { return found; }
            }
        }
        return null;
    }

    private static void click(MainFrame frame, String name) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AbstractButton button = control(frame, name);
            check(button != null && button.isEnabled(), "Control exists and is enabled: " + name);
            button.doClick(0);
        });
    }

    private static void message(MainFrame frame, String type, String data) throws Exception {
        frame.onMessage(new Message(type, data));
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static GameState state() {
        GameState state = new GameState(); state.setRoomId("audio-room"); state.setStatus("RUNNING"); state.setRemainingTime(180);
        state.setPlayer1(new Player("Alex", 0, 0, Player.Status.PLAYING, new Position(0, 0), 15, 1));
        state.setPlayer2(new Player("Nina", 0, 0, Player.Status.PLAYING, new Position(6, 6), 5, 1));
        return state;
    }

    public static void main(String[] args) throws Exception {
        check(MainFrame.class.getProtectionDomain().getCodeSource().getLocation().getPath().endsWith("TreasureRushClient.jar"),
                "MainFrame comes from the built client JAR");
        Backend backend = new Backend(); SoundManager sound = new SoundManager(backend);
        SoundManager otherClient = new SoundManager(unused -> { throw new AssertionError("Other client must stay silent"); });
        MainFrame[] holder = new MainFrame[1];
        String lobby = SoundManager.Music.LOBBY.resource(), game = SoundManager.Music.GAME.resource();
        try {
            SwingUtilities.invokeAndWait(() -> {
                holder[0] = new MainFrame(new Connection(), sound);
                holder[0].setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                holder[0].addNotify(); // Register shared mute controls without showing a window.
                holder[0].showLogin();
            });
            MainFrame frame = holder[0];
            check(sound.getRequestedMusic() == null && backend.lines.isEmpty(), "Login starts silent");
            check(field(field(frame, MainFrame.class, "gamePanel"), GamePanel.class, "sound") == sound,
                    "GamePanel uses the same SoundManager as MainFrame");
            message(frame, Protocol.LOGIN_SUCCESS, "Alex");
            message(frame, Protocol.ONLINE_LIST, "Alex,0,0,FREE;Nina,0,0,FREE");
            await(() -> backend.starts(lobby) == 1 && backend.liveMusic() == 1, "Successful login starts lobby BGM");
            click(frame, "lobby-ranking");
            message(frame, Protocol.RANKING, "Alex,0,0,0,0;Nina,0,0,0,0");
            click(frame, "ranking-lobby"); click(frame, "lobby-history");
            message(frame, Protocol.HISTORY, ""); click(frame, "history-lobby");
            check(backend.starts(lobby) == 1, "Ranking/history/return do not restart lobby music");
            await(() -> backend.starts(SoundManager.Effect.CLICK.resource()) >= 4, "Shared UI click effects are connected");

            click(frame, "lobby-sound-toggle");
            await(() -> backend.liveMusic() == 0, "Lobby speaker mutes music");
            SwingUtilities.invokeAndWait(() -> {
                check(!control(frame, "login-sound-toggle").isSelected()
                        && !control(frame, "sound-toggle").isSelected(), "Login/game speakers share the lobby mute state");
            });
            check(otherClient.isEnabled(), "Muting one client leaves other clients enabled");
            message(frame, Protocol.GAME_START, "Alex;Nina");
            message(frame, Protocol.GAME_STATE, GameStateCodec.encode(state()));
            check(sound.getRequestedMusic() == SoundManager.Music.GAME && backend.liveMusic() == 0,
                    "Starting a game while muted remembers game BGM without playing it");
            click(frame, "sound-toggle");
            await(() -> backend.starts(game) == 1 && backend.liveMusic() == 1, "Unmute resumes game BGM");
            for (int i = 0; i < 8; i++) { message(frame, Protocol.GAME_STATE, GameStateCodec.encode(state())); }
            check(backend.starts(game) == 1, "Repeated game updates do not restart music");

            GameResult result = new GameResult("audio-room", "Alex", "Nina", GameResult.PLAYER1, 15, 5, 1, 1, 3, 0);
            message(frame, Protocol.GAME_OVER, GameResultCodec.encode(result));
            await(() -> backend.starts(lobby) == 2 && backend.liveMusic() == 1
                    && backend.starts(SoundManager.Effect.WIN.resource()) == 1, "Game result restores lobby BGM and plays win once");
            message(frame, Protocol.GAME_OVER, GameResultCodec.encode(result));
            click(frame, "return-lobby");
            check(backend.starts(lobby) == 2 && backend.starts(SoundManager.Effect.WIN.resource()) == 1,
                    "Duplicate results and return to lobby do not replay BGM/win");
            click(frame, "lobby-logout");
            await(() -> sound.getRequestedMusic() == null && backend.liveMusic() == 0, "Logout stops music and returns to silent login");
            SwingUtilities.invokeAndWait(frame::dispose);
            await(() -> backend.lines.stream().allMatch(line -> line.closed), "Closing client releases every audio clip");
            Thread worker = (Thread) field(sound, SoundManager.class, "worker"); worker.join(5000);
            check(!worker.isAlive(), "Closing client terminates its audio worker");
        } finally {
            SwingUtilities.invokeAndWait(() -> { if (holder[0] != null) { holder[0].dispose(); } });
            sound.close(); otherClient.close();
        }
        System.out.println("PASS: packaged MainFrame login/lobby BGM, ranking/history continuity, shared mute, game/result transitions, logout and disposal");
    }
}
