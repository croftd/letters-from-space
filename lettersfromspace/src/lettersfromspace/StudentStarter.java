// Letters from Space - English rewrite (2026) of the 2001 ESI neural network lab.
// Original: the TestApp listed in the 2001 practical ("travauxPractique.html").
package lettersfromspace;

import java.io.IOException;
import java.nio.file.Path;

import lettersfromspace.space.Frame;
import lettersfromspace.space.Video;
import lettersfromspace.space.VideoViewer;

/**
 * The starting point handed to students in 2001: load the video and look at the frames one by
 * one. "Clearly, it is not worth the effort to look at every frame by hand to find a message
 * from space"... which is why we train a neural network to do it.
 * <pre>
 * java -cp build/classes lettersfromspace.StudentStarter [data/space_video.dat] [firstFrame]
 * </pre>
 */
public class StudentStarter {

    public static void main(String[] args) throws IOException {
        Path file = Path.of(args.length > 0 ? args[0] : "data/space_video.dat");
        int first = args.length > 1 ? Integer.parseInt(args[1]) : 0;

        // Load the video
        Video video = Video.load(file);
        System.out.println("Loaded " + video.size() + " frames from " + file);

        // Create a viewer to display the frames
        VideoViewer viewer = new VideoViewer();

        // Go through the frames (this takes far too long: press Ctrl-C to stop)
        for (int i = first; i < video.size(); i++) {
            System.out.println("Showing frame " + i + " / " + video.size());
            Frame frame = video.getFrame(i);
            boolean answer = viewer.confirmFrame(frame);
            if (answer) {
                System.out.println("I will accept this frame as the letter we are looking for");
            }
        }
    }
}
