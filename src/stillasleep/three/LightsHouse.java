package stillasleep.three;

import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Sphere;

/**
 * Chapter 4: the power is out. Only the television works, and it shows
 * nothing but a repeating sequence of colours. Four lamps around the house
 * each glow faintly in one of those colours; switching them on in the order
 * the television shows brings the power back and breaks the dream.
 */
final class LightsHouse extends HouseStage {

    /** The four lamp colours as 0xRRGGBB: red, blue, yellow, green. */
    private static final int[] COLORS = {0xff5a4a, 0x4a8cff, 0xffd23f, 0x5cd66a};
    /** The colour names used in messages, in the same order as {@link #COLORS}. */
    private static final String[] NAMES = {"red", "blue", "yellow", "green"};
    /**
     * The order to press the lamps in, as indexes into {@link #COLORS}:
     * green, red, yellow, blue, red.
     */
    static final int[] SEQUENCE = {3, 0, 2, 1, 0};
    /**
     * The bulb position {x, y, z} of each lamp: the bedside lamp (red), the lamp
     * on the hallway side table (blue), the living room floor lamp (yellow) and
     * the floor lamp at the west end of the hallway (green).
     */
    private static final float[][] LAMPS = {{3.18f, 0.94f, 0.26f}, {9.7f, 1.2f, 5.5f},
        {10.2f, 1.75f, 6.3f}, {-1.2f, 1.72f, 5.4f}};

    /** Each lamp's own glowing bulb material, so its brightness can be changed. */
    private final Material[] bulbs = new Material[4];
    /** The coloured light each lamp gives off while it is switched on. */
    private final PointLight[] lights = new PointLight[4];
    /** A light in front of the television that copies the colour of the screen. */
    private PointLight tvLight;
    /** How many lamps of the sequence have been pressed correctly so far. */
    private int progress;
    /** True once the whole sequence has been entered. */
    private boolean solved;

    /**
     * Sets up the house with the power off.
     *
     * @param game the running game
     */
    LightsHouse(Game3D game) {
        super(game);
        powerOut = true;
    }

    /** Builds the dark house, adds the four coloured lamps and wakes the player. */
    @Override
    protected void buildChapter() {
        buildBedroomAndCloset();
        buildHallwayAndKitchen();
        addAmbient();
        // The blue lamp needs something to sit in: a table lamp on the hallway side table.
        build.model(scene, "lampRoundTable", 9.7f, 0.76f, 5.52f, 0, 1);
        // For each lamp: a coloured bulb, a light that is only in the scene while the lamp is
        // on, and a spot called "lamp0" to "lamp3" so the player can press it.
        for (int i = 0; i < 4; i++) {
            bulbs[i] = build.glow(COLORS[i]).clone();
            Geometry bulb = new Geometry("bulb", new Sphere(12, 12, 0.11f));
            bulb.setMaterial(bulbs[i]);
            bulb.setLocalTranslation(LAMPS[i][0], LAMPS[i][1], LAMPS[i][2]);
            scene.attachChild(bulb);
            lights[i] = new PointLight(new Vector3f(LAMPS[i][0], LAMPS[i][1], LAMPS[i][2]),
                    Build.rgba(COLORS[i]).mult(1.2f), 5.5f);
            spots.add(new Spot("lamp" + i, new Vector3f(LAMPS[i][0], LAMPS[i][1], LAMPS[i][2])));
        }
        // Start with every lamp dim.
        showLamps();
        // The television's light starts black. animate() gives it a colour every frame.
        tvLight = new PointLight(new Vector3f(4.6f, 1.1f, 8.2f), ColorRGBA.Black, 6f);
        scene.addLight(tvLight);
        wake("Dark. The power is out.",
                "Something is flickering at the far end of the house. Different colours.");
    }

    /**
     * Updates every lamp to match the puzzle. Lamps glow faintly in their colour
     * when off and brightly when on.
     */
    private void showLamps() {
        for (int i = 0; i < 4; i++) {
            // A lamp is on if the puzzle is solved or it is one of the steps pressed so far.
            boolean on = solved;
            for (int step = 0; step < progress; step++) {
                on |= SEQUENCE[step] == i;
            }
            // A lamp that is off is shown at 28% brightness, so its colour can still be seen.
            bulbs[i].setColor("Color", Build.rgba(COLORS[i]).mult(on ? 1f : 0.28f));
            // Take the light out first so that it is never added twice.
            scene.removeLight(lights[i]);
            if (on) {
                scene.addLight(lights[i]);
            }
        }
    }

