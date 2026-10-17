// Letters from Space - English rewrite (2026) of the 2001 ESI neural network lab.
package lettersfromspace.web;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Builds the static web site of the lab, for GitHub Pages or any plain web host: the dashboard with the
 * network running in each visitor's browser (web/engine), the letter files and the video, and the two
 * downloads as ready-made zips. Nothing in it needs a server.
 * <pre>
 * java -cp build/classes lettersfromspace.web.SiteBuilder [--out build/site]
 *      [--url https://r2do.com/lettersfromspace/] [--counter abacus | --counter abacus:NAMESPACE/KEY]
 * </pre>
 * Run it from the lab folder (the one holding web/ and data/). {@code --url} adds the canonical address
 * and a sitemap.xml. {@code --counter abacus} turns on the visitor counter, an Abacus counter
 * (https://abacus.jasoncameron.dev, free, no account) named after the address: r2do.com/lettersfromspace
 * for https://r2do.com/lettersfromspace/. Copy the output folder to the web
 * site, for example into a {@code lettersfromspace/} folder of the repository that serves r2do.com.
 */
public final class SiteBuilder {

    private static final String MARKER = ".letters-from-space-site";

    private SiteBuilder() {}

    public static void main(String[] args) throws IOException {
        Path labRoot = Path.of("").toAbsolutePath().normalize();
        Path out = labRoot.resolve("build/site");
        String url = null;
        String counter = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--out" -> out = Path.of(args[++i]).toAbsolutePath().normalize();
                case "--url" -> url = args[++i].endsWith("/") ? args[i] : args[i] + "/";
                case "--counter" -> counter = args[++i];
                default -> {
                    System.err.println("Unknown option " + args[i]);
                    System.exit(2);
                }
            }
        }
        String abacus = counter == null ? null : abacusName(counter, url);
        Path web = labRoot.resolve("web");
        Path data = labRoot.resolve("data");
        if (!Files.isDirectory(web) || !Files.isDirectory(data)) {
            System.err.println("Run SiteBuilder from the lab folder (the one holding web/ and data/).");
            System.exit(2);
        }
        prepare(out);

        // the page and its scripts, styles and images
        try (Stream<Path> walk = Files.walk(web)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                Path target = out.resolve(web.relativize(file).toString());
                Files.createDirectories(target.getParent());
                if (file.getFileName().toString().equals("index.html")) {
                    Files.writeString(target, page(Files.readString(file, StandardCharsets.UTF_8), url, abacus), StandardCharsets.UTF_8);
                } else {
                    Files.copy(file, target);
                }
            }
        }
        // the data the browser lab loads (the video as .txt so that web hosts compress it)
        copy(data.resolve("letters/allLetters.dat"), out.resolve("data/letters/allLetters.dat"));
        copy(data.resolve("letters/letterA.dat"), out.resolve("data/letters/letterA.dat"));
        copy(data.resolve("space_video.dat"), out.resolve("data/space_video.txt"));
        // the downloads offered at the bottom of the page
        Files.createDirectories(out.resolve("downloads"));
        try (OutputStream zip = Files.newOutputStream(out.resolve("downloads/" + Downloads.ENGLISH))) {
            Downloads.writeEnglish(labRoot, zip);
        }
        boolean french = Downloads.frenchAvailable(labRoot.getParent());
        if (french) {
            try (OutputStream zip = Files.newOutputStream(out.resolve("downloads/" + Downloads.FRENCH))) {
                Downloads.writeFrench(labRoot.getParent(), zip);
            }
        }
        // what the browser lab reports as /api/status "downloads" (see web/engine/lab.js)
        Files.writeString(out.resolve("data/site.json"), "{\"downloads\": {\"english\": \"downloads/" + Downloads.ENGLISH
                + "\", \"french\": " + (french ? "\"downloads/" + Downloads.FRENCH + "\"" : "null") + "}}\n", StandardCharsets.UTF_8);
        if (url != null) {
            Files.writeString(out.resolve("sitemap.xml"), """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                      <url><loc>%s</loc></url>
                    </urlset>
                    """.formatted(url), StandardCharsets.UTF_8);
        }

        long total = 0;
        int count = 0;
        try (Stream<Path> walk = Files.walk(out)) {
            for (Path file : walk.filter(Files::isRegularFile).filter(f -> !f.getFileName().toString().equals(MARKER)).toList()) {
                total += Files.size(file);
                count++;
            }
        }
        System.out.printf("Built the static site in %s: %d files, %.1f MB%s%s%n", out, count, total / 1e6,
                url == null ? "" : " (canonical address " + url + ")", abacus == null ? "" : ", visitor counter abacus:" + abacus);
    }

    /** The page with the lab switched to the browser, plus the counter and canonical address when given. */
    static String page(String html, String url, String abacus) {
        String engine = "  <meta name=\"lfs-engine\" content=\"browser\">\n";
        if (url != null) {
            engine += "  <link rel=\"canonical\" href=\"" + url + "\">\n";
        }
        html = html.replace("</head>", engine + "</head>");
        if (abacus != null) {
            html = html.replace("<meta name=\"lfs-abacus\" content=\"\">", "<meta name=\"lfs-abacus\" content=\"" + abacus + "\">");
        }
        return html;
    }

    /**
     * The Abacus counter of {@code --counter}: "abacus:NAMESPACE/KEY" as given, or for plain "abacus" the
     * host of {@code --url} and its path ("r2do.com/lettersfromspace"). Abacus accepts 3 to 64 letters,
     * digits, '_', '-' and '.' for each part.
     */
    static String abacusName(String option, String url) {
        String name;
        if (option.startsWith("abacus:")) {
            name = option.substring("abacus:".length());
        } else if (option.equals("abacus")) {
            if (url == null) {
                fail("--counter abacus names the counter after --url: give --url too, or --counter abacus:NAMESPACE/KEY");
            }
            java.net.URI address = java.net.URI.create(url);
            String path = address.getPath().replaceAll("^/+|/+$", "").replace('/', '-');
            name = address.getHost() + "/" + (path.isEmpty() ? "home" : path);
        } else {
            fail("--counter must be abacus or abacus:NAMESPACE/KEY");
            return null;
        }
        String[] parts = name.split("/", -1);
        if (parts.length != 2 || !parts[0].matches("[A-Za-z0-9_.-]{3,64}") || !parts[1].matches("[A-Za-z0-9_.-]{3,64}")) {
            fail("Not a valid Abacus counter name: " + name + " (NAMESPACE/KEY, each 3 to 64 letters, digits, _ - .)");
        }
        return name;
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(2);
    }

    /** Empties the output folder, refusing any non-empty folder that this tool did not create. */
    private static void prepare(Path out) throws IOException {
        if (Files.isDirectory(out)) {
            boolean empty;
            try (Stream<Path> list = Files.list(out)) {
                empty = list.findAny().isEmpty();
            }
            if (!empty && !Files.exists(out.resolve(MARKER))) {
                System.err.println(out + " is not empty and was not made by SiteBuilder: choose another --out folder.");
                System.exit(2);
            }
            try (Stream<Path> walk = Files.walk(out)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                    if (!p.equals(out)) {
                        Files.delete(p);
                    }
                }
            }
        }
        Files.createDirectories(out);
        Files.writeString(out.resolve(MARKER), "Made by lettersfromspace.web.SiteBuilder; the whole folder is replaced on every build.\n");
    }

    private static void copy(Path from, Path to) throws IOException {
        Files.createDirectories(to.getParent());
        Files.copy(from, to);
    }
}
