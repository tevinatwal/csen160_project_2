package stillasleep.three;

import com.jme3.app.SimpleApplication;
import com.jme3.app.state.AppState;
import com.jme3.app.state.ScreenshotAppState;
import com.jme3.material.TechniqueDef;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.system.AppSettings;

import java.util.function.Supplier;

/**
 * The 3D version of the game, built on jMonkeyEngine. Sets up the window,
 * owns the shared helpers, runs the chapters in order and fades between them.
 *
 * <p>jMonkeyEngine's {@code SimpleApplication} does the heavy lifting: it
 * opens the window, then calls {@link #simpleInitApp} once and
 * {@link #simpleUpdate} once for every frame that is drawn. Fields such as
 * {@code cam}, {@code inputManager}, {@code assetManager}, {@code guiNode}
 * and {@code stateManager} are inherited from it.
 *
 * <p>The game is a list of chapters. Each chapter is a {@link Stage}, and only
 * one is active at a time. To change chapter the screen fades to a colour,
 * the old stage is swapped for the new one while nothing can be seen, and the
 * screen fades back in.
 */
public class Game3D extends SimpleApplication {

    /** Number of chapters, including the ending. */
    static final int CHAPTERS = 12;

    /** Folder for test screenshots, or null when the game is played normally. */
    private final String shotsFolder;
    /** Keyboard and mouse state, shared by every chapter. */
    private Controls controls;
    /** On-screen text and the fade overlay, shared by every chapter. */
    private Hud hud;
    /** Helpers for making boxes, materials and furniture, shared by every chapter. */
    private Build build;

    /** Number of the chapter being played, from 0 to {@link #CHAPTERS} - 1. */
    private int chapter;
    /** How many times the player has finished the game and started again. */
    private int timesPlayed;
    /** The chapter that is on screen right now. */
    private Stage current;
    /** Recipe for the chapter to switch to once the fade-out ends; null if none. */
    private Supplier<Stage> pending;
    /** Colour the screen is fading to or from. */
    private ColorRGBA fadeColor = ColorRGBA.Black;
    /** How long one half of a fade (out, or in) lasts, in seconds. */
    private float fadeSeconds = 0.6f;
    /** Seconds since the current half of the fade began. */
    private float fadeTime;
    /** 0 = no fade, 1 = fading out, 2 = fading in. */
    private int fadePhase = 2;

    /** Engine helper that saves pictures of the screen; null unless testing. */
    private ScreenshotAppState screenshots;
    /** Chapter the automated test is working on; -1 before it starts. */
    private int testChapter = -1;
    /** Which step of the test is next (see {@link #runTest}). */
    private int testStep;
    /** Which of the chapter's test viewpoints is photographed next. */
    private int poseIndex;
    /** Seconds the test still has to wait before its next step. */
    private float testWait;
    /** Roughly how long the test has been trying to finish the chapter, in seconds. */
    private float completeTimer;
    /** Flips every try, so the test presses "interact" and "confirm" in turn. */
    private boolean tapToggle;

    /**
     * Creates the game. Private: use {@link #launch} instead.
     *
     * @param startChapter chapter to begin at (0 for a normal game)
     * @param shotsFolder  folder for test screenshots, or null for normal play
     */
    private Game3D(int startChapter, String shotsFolder) {
        // No default camera controller: it would un-hide the mouse cursor.
        // (An empty list also leaves out the engine's fly-camera and debug
        // displays, which this game does not use.)
        super(new AppState[0]);
        this.chapter = startChapter;
        // The test begins by adding one to this number, so start one below.
        this.testChapter = startChapter - 1;
        this.shotsFolder = shotsFolder;
    }