    /**
     * Flashes the colour sequence on the television, even while a dialog is open.
     *
     * @param tpf seconds since the last frame
     */
    @Override
    protected void animate(float tpf) {
        // The living room has not been built yet.
        if (tvScreen == null) {
            return;
        }
        // Almost black: the screen between two flashes.
        ColorRGBA color = new ColorRGBA(0.03f, 0.03f, 0.05f, 1f);
        if (solved) {
            // Solved: the screen stays bright white.
            color = new ColorRGBA(0.9f, 0.9f, 0.95f, 1f);
        } else {
            // Each colour gets 1.25 seconds: shown for 0.9 seconds, then dark. After the last
            // colour there is a 2.2 second pause and the sequence starts again.
            float cycle = time % (SEQUENCE.length * 1.25f + 2.2f);
            int step = (int) (cycle / 1.25f);
            if (step < SEQUENCE.length && cycle - step * 1.25f < 0.9f) {
                color = Build.rgba(COLORS[SEQUENCE[step]]);
            }
        }
        // Colour the screen and make the room glow in the same colour.
        tvScreen.setColor("Color", color);
        tvLight.setColor(color.mult(0.9f));
    }

    /**
     * The TV, bed and fridge have power-cut text here; everything else is as usual.
     *
     * @param id what was inspected
     * @return the dialog pages to show for it
     */
    @Override
    protected String[] describe(String id) {
        return switch (id) {
            case "tv" -> new String[] {"The TV is on. No channel. Just colours, one after another.",
                "The same colours, in the same order, again and again."};
            case "bed" -> new String[] {"You are not going back to sleep in the dark."};
            case "fridge" -> new String[] {"The fridge light is off. The pizza will not keep."};
            default -> super.describe(id);
        };
    }

    /**
     * Handles pressing a lamp: the next one in the sequence, or a wrong one.
     *
     * @param id what was inspected
     */
    @Override
    protected void interact(String id) {
        // Everything that is not a lamp behaves as usual.
        if (!id.startsWith("lamp")) {
            super.interact(id);
            return;
        }
        // Once solved, the lamps do nothing more.
        if (solved) {
            return;
        }
        // The ids are "lamp0" to "lamp3", so the fifth character is the lamp number.
        int lamp = id.charAt(4) - '0';
        // The right lamp: move one step along the sequence.
        if (lamp == SEQUENCE[progress]) {
            progress++;
            // That was the last step. Every lamp comes on, and the chapter ends with a fade to
            // white when the dialog is closed.
            if (progress == SEQUENCE.length) {
                solved = true;
                showLamps();
                game.hud().setHint(null);
                game.hud().openDialog("Every lamp comes on at once. The fridge starts to hum.",
                        "...The fridge is humming. Just like the closet did.");
                afterDialog = () -> finish(ColorRGBA.White, 1.0f);
                return;
            }
            // Not finished yet: light this lamp and show the progress.
            showLamps();
            game.hud().setHint("The " + NAMES[lamp] + " lamp comes on.   " + progress + " of "
                    + SEQUENCE.length);
        } else {
            // The wrong lamp: everything goes dark and the player starts again.
            progress = 0;
            showLamps();
            game.hud().setHint(null);
            game.hud().openDialog(
                    "The " + NAMES[lamp] + " lamp flickers, and every lamp goes dark.",
                    "Wrong order. The television knows the right one.");
        }
    }

    /**
     * Camera positions used for automated test screenshots.
     *
     * @return rows of {x, feet y, z, yaw degrees, pitch degrees}
     */
    @Override
    float[][] debugPoses() {
        return new float[][] {{9.9f, 0, 9.4f, 195, -6}, {4.3f, 0, 9.8f, -45, -8},
            {0.65f, 0, 6.3f, 100, -14}, {-0.9f, 0, 7.4f, -20, -10}, {1.0f, 0, 4.95f, 180, 0}};
    }

    /** Test helper: presses the lamps in the right order. */
    @Override
    void debugComplete() {
        for (int lamp : SEQUENCE) {
            interact("lamp" + lamp);
        }
    }
}
