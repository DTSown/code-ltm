import java.io.BufferedInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.jar.JarFile;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.SwingUtilities;

/** Runs with only test classes on the application classpath; audio comes from the client JAR. */
public final class AudioResourceTest {
    private static final List<String> NAMES = List.of("adventure", "pickup", "gold", "trap", "win", "lose",
            "lobby_bgm", "treasure_pickup", "treasure_gold", "trap_stun", "ui_click");

    private static void check(boolean value, String message) {
        if (!value) { throw new AssertionError(message); }
    }

    private static void await(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(10); }
        check(condition.getAsBoolean(), message);
    }

    private static void resources(URLClassLoader loader, Path server) throws Exception {
        check(loader.loadClass("client.SoundManager").getField("MUSIC_RESOURCE").get(null).equals("/audio/adventure.wav"),
                "Music resource contract");
        Class<?> effectType = loader.loadClass("client.SoundManager$Effect");
        Set<String> effectResources = new HashSet<>();
        for (Object effect : effectType.getEnumConstants()) {
            effectResources.add((String) effectType.getMethod("resource").invoke(effect));
        }
        check(effectResources.equals(Set.of("/audio/treasure_pickup.wav", "/audio/treasure_gold.wav",
                "/audio/trap_stun.wav", "/audio/ui_click.wav", "/audio/win.wav", "/audio/lose.wav")),
                "Effect resource contract");
        Set<String> digests = new HashSet<>();
        for (String name : NAMES) {
            URL resource = loader.getResource("audio/" + name + ".wav");
            check(resource != null && resource.getProtocol().equals("jar"), "Resource must come from isolated JAR: " + name);
            try (AudioInputStream input = AudioSystem.getAudioInputStream(new BufferedInputStream(resource.openStream()))) {
                AudioFormat format = input.getFormat();
                check(format.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)
                        && format.getSampleSizeInBits() == 16 && format.getChannels() == 1
                        && format.getSampleRate() == 22050 && !format.isBigEndian(), "Portable PCM: " + name);
                byte[] pcm = input.readAllBytes();
                check(pcm.length == input.getFrameLength() * format.getFrameSize(), "Complete frames: " + name);
                double seconds = input.getFrameLength() / format.getFrameRate();
                check(name.equals("adventure") ? seconds >= 20 && seconds <= 30
                        : name.equals("lobby_bgm") ? seconds >= 12 && seconds <= 30
                        : name.equals("ui_click") ? seconds >= .1 && seconds <= .25
                        : name.equals("treasure_pickup") ? seconds >= .3 && seconds <= .8
                        : name.equals("trap_stun") ? seconds >= .8 && seconds <= 1.5 : seconds >= .2 && seconds <= 3,
                        "Expected duration: " + name);
                long energy = 0; int peak = 0, changes = 0, last = sample(pcm, 0);
                for (int i = 0; i < pcm.length; i += 2) {
                    int value = sample(pcm, i);
                    peak = Math.max(peak, Math.abs(value)); energy += (long) value * value;
                    if ((last < 0) != (value < 0)) { changes++; }
                    last = value;
                }
                check(peak > 1000 && peak < 32000 && Math.sqrt(energy / (pcm.length / 2.0)) > 100,
                        "Audible and unclipped: " + name);
                check(changes > 50, "Oscillating audio: " + name);
                if (name.equals("adventure") || name.equals("lobby_bgm")) {
                    check(Math.abs(sample(pcm, 0) - sample(pcm, pcm.length - 2)) < 800, "Continuous loop boundary");
                    check(!Arrays.equals(Arrays.copyOfRange(pcm, 0, 44100), Arrays.copyOfRange(pcm, 44100, 88200)),
                            "Music develops over time");
                }
                check(digests.add(java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pcm))),
                        "Distinct music/effect: " + name);
            }
        }
        try (JarFile jar = new JarFile(server.toFile())) {
            check(jar.stream().noneMatch(entry -> entry.getName().startsWith("audio/")
                    || entry.getName().startsWith("client/")), "Server contains no client audio or classes");
        }
    }

    private static int sample(byte[] pcm, int offset) {
        return (short) ((pcm[offset] & 255) | ((pcm[offset + 1] & 255) << 8));
    }

    private static final class FakeClip {
        final String resource;
        final AtomicBoolean running = new AtomicBoolean();
        final AtomicInteger loops = new AtomicInteger(), starts = new AtomicInteger(), closes = new AtomicInteger();
        final AtomicBoolean onEdt;
        final Clip clip;

        FakeClip(String resource, AtomicBoolean onEdt) {
            this.resource = resource; this.onEdt = onEdt;
            clip = (Clip) Proxy.newProxyInstance(Clip.class.getClassLoader(), new Class<?>[] {Clip.class}, (proxy, method, args) -> {
                if (SwingUtilities.isEventDispatchThread()) { onEdt.set(true); }
                switch (method.getName()) {
                    case "loop":
                        check((Integer) args[0] == Clip.LOOP_CONTINUOUSLY, "Infinite background loop");
                        loops.incrementAndGet(); running.set(true); return null;
                    case "start": starts.incrementAndGet(); running.set(true); return null;
                    case "setFramePosition": check((Integer) args[0] == 0, "Cached effect rewound before playback"); return null;
                    case "stop": running.set(false); return null;
                    case "close": closes.incrementAndGet(); running.set(false); return null;
                    case "isRunning": case "isActive": return running.get();
                    case "isOpen": return closes.get() == 0;
                    case "toString": return resource;
                    default: throw new AssertionError("Unexpected clip operation: " + method.getName());
                }
            });
        }
    }

    private static final class Backend implements Function<String, Clip> {
        final List<FakeClip> clips = new CopyOnWriteArrayList<>();
        final AtomicBoolean onEdt = new AtomicBoolean();
        @Override public Clip apply(String resource) {
            if (SwingUtilities.isEventDispatchThread()) { onEdt.set(true); }
            if (resource.equals("/audio/adventure.wav") || resource.equals("/audio/lobby_bgm.wav")) {
                check(clips.stream().noneMatch(clip -> clip.loops.get() > 0 && clip.running.get()),
                        "Previous BGM stopped before opening the next");
            }
            FakeClip fake = new FakeClip(resource, onEdt); clips.add(fake); return fake.clip;
        }
        int live() { return (int) clips.stream().filter(clip -> clip.closes.get() == 0).count(); }
    }

    private static final class Manager implements AutoCloseable {
        final Object object;
        final Class<?> type;
        final Class<?> effectType;

        Manager(URLClassLoader loader, Function<String, Clip> opener) throws Exception {
            type = loader.loadClass("client.SoundManager");
            effectType = loader.loadClass("client.SoundManager$Effect");
            Constructor<?> constructor = type.getDeclaredConstructor(Function.class); constructor.setAccessible(true);
            object = constructor.newInstance(opener);
        }
        void call(String method) throws Exception { type.getMethod(method).invoke(object); }
        void enable(boolean enabled) throws Exception { type.getMethod("setEnabled", boolean.class).invoke(object, enabled); }
        boolean enabled() throws Exception { return (Boolean) type.getMethod("isEnabled").invoke(object); }
        void play(String name) throws Exception {
            Object effect = Arrays.stream(effectType.getEnumConstants()).filter(value -> value.toString().equals(name)).findFirst().orElseThrow();
            type.getMethod("play", effectType).invoke(object, effect);
        }
        void music(String name) throws Exception {
            Class<?> musicType = type.getClassLoader().loadClass("client.SoundManager$Music");
            Object music = Arrays.stream(musicType.getEnumConstants()).filter(value -> value.toString().equals(name)).findFirst().orElseThrow();
            type.getMethod("startMusic", musicType).invoke(object, music);
        }
        Object field(String name) throws Exception {
            Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object);
        }
        void waitForClose() throws Exception {
            Thread worker = (Thread) field("worker");
            if (worker != null) { worker.join(5000); check(!worker.isAlive(), "Worker terminates after close"); }
        }
        @Override public void close() throws Exception { call("close"); }
    }

    private static void lifecycle(URLClassLoader loader) throws Exception {
        Backend backend = new Backend();
        try (Manager manager = new Manager(loader, backend); Manager other = new Manager(loader, new Backend())) {
            check(manager.field("worker") == null, "Construction is lazy");
            SwingUtilities.invokeAndWait(() -> {
                try { for (int i = 0; i < 100; i++) { manager.call("startMusic"); } }
                catch (Exception failure) { throw new RuntimeException(failure); }
            });
            await(() -> backend.clips.size() == 1 && backend.clips.get(0).loops.get() == 1, "Music starts once");
            Thread worker = (Thread) manager.field("worker");
            check(worker.isDaemon(), "Audio worker cannot retain process");
            manager.play("PICKUP");
            await(() -> backend.clips.size() == 2 && backend.clips.get(1).starts.get() == 1, "Effect starts");
            manager.call("stopMusic");
            await(() -> backend.clips.get(0).closes.get() == 1, "stopMusic releases loop");
            check(backend.clips.get(1).running.get(), "stopMusic preserves effects");
            backend.clips.get(1).running.set(false);
            manager.play("PICKUP");
            await(() -> backend.clips.get(1).starts.get() == 2, "Completed effect replayed from cached Clip");
            check(backend.clips.size() == 2 && backend.live() == 1, "Completed effect cache stays bounded");
            manager.call("stopAll");
            await(() -> backend.live() == 0, "stopAll releases cached effects");
            manager.call("startMusic");
            await(() -> backend.live() == 1, "Loop restarts after stop");
            manager.enable(false);
            check(!manager.enabled() && other.enabled(), "Toggle belongs to instance");
            await(() -> backend.live() == 0, "Mute releases lines");
            int before = backend.clips.size();
            manager.play("GOLD"); manager.enable(true);
            await(() -> backend.clips.size() == before + 1 && backend.live() == 1, "Unmute resumes requested loop");
            manager.call("stopAll");
            await(() -> backend.live() == 0, "stopAll releases lines");
            manager.enable(false); manager.enable(true); manager.play("WIN");
            await(() -> backend.clips.size() == before + 2 && backend.live() == 1, "stopAll forgets music intent");
            check(backend.clips.get(backend.clips.size() - 1).resource.equals("/audio/win.wav"), "Only result effect plays");
            manager.close(); manager.close(); manager.waitForClose();
            manager.call("startMusic"); manager.play("LOSE"); manager.enable(true);
            check(!manager.enabled() && backend.live() == 0 && !worker.isAlive(), "Close is terminal and idempotent");
            check(!backend.onEdt.get(), "No clip operations on EDT");
            check(backend.clips.stream().allMatch(clip -> clip.closes.get() == 1), "Every line closed exactly once");
        }
    }

    private static void effects(URLClassLoader loader) throws Exception {
        Backend backend = new Backend();
        Manager manager = new Manager(loader, backend);
        try {
            for (String effect : List.of("PICKUP", "GOLD", "TRAP", "WIN", "LOSE", "CLICK")) { manager.play(effect); }
            await(() -> backend.clips.size() == 6 && backend.clips.stream().allMatch(clip -> clip.starts.get() == 1),
                    "All six effects can overlap");
            manager.play("GOLD");
            await(() -> backend.clips.get(1).starts.get() == 2, "Repeated effect starts");
            check(backend.clips.size() == 6 && backend.live() == 6 && backend.clips.get(1).closes.get() == 0,
                    "Repeated effects reuse one bounded line per type");
            manager.call("stopAll");
            await(() -> backend.live() == 0, "stopAll releases every effect");
        } finally { manager.close(); manager.waitForClose(); }
        check(backend.clips.stream().allMatch(clip -> clip.closes.get() == 1), "No effect line leaks");
    }

    private static void blockedDevice(URLClassLoader loader) throws Exception {
        CountDownLatch entered = new CountDownLatch(1), unblock = new CountDownLatch(1);
        Backend backend = new Backend();
        Manager manager = new Manager(loader, resource -> {
            entered.countDown();
            try { check(unblock.await(5, TimeUnit.SECONDS), "Release simulated device"); }
            catch (InterruptedException interrupted) { throw new RuntimeException(interrupted); }
            return backend.apply(resource);
        });
        try {
            manager.call("startMusic"); check(entered.await(5, TimeUnit.SECONDS), "Device opening");
            SwingUtilities.invokeAndWait(() -> {
                try {
                    for (int i = 0; i < 10000; i++) { manager.play("GOLD"); manager.call("startMusic"); }
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
            synchronized (manager.field("lock")) {
                check(((ArrayDeque<?>) manager.field("pending")).size() <= 16, "Queue stays bounded during device stall");
            }
            SwingUtilities.invokeAndWait(() -> {
                try { manager.enable(false); manager.call("stopAll"); manager.close(); }
                catch (Exception failure) { throw new RuntimeException(failure); }
            });
        } finally { unblock.countDown(); manager.close(); manager.waitForClose(); }
        check(backend.clips.size() == 1 && backend.live() == 0, "In-flight open closed after disposal");
        check(backend.clips.get(0).loops.get() == 0 && backend.clips.get(0).starts.get() == 0, "Stale audio never starts");
    }

    private static void unavailable(URLClassLoader loader) throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        Manager manager = new Manager(loader, resource -> {
            attempts.incrementAndGet(); throw new IllegalArgumentException("No audio device");
        });
        try {
            manager.call("startMusic");
            for (String effect : List.of("PICKUP", "GOLD", "TRAP", "WIN", "LOSE", "CLICK")) { manager.play(effect); }
            await(() -> attempts.get() == 7, "Missing device handled for each resource");
            for (int i = 0; i < 100; i++) { manager.play("GOLD"); manager.call("startMusic"); }
            manager.call("stopAll");
        } finally { manager.close(); manager.waitForClose(); }
        check(attempts.get() == 7, "Unavailable audio does not retry endlessly");
    }

    private static void musicTransitions(URLClassLoader loader) throws Exception {
        Backend backend = new Backend();
        Manager manager = new Manager(loader, backend);
        try {
            manager.music("LOBBY");
            await(() -> backend.clips.size() == 1 && backend.clips.get(0).loops.get() == 1, "Lobby loops");
            check(backend.clips.get(0).resource.equals("/audio/lobby_bgm.wav"), "Correct lobby resource");
            for (int i = 0; i < 100; i++) { manager.music("LOBBY"); }
            manager.play("CLICK");
            await(() -> backend.clips.size() == 2 && backend.clips.get(1).starts.get() == 1, "Click alongside BGM");
            check(backend.clips.get(0).loops.get() == 1 && backend.clips.get(0).running.get(), "Lobby updates and clicks preserve loop");
            for (int i = 0; i < 8; i++) {
                String scene = i % 2 == 0 ? "GAME" : "LOBBY";
                int before = backend.clips.size(); manager.music(scene);
                await(() -> backend.clips.size() == before + 1 && backend.clips.get(before).loops.get() == 1, "Repeated scene transition");
                check(backend.live() == 2, "Exactly one BGM plus cached click");
            }
            manager.enable(false); await(() -> backend.live() == 0, "Mute stops BGM and SFX");
            manager.music("GAME"); manager.music("LOBBY"); manager.music("GAME");
            int before = backend.clips.size(); manager.enable(true);
            await(() -> backend.clips.size() == before + 1 && backend.clips.get(before).loops.get() == 1, "Unmute resumes latest scene");
            check(backend.clips.get(before).resource.equals("/audio/adventure.wav"), "No stale lobby music after mute");
            manager.call("stopMusic"); await(() -> backend.live() == 0, "Login stops music");
            manager.enable(false); manager.enable(true);
            check(manager.type.getMethod("getRequestedMusic").invoke(manager.object) == null, "Login has no remembered music intent");
        } finally { manager.close(); manager.waitForClose(); }
        check(!backend.onEdt.get() && backend.clips.stream().allMatch(clip -> clip.closes.get() == 1), "Transition resources closed off EDT");
    }

    public static void main(String[] args) throws Exception {
        Path dist = args.length == 0 ? Path.of("dist") : Path.of(args[0]);
        URL client = dist.resolve("TreasureRushClient.jar").toAbsolutePath().toUri().toURL();
        // A platform parent cannot see test classes, the workspace, or resources/.
        try (URLClassLoader loader = new URLClassLoader(new URL[] {client}, ClassLoader.getPlatformClassLoader())) {
            check(loader.loadClass("client.SoundManager").getClassLoader() == loader, "Isolated SoundManager");
            check(loader.getResource("AudioResourceTest.class") == null, "No application-classpath fallback");
            resources(loader, dist.resolve("TreasureRushServer.jar"));
            lifecycle(loader); effects(loader); musicTransitions(loader); blockedDevice(loader); unavailable(loader);
        }
        try (URLClassLoader missing = new URLClassLoader(new URL[] {client}, ClassLoader.getPlatformClassLoader()) {
            @Override public URL getResource(String name) { return name.startsWith("audio/") ? null : super.getResource(name); }
        }) {
            Class<?> type = missing.loadClass("client.SoundManager");
            Object manager = type.getConstructor().newInstance();
            type.getMethod("startMusic").invoke(manager);
            Field workerField = type.getDeclaredField("worker"); workerField.setAccessible(true);
            Thread worker = (Thread) workerField.get(manager);
            await(() -> worker.getState() == Thread.State.WAITING, "Missing resource is graceful");
            ((AutoCloseable) manager).close(); worker.join(5000);
            check(!worker.isAlive(), "Missing-resource worker closes");
        }
        System.out.println("PASS: isolated JAR PCM decoding, client-only resources, bounded worker, EDT controls, loop/toggle/stop/close, unavailable audio");
    }
}