    /**
     * Opens the window and starts the game.
     *
     * @param args optional "chapter=N" to start at a chapter (0-11), and optional
     *             "shots=folder" to run the automated screenshot test
     */
    public static void launch(String[] args) {
        // Read the optional command-line arguments.
        int start = 0;
        String shots = null;
        for (String arg : args) {
            if (arg.startsWith("chapter=")) {
                // substring(8) is the text after "chapter=" (8 characters).
                // min/max keep the number inside the range of real chapters.
                start = Math.max(0, Math.min(CHAPTERS - 1, Integer.parseInt(arg.substring(8))));
            } else if (arg.startsWith("shots=")) {
                // substring(6) is the folder name after "shots=".
                shots = arg.substring(6);
            }
        }
        Game3D game = new Game3D(start, shots);

        // Window settings. "true" starts from the engine's default values.
        AppSettings settings = new AppSettings(true);
        settings.setTitle("Still Asleep");
        settings.setResolution(1280, 720);
        // VSync draws in step with the monitor, which avoids a torn picture.
        settings.setVSync(true);
        // 4x anti-aliasing smooths the jagged edges of objects.
        settings.setSamples(4);
        // OpenGL 3.2 is the modern kind of OpenGL that macOS supports.
        settings.setRenderer(AppSettings.LWJGL_OPENGL32);
        // Leave colours exactly as they were painted, with no extra brightening.
        settings.setGammaCorrection(false);
        // The game has no sound, so do not start the audio system at all.
        settings.setAudioRenderer(null);
        game.setSettings(settings);

        // Skip the engine's settings window, and keep running even when
        // another window is clicked.
        game.setShowSettings(false);
        game.setPauseOnLostFocus(false);
        game.start();
    }

    /**
     * Called once by the engine after the window opens. Sets up the camera
     * and lighting, creates the shared helpers and starts the first chapter.
     */
    @Override
    public void simpleInitApp() {
        // Hide the mouse pointer; the mouse is used for looking around.
        inputManager.setCursorVisible(false);

        // Camera lens: a 72 degree field of view (top to bottom), the
        // window's width-to-height ratio, and the nearest (5 cm) and farthest
        // (400 m) distances at which things are still drawn.
        cam.setFrustumPerspective(72f, (float) cam.getWidth() / cam.getHeight(), 0.05f, 400f);

        // Work out up to 8 lights in a single drawing pass per object. The
        // engine's default draws an object again for each light, which is slower.
        renderManager.setPreferredLightMode(TechniqueDef.LightMode.SinglePass);
        renderManager.setSinglePassLightBatchSize(8);

        // Helpers that every chapter shares.
        controls = new Controls(inputManager);
        build = new Build(assetManager);
        hud = new Hud(assetManager, guiNode, cam.getWidth(), cam.getHeight());

        // Create the first chapter. Attaching it to the state manager makes
        // the engine build it and update it every frame.
        current = create(chapter);
        stateManager.attach(current);

        // Test mode only: add the engine's screenshot helper. Pictures go in
        // the chosen folder, named by the test rather than numbered.
        if (shotsFolder != null) {
            screenshots = new ScreenshotAppState(shotsFolder + "/", "shot");
            screenshots.setIsNumbered(false);
            stateManager.attach(screenshots);
        }
    }

    /**
     * The order of the game. Each chapter looks a little more like real life
     * than the one before it.
     *
     * @param index chapter number
     * @return a new stage for that chapter
     */
    private Stage create(int index) {
        // Chapters 6 and 7 use the same class; the true/false picks which of
        // its two versions to build.
        return switch (index) {
            case 0 -> new RealHouse(this);
            case 1 -> new LevelState(this);
            case 2 -> new VoidHouse(this);
            case 3 -> new MazeHouse(this);
            case 4 -> new LightsHouse(this);
            case 5 -> new ChessHouse(this);
            case 6 -> new DetailHouse(this, false);
            case 7 -> new DetailHouse(this, true);
            case 8 -> new FinalHouse(this);
            case 9 -> new OutsideState(this);
            case 10 -> new AwakeHouse(this);
            default -> new EndingState(this);
        };
    }

