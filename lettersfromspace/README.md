# Letters from Space

A hands-on lab about how a neural network learns. You train a small model for each letter (a neural
network, trained with back-propagation) to find letters hidden in a noisy video of the night sky, then
put the letters together to read a secret message. Nothing is hidden from you: you see every training example, watch the error fall
epoch by epoch, look at what each hidden unit has learned, decide how sure a model must be before you
believe it, and check its answers yourself. Along the way you meet the questions behind every
machine-learning project: what makes good training data, why a model that is perfect on its examples
can still be wrong on new data, and why a person still checks the results.

One night, driving back from Nasso, Professor Manso saw a group of lights in the sky that looked a
little like the letter A. Too busy to spend his nights watching the stars, he pointed a video camera
at that part of the sky. The recording has 20,000 frames of 8×8 pixels, mostly random stars. Somewhere
in it, one letter at a time, there is a message. Nobody can look at 20,000 frames by hand: that is the
models' job.

The neural network inside each model is the same hand-written Java code students used in 2001, one
object per neuron and one per synapse, so you can read every number it computes. On the lab's public
web page the same code runs in your own browser, translated line by line into JavaScript. This is the English edition (2026) of "Lettres
d'Espace", the neural-network practical taught at ESI, Université Polytechnique de Bobo-Dioulasso, in
2001, prepared by David Croft.

Try it online, with nothing to install: <https://r2do.com/lettersfromspace/> (the network runs in your
browser). The source code is on GitHub: <https://github.com/croftd/letters-from-space>.

## What you need

A JDK, version 17 or newer (for example [Temurin](https://adoptium.net)), and a web browser. There are
no libraries to install.

## Run it

Linux or macOS:

```bash
./run_dashboard.sh
```

Windows:

```bat
run_dashboard.bat
```

Then open <http://localhost:8080/>. Both scripts compile the code first (about two seconds), so your
changes to the Java sources are always picked up. To use another port: `./run_dashboard.sh 9090`. In a
copy unpacked from the dashboard's .zip download, start the scripts with `bash run_dashboard.sh` (zip
files do not keep the "executable" flag).

Other programs (after `./build.sh` or `build.bat`):

```bash
./run_selftest.sh                                         # checks that the code still works (Windows: run_selftest.bat)
java -cp build/classes lettersfromspace.StudentStarter    # the 2001 starting point: look at the frames one by one
```

### Publish it as a web page

The dashboard can also run without the Java server, as a plain web site: the network is then the
JavaScript translation in `web/engine/`, running in each visitor's browser, with training spread over
the processor cores (Web Workers). It gives the same results as the Java code for the same settings
and seed.

```bash
./build_site.sh                                           # Windows: build_site.bat
python3 -m http.server 8000 --directory build/site        # then open http://localhost:8000/
```

`build/site` can be copied as it is to GitHub Pages or any web host. `--url https://example.org/lab/`
adds the page's address (and a `sitemap.xml`); `--counter abacus` adds an old-school visitor counter
kept by [Abacus](https://abacus.jasoncameron.dev), a free counting service that needs no account (one
visit per browser session; the counter is named after the address). The page must be served over
http(s), not opened as a file.

## The dashboard in four steps

1. **Training data.** Pick a letter. The *positives* are the letter's samples plus noisy copies (the
   letters in the video have 1 to 4 flipped pixels). The *negatives* are every other letter plus frames
   of empty sky. With *new noise every epoch* the noisy copies and the sky are drawn again before every
   pass over the data. "Simulate filling in" replays how the samples get written into a `.dat` file,
   which is what the 2001 students did by hand. You can also draw your own samples.
2. **Back-propagation.** Choose the model's settings. Before training, the network diagram shows the
   starting weights (small random numbers). Train, and the diagram replays the training over 10
   seconds while the error curve is drawn epoch by epoch; the results appear when it ends: every connection coloured and sized by its weight, the weights from one
   input pixel (click another to follow it) and from each hidden unit to the output written in boxes,
   the hidden units lighting up as they learn the letter. You also see the error after every
   epoch, what each hidden unit learned (its 64 incoming weights drawn as an 8×8 picture), and how the
   model answers its own training examples. "Test a frame" scores anything you draw, live.
3. **Scan the video.** The detector scores all 20,000 frames. The frames at or above the threshold
   are the *candidates*. Untick the ones that are not really the letter: in 2001 the program showed
   each candidate and the student answered *o* or *n* (oui/non).
4. **Message from space.** Every detection, sorted by frame number, gives the message. "Train and
   scan all 31 letters" does steps 2 and 3 for every letter with your settings.

## How the code is organised

All sources are in `src/lettersfromspace/`.

| Package | Classes | What they do |
|---|---|---|
| `neuralnet` | `MultiLayerNetwork`, `Layer`, `Neuron`, `Synapse` | The network. `Neuron.computeOutput` is the forward pass (weighted sum, then sigmoid). `computeOutputDelta` and `computeHiddenDelta` are the backward pass. `updateWeights` is the gradient-descent step with momentum. `MultiLayerNetwork.train` runs the epochs. |
| | `IOVector`, `DoubleIOVector`, `TrainingResult`, `EpochListener` | Inputs and targets; what `train` returns (epochs, final error, the error of every epoch). |
| `space` | `Frame`, `Video`, `FrameLoader` | An 8×8 frame (it *is* an `IOVector`, so it goes straight into the network); the video; reading `.dat` files. |
| | `Noise`, `TrainingSets` | Flipping random pixels, making empty sky; building positives and negatives. |
| | `LetterDetector`, `MessageDecoder` | One model per letter: train, score, scan the video; merging the detections into text. |
| | `VideoViewer`, `GraphicalVideoViewer` | The console and window viewers of 2001, with the y/n confirmation. |
| `web` | `DashboardServer`, `Json` | The web server behind the dashboard (JDK built-in HTTP server) and its JSON API. The page itself is in `web/`. |
| | `SiteBuilder`, `Downloads` | The static web site (`build_site.sh`); the two .zip downloads. |
| `web/engine/` (JavaScript) | `core.js`, `lab.js`, `worker.js` | The network, noise, training sets and detectors translated line by line from the Java (same objects, arithmetic and random numbers); the dashboard's API answered in the browser; one training thread per core. |
| (top) | `SelfTest`, `StudentStarter` | Automatic checks; the 2001 starter program. |

