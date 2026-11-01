import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/** Original procedural score and effects. No samples, recordings, or external dependencies. */
public final class GenerateAudio {
    private static final int RATE = 22050;
    private static final double TAU = Math.PI * 2;
    private static final double BEAT = 60.0 / 90;

    public static void main(String[] args) throws IOException {
        Path directory = Path.of(args.length == 0 ? "resources/audio" : args[0]);
        Files.createDirectories(directory);
        write(directory, "adventure", adventure());
        write(directory, "pickup", phrase(new int[] {79, 86}, .13, .18));
        write(directory, "gold", phrase(new int[] {76, 79, 83, 88}, .12, .22));
        write(directory, "trap", phrase(new int[] {55, 54, 48}, .15, .19));
        write(directory, "win", phrase(new int[] {72, 76, 79, 83, 84}, .23, .22));
        write(directory, "lose", phrase(new int[] {69, 65, 62, 57}, .29, .18));
        write(directory, "lobby_bgm", lobby());
        write(directory, "treasure_pickup", phrase(new int[] {84, 91, 96}, .09, .18));
        write(directory, "treasure_gold", phrase(new int[] {79, 84, 88, 91, 96}, .085, .21));
        write(directory, "trap_stun", stun());
        write(directory, "ui_click", click());
    }

    private static double[] lobby() {
        // "Harbor of Maps": a brighter, quieter D-major score at 104 BPM.
        double beat = 60.0 / 104;
        double[] song = new double[(int) Math.round(32 * beat * RATE)];
        int[][] chords = {{50, 54, 57}, {47, 50, 54}, {43, 47, 50}, {45, 49, 52},
                          {50, 54, 57}, {43, 47, 50}, {47, 50, 54}, {45, 49, 52}};
        int[][] melody = {{78, 81, 85, 83, 81}, {78, 76, 74, 78, 81},
                          {79, 83, 86, 83, 81}, {80, 81, 83, 81, 76},
                          {81, 85, 88, 86, 85}, {83, 81, 79, 78, 79},
                          {78, 81, 83, 81, 78}, {76, 73, 76, 80, 81}};
        double[] starts = {0, 1, 1.5, 2.5, 3};
        for (int bar = 0; bar < 8; bar++) {
            for (int pitch : chords[bar]) { note(song, bar * 4 * beat, 4.2 * beat, pitch, .018, 0, true); }
            for (int step = 0; step < 8; step++) {
                note(song, (bar * 4 + step * .5) * beat, .9 * beat,
                        chords[bar][step % 3] + 12, .032, 1, true);
            }
            for (int i = 0; i < starts.length; i++) {
                double start = (bar * 4 + starts[i]) * beat;
                note(song, start, .8 * beat, melody[bar][i], .085, 2, true);
                note(song, start + .19, .8 * beat, melody[bar][i], .012, 2, true);
            }
        }
        return song;
    }

    private static double[] stun() {
        double[] samples = new double[(int) (1.2 * RATE)];
        for (int i = 0; i < samples.length; i++) {
            double t = i / (double) RATE;
            double envelope = Math.min(1, t / .008) * Math.exp(-3.4 * t)
                    * Math.min(1, (1.2 - t) / .12);
            double phase = TAU * (180 * t + 52 * (1 - Math.exp(-9 * t)) / 9)
                    + 3.2 * Math.sin(TAU * 8 * t) * Math.exp(-1.2 * t);
            samples[i] = .21 * envelope * (Math.sin(phase) + .22 * Math.sin(phase * 2));
        }
        for (int i = 0; i < 4; i++) { note(samples, .16 + i * .19, .36, 86 - i * 3, .08, 1, false); }
        return samples;
    }

    private static double[] click() {
        double[] samples = new double[(int) (.16 * RATE)];
        note(samples, 0, .09, 91, .15, 1, false);
        note(samples, .035, .12, 98, .065, 1, false);
        return samples;
    }