    /**
     * Fades to the next chapter. Ignored while a fade-out is already running.
     *
     * @param color   colour to fade through
     * @param seconds length of each half of the fade
     */
    void nextChapter(ColorRGBA color, float seconds) {
        // Already leaving this chapter: do not skip two chapters at once.
        if (fadePhase == 1) {
            return;
        }
        // Go up by one, but never past the last chapter.
        chapter = Math.min(CHAPTERS - 1, chapter + 1);
        // Copy the number into a local variable, because the lambda below
        // runs later and is only allowed to use a variable that never changes.
        int target = chapter;
        fadeTo(() -> create(target), color, seconds);
    }

    /** Starts the whole game again from the real house. */
    void playAgain() {
        if (fadePhase == 1) {
            return;
        }
        chapter = 0;
        timesPlayed++;
        fadeTo(() -> create(0), ColorRGBA.Black, 1.0f);
    }

    /**
     * Begins a fade-out. When the screen is fully covered, {@link #simpleUpdate}
     * creates the next stage and fades back in.
     *
     * @param next    makes the stage to switch to; it is only called once the
     *                screen is covered, so the new scene is not built too early
     * @param color   colour to fade through
     * @param seconds length of each half of the fade
     */
    private void fadeTo(Supplier<Stage> next, ColorRGBA color, float seconds) {
        pending = next;
        fadeColor = color;
        fadeSeconds = seconds;
        fadeTime = 0;
        fadePhase = 1;
    }

    /** @return how many times the player has reached the ending and "woken up" again */
    int timesPlayed() {
        return timesPlayed;
    }

    /** @return true while the current stage is fading out */
    boolean isChanging() {
        return fadePhase == 1;
    }

    /**
     * Swaps the stage on screen straight away, with no fade of its own.
     *
     * @param next the stage to show from now on
     */
    private void switchNow(Stage next) {
        // Detaching makes the engine call the old stage's cleanup();
        // attaching makes it call the new stage's initialize().
        stateManager.detach(current);
        current = next;
        stateManager.attach(current);
    }

    /**
     * Called by the engine once per frame. Moves the fade along, updates the
     * on-screen text and handles the "skip chapter" key. (The chapter itself
     * is updated separately by the engine, because it is an attached state.)
     *
     * @param tpf "time per frame": seconds since the previous frame
     */
    @Override
    public void simpleUpdate(float tpf) {
        // Keep the mouse captured so the player can turn all the way round.
        inputManager.setCursorVisible(false);

        if (fadePhase != 0) {
            // t runs from 0 to 1 over one half of the fade.
            fadeTime += tpf;
            float t = Math.min(1f, fadeTime / fadeSeconds);

            // Fading out, the cover grows from clear to solid (t). Fading in,
            // it goes from solid back to clear (1 - t).
            hud.setFade(fadeColor, fadePhase == 1 ? t : 1 - t);

            if (t >= 1f) {
                if (fadePhase == 1) {
                    // The screen is fully covered, so the player cannot see
                    // the swap: build the next stage and start fading in.
                    switchNow(pending.get());
                    pending = null;
                    fadePhase = 2;
                    fadeTime = 0;
                } else {
                    // The fade-in is finished.
                    fadePhase = 0;
                }
            }
        }
        hud.update(tpf);

        // Shortcut: the "]" key jumps to the next chapter with a quick fade.
        if (controls.wasPressed("skip") && chapter < CHAPTERS - 1) {
            nextChapter(ColorRGBA.Black, 0.4f);
        }

        // Forget this frame's key presses and mouse movement. This must come
        // after everything above that reads them.
        controls.endFrame();

        // Test mode only.
        if (screenshots != null) {
            runTest(tpf);
        }
    }

    /** @return keyboard and mouse state */
    Controls controls() {
        return controls;
    }

    /** @return the on-screen text layer */
    Hud hud() {
        return hud;
    }

    /** @return scene-building helpers */
    Build build() {
        return build;
    }

