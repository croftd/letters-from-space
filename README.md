# Letters from Space

Simple neural network example, updated from a lab prepared for L'ESI in Burkina Faso, 2001.

A hands-on lab about how a neural network learns, first taught in 2001 at ESI, Université Polytechnique
de Bobo-Dioulasso (Burkina Faso), and rebuilt in English in 2026. You train one small neural network per
letter with back-propagation to find letters hidden in a noisy video of the night sky, then put the
letters together to read a secret message. Every training example, every weight and every epoch of the
error is visible, and you decide which detections to believe.

**Try it online:** <https://r2do.com/lettersfromspace/>. Nothing to install: the network trains in your
browser, one thread per processor core.

## What is in this repository

| Where | What |
|---|---|
| [`lettersfromspace/`](lettersfromspace/) | The English edition (2026): the same hand-written Java network the 2001 students used (one object per neuron and per synapse, sigmoid units, back-propagation with momentum), a web dashboard, a line-by-line JavaScript translation that runs in the browser with identical results, the letter files and the video, and eleven exercises. **Start with its [README](lettersfromspace/README.md).** |
| `index.html`, `classe1.html`, `classe2.html`, `travauxPractique.html`, `miscNotes.html` | The 2001 course pages, in French (ISO-8859-1): an introduction to neural networks, multi-layer networks and back-propagation, and the practical "Lettres d'Espace". |
| [`lettres_d_espace/`](lettres_d_espace/) | The 2001 Java code (`edu.esi.*`, written for Java 1.1), its javadoc (`api/`) and the figures of the course pages (`images/`). |

The 2001 pages are also still online where they were first published:
<https://zooid.org/~tforcdivad/reseauxNeuronaux/>. In this repository their figures are in
`lettres_d_espace/images/`; the "original 2001 lab" download on the lab's page has the 2001 layout.

## Quick start

You need a JDK, version 17 or newer (for example [Temurin](https://adoptium.net)), and a web browser.
There are no libraries to install.

```bash
cd lettersfromspace
./run_dashboard.sh          # Windows: run_dashboard.bat
```

Then open <http://localhost:8080/>. `./run_selftest.sh` checks the code, and `./build_site.sh` builds
the static version of the page (the one online) for any web host such as GitHub Pages; see the lab's
README.

## For instructors

The solution programs, the video generators and the answer keys (2001 and 2026) are not in this
repository, as the 2001 instructions asked: the students should find the message themselves. The
self-test skips the checks that need them.

## Credits

Letters from Space is a neural-network practical lab at ESI, Université Polytechnique de
Bobo-Dioulasso, from 2001: Professor Manso's story about Letters from Space, the video with its hidden
poem, and the network his students trained. This English edition (2026) still runs that network, the
same hand-written Java code the students used, one object per neuron and per synapse. prepared by David
Croft, Burkina, 2001

The 2001 course pages were prepared from Kevin Gurney's *An Introduction to Neural Networks* (UCL
Press, 1997), EPFL's *Neural Java* tutorial and Rich and Knight's *Artificial Intelligence* (1991).

## Licence

The code is under the [MIT licence](LICENSE). The course text and the images are under
[CC BY 4.0](LICENSE-content.md); the figures of the 2001 pages were redrawn in 2026, and passages taken
from the sources above are not covered (see [LICENSE-content.md](LICENSE-content.md)).
