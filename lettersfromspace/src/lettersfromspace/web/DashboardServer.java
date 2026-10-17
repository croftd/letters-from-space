// Letters from Space - English rewrite (2026) of the 2001 ESI neural network lab.
package lettersfromspace.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import lettersfromspace.neuralnet.MultiLayerNetwork;
import lettersfromspace.neuralnet.TrainingResult;
import lettersfromspace.space.Frame;
import lettersfromspace.space.LetterDetector;
import lettersfromspace.space.MessageDecoder;
import lettersfromspace.space.TrainingSets;
import lettersfromspace.space.Video;

/**
 * A small web server (JDK built-in, no libraries) that drives the "Letters from Space" lab from a
 * browser: it serves the dashboard pages from the {@code web/} folder and exposes the Java network
 * through a JSON API.
 * <pre>
 * java -cp build/classes lettersfromspace.web.DashboardServer [--port 8080] [--host ADDRESS] [--data data] [--web web]
 *      [--video data/space_video.dat]
 * </pre>
 * The video defaults to {@code space_video.dat} in the data folder (the English message); pass
 * {@code --video data/space_video_2001.dat} to work on the 2001 recording and its French poem.
 * By default the server listens on all network interfaces: a browser on Windows reaches a server
 * running in WSL 2 only that way, and a classroom can share the instructor's machine. The API has no
 * login (it can only train networks and read the video), so on an untrusted network pass
 * {@code --host 127.0.0.1} to accept local connections only.
 * <p>
 * API (all responses are JSON; parameters are query or form parameters):
 * <ul>
 * <li>GET  /api/status                               video size, letters, trained detectors, default settings</li>
 * <li>GET  /api/letters                              the training samples per letter</li>
 * <li>GET  /api/trainingset?letter=A&amp;variants=8&amp;noise=8&amp;sky=50&amp;seed=1   the examples of a training run's first epoch</li>
 * <li>GET  /api/video?offset=0&amp;limit=100          frames of the recording</li>
 * <li>GET  /api/frames?indexes=1,2,3</li>
 * <li>POST /api/samples?letter=A&amp;frame=&lt;64 chars&gt;   add a hand-drawn sample (in memory)</li>
 * <li>POST /api/samples/clear?letter=A</li>
 * <li>POST /api/train?letter=A&amp;hidden=16&amp;learningRate=0.2&amp;momentum=0.9&amp;maxEpochs=10000&amp;errorThreshold=0.01&amp;bias=false&amp;variants=8&amp;noise=8&amp;sky=50&amp;fresh=true&amp;restarts=2&amp;seed=1[&amp;trace=90]
 *      ({@code trace}: also return about that many snapshots of the weights during training, for the animation)</li>
 * <li>GET  /api/recognize?letter=A&amp;frame=&lt;64 chars&gt;</li>
 * <li>POST /api/scan?letter=A&amp;threshold=0.85</li>
 * <li>GET  /api/message                              the message assembled from all scans</li>
 * <li>POST /api/reset</li>
 * <li>POST /api/visits                               counts a page load (the page's visitor counter; kept in memory)</li>
 * </ul>
 * Downloads, zipped on request (see {@link Downloads}): {@code /downloads/letters-from-space-2026.zip}
 * (this lab and the dashboard) and {@code /downloads/lettres-d-espace-2001.zip} (the 2001 materials,
 * when the repository holds them).
 */
public class DashboardServer {

    private final Path dataDir;
    private final Path webDir;
    private final Video video;
    private final Map<Character, List<Frame>> samplesByLetter;          // from the data files
    private final Map<Character, List<Frame>> userSamples = new ConcurrentHashMap<>();
    private final Map<Character, LetterDetector> detectors = new ConcurrentHashMap<>();
    private final Map<Character, List<LetterDetector.Hit>> hits = new ConcurrentHashMap<>();
    private final Map<Character, Double> scanThresholds = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong visits = new java.util.concurrent.atomic.AtomicLong();

    public DashboardServer(Path dataDir, Path webDir, Path videoFile) throws IOException {
        this.dataDir = dataDir;
        this.webDir = webDir;
        this.video = Video.load(videoFile);
        // the master glyph of every letter plus the five hand-drawn A's from 2001
        this.samplesByLetter = TrainingSets.loadStudentSamples(dataDir.resolve("letters"));
    }

