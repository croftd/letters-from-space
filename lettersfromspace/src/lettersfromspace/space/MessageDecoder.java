// Letters from Space - English rewrite (2026) of the 2001 ESI neural network lab.
package lettersfromspace.space;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Assembles the message hidden in a video from the hits of several letter detectors: the
 * detections are sorted by frame index and their letters concatenated. In 2001 each group of
 * students searched for two letters and the class combined its lists of indexes by hand (or
 * with a small program, "the challenge"); this class is that small program.
 */
public final class MessageDecoder {

    /** One decoded character: the frame where it was found, the letter and the detector's score. */
    public record Detection(int frameIndex, char letter, double score) {}

    private MessageDecoder() {}

    /**
     * Merges the hits of several detectors into one list sorted by frame index. When two detectors
     * claim the same frame, the higher score wins (letters such as O and Q, or I and J, are easy to
     * confuse). Spaces were written as '_' in the video, so '_' is kept here and turned into a space
     * by {@link #toText}.
     */
    public static List<Detection> merge(Map<Character, List<LetterDetector.Hit>> hitsByLetter) {
        Map<Integer, Detection> best = new TreeMap<>();
        for (Map.Entry<Character, List<LetterDetector.Hit>> e : hitsByLetter.entrySet()) {
            for (LetterDetector.Hit hit : e.getValue()) {
                Detection candidate = new Detection(hit.frameIndex(), e.getKey(), hit.score());
                Detection current = best.get(hit.frameIndex());
                if (current == null || candidate.score() > current.score()) {
                    best.put(hit.frameIndex(), candidate);
                }
            }
        }
        List<Detection> sorted = new ArrayList<>(best.values());
        sorted.sort(Comparator.comparingInt(Detection::frameIndex));
        return sorted;
    }

    /** The detections as text, with '_' shown as a space. */
    public static String toText(List<Detection> detections) {
        StringBuilder sb = new StringBuilder(detections.size());
        for (Detection d : detections) {
            sb.append(d.letter() == '_' ? ' ' : d.letter());
        }
        return sb.toString();
    }
}