    /**
     * Automated test, run with "Main shots=folder". It visits every chapter,
     * saves pictures from each chapter's test viewpoints, then completes the
     * chapter and checks that the game moves on. Not used in normal play.
     *
     * <p>It works in small steps, one per call, with a short wait between
     * them. {@code testStep} remembers which step comes next:
     * 0 = go to the next chapter, 1 = move the camera to a viewpoint,
     * 2 = take the picture, 3 = set the chapter up to be finished,
     * 4 = press keys until the game moves on.
     *
     * @param tpf seconds since the last frame
     */
    private void runTest(float tpf) {
        // Let go of the two keys that the test pressed last time, so every
        // press it makes is a fresh tap rather than a key held down.
        controls.onAction("interact", false, 0);
        controls.onAction("confirm", false, 0);

        // Count down the waiting time. Do nothing while waiting, or while the
        // screen is fading out.
        testWait -= tpf;
        if (testWait > 0 || fadePhase == 1) {
            return;
        }
        switch (testStep) {
            case 0 -> {
                // Step 0: move on to the next chapter, or stop after the last.
                testChapter++;
                if (testChapter >= CHAPTERS) {
                    System.out.println("TEST finished");
                    stop();
                    return;
                }
                // The game should have arrived here by itself. If it did not,
                // report the problem and jump there so the test can carry on.
                if (chapter != testChapter) {
                    System.out.println("TEST chapter " + testChapter
                            + " was NOT reached; game is at " + chapter);
                    chapter = testChapter;
                    switchNow(create(chapter));
                }
                // Wait 1.6 s so the fade-in is over before any pictures.
                poseIndex = 0;
                testStep = 1;
                testWait = 1.6f;
            }
            case 1 -> {
                // Step 1: clear text off the screen and move to a viewpoint.
                while (hud.isDialogOpen()) {
                    hud.advanceDialog();
                }
                hud.setHint(null);
                float[][] poses = current.debugPoses();
                if (poseIndex >= poses.length) {
                    // Every viewpoint is done; go on to finishing the chapter.
                    testStep = 3;
                    return;
                }
                // A pose is {x, feet y, z, yaw, pitch}. The angles are given
                // in degrees, but the player uses radians.
                float[] pose = poses[poseIndex];
                current.body().place(pose[0], pose[1], pose[2], pose[3] * FastMath.DEG_TO_RAD);
                current.body().pitch = pose[4] * FastMath.DEG_TO_RAD;
                // Give the scene a moment to be drawn from the new place.
                testStep = 2;
                testWait = 0.35f;
            }
            case 2 -> {
                // Step 2: save a picture named after the chapter and
                // viewpoint (for example "ch3_0"), then go back for the next.
                screenshots.setFileName("ch" + testChapter + "_" + poseIndex);
                screenshots.takeScreenshot();
                poseIndex++;
                testStep = 1;
                testWait = 0.2f;
            }
            case 3 -> {
                // Step 3: close any dialog and ask the chapter to set itself
                // up as if the player had nearly finished it.
                while (hud.isDialogOpen()) {
                    hud.advanceDialog();
                }
                current.debugComplete();
                completeTimer = 0;
                testStep = 4;
                testWait = 0.3f;
            }
            default -> {
                // Step 4: tap keys until the chapter ends. This runs about
                // every 0.3 s, so adding 0.3 keeps a rough count of the time.
                completeTimer += 0.3f;
                if (testChapter == CHAPTERS - 1) {
                    // the ending has nothing after it; just photograph its last screen
                    screenshots.setFileName("ch" + testChapter + "_end");
                    screenshots.takeScreenshot();
                    System.out.println("TEST chapter " + testChapter + " completed");
                    testStep = 0;
                    testWait = 0.4f;
                    return;
                }
                // Success: the game has moved on to a different chapter.
                if (chapter != testChapter) {
                    System.out.println("TEST chapter " + testChapter + " completed");
                    testStep = 0;
                    return;
                }
                // Failure: give up on this chapter after about 12 seconds.
                if (completeTimer > 12f) {
                    System.out.println("TEST chapter " + testChapter + " did NOT complete");
                    testStep = 0;
                    return;
                }
                // Press "interact" and "confirm" on alternate tries, because
                // different chapters need different keys to finish.
                tapToggle = !tapToggle;
                controls.onAction(tapToggle ? "interact" : "confirm", true, 0);
                testWait = 0.3f;
            }
        }
    }
}
