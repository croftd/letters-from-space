// Letters from Space - English rewrite (2026) of the 2001 ESI neural network lab.
package lettersfromspace.web;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The two downloads offered by the dashboard, zipped on request from the files on disk so they always
 * match the current code. Both are student copies: the instructor material stays out, as the 2001
 * {@code LISEZMOI.TXT} asked ("the prof package must not be given to the students").
 * <ul>
 * <li>{@link #ENGLISH}: this lab with the dashboard ({@code lettersfromspace/}), without
 *     {@code src/lettersfromspace/instructor}, {@code data/instructor}, {@code tools} and build output.</li>
 * <li>{@link #FRENCH}: the 2001 materials in the layout of the 2001 archive (course pages, {@code images/},
 *     {@code code/}, {@code api/}), from the repository's root pages and {@code lettres_d_espace/}, without
 *     the {@code prof} package and compiled classes.</li>
 * </ul>
 */
final class Downloads {

    static final String ENGLISH = "letters-from-space-2026.zip";
    static final String FRENCH = "lettres-d-espace-2001.zip";

    private static final List<String> ENGLISH_FILES = List.of("README.md", "build.sh", "build.bat", "java_env.sh",
            "run_dashboard.sh", "run_dashboard.bat", "run_selftest.sh", "run_selftest.bat", "build_site.sh", "build_site.bat");
    private static final List<String> ENGLISH_FOLDERS = List.of("src", "data", "web");
    private static final List<String> ENGLISH_EXCLUDED = List.of("src/lettersfromspace/instructor", "data/instructor");
    private static final List<String> COURSE_PAGES = List.of("index.html", "classe1.html", "classe2.html",
            "travauxPractique.html", "miscNotes.html");

    private Downloads() {}

    /** True when the 2001 materials are next to the lab (a full copy of the repository, not a student zip). */
    static boolean frenchAvailable(Path repoRoot) {
        return Files.isDirectory(repoRoot.resolve("lettres_d_espace/code")) && Files.isRegularFile(repoRoot.resolve("travauxPractique.html"));
    }

    /** Zips the lab folder {@code labRoot} (the folder holding src/, data/ and web/) as {@code letters-from-space/...}. */
    static void writeEnglish(Path labRoot, OutputStream out) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            String root = "letters-from-space/";
            for (String name : ENGLISH_FILES) {
                Path file = labRoot.resolve(name);
                if (Files.isRegularFile(file)) {
                    add(zip, root + name, file);
                }
            }
            for (String folder : ENGLISH_FOLDERS) {
                addTree(zip, labRoot, labRoot.resolve(folder), root, ENGLISH_EXCLUDED);
            }
        }
    }

    /** Zips the 2001 materials as {@code lettres-d-espace-2001/...}, in the layout of the 2001 archive. */
    static void writeFrench(Path repoRoot, OutputStream out) throws IOException {
        Path legacy = repoRoot.resolve("lettres_d_espace");
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            String root = "lettres-d-espace-2001/";
            zip.putNextEntry(new ZipEntry(root + "README.txt"));
            zip.write(FRENCH_README.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (String page : COURSE_PAGES) {
                Path file = repoRoot.resolve(page);
                if (Files.isRegularFile(file)) {
                    add(zip, root + page, file);
                }
            }
            for (String folder : List.of("images", "code", "api")) {
                addTree(zip, legacy, legacy.resolve(folder), root, List.of("code/edu/esi/lettresdespace/prof"));
            }
        }
    }

    private static final String FRENCH_README = String.join("\r\n",
            "Lettres d'Espace - the neural-network practical of the AI course at ESI,",
            "Universite Polytechnique de Bobo-Dioulasso, 2001. Prepared by David Croft, Burkina, 2001.",
            "",
            "Course pages (in French): index.html, classe1.html, classe2.html, travauxPractique.html",
            "(the practical) and miscNotes.html. The Java code is in code/edu/esi (written for Java 1.1;",
            "video.dat is the recording with the hidden message) and its javadoc in api/.",
            "",
            "The instructor package code/edu/esi/lettresdespace/prof (the solution and the video",
            "generator) is not included: its LISEZMOI.TXT asked that it never be given to students.",
            "",
            "The English edition of 2026, with a web dashboard, is the other download: letters-from-space-2026.zip.",
            "");

    /** Adds every file under {@code dir}, named by its path relative to {@code base}, except excluded subtrees and .class files. */
    private static void addTree(ZipOutputStream zip, Path base, Path dir, String root, List<String> excluded) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dir)) {
            files = walk.filter(Files::isRegularFile).sorted().toList();
        }
        for (Path file : files) {
            String relative = base.relativize(file).toString().replace('\\', '/');
            boolean skip = relative.endsWith(".class");
            for (String prefix : excluded) {
                skip |= relative.equals(prefix) || relative.startsWith(prefix + "/");
            }
            if (!skip) {
                add(zip, root + relative, file);
            }
        }
    }

    private static void add(ZipOutputStream zip, String name, Path file) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(Files.getLastModifiedTime(file).toMillis());
        zip.putNextEntry(entry);
        Files.copy(file, zip);
        zip.closeEntry();
    }
}