A *model* is what you train and then use to make predictions. Here each model is a `LetterDetector`:
a small neural network (`MultiLayerNetwork`) plus the weights it has learned. The network keeps the
2001 design on purpose: sigmoid units everywhere, squared error, weights updated
after every example, starting weights between 0 and 0.2, and an optional bias node connected only to
the hidden units. Later labs use matrices, ReLU, cross-entropy and frameworks. Here you can follow
every multiplication.

### File formats

A `.dat` file holds frames as 8 lines of 8 characters, `O` for a star and `.` for dark sky. A line
`LETTER=X` says that the frames that follow are samples of X. Lines starting with `#` are comments. The
video `data/space_video.dat` uses the same format, one frame after another, frame 0 first.

`data/space_video_2001.dat` is the recording the 2001 class decoded; its message is in French. To try
it: `./run_dashboard.sh 8080 --video data/space_video_2001.dat`.

### Reading the 2001 code

The original French code is in the repository's `lettres_d_espace/code/edu/esi/`, or in the "original
2001 lab" download at the bottom of the dashboard. The main names:

| 2001 (French) | 2026 |
|---|---|
| `ReseauMulticouche`, `forme()`, `reconnais()` | `MultiLayerNetwork`, `train()`, `recognize()` |
| `VecteurES`, `VecteurESDouble` | `IOVector`, `DoubleIOVector` |
| `Cadre` (`HAUTEUR`, `LARGEUR`) | `Frame` (`HEIGHT`, `WIDTH`) |
| `CadreChargeur`, `Video.charge` | `FrameLoader`, `Video.load` |
| `VideoLecteur`, `confirmCadre` | `VideoViewer`, `confirmFrame` |
| `lettreA.dat`, `toutesLettres.dat`, `toutesLettresSansA.dat` | `letterA.dat`, `allLetters.dat`, `allLettersExceptA.dat` |
| `Debug.ASSERT` (stopped the program) | `Check.require` (throws an exception) |

## Exercises

Write down what you expected *before* you press the button, then what happened and why.

1. **The plateau.** Train the A detector with the default settings and look at the error curve. For a
   long time the error stays flat at 6.5. What is the model answering during that time? (Count
   the positives. The error of one example is ½·(target − output)².) What makes it escape suddenly?
2. **Bigger is not always better.** Train A with 8, 16 and 32 hidden units. Two of the three fail,
   even though the dashboard restarts a stuck run twice from new random weights (so 32 units take a
   while). Look at the curves and at the hidden units. Why might the starting weights (all positive,
   between 0 and 0.2) be the culprit? Try the bias node too.
3. **What does "nothing" look like?** Set *Empty-sky negatives* to 0 and train and scan A, R and O.
   Count the candidates and look at them. Then go back to 50. Why does a detector need to see the
   frames it should *ignore*? (The 2001 class trained without them.)
4. **The threshold.** Scan the O detector at 0.6, 0.8, 0.9 and 0.95. For each, how many candidates,
   and how many are really O's? Lowering the threshold finds more real letters (*recall*) but also more
   false ones (*precision* drops). Which mistakes are worse for decoding the message?
5. **Look-alikes.** O and Q, P and R, I, J, T and !, E and F, U and V. Draw a Q in "Test a frame" and
   ask the O detector. In 2001 the instructions said: add extra examples of the look-alike to the
   negatives. Which samples would you add, and to which letter? Try it.
6. **The missing letter.** After "Train and scan all 31 letters", one detector finds nothing at all,
   even though the message clearly needs it. Find a place in the message where it should be, look at
   the frames around that spot with "Watch the video", and compare them with the training samples. Fix
   it with "Draw your own sample", retrain and rescan. What does this tell you about training data?
7. **Training data versus the video.** After training, "Examples on the right side" usually says
   *all* of them, yet the scan still makes mistakes. Why is a perfect score on the training data not
   the same as a good detector? Which part of this lab plays the role of the *test set*?
8. **Memorising or learning?** Untick *New noise every epoch*, then train and scan B, and the whole
   message. With a fixed training set the model sees the same 9 B's and the same 50 skies thousands
   of times. What happens on the video, and why does drawing new noisy copies every epoch help? (This
   trick is called *data augmentation*.)
9. **Learning rate and momentum.** Train with learning rate 0.05, 0.2 and 1.0, then with momentum 0.
   Compare the epochs and the curves. The dashboard keeps the previous run of a letter as a faint brown curve.
10. **Inside back-propagation.** In `Neuron.java`, find the line that computes the output delta. Where
    does `output * (1.0 - output)` come from? (Differentiate the sigmoid.) Why does a hidden neuron add
    up the deltas of the neurons it feeds?
11. **Decode it.** Get the cleanest message you can with any settings you like. Explain the errors
    that remain.