    public static void main(String[] args) throws IOException {
        int port = 8080;
        String host = null;                                     // null: all interfaces
        Path data = Path.of("data");
        Path web = Path.of("web");
        Path videoFile = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--host" -> host = args[++i];
                case "--data" -> data = Path.of(args[++i]);
                case "--web" -> web = Path.of(args[++i]);
                case "--video" -> videoFile = Path.of(args[++i]);
                default -> {
                    System.err.println("Unknown option " + args[i]);
                    System.exit(2);
                }
            }
        }
        DashboardServer app = new DashboardServer(data, web, videoFile != null ? videoFile : data.resolve("space_video.dat"));
        InetSocketAddress address = host == null ? new InetSocketAddress(port) : new InetSocketAddress(host, port);
        HttpServer server = HttpServer.create(address, 0);
        server.createContext("/api/", app::handleApi);
        server.createContext("/downloads/", app::handleDownload);
        server.createContext("/", app::handleStatic);
        // training is CPU bound and the page trains several letters at once: one thread per core
        server.setExecutor(Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors())));
        server.start();
        boolean everywhere = host == null || host.equals("0.0.0.0") || host.equals("::");
        System.out.println("Letters from Space dashboard: http://" + (everywhere ? "localhost" : host) + ":" + port + "/"
                + (everywhere ? "  (listening on all network interfaces; --host 127.0.0.1 for this machine only)" : ""));
        System.out.println("  video: " + (videoFile != null ? videoFile : data.resolve("space_video.dat")) + ", " + app.video.size() + " frames; "
                + app.samplesByLetter.size() + " letters with samples. Press Ctrl-C to stop.");
    }

    // ------------------------------------------------------------------ API

    private void handleApi(HttpExchange ex) throws IOException {
        try {
            Map<String, String> p = params(ex);
            String path = ex.getRequestURI().getPath();
            Object result = switch (path) {
                case "/api/status" -> status();
                case "/api/letters" -> letters();
                case "/api/trainingset" -> trainingSet(p);
                case "/api/video" -> videoFrames(p);
                case "/api/frames" -> frames(p);
                case "/api/samples" -> addSample(p);
                case "/api/samples/clear" -> clearSamples(p);
                case "/api/train" -> train(p);
                case "/api/recognize" -> recognize(p);
                case "/api/scan" -> scan(p);
                case "/api/message" -> message();
                case "/api/reset" -> reset();
                case "/api/visits" -> Map.of("count", visits.incrementAndGet());
                default -> null;
            };
            if (result == null) {
                sendJson(ex, 404, Map.of("error", "Unknown API path " + path));
            } else {
                sendJson(ex, 200, result);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            sendJson(ex, 400, Map.of("error", String.valueOf(e.getMessage())));
        } catch (RuntimeException e) {
            e.printStackTrace();
            sendJson(ex, 500, Map.of("error", e.toString()));
        }
    }

    private Map<String, Object> status() {
        Map<String, Object> trained = new LinkedHashMap<>();
        for (Map.Entry<Character, LetterDetector> e : detectors.entrySet()) {
            TrainingResult r = e.getValue().getTrainingResult();
            List<LetterDetector.Hit> h = hits.get(e.getKey());
            trained.put(String.valueOf(e.getKey()), Map.of(
                    "epochs", r.epochs(), "finalError", r.finalError(), "reachedThreshold", r.reachedThreshold(),
                    "hits", h == null ? -1 : h.size()));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("videoFrames", video.size());
        m.put("frameHeight", Frame.HEIGHT);
        m.put("frameWidth", Frame.WIDTH);
        m.put("letters", lettersList());
        m.put("trained", trained);
        m.put("defaults", defaults());
        Map<String, Object> downloads = new LinkedHashMap<>();
        downloads.put("english", "downloads/" + Downloads.ENGLISH);
        downloads.put("french", Downloads.frenchAvailable(repoRoot()) ? "downloads/" + Downloads.FRENCH : null);
        m.put("downloads", downloads);
        return m;
    }

    /** The lab folder (holding src/, data/ and web/) and the repository around it. */
    private Path labRoot() {
        return webDir.toAbsolutePath().normalize().getParent();
    }

    private Path repoRoot() {
        return labRoot().getParent();
    }

    /** The settings used when a request leaves a parameter out; the page starts from these. */
    private static Map<String, Object> defaults() {
        LetterDetector.Config c = LetterDetector.Config.defaults();
        TrainingSets.Options o = TrainingSets.Options.defaults();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hidden", c.hiddenUnits());
        m.put("learningRate", c.learningRate());
        m.put("momentum", c.momentum());
        m.put("maxEpochs", c.maxEpochs());
        m.put("errorThreshold", c.errorThreshold());
        m.put("bias", c.biasNode());
        m.put("seed", c.seed());
        m.put("restarts", c.restarts());
        m.put("variants", o.variants());
        m.put("noise", o.noisePercent());
        m.put("sky", o.skyFrames());
        m.put("fresh", o.freshNoise());
        m.put("threshold", LetterDetector.DEFAULT_THRESHOLD);
        return m;
    }

    private List<String> lettersList() {
        List<String> l = new ArrayList<>();
        for (Character c : samplesByLetter.keySet()) {
            l.add(String.valueOf(c));
        }
        return l;
    }

    private Map<String, Object> letters() {
        List<Object> list = new ArrayList<>();
        for (Map.Entry<Character, List<Frame>> e : samplesByLetter.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("letter", String.valueOf(e.getKey()));
            m.put("samples", compact(e.getValue()));
            m.put("userSamples", compact(userSamples.getOrDefault(e.getKey(), List.of())));
            list.add(m);
        }
        return Map.of("letters", list, "source", dataDir.resolve("letters").toString());
    }

    /** How the training examples are made, from the request (see {@link TrainingSets.Options}). */
    private static TrainingSets.Options options(Map<String, String> p) {
        TrainingSets.Options defaults = TrainingSets.Options.defaults();
        TrainingSets.Options options = new TrainingSets.Options(
                intParam(p, "variants", defaults.variants()),
                intParam(p, "noise", defaults.noisePercent()),
                intParam(p, "sky", defaults.skyFrames()),
                Boolean.parseBoolean(p.getOrDefault("fresh", String.valueOf(defaults.freshNoise()))));
        if (options.variants() < 0 || options.variants() > 100) {
            throw new IllegalArgumentException("variants must be between 0 and 100");
        }
        if (options.noisePercent() < 0 || options.noisePercent() > 50) {
            throw new IllegalArgumentException("noise must be between 0 and 50 percent");
        }
        if (options.skyFrames() < 0 || options.skyFrames() > 1000) {
            throw new IllegalArgumentException("sky must be between 0 and 1000 frames");
        }
        return options;
    }

    /** The samples of every letter: the data files plus the ones drawn in the dashboard. */
    private Map<Character, List<Frame>> allSamples() {
        Map<Character, List<Frame>> all = new LinkedHashMap<>();
        for (Map.Entry<Character, List<Frame>> e : samplesByLetter.entrySet()) {
            List<Frame> l = new ArrayList<>(e.getValue());
            l.addAll(userSamples.getOrDefault(e.getKey(), List.of()));
            all.put(e.getKey(), l);
        }
        return all;
    }

    /**
     * The examples of the first epoch of a training run with the same settings (with new noise every
     * epoch, the noisy copies and the sky change after that): what is shown is what is trained on.
     */
    private TrainingSets.Sets buildSets(char letter, Map<String, String> p, long seed) {
        return TrainingSets.build(letter, allSamples(), options(p), new Random(seed));
    }

    private Map<String, Object> trainingSet(Map<String, String> p) {
        char letter = letter(p);
        TrainingSets.Sets sets = buildSets(letter, p, longParam(p, "seed", LetterDetector.Config.defaults().seed()));
        List<Object> negatives = new ArrayList<>();
        for (Frame f : sets.negatives()) {
            // empty-sky frames have no letter: sent as ""
            negatives.add(Map.of("letter", f.hasLetter() ? String.valueOf(f.getLetter()) : "", "frame", f.toCompactString()));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("letter", String.valueOf(letter));
        m.put("positives", compact(sets.positives()));
        m.put("negatives", negatives);
        return m;
    }

    private Map<String, Object> videoFrames(Map<String, String> p) {
        int offset = Math.max(0, intParam(p, "offset", 0));
        int limit = Math.min(500, Math.max(1, intParam(p, "limit", 100)));
        List<String> frames = new ArrayList<>();
        for (int i = offset; i < Math.min(video.size(), offset + limit); i++) {
            frames.add(video.getFrame(i).toCompactString());
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("size", video.size());
        m.put("offset", offset);
        m.put("frames", frames);
        return m;
    }

    private Map<String, Object> frames(Map<String, String> p) {
        List<Object> list = new ArrayList<>();
        String indexes = p.getOrDefault("indexes", "");
        if (!indexes.isBlank()) {
            for (String s : indexes.split(",")) {
                int index = Integer.parseInt(s.trim());
                list.add(Map.of("index", index, "frame", video.getFrame(index).toCompactString()));
            }
        }
        return Map.of("frames", list);
    }

    private Map<String, Object> addSample(Map<String, String> p) {
        char letter = letter(p);
        Frame frame = Frame.fromCompactString(p.get("frame"));
        if (frame.countStars() == 0) {
            throw new IllegalArgumentException("The sample is empty: draw a few stars first");
        }
        frame.setLetter(letter);
        userSamples.computeIfAbsent(letter, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(frame);
        return Map.of("letter", String.valueOf(letter), "userSamples", compact(userSamples.get(letter)));
    }

    private Map<String, Object> clearSamples(Map<String, String> p) {
        char letter = letter(p);
        userSamples.remove(letter);
        return Map.of("letter", String.valueOf(letter), "userSamples", List.of());
    }

    private Map<String, Object> train(Map<String, String> p) {
        char letter = letter(p);
        LetterDetector.Config defaults = LetterDetector.Config.defaults();
        LetterDetector.Config config = new LetterDetector.Config(
                intParam(p, "hidden", defaults.hiddenUnits()),
                doubleParam(p, "learningRate", defaults.learningRate()),
                doubleParam(p, "momentum", defaults.momentum()),
                intParam(p, "maxEpochs", defaults.maxEpochs()),
                doubleParam(p, "errorThreshold", defaults.errorThreshold()),
                Boolean.parseBoolean(p.getOrDefault("bias", String.valueOf(defaults.biasNode()))),
                longParam(p, "seed", defaults.seed()),
                intParam(p, "restarts", defaults.restarts()));
        if (config.hiddenUnits() < 1 || config.hiddenUnits() > 256) {
            throw new IllegalArgumentException("hidden units must be between 1 and 256");
        }
        if (config.maxEpochs() < 1 || config.maxEpochs() > 200_000) {
            throw new IllegalArgumentException("maxEpochs must be between 1 and 200000");
        }
        if (config.restarts() < 0 || config.restarts() > 5) {
            throw new IllegalArgumentException("restarts must be between 0 and 5");
        }
        TrainingSets.Options options = options(p);
        TrainingSets.Sets sets = buildSets(letter, p, config.seed());        // the first epoch's examples, shown in step 1
        int traceFrames = intParam(p, "trace", 0);
        if (traceFrames < 0 || traceFrames > 300) {
            throw new IllegalArgumentException("trace must be between 0 and 300 frames");
        }
        Tracer tracer = traceFrames > 0 ? new Tracer(samplesByLetter.get(letter).get(0), (int) Math.ceil(config.maxEpochs() / 1500.0)) : null;
        long t0 = System.currentTimeMillis();
        LetterDetector detector = LetterDetector.train(letter, allSamples(), options, config, null, tracer);
        long millis = System.currentTimeMillis() - t0;
        Map<String, Object> trace = tracer == null ? null : tracer.result(detector, traceFrames);
        detectors.put(letter, detector);
        hits.remove(letter);

        TrainingResult r = detector.getTrainingResult();
        double[] history = r.errorHistory();
        int step = Math.max(1, (int) Math.ceil(history.length / 600.0));
        List<Double> curve = new ArrayList<>();
        for (int i = 0; i < history.length; i += step) {
            curve.add(history[i]);
        }
        if ((history.length - 1) % step != 0) {
            curve.add(history[history.length - 1]);
        }
        MultiLayerNetwork net = detector.getNetwork();
        List<Object> hiddenWeights = new ArrayList<>();
        for (int unit = 0; unit < config.hiddenUnits(); unit++) {
            hiddenWeights.add(net.getIncomingWeights(1, unit));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("letter", String.valueOf(letter));
        m.put("structure", net.getUnitsPerLayer());
        m.put("bias", config.biasNode());
        m.put("positives", sets.positives().size());
        m.put("negatives", sets.negatives().size());
        m.put("fresh", options.freshNoise());
        m.put("attempts", detector.getAttempts());
        m.put("epochs", r.epochs());
        m.put("finalError", r.finalError());
        m.put("reachedThreshold", r.reachedThreshold());
        m.put("millis", millis);
        m.put("errorHistory", curve);
        m.put("historyStep", step);
        m.put("hiddenWeights", hiddenWeights);
        m.put("outputWeights", net.getIncomingWeights(2, 0));
        // what the trained network answers on its own training samples (same order as /api/trainingset)
        synchronized (detector) {
            m.put("positiveScores", scores(detector, sets.positives()));
            m.put("negativeScores", scores(detector, sets.negatives()));
        }
        if (trace != null) {
            m.put("trace", trace);
        }
        return m;
    }

    /**
     * Records a training for the animation of step 2: every {@code stride} epochs, and whenever the
     * error has moved by more than 5 % since the last record (so a sudden drop is caught epoch by
     * epoch), the weights of both layers and what the network answers on the letter's master glyph
     * (the output score and the hidden activations). Only the run that is kept is returned, as about {@code frames} snapshots
     * chosen so that the animation moves quickly through flat stretches of the error curve and slowly
     * where the error changes.
     */
    private static final class Tracer implements LetterDetector.TrainingObserver {

        private record Snapshot(int epoch, double error, double[] hidden, double[] output, double[] activations, double score) {}

        private final Frame master;
        private final int stride;
        private final Map<Integer, List<Snapshot>> runs = new java.util.HashMap<>();
        private final Map<Integer, Double> lastLogError = new java.util.HashMap<>();

        Tracer(Frame master, int stride) {
            this.master = master;
            this.stride = Math.max(1, stride);
        }

        @Override
        public void onEpoch(int run, int epoch, double error, MultiLayerNetwork network) {
            double logError = Double.isNaN(error) ? Double.NaN : Math.log10(Math.max(error, 1e-9));
            Double last = lastLogError.get(run);
            boolean moved = !Double.isNaN(logError) && (last == null || last.isNaN() || Math.abs(logError - last) > 0.02);
            if (epoch % stride == 0 || moved) {
                runs.computeIfAbsent(run, k -> new ArrayList<>()).add(snapshot(epoch, error, network));
                lastLogError.put(run, logError);
            }
        }

        /** Weights rounded to 3 decimals: plenty for the boxes of the animation, and a third of the JSON. */
        private Snapshot snapshot(int epoch, double error, MultiLayerNetwork network) {
            double[][] hidden = network.getWeights(1);
            double[] flat = new double[hidden.length * hidden[0].length];
            for (int u = 0; u < hidden.length; u++) {
                for (int i = 0; i < hidden[u].length; i++) {
                    flat[u * hidden[u].length + i] = round(hidden[u][i]);
                }
            }
            double[] output = network.getWeights(2)[0];
            double score = network.recognize(master).get(0);     // between epochs: the next pattern overwrites the activations
            double[] activations = network.getOutputs(1);
            for (int u = 0; u < output.length; u++) {
                output[u] = round(output[u]);
                activations[u] = round(activations[u]);
            }
            return new Snapshot(epoch, error, flat, output, activations, round(score));
        }

        private static double round(double x) {
            return Math.round(x * 1000.0) / 1000.0;
        }

        Map<String, Object> result(LetterDetector detector, int frames) {
            List<Snapshot> all = new ArrayList<>(runs.getOrDefault(detector.getKeptRun(), List.of()));
            TrainingResult r = detector.getTrainingResult();
            if (all.isEmpty() || all.get(all.size() - 1).epoch() != r.epochs()) {
                all.add(snapshot(r.epochs(), r.finalError(), detector.getNetwork()));   // the final weights
            }
            List<Object> list = new ArrayList<>();
            for (Snapshot sn : select(all, frames)) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("epoch", sn.epoch());
                f.put("error", sn.error());
                f.put("score", sn.score());
                f.put("hidden", sn.hidden());
                f.put("output", sn.output());
                f.put("activations", sn.activations());
                list.add(f);
            }
            MultiLayerNetwork network = detector.getNetwork();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("hiddenUnits", network.getLayerSize(1));
            m.put("weightsPerUnit", network.getInputSize() + (network.hasBiasNode() ? 1 : 0));
            m.put("bias", network.hasBiasNode());
            m.put("master", master.toCompactString());
            m.put("epochs", r.epochs());
            m.put("run", detector.getKeptRun());
            m.put("frames", list);
            return m;
        }

        /** About {@code frames} snapshots, spread half by epoch and half by how much the (log) error moved. */
        private static List<Snapshot> select(List<Snapshot> all, int frames) {
            int n = all.size();
            if (n <= frames) {
                return all;
            }
            double[] logError = new double[n];
            for (int i = 0; i < n; i++) {
                double e = all.get(i).error();
                if (Double.isNaN(e)) {                    // before the first epoch: use the first known error
                    e = n > 1 ? all.get(1).error() : 1.0;
                }
                logError[i] = Math.log10(Math.max(e, 1e-9));
            }
            double[] moved = new double[n];
            for (int i = 1; i < n; i++) {
                moved[i] = moved[i - 1] + Math.abs(logError[i] - logError[i - 1]);
            }
            double lastEpoch = Math.max(1, all.get(n - 1).epoch());
            double totalMoved = moved[n - 1];
            List<Snapshot> out = new ArrayList<>();
            int i = 0;
            for (int j = 0; j < frames; j++) {
                double target = j / (frames - 1.0);
                while (i < n - 1 && progress(all.get(i).epoch() / lastEpoch, totalMoved > 0 ? moved[i] / totalMoved : -1) < target) {
                    i++;
                }
                if (out.isEmpty() || out.get(out.size() - 1) != all.get(i)) {
                    out.add(all.get(i));
                }
            }
            if (out.get(out.size() - 1) != all.get(n - 1)) {
                out.add(all.get(n - 1));
            }
            return out;
        }

        /** Half by epoch, half by error movement; by epoch alone when the error never moved ({@code errorFraction} -1). */
        private static double progress(double epochFraction, double errorFraction) {
            return errorFraction < 0 ? epochFraction : 0.5 * epochFraction + 0.5 * errorFraction;
        }
    }

    private static double[] scores(LetterDetector detector, List<Frame> frames) {
        double[] s = new double[frames.size()];
        for (int i = 0; i < s.length; i++) {
            s[i] = detector.score(frames.get(i));
        }
        return s;
    }

    private Map<String, Object> recognize(Map<String, String> p) {
        char letter = letter(p);
        LetterDetector d = detectorFor(letter);
        Frame frame = Frame.fromCompactString(p.get("frame"));
        synchronized (d) {             // a network keeps its activations in its neurons: one user at a time
            return Map.of("letter", String.valueOf(letter), "score", d.score(frame));
        }
    }

    private Map<String, Object> scan(Map<String, String> p) {
        char letter = letter(p);
        LetterDetector d = detectorFor(letter);
        double threshold = doubleParam(p, "threshold", LetterDetector.DEFAULT_THRESHOLD);
        long t0 = System.currentTimeMillis();
        List<LetterDetector.Hit> found;
        synchronized (d) {
            found = d.scan(video, threshold);
        }
        long millis = System.currentTimeMillis() - t0;
        hits.put(letter, found);
        scanThresholds.put(letter, threshold);
        List<Object> list = new ArrayList<>();
        for (LetterDetector.Hit h : found) {
            list.add(Map.of("index", h.frameIndex(), "score", h.score(), "frame", video.getFrame(h.frameIndex()).toCompactString()));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("letter", String.valueOf(letter));
        m.put("threshold", threshold);
        m.put("frames", video.size());
        m.put("millis", millis);
        m.put("hits", list);
        return m;
    }

    private Map<String, Object> message() {
        List<MessageDecoder.Detection> detections = MessageDecoder.merge(hits);
        List<Object> list = new ArrayList<>();
        for (MessageDecoder.Detection d : detections) {
            list.add(Map.of("index", d.frameIndex(), "letter", String.valueOf(d.letter()), "score", d.score()));
        }
        List<String> scanned = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (Character c : samplesByLetter.keySet()) {
            (hits.containsKey(c) ? scanned : missing).add(String.valueOf(c));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("text", MessageDecoder.toText(detections));
        m.put("detections", list);
        m.put("scanned", scanned);
        m.put("missing", missing);
        return m;
    }

    private Map<String, Object> reset() {
        detectors.clear();
        hits.clear();
        scanThresholds.clear();
        return Map.of("ok", true);
    }

    private LetterDetector detectorFor(char letter) {
        LetterDetector d = detectors.get(letter);
        if (d == null) {
            throw new IllegalArgumentException("No detector has been trained for the letter '" + letter + "' yet");
        }
        return d;
    }

    // ------------------------------------------------------------------ helpers

    private static List<String> compact(List<Frame> frames) {
        List<String> l = new ArrayList<>(frames.size());
        for (Frame f : frames) {
            l.add(f.toCompactString());
        }
        return l;
    }

    private char letter(Map<String, String> p) {
        String s = p.get("letter");
        if (s == null || s.length() != 1) {
            throw new IllegalArgumentException("Parameter 'letter' must be a single character");
        }
        char c = s.charAt(0);
        if (!samplesByLetter.containsKey(c)) {
            throw new IllegalArgumentException("There are no samples for the letter '" + c + "'");
        }
        return c;
    }

    private static int intParam(Map<String, String> p, String name, int def) {
        String v = p.get(name);
        try {
            return v == null || v.isBlank() ? def : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Parameter '" + name + "' must be a whole number (got '" + v + "')");
        }
    }

    private static long longParam(Map<String, String> p, String name, long def) {
        String v = p.get(name);
        try {
            return v == null || v.isBlank() ? def : Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Parameter '" + name + "' must be a whole number (got '" + v + "')");
        }
    }

    private static double doubleParam(Map<String, String> p, String name, double def) {
        String v = p.get(name);
        try {
            return v == null || v.isBlank() ? def : Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Parameter '" + name + "' must be a number (got '" + v + "')");
        }
    }

    /** Query parameters plus (for POST) form-encoded body parameters. */
    private static Map<String, String> params(HttpExchange ex) throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        parseInto(m, ex.getRequestURI().getRawQuery());
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            try (InputStream in = ex.getRequestBody()) {
                parseInto(m, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return m;
    }

    private static void parseInto(Map<String, String> m, String query) {
        if (query == null || query.isBlank()) {
            return;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            m.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
    }

    private static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    // ------------------------------------------------------------------ downloads

    private void handleDownload(HttpExchange ex) throws IOException {
        String name = ex.getRequestURI().getPath().substring("/downloads/".length());
        java.io.ByteArrayOutputStream zip = new java.io.ByteArrayOutputStream();
        if (name.equals(Downloads.ENGLISH)) {
            Downloads.writeEnglish(labRoot(), zip);
        } else if (name.equals(Downloads.FRENCH) && Downloads.frenchAvailable(repoRoot())) {
            Downloads.writeFrench(repoRoot(), zip);
        } else {
            byte[] bytes = ("Not found: " + name).getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(404, bytes.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(bytes);
            }
            return;
        }
        byte[] bytes = zip.toByteArray();
        ex.getResponseHeaders().set("Content-Type", "application/zip");
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + name + "\"");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    // ------------------------------------------------------------------ static files

    private void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) {
            path = "/index.html";
        }
        Path root = webDir.toAbsolutePath().normalize();
        Path file = root.resolve(path.substring(1)).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            byte[] bytes = ("Not found: " + path).getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(404, bytes.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(bytes);
            }
            return;
        }
        String name = file.getFileName().toString();
        String type = name.endsWith(".html") ? "text/html; charset=utf-8"
                : name.endsWith(".js") ? "text/javascript; charset=utf-8"
                : name.endsWith(".css") ? "text/css; charset=utf-8"
                : name.endsWith(".svg") ? "image/svg+xml"
                : name.endsWith(".png") ? "image/png"
                : name.endsWith(".jpg") || name.endsWith(".jpeg") ? "image/jpeg"
                : name.endsWith(".gif") ? "image/gif"
                : name.endsWith(".webp") ? "image/webp"
                : name.endsWith(".json") ? "application/json"
                : "application/octet-stream";
        byte[] bytes = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
