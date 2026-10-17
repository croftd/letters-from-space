// Letters from Space - English rewrite (2026) of the 2001 ESI neural network lab.
package lettersfromspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import lettersfromspace.neuralnet.DoubleIOVector;
import lettersfromspace.neuralnet.IOVector;
import lettersfromspace.neuralnet.MultiLayerNetwork;
import lettersfromspace.neuralnet.TrainingResult;
import lettersfromspace.space.Frame;
import lettersfromspace.space.FrameLoader;
import lettersfromspace.space.LetterDetector;
import lettersfromspace.space.MessageDecoder;
import lettersfromspace.space.Noise;
import lettersfromspace.space.TrainingSets;
import lettersfromspace.space.Video;

/**
 * Self-test of the rewrite (no test framework needed):
 * <pre>java -cp build/classes lettersfromspace.SelfTest [--data data]</pre>
 * Checks the frame/vector mapping, the file loader, the video format, that the network learns
 * XOR, the 2001 regression (the letter-A detector trained like the original solution must find
 * the A frames of the 2001 recording, {@code data/space_video_2001.dat}) and the whole message: one
 * detector per letter, trained with the dashboard defaults, must decode both the 2001 French poem
 * and the English poem of the students' video, {@code data/space_video.dat}. The message checks
 * need the instructor's answer keys and are skipped when {@code data/instructor} is absent.
 */
public class SelfTest {

    /** The 2001 recording (a French poem) and the students' video (an English poem, made in 2026). */
    static final String VIDEO_2001 = "space_video_2001.dat";
    static final String VIDEO = "space_video.dat";

    /**
     * Frames of the surviving 2001 video that show the letter A: every frame that differs from the
     * master A glyph by at most 5 pixels (the generator flipped at most 4). The indexes printed in
     * the 2001 practical ({1635, 3433, 7824, ...}) belong to an earlier generation of the video and
     * do not match the {@code video.dat} that was kept, so they are not used here.
     */
    static final int[] LETTER_A_INDEXES = {248, 1468, 2351, 2644, 5359, 5826, 6393, 9639, 11143, 11475,
            11827, 14026, 14326, 14852, 15247, 16746, 17469, 18223, 19291};

    /** Seed and threshold of the whole-message check (the dashboard defaults, with the seed of the 2026 tests). */
    static final long MESSAGE_SEED = 7;
    static final double MESSAGE_THRESHOLD = LetterDetector.DEFAULT_THRESHOLD;

    private static int failures = 0;