    private static double[] adventure() {
        // "Lantern Path": eight original 4/4 bars, C / Am / F / G, at 90 BPM.
        double[] song = new double[(int) Math.round(32 * BEAT * RATE)];
        int[][] chords = {{48, 52, 55}, {45, 48, 52}, {41, 45, 48}, {43, 47, 50},
                          {48, 52, 55}, {45, 48, 52}, {41, 45, 48}, {43, 47, 50}};
        int[][] melody = {{76, 79, 81, 79, 74, 76}, {72, 76, 79, 76, 74, 72},
                          {69, 72, 77, 76, 72, 69}, {71, 74, 79, 77, 74, 71},
                          {76, 79, 84, 83, 79, 76}, {81, 79, 76, 74, 72, 76},
                          {77, 76, 72, 69, 72, 74}, {74, 71, 67, 71, 74, 79}};
        double[] starts = {0, 1, 1.5, 2, 3, 3.5};
        double[] lengths = {.9, .43, .43, .9, .43, .43};
        for (int bar = 0; bar < 8; bar++) {
            for (int pitch : chords[bar]) {
                note(song, bar * 4 * BEAT, 4 * BEAT, pitch, .027, 0, true);
            }
            for (int step = 0; step < 8; step++) {
                note(song, (bar * 4 + step * .5) * BEAT, .8 * BEAT,
                        chords[bar][new int[] {0, 1, 2, 1, 0, 2, 1, 2}[step]] + 12, .045, 1, true);
            }
            for (int n = 0; n < starts.length; n++) {
                double start = (bar * 4 + starts[n]) * BEAT;
                note(song, start, lengths[n] * BEAT, melody[bar][n], .12, 2, true);
                note(song, start + .24, lengths[n] * BEAT, melody[bar][n], .022, 2, true);
            }
        }
        return song;
    }

    private static double[] phrase(int[] pitches, double spacing, double level) {
        double[] result = new double[(int) ((pitches.length * spacing + .45) * RATE)];
        for (int i = 0; i < pitches.length; i++) {
            note(result, i * spacing, spacing + .4, pitches[i], level, 1, false);
        }
        return result;
    }

    private static void note(double[] out, double start, double duration, int midi,
                             double amplitude, int voice, boolean wrap) {
        int offset = (int) Math.round(start * RATE), length = (int) Math.round(duration * RATE);
        double frequency = 440 * Math.pow(2, (midi - 69) / 12.0);
        for (int i = 0; i < length; i++) {
            double t = i / (double) RATE, phase = TAU * frequency * t;
            double envelope = Math.min(1, t / (voice == 0 ? .22 : .015))
                    * Math.min(1, (length - 1 - i) / (RATE * (voice == 0 ? .3 : .08)));
            double tone;
            if (voice == 0) { tone = Math.sin(phase) + .18 * Math.sin(phase * 2); }
            else if (voice == 1) {
                tone = (Math.sin(phase) + .35 * Math.sin(phase * 2) + .12 * Math.sin(phase * 3)) * Math.exp(-5 * t);
            } else {
                tone = Math.sin(phase + .012 * Math.sin(TAU * 5 * t)) + .12 * Math.sin(phase * 2);
            }
            int position = offset + i;
            // Wrap tails into the beginning, so reverb and arpeggios cross the loop boundary.
            if (wrap) { position %= out.length; }
            if (position < out.length) { out[position] += amplitude * envelope * tone; }
        }
    }

    private static void write(Path directory, String name, double[] samples) throws IOException {
        byte[] pcm = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            int value = (int) Math.round(Math.max(-.95, Math.min(.95, samples[i])) * 32767);
            pcm[i * 2] = (byte) value; pcm[i * 2 + 1] = (byte) (value >> 8);
        }
        AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(pcm), format, samples.length)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, directory.resolve(name + ".wav").toFile());
        }
        System.out.printf(java.util.Locale.ROOT, "%s.wav: %.2fs, 16-bit mono PCM, %d Hz%n", name, samples.length / (double) RATE, RATE);
    }
}
