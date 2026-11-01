package client;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

/** Instance-local audio. Public controls never perform audio I/O or wait for the worker. */
public final class SoundManager implements AutoCloseable {
    public static final String MUSIC_RESOURCE = "/audio/adventure.wav";

    public enum Music {
        LOBBY("/audio/lobby_bgm.wav"), GAME(MUSIC_RESOURCE);
        private final String resource;
        Music(String resource) { this.resource = resource; }
        public String resource() { return resource; }
    }

    public enum Effect {
        PICKUP("treasure_pickup"), GOLD("treasure_gold"), TRAP("trap_stun"),
        CLICK("ui_click"), WIN("win"), LOSE("lose");
        private final String name;
        Effect(String name) { this.name = name; }
        public String resource() { return "/audio/" + name + ".wav"; }
    }

    private static final int MAX_PENDING = 16;
    private final Object lock = new Object();
    private final ArrayDeque<Effect> pending = new ArrayDeque<>();
    private final Function<String, Clip> opener;
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private boolean enabled = true, closed, dirty;
    private Music requestedMusic;
    private long musicGeneration, allGeneration;
    private Thread worker;

    public SoundManager() { this(SoundManager::openClip); }

    // Injection keeps lifecycle tests independent of the host's audio hardware.
    SoundManager(Function<String, Clip> opener) { this.opener = java.util.Objects.requireNonNull(opener); }

    public void startMusic() {
        startMusic(Music.GAME);
    }

    public void startMusic(Music music) {
        java.util.Objects.requireNonNull(music);
        synchronized (lock) {
            if (closed || requestedMusic == music) { return; }
            requestedMusic = music; musicGeneration++;
            signal(enabled);
        }
    }

    public void stopMusic() {
        synchronized (lock) {
            if (closed) { return; }
            requestedMusic = null; musicGeneration++;
            signal(false);
        }
    }

    /** Stops and releases all lines; a later startMusic/play may start new audio. */
    public void stopAll() {
        synchronized (lock) {
            if (closed) { return; }
            requestedMusic = null; pending.clear(); allGeneration++; musicGeneration++;
            signal(false);
        }
    }

    public void play(Effect effect) {
        if (effect == null) { return; }
        synchronized (lock) {
            if (closed || !enabled) { return; }
            if (pending.size() == MAX_PENDING) { pending.removeFirst(); }
            pending.addLast(effect);
            signal(true);
        }
    }

    /** Muting discards effects; unmuting resumes music only if still requested. */
    public void setEnabled(boolean value) {
        synchronized (lock) {
            if (closed || enabled == value) { return; }
            enabled = value;
            if (!value) { pending.clear(); allGeneration++; musicGeneration++; }
            signal(value && requestedMusic != null);
        }
        changes.firePropertyChange("enabled", !value, value);
    }

    public boolean isEnabled() { synchronized (lock) { return enabled && !closed; } }

    public Music getRequestedMusic() { synchronized (lock) { return requestedMusic; } }
    public void addEnabledListener(PropertyChangeListener listener) { changes.addPropertyChangeListener("enabled", listener); }
    public void removeEnabledListener(PropertyChangeListener listener) { changes.removePropertyChangeListener("enabled", listener); }

    /** Terminal, idempotent, asynchronous disposal, safe on the Swing EDT. */
    @Override public void close() {
        synchronized (lock) {
            if (closed) { return; }
            closed = true; enabled = false; requestedMusic = null; pending.clear();
            signal(false);
        }
        changes.firePropertyChange("enabled", true, false);
    }

    private void signal(boolean create) {
        dirty = true;
        if (worker == null && create) {
            worker = new Thread(this::run, "treasure-audio");
            worker.setDaemon(true);
            worker.start();
        }
        lock.notifyAll();
    }

    private boolean current(long generation, boolean music, long musicVersion) {
        synchronized (lock) {
            return !closed && enabled && allGeneration == generation
                    && (!music || (requestedMusic != null && musicGeneration == musicVersion));
        }
    }

    private void run() {
        Clip music = null;
        EnumMap<Effect, Clip> effects = new EnumMap<>(Effect.class);
        Set<String> unavailable = new HashSet<>();
        long appliedAll = -1, appliedMusic = -1;
        try {
            while (true) {
                Effect[] requests;
                Music wantedMusic;
                long generation, musicVersion;
                synchronized (lock) {
                    while (!closed && !dirty) { lock.wait(); }
                    if (closed) { return; }
                    dirty = false;
                    generation = allGeneration; musicVersion = musicGeneration;
                    wantedMusic = enabled ? requestedMusic : null;
                    requests = pending.toArray(Effect[]::new); pending.clear();
                }
                if (appliedAll != generation) {
                    effects.values().forEach(SoundManager::release); effects.clear();
                    appliedAll = generation;
                }
                if (wantedMusic == null || appliedMusic != musicVersion) {
                    release(music); music = null; appliedMusic = musicVersion;
                }
                if (wantedMusic != null && music == null && current(generation, true, musicVersion)) {
                    music = load(wantedMusic.resource(), unavailable);
                    if (music != null) {
                        if (current(generation, true, musicVersion)) {
                            try { music.loop(Clip.LOOP_CONTINUOUSLY); }
                            catch (RuntimeException failure) {
                                release(music); music = null; unavailable.add(wantedMusic.resource());
                            }
                        } else { release(music); music = null; }
                    }
                }
                for (Effect effect : requests) {
                    if (!current(generation, false, musicVersion)) { break; }
                    Clip clip = effects.get(effect);
                    if (clip == null) {
                        clip = load(effect.resource(), unavailable);
                        if (clip != null) { effects.put(effect, clip); }
                    }
                    if (clip == null) { continue; }
                    if (!current(generation, false, musicVersion)) { break; }
                    try { clip.stop(); clip.setFramePosition(0); clip.start(); }
                    catch (RuntimeException failure) {
                        release(effects.remove(effect)); unavailable.add(effect.resource());
                    }
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            release(music); effects.values().forEach(SoundManager::release);
        }
    }

    private Clip load(String resource, Set<String> unavailable) {
        if (unavailable.contains(resource)) { return null; }
        try {
            Clip clip = opener.apply(resource);
            if (clip != null) { return clip; }
        } catch (RuntimeException failure) { /* Missing resources or devices leave the game playable. */ }
        unavailable.add(resource);
        return null;
    }

    private static Clip openClip(String resource) {
        Clip clip = null;
        try (InputStream raw = SoundManager.class.getResourceAsStream(resource)) {
            if (raw == null) { return null; }
            try (AudioInputStream audio = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
                clip = AudioSystem.getClip();
                clip.open(audio);
                return clip;
            }
        } catch (Exception unavailable) {
            release(clip);
            return null;
        }
    }

    private static void release(Clip clip) {
        if (clip == null) { return; }
        try { clip.stop(); } catch (RuntimeException ignored) { }
        try { clip.close(); } catch (RuntimeException ignored) { }
    }
}