    public static void main(String[] args) throws IOException {
        Path data = Path.of("data");
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--data")) {
                data = Path.of(args[++i]);
            }
        }
        testFrameMapping();
        testLoader(data);
        testVideoRoundTrip();
        testNoise();
        testXor();
        testMessageDecoder();
        Video video2001 = testLetterARegression(data);
        testWholeMessage(data, video2001);
        System.out.println();
        if (failures == 0) {
            System.out.println("ALL TESTS PASSED");
        } else {
            System.out.println(failures + " TEST(S) FAILED");
            System.exit(1);
        }
    }

    static int pixelDifference(Frame a, Frame b) {
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            if (a.get(i) != b.get(i)) {
                diff++;
            }
        }
        return diff;
    }

    static void skip(String what) {
        System.out.println("  skip " + what);
    }

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + what);
        if (!ok) {
            failures++;
        }
    }

    static void testFrameMapping() {
        System.out.println("Frame <-> vector mapping");
        Frame f = new Frame();
        f.setPixel(3, 5, 1);
        check(f.get(3 * Frame.WIDTH + 5) == 1.0, "pixel (3,5) is vector element 29");
        f.set(63, 1.0);
        check(f.getPixel(7, 7) == 1, "vector element 63 is pixel (7,7)");
        check(f.countStars() == 2 && f.length() == 64, "two stars, length 64");
        Frame g = Frame.fromCompactString(f.toCompactString());
        check(g.samePixels(f), "compact string round trip");
    }

    static void testLoader(Path data) throws IOException {
        System.out.println("FrameLoader");
        List<Frame> all = FrameLoader.load(data.resolve("letters/allLetters.dat"));
        check(all.size() == 31, "allLetters.dat has 31 samples (got " + all.size() + ")");
        check(all.get(0).getLetter() == 'A' && all.get(25).getLetter() == 'Z' && all.get(30).getLetter() == '!',
                "letters are tagged A..Z then _ ' , . !");
        List<Frame> a = FrameLoader.load(data.resolve("letters/letterA.dat"));
        check(a.size() == 5, "letterA.dat has 5 hand-drawn samples (got " + a.size() + ")");
        check(a.get(0).samePixels(all.get(0)), "first A sample equals the master glyph of A");
        Path instructorLetters = data.resolve("instructor/allLetters.dat");
        if (Files.exists(instructorLetters)) {
            Map<Character, List<Frame>> groups = FrameLoader.groupByLetter(FrameLoader.load(instructorLetters));
            check(groups.size() == 31 && groups.get('A').size() == 2, "instructor file has 2 samples per letter");
        } else {
            skip(instructorLetters + " not found (instructor data)");
        }
        List<Frame> legacy = FrameLoader.read(new java.io.BufferedReader(new java.io.StringReader(
                "LETTRE=Q\r\n..OOOO..\r\n.O....O.\r\nO......O\r\nO......O\r\nO..O...O\r\nO...O..O\r\n.O....O.\r\n..OOOO.O\r\n")), "test");
        check(legacy.size() == 1 && legacy.get(0).getLetter() == 'Q', "legacy French tag LETTRE= and CRLF accepted");
    }

    static void testVideoRoundTrip() throws IOException {
        System.out.println("Video save/load");
        Random r = new Random(42);
        Frame[] frames = new Frame[5];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = Noise.randomSky(50, r);
        }
        Video v = new Video(frames);
        Path tmp = Files.createTempFile("lfs-video", ".dat");
        v.save(tmp);
        Video loaded = Video.load(tmp);
        boolean same = loaded.size() == 5;
        for (int i = 0; same && i < 5; i++) {
            same = loaded.getFrame(i).samePixels(frames[i]) && loaded.getFrame(i).getFrameIndex() == i;
        }
        check(same, "5 random frames survive a save/load round trip");
        Files.delete(tmp);
    }

    static void testNoise() {
        System.out.println("Noise");
        Frame f = new Frame();
        Noise.flipRandomPixels(f, 8, new Random(1));
        check(f.countStars() >= 1 && f.countStars() <= 5, "8% noise flips 1 to 5 pixels (got " + f.countStars() + ")");
    }

    static void testXor() {
        System.out.println("XOR with a 2-4-1 network and a bias node");
        List<IOVector> inputs = List.of(DoubleIOVector.of(0, 0), DoubleIOVector.of(0, 1), DoubleIOVector.of(1, 0), DoubleIOVector.of(1, 1));
        List<IOVector> targets = List.of(DoubleIOVector.of(0), DoubleIOVector.of(1), DoubleIOVector.of(1), DoubleIOVector.of(0));
        int solvedSeeds = 0;
        int seeds = 5;
        for (long seed = 1; seed <= seeds; seed++) {
            MultiLayerNetwork net = new MultiLayerNetwork(new int[] {2, 4, 1}, true, seed).setLearningRate(0.5).setMomentum(0.9);
            TrainingResult result = net.train(inputs, targets, 20_000);
            boolean allCorrect = true;
            for (int i = 0; i < 4; i++) {
                double out = net.recognize(inputs.get(i)).get(0);
                allCorrect &= (out >= 0.5) == (targets.get(i).get(0) >= 0.5);
            }
            if (allCorrect) {
                solvedSeeds++;
            }
            System.out.printf("       seed %d: %d epochs, error %.4f, %s%n", seed, result.epochs(), result.finalError(), allCorrect ? "solved" : "not solved");
        }
        check(solvedSeeds >= 3, "XOR solved for at least 3 of " + seeds + " seeds (" + solvedSeeds + ")");
    }

    static void testMessageDecoder() {
        System.out.println("MessageDecoder");
        Map<Character, List<LetterDetector.Hit>> hits = new java.util.HashMap<>();
        hits.put('I', List.of(new LetterDetector.Hit(10, 0.9), new LetterDetector.Hit(40, 0.85)));
        hits.put('H', List.of(new LetterDetector.Hit(5, 0.95)));
        hits.put('_', List.of(new LetterDetector.Hit(20, 0.99)));
        hits.put('J', List.of(new LetterDetector.Hit(40, 0.95)));   // J beats I on frame 40
        hits.put('O', List.of(new LetterDetector.Hit(60, 0.9)));
        String text = MessageDecoder.toText(MessageDecoder.merge(hits));
        check(text.equals("HI JO"), "merge sorts by frame, resolves the I/J collision and turns _ into a space (got '" + text + "')");
    }

    /** Returns the loaded video for the next test, or null when it is missing. */
    static Video testLetterARegression(Path data) throws IOException {
        System.out.println("2001 regression: the letter-A detector on the original video");
        Path videoFile = data.resolve(VIDEO_2001);
        if (!Files.exists(videoFile)) {
            check(false, videoFile + " is missing (run tools/legacy/convert.sh first)");
            return null;
        }
        long t0 = System.currentTimeMillis();
        Video video = Video.load(videoFile);
        System.out.println("       loaded " + video.size() + " frames in " + (System.currentTimeMillis() - t0) + " ms");
        check(video.size() == 20_000, "the 2001 video has 20,000 frames");
        List<Frame> aSamples = FrameLoader.load(data.resolve("letters/letterA.dat"));
        Frame masterA = aSamples.get(0);
        List<Integer> nearA = new ArrayList<>();
        for (int index = 0; index < video.size(); index++) {
            if (pixelDifference(video.getFrame(index), masterA) <= 5) {
                nearA.add(index);
            }
        }
        List<Integer> expected = new ArrayList<>();
        for (int index : LETTER_A_INDEXES) {
            expected.add(index);
        }
        check(nearA.equals(expected), "the frames within 5 pixels of the master A are exactly the " + LETTER_A_INDEXES.length + " known A frames (found " + nearA + ")");

        List<Frame> negatives = FrameLoader.load(data.resolve("letters/allLettersExceptA.dat"));
        int bestFound = -1;
        int bestFalse = Integer.MAX_VALUE;
        long bestSeed = -1;
        for (long seed = 1; seed <= 3; seed++) {
            t0 = System.currentTimeMillis();
            LetterDetector detector = LetterDetector.train('A', aSamples, negatives, LetterDetector.Config.defaults().withSeed(seed));
            List<LetterDetector.Hit> hits = detector.scan(video, 0.8);
            int found = 0;
            for (int index : LETTER_A_INDEXES) {
                for (LetterDetector.Hit h : hits) {
                    if (h.frameIndex() == index) {
                        found++;
                        break;
                    }
                }
            }
            int falsePositives = hits.size() - found;
            System.out.printf("       seed %d: %d epochs, error %.4f, %d/%d A frames found, %d other frames above 0.8 (%d ms)%n",
                    seed, detector.getTrainingResult().epochs(), detector.getTrainingResult().finalError(), found, LETTER_A_INDEXES.length,
                    falsePositives, System.currentTimeMillis() - t0);
            if (found > bestFound || (found == bestFound && falsePositives < bestFalse)) {
                bestFound = found;
                bestFalse = falsePositives;
                bestSeed = seed;
            }
        }
        check(bestFound >= LETTER_A_INDEXES.length - 2, "a detector finds at least " + (LETTER_A_INDEXES.length - 2) + " of the " + LETTER_A_INDEXES.length + " A frames (best seed " + bestSeed + ": " + bestFound + ", " + bestFalse + " false alarms)");
        return video;
    }

    /**
     * One detector per letter, trained with the dashboard defaults (samples and noisy copies as
     * positives, the other letters and 50 frames of empty sky as negatives, new noise every epoch,
     * restarts when a run gets stuck). The detectors do not
     * depend on the video, so they are trained once and then scan both videos; the merged detections
     * are compared with each answer key. Without the empty-sky negatives detectors such as A, O and R
     * fire on hundreds of sky frames.
     */
    static void testWholeMessage(Path data, Video video2001) throws IOException {
        System.out.println("The whole message: one detector per letter, dashboard defaults, threshold " + MESSAGE_THRESHOLD);
        Path key2001 = data.resolve("instructor/space_video_2001.answer_key.txt");
        Path key2026 = data.resolve("instructor/space_video.answer_key.txt");
        if (!Files.exists(key2001) && !Files.exists(key2026)) {
            skip("no answer key in " + data.resolve("instructor") + " (instructor data)");
            return;
        }
        Map<Character, List<Frame>> samples = TrainingSets.loadStudentSamples(data.resolve("letters"));
        Map<Character, LetterDetector> detectors = new ConcurrentHashMap<>();
        long t0 = System.currentTimeMillis();
        samples.keySet().parallelStream().forEach(letter ->           // the letters are independent
            detectors.put(letter, LetterDetector.train(letter, samples, TrainingSets.Options.defaults(),
                    LetterDetector.Config.defaults().withSeed(MESSAGE_SEED), null)));
        System.out.println("       " + detectors.size() + " detectors trained in " + (System.currentTimeMillis() - t0) + " ms");

        if (video2001 != null && Files.exists(key2001)) {
            AnswerKey key = readAnswerKey(key2001);
            List<Integer> keyA = new ArrayList<>();
            key.letters().forEach((index, letter) -> {
                if (letter == 'A') {
                    keyA.add(index);
                }
            });
            keyA.sort(null);
            check(keyA.equals(java.util.Arrays.stream(LETTER_A_INDEXES).boxed().toList()), "the A frames of the 2001 answer key are the " + LETTER_A_INDEXES.length + " known A frames");
            decodeAndCompare("2001 video, French poem", video2001, key, detectors);
        }
        if (Files.exists(key2026)) {
            decodeAndCompare("students' video, English poem", Video.load(data.resolve(VIDEO)), readAnswerKey(key2026), detectors);
        }
    }

    /** Scans the video with every detector, merges the hits and compares them with the answer key. */
    static void decodeAndCompare(String label, Video video, AnswerKey key, Map<Character, LetterDetector> detectors) {
        int expectedLength = key.message().length();
        check(key.letters().size() == expectedLength, label + ": the answer key lists every character of its message (" + key.letters().size() + " of " + expectedLength + ")");
        Map<Character, List<LetterDetector.Hit>> hitsByLetter = new ConcurrentHashMap<>();
        detectors.entrySet().parallelStream().forEach(e -> hitsByLetter.put(e.getKey(), e.getValue().scan(video, MESSAGE_THRESHOLD)));
        List<MessageDecoder.Detection> message = MessageDecoder.merge(hitsByLetter);
        int correct = 0;
        int wrongLetter = 0;
        int onSky = 0;
        for (MessageDecoder.Detection d : message) {
            Character expected = key.letters().get(d.frameIndex());
            if (expected == null) {
                onSky++;
            } else if (expected == d.letter()) {
                correct++;
            } else {
                wrongLetter++;
            }
        }
        System.out.println("       " + label + ": decoded " + message.size() + " characters: " + correct + " correct, " + wrongLetter + " wrong letter, " + onSky + " on empty sky");
        System.out.println("       " + MessageDecoder.toText(message));
        check(correct >= Math.ceil(0.97 * expectedLength), label + ": at least 97% of the letters found at the right frame (" + correct + " of " + expectedLength + ")");
        check(wrongLetter + onSky <= 10, label + ": at most 10 false detections (" + (wrongLetter + onSky) + ")");
    }

    /** An answer key: the letter at each frame index, and the message it spells (upper case, spaces as '_'). */
    record AnswerKey(Map<Integer, Character> letters, String message) {}

    /**
     * Reads an answer key written by CreateVideoApp or tools/legacy/RecoverAnswerKey: a comment line
     * "# message: ..." and lines of "frameIndex letter".
     */
    static AnswerKey readAnswerKey(Path file) throws IOException {
        Map<Integer, Character> letters = new java.util.HashMap<>();
        String message = "";
        for (String line : Files.readAllLines(file)) {
            if (line.startsWith("# message: ")) {
                message = line.substring("# message: ".length()).toUpperCase().replace(' ', '_');
            }
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int space = line.indexOf(' ');
            letters.put(Integer.parseInt(line.substring(0, space)), line.charAt(space + 1));
        }
        return new AnswerKey(letters, message);
    }
}
