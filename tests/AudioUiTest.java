package client;

import common.GameStateCodec;
import common.TrapEvent;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javax.sound.sampled.Clip;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import model.GameResult;
import model.GameState;
import model.Player;
import model.Position;
import model.Treasure;

public final class AudioUiTest {
    private static void check(boolean value, String message) {
        if (!value) { throw new AssertionError(message); }
    }

    private static void await(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(5); }
        check(condition.getAsBoolean(), message);
    }

    private static final class Backend implements Function<String, Clip> {
        final Map<String, AtomicInteger> starts = new ConcurrentHashMap<>();
        final AtomicInteger opens = new AtomicInteger(), closes = new AtomicInteger();
        @Override public Clip apply(String resource) {
            check(!SwingUtilities.isEventDispatchThread(), "Audio opens off EDT"); opens.incrementAndGet();
            return (Clip) Proxy.newProxyInstance(Clip.class.getClassLoader(), new Class<?>[] {Clip.class}, (proxy, method, args) -> {
                check(!SwingUtilities.isEventDispatchThread(), "Clip operations off EDT");
                switch (method.getName()) {
                    case "start": case "loop": starts.computeIfAbsent(resource, unused -> new AtomicInteger()).incrementAndGet(); return null;
                    case "stop": case "setFramePosition": return null;
                    case "close": closes.incrementAndGet(); return null;
                    default: throw new AssertionError("Unexpected audio operation: " + method.getName());
                }
            });
        }
        int count(SoundManager.Effect effect) { return starts.getOrDefault(effect.resource(), new AtomicInteger()).get(); }
    }

    private static Object field(SoundManager sound, String name) throws Exception {
        Field field = SoundManager.class.getDeclaredField(name); field.setAccessible(true); return field.get(sound);
    }

    private static void idle(SoundManager sound) throws Exception {
        Thread worker = (Thread) field(sound, "worker");
        if (worker != null) { await(() -> worker.getState() == Thread.State.WAITING, "Audio queue drained"); }
    }

    private static AbstractButton button(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) { return (AbstractButton) child; }
            if (child instanceof Container) {
                AbstractButton found = button((Container) child, name); if (found != null) { return found; }
            }
        }
        return null;
    }

    private static GameState state(String room) {
        GameState state = new GameState(); state.setRoomId(room); state.setStatus("RUNNING"); state.setRemainingTime(180);
        state.setPlayer1(new Player("Alex", 0, 0, Player.Status.PLAYING, new Position(0, 0), 15, 1));
        state.setPlayer2(new Player("Nina", 0, 0, Player.Status.PLAYING, new Position(6, 6), 5, 1));
        return state;
    }

    private static void clicks() throws Exception {
        Backend backend = new Backend(); SoundManager sound = new SoundManager(backend);
        AtomicInteger commands = new AtomicInteger();
        try {
            SwingUtilities.invokeAndWait(() -> {
                JPanel panel = new JPanel(); JButton action = new JButton("Action"); panel.add(action);
                action.addActionListener(event -> { commands.incrementAndGet(); action.setEnabled(false); });
                UiSounds.install(panel, sound); UiSounds.install(panel, sound);
                action.doClick(0); action.doClick(0);
                JButton added = new JButton("Added later"); panel.add(added); added.doClick(0);
                JButton movement = new JButton("Up"); movement.setName("MOVE_UP"); panel.add(movement); movement.doClick(0);
            });
            await(() -> backend.count(SoundManager.Effect.CLICK) == 2, "One click per activation, including dynamic button"); idle(sound);
            check(commands.get() == 1 && backend.opens.get() == 1, "Disabled button silent; cached click Clip reused");
            SwingUtilities.invokeAndWait(() -> {
                LoginPanel login = new LoginPanel(() -> { }, () -> { }, sound); UiSounds.install(login, sound);
                button(login, "auth-secondary").doClick(0);
                check(login.isRegistrationMode(), "Click preserves registration navigation");
                InvitationPanel invitation = new InvitationPanel("Nina", accepted -> commands.incrementAndGet());
                UiSounds.install(invitation, sound); button(invitation, "invitation-accept").doClick(0);
                LobbyPanel lobby = new LobbyPanel(name -> { }, () -> { }, () -> { }, () -> { }, () -> { }, sound);
                UiSounds.install(lobby, sound); button(lobby, "lobby-refresh").doClick(0);
                JPanel feedback = AuthFeedback.createContent("Message", "Test", false, () -> { });
                UiSounds.install(feedback, sound); button(feedback, "auth-dialog-close").doClick(0);
            });
            await(() -> backend.count(SoundManager.Effect.CLICK) == 6, "Login, invitation, lobby and feedback clicks");
            SwingUtilities.invokeAndWait(() -> {
                SoundToggle toggle = new SoundToggle(sound, "test-toggle"); JPanel root = new JPanel(); root.add(toggle);
                UiSounds.install(root, sound); toggle.doClick(0);
                check(!sound.isEnabled(), "Toggle mutes shared audio");
                JButton muted = new JButton("Muted"); root.add(muted); muted.doClick(0);
            });
            idle(sound); check(backend.count(SoundManager.Effect.CLICK) == 6, "Mute silences click and toggle-off");
            SwingUtilities.invokeAndWait(() -> {
                SoundToggle toggle = new SoundToggle(sound, "test-toggle"); UiSounds.install(toggle, sound); toggle.doClick(0);
            });
            await(() -> backend.count(SoundManager.Effect.CLICK) == 7, "Toggle-on clicks once, without duplicate binding");
        } finally { sound.close(); }
        Thread worker = (Thread) field(sound, "worker"); worker.join(5000);
        check(!worker.isAlive() && backend.opens.get() == backend.closes.get(), "UI audio resources released");
    }

    private static void gameEvents() throws Exception {
        Backend a = new Backend(), b = new Backend();
        SoundManager soundA = new SoundManager(a), soundB = new SoundManager(b);
        GamePanel[] panels = new GamePanel[2];
        try {
            SwingUtilities.invokeAndWait(() -> {
                panels[0] = new GamePanel(direction -> { }, () -> { }, soundA);
                panels[1] = new GamePanel(direction -> { }, () -> { }, soundB);
                panels[0].setCurrentUsername("Alex"); panels[1].setCurrentUsername("Nina");
                panels[0].setGameState(state("room-a")); panels[1].setGameState(state("room-a"));
                soundA.startMusic(SoundManager.Music.GAME); soundB.startMusic(SoundManager.Music.GAME);
                for (GamePanel panel : panels) { panel.showTreasureCollected("Alex", Treasure.NORMAL, 10, 25); }
            });
            await(() -> a.count(SoundManager.Effect.PICKUP) == 1, "Collector hears regular pickup"); idle(soundB);
            check(b.count(SoundManager.Effect.PICKUP) == 0, "Opponent does not hear reward pickup");
            SwingUtilities.invokeAndWait(() -> {
                for (GamePanel panel : panels) {
                    panel.showTreasureCollected("Alex", Treasure.GOLD, 30, 55);
                    panel.showTreasureCollected("Nina", Treasure.SILVER, 20, 25);
                    panel.setGameState(state("room-a")); panel.updateTime(179);
                }
                panels[0].getActionMap().get("MOVE_UP").actionPerformed(null);
                button(panels[0], "MOVE_UP").doClick(0);
            });
            await(() -> a.count(SoundManager.Effect.GOLD) == 1 && b.count(SoundManager.Effect.PICKUP) == 1, "Gold and silver collector-only cues");
            idle(soundA); idle(soundB);
            check(a.count(SoundManager.Effect.PICKUP) == 1 && b.count(SoundManager.Effect.GOLD) == 0
                    && a.count(SoundManager.Effect.CLICK) == 0, "Snapshots do not replay rewards; movement has no UI click");
            SwingUtilities.invokeAndWait(() -> {
                TrapEvent trap = new TrapEvent("room-a", 1, "Alex", 10, 5, 2000);
                for (GamePanel panel : panels) {
                    String before = GameStateCodec.encode(panel.getGameState());
                    panel.showTrapTriggered(trap); panel.showTrapTriggered(trap);
                    panel.showTrapTriggered(new TrapEvent("old-room", 99, "Alex", 10, 5, 2000));
                    panel.setGameState(state("room-a")); panel.updateTime(179);
                    check(before.equals(GameStateCodec.encode(panel.getGameState())), "Audio/trap presentation never changes server state");
                }
            });
            await(() -> a.count(SoundManager.Effect.TRAP) == 1 && b.count(SoundManager.Effect.TRAP) == 1, "One confirmed trap cue on each client");
            idle(soundA); idle(soundB);
            check(a.count(SoundManager.Effect.PICKUP) == 1 && a.count(SoundManager.Effect.GOLD) == 1
                    && b.count(SoundManager.Effect.PICKUP) == 1, "Trap never emits a reward cue");
            check(a.starts.get(SoundManager.MUSIC_RESOURCE).get() == 1 && b.starts.get(SoundManager.MUSIC_RESOURCE).get() == 1,
                    "Rewards and traps do not interrupt or restart BGM");
            SwingUtilities.invokeAndWait(() -> {
                for (GamePanel panel : panels) {
                    GameResult result = GameResult.fromState(panel.getGameState()); panel.finishGame(result); panel.finishGame(result);
                }
                soundA.startMusic(SoundManager.Music.LOBBY); soundB.startMusic(SoundManager.Music.LOBBY);
            });
            await(() -> a.count(SoundManager.Effect.WIN) == 1 && b.count(SoundManager.Effect.LOSE) == 1, "Win/lose once alongside lobby transition");
            SwingUtilities.invokeAndWait(() -> {
                for (GamePanel panel : panels) { panel.setGameState(state("room-b")); panel.close(); }
                check(soundA.isEnabled() && soundB.isEnabled(), "Panel cleanup does not close shared client manager");
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> { for (GamePanel panel : panels) { if (panel != null) { panel.close(); } } });
            soundA.close(); soundB.close();
        }
        for (SoundManager sound : new SoundManager[] {soundA, soundB}) {
            Thread worker = (Thread) field(sound, "worker"); if (worker != null) { worker.join(5000); check(!worker.isAlive(), "Client audio worker closes"); }
        }
        check(a.opens.get() == a.closes.get() && b.opens.get() == b.closes.get(), "Both clients release every Clip");
    }

    public static void main(String[] args) throws Exception {
        for (String name : new String[] {"lobby_bgm", "treasure_pickup", "treasure_gold", "trap_stun", "ui_click"}) {
            java.net.URL resource = SoundManager.class.getResource("/audio/" + name + ".wav");
            check(resource != null, "Resource available from source classpath: " + name);
            try (javax.sound.sampled.AudioInputStream audio = javax.sound.sampled.AudioSystem.getAudioInputStream(resource)) {
                check(audio.getFrameLength() > 0 && audio.getFormat().getSampleSizeInBits() == 16, "Source WAV decodes: " + name);
            }
        }
        clicks(); gameEvents();
        System.out.println("PASS: functional/dynamic/disabled/muted clicks, collector-only rewards, trap deduplication, BGM continuity, two-client isolation and cleanup");
    }
}
