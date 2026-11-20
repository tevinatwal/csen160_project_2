package stillasleep.three;

import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Chapter 3: the house looks normal, but outside the bedroom the hallway has
 * grown into a maze of identical corridors. The lit ceiling lamps mark the
 * way to the front door; the dark corridors loop back on themselves.
 *
 * <p>The front door has a lock with three dials. The three digits of the code
 * are written on notes taped to the maze walls, so the player has to explore
 * the side corridors as well as find the door.
 */
final class MazeHouse extends HouseStage {

    /**
     * The map of the maze, seen from above with north at the top. Each
     * character is one square cell of floor: '#' is wall, '.' corridor, 'S'
     * the cell outside the bedroom, 'X' the exit, 'L' a lit lamp. A position
     * in it is given as (column, row), counting from 0 at the top left.
     */
    static final String[] MAZE = {
        "#####S#####",
        "#L.L#L....#",
        "#.#.#.###.#",
        "#L#L.L#...#",
        "#.#####.###",
        "#L....#...#",
        "#.###.###.#",
        "#L.L#.....#",
        "###X#######",
    };

    /** The width and depth of one maze cell, in metres. */
    private static final float CELL = 2f;
    /** The column of the 'S' cell, the one just outside the bedroom door. */
    private static final int ENTRY_COL = 5;
    /**
     * The x of the maze's west edge. It is worked out backwards so that the
     * middle of the 'S' cell lines up with the middle of the bedroom door (x = 3.65).
     */
    private static final float X0 = 3.65f - CELL / 2 - ENTRY_COL * CELL;
    /** The z of the maze's north edge, which is where the hallway starts. */
    private static final float Z0 = 4.15f;

    /** The door code. Each digit is written on a note somewhere in the maze. */
    static final int[] CODE = {4, 1, 7};

    /** The digits currently showing on the lock's three dials. */
    private final int[] entry = new int[CODE.length];
    /** Which dial is selected (0, 1 or 2). */
    private int slot;
    /** True while the lock is on screen and the keys turn its dials. */
    private boolean keypad;
    /** True once the player has looked at the lock, so its introduction is shown only once. */
    private boolean keypadSeen;
    /** True once the right code has been entered. */
    private boolean unlocked;
    /** True once the player has stepped into the maze (for a one-off line of dialog). */
    private boolean entered;
    /** True once the "you are lost" dialog has been shown (it is shown only once). */
    private boolean lostLine;

    /**
     * Creates the chapter. The bedside clock runs backwards here, as a sign
     * that this is still a dream.
     *
     * @param game the running game
     */
    MazeHouse(Game3D game) {
        super(game);
        clockBackwards = true;
    }

    /**
     * Tells whether a maze cell is floor rather than wall. Anything outside
     * the map counts as wall.
     *
     * @param col column in the maze
     * @param row row in the maze
     * @return true if the cell can be walked through
     */
    static boolean isOpen(int col, int row) {
        return row >= 0 && row < MAZE.length && col >= 0 && col < MAZE[0].length()
                && MAZE[row].charAt(col) != '#';
    }

    /**
     * Checks the maze has a route from the bedroom door to the exit. This is
     * a "breadth-first search": it spreads out from the start one step at a
     * time, so the first time it reaches a cell it has found the shortest way there.
     *
     * @return number of steps on the shortest route, or -1 if there is none
     */
    static int shortestRoute() {
        int rows = MAZE.length;
        int cols = MAZE[0].length();
        // distance[row][col] = steps from the start, or -1 for "not reached yet"
        int[][] distance = new int[rows][cols];
        for (int[] row : distance) {
            java.util.Arrays.fill(row, -1);
        }
        // the queue holds cells, as {col, row}, that have been reached but whose
        // neighbours have not been looked at yet. It begins with just the start cell
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {ENTRY_COL, 0});
        distance[0][ENTRY_COL] = 0;
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            // reached the exit: its distance is the answer
            if (MAZE[cell[1]].charAt(cell[0]) == 'X') {
                return distance[cell[1]][cell[0]];
            }
            // try the four neighbours (east, west, south, north). Any open cell not
            // seen before is one step further than this one, and joins the queue
            int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] step : steps) {
                int c = cell[0] + step[0];
                int r = cell[1] + step[1];
                if (isOpen(c, r) && distance[r][c] < 0) {
                    distance[r][c] = distance[cell[1]][cell[0]] + 1;
                    queue.add(new int[] {c, r});
                }
            }
        }
        // ran out of cells without finding the exit
        return -1;
    }

    /** Builds the bedroom, then the maze cell by cell, then the things hung on its walls. */
    @Override
    protected void buildChapter() {
        buildBedroomAndCloset();
        addAmbient();
        Material hall = wallpaper('h');
        // go through the map and build every cell that is not solid wall
        for (int row = 0; row < MAZE.length; row++) {
            for (int col = 0; col < MAZE[0].length(); col++) {
                if (!isOpen(col, row)) {
                    continue;
                }
                // the cell's edges in metres: x0 west, x1 east, z0 north, z1 south
                float x0 = X0 + col * CELL;
                float z0 = Z0 + row * CELL;
                float x1 = x0 + CELL;
                float z1 = z0 + CELL;
                floorPiece('h', x0, z0, x1, z1);
                ceilingPiece(x0, z0, x1, z1);
                char kind = MAZE[row].charAt(col);
                // north wall. The start cell's north wall has the bedroom doorway in
                // it (x 3.2 to 4.1), so it is built in three parts with only the
                // strip above the door in the middle
                if (kind == 'S') {
                    wallPiece(hall, NORTH, z0, x0, 3.2f, 0, CEILING);
                    wallPiece(hall, NORTH, z0, 3.2f, 4.1f, DOOR_HEIGHT, CEILING);
                    wallPiece(hall, NORTH, z0, 4.1f, x1, 0, CEILING);
                } else if (!isOpen(col, row - 1)) {
                    wallPiece(hall, NORTH, z0, x0, x1, 0, CEILING);
                }
                // the other three sides get a wall wherever the neighbouring cell is
                // not open. Two open cells side by side have no wall between them
                if (!isOpen(col, row + 1)) {
                    wallPiece(hall, SOUTH, z1, x0, x1, 0, CEILING);
                }
                if (!isOpen(col - 1, row)) {
                    wallPiece(hall, WEST, x0, z0, z1, 0, CEILING);
                }
                if (!isOpen(col + 1, row)) {
                    wallPiece(hall, EAST, x1, z0, z1, 0, CEILING);
                }
                // lamps, hung in the middle of the cell. 'L' and 'S' cells get a
                // lamp that is switched on (with a real light); a third of the
                // other cells get a lamp that is off, so lamps alone give nothing away
                float cx = (x0 + x1) / 2;
                float cz = (z0 + z1) / 2;
                if (kind == 'L' || kind == 'S') {
                    ceilingLamp(cx, cz, true);
                    scene.addLight(new PointLight(new Vector3f(cx, 2.1f, cz),
                            new ColorRGBA(1f, 0.82f, 0.56f, 1f).mult(0.85f), 4.6f));
                } else if ((col + row) % 3 == 0) {
                    ceilingLamp(cx, cz, false);
                }
                // the exit cell: the front door on its south wall, a "spot" so the
                // player can press E on it, a doormat, and a light to draw the eye
                if (kind == 'X') {
                    hangFrontDoor(SOUTH, z1, cx);
                    spots.add(new Spot("frontdoor", new Vector3f(cx, 1.2f, z1 - 0.03f)));
                    build.model(scene, "rugDoormat", cx, 0.004f, z1 - 0.5f, 0, 1);
                    scene.addLight(new PointLight(new Vector3f(cx, 1.6f, z1 - 0.8f),
                            new ColorRGBA(1f, 0.9f, 0.7f, 1f).mult(0.8f), 3.5f));
                }
            }
        }

        // the same few landmarks, repeated, so every corridor looks like the last one
        // (mirrors: each entry is {column, row, which wall of that cell}. "fixed" is
        // the x of that wall: the cell's west edge, plus one cell for an east wall)
        int[][] mirrors = {{9, 2, EAST}, {7, 4, WEST}, {1, 6, WEST}, {5, 6, EAST}, {3, 2, EAST}};
        for (int[] m : mirrors) {
            float cz = Z0 + m[1] * CELL + CELL / 2;
            float fixed = X0 + m[0] * CELL + (m[2] == EAST ? CELL : 0);
            hangCrop('h', "mirror", 44, 22, 84, 88, m[2], fixed, cz, false);
            spots.add(new Spot("mazemirror", new Vector3f(fixed, 1.4f, cz)));
        }
        // (family photos: each entry is {column, row}, hung on the cell's north wall)
        int[][] photos = {{2, 1}, {7, 1}, {8, 5}, {3, 5}, {7, 7}, {2, 7}, {8, 3}};
        for (int[] p : photos) {
            hangCrop('h', "photos", 53, 37, 91, 67, NORTH, Z0 + p[1] * CELL,
                    X0 + p[0] * CELL + CELL / 2, false);
        }
        // (fake front doors: pictures of the door on walls that lead nowhere)
        int[][] fakeDoors = {{9, 1, EAST}, {9, 7, EAST}, {1, 1, WEST}};
        for (int[] d : fakeDoors) {
            float cz = Z0 + d[1] * CELL + CELL / 2;
            float fixed = X0 + d[0] * CELL + (d[2] == EAST ? CELL : 0);
            hangFrontDoor(d[2], fixed, cz);
            spots.add(new Spot("fakedoor", new Vector3f(fixed, 1.2f, cz)));
        }
        // the three notes with the door code, each {column, row} on the cell's
        // north wall. Each has a spot so it can be read, and a small light of its
        // own so it can be seen in a dark corridor
        int[][] notes = {{8, 1}, {9, 5}, {6, 7}};
        String[] order = {"1st", "2nd", "3rd"};
        for (int i = 0; i < notes.length; i++) {
            float cx = X0 + notes[i][0] * CELL + CELL / 2;
            float wall = Z0 + notes[i][1] * CELL;
            hang(NORTH, wall, cx, 1.75f, 0.52f, 0.39f, build.note(order[i] + ": " + CODE[i]),
                    false);
            spots.add(new Spot("note" + i, new Vector3f(cx, 1.55f, wall + 0.03f)));
            scene.addLight(new PointLight(new Vector3f(cx, 1.7f, wall + 0.7f),
                    new ColorRGBA(0.9f, 0.85f, 0.7f, 1f).mult(0.5f), 2.2f));
        }
        // three identical pot plants in the corners of cells (9,3), (7,5) and (1,1)
        build.model(scene, "pottedPlant", X0 + 9 * CELL + 1.6f, 0, Z0 + 3 * CELL + 0.4f, 0, 1);
        build.model(scene, "pottedPlant", X0 + 7 * CELL + 0.4f, 0, Z0 + 5 * CELL + 1.6f, 0, 1);
        build.model(scene, "pottedPlant", X0 + 1 * CELL + 0.4f, 0, Z0 + 1 * CELL + 0.4f, 0, 1);

        // the lines shown as the player wakes up in bed
        wake("Again?", "Okay. Bed. Bedroom. Awake. Definitely awake this time.");
    }

    /**
     * Gives the text shown when the player examines something. The maze's own
     * things are answered here; anything else uses the usual house text.
     *
     * @param id name of the thing being looked at
     * @return the dialog pages to show for it
     */
    @Override
    protected String[] describe(String id) {
        return switch (id) {
            case "frontdoor" -> new String[] {"Click.", "It's unlocked."};
            case "note0" -> new String[] {"A note taped to the wall. \"1st: " + CODE[0] + "\""};
            case "note1" -> new String[] {"A note taped to the wall. \"2nd: " + CODE[1] + "\""};
            case "note2" -> new String[] {"A note taped to the wall. \"3rd: " + CODE[2] + "\""};
            case "fakedoor" -> new String[] {
                    "It looks like the front door. It's painted on the wall."};
            case "mazemirror" -> new String[] {
                    "That's you. You don't remember a mirror here. Or there. Or back there."};
            case "bed" -> new String[] {"You JUST woke up. You're almost sure."};
            default -> super.describe(id);
        };
    }

    /**
     * Called when the player presses E on something. Everything except the
     * front door behaves as usual; the front door opens the code lock.
     *
     * @param id name of the thing being used
     */
    @Override
    protected void interact(String id) {
        if (!"frontdoor".equals(id)) {
            super.interact(id);
            return;
        }
        if (unlocked) {
            // code already entered: show "Click. It's unlocked." and, when that
            // dialog is closed, finish the chapter with a fade to white
            super.interact(id);
            afterDialog = () -> finish(ColorRGBA.White, 1.0f);
        } else if (!keypadSeen) {
            // first look at the door: explain the lock, then show the dials
            keypadSeen = true;
            game.hud().openDialog("The front door. There is a lock on it with three dials.",
                    "Somebody in this house writes things down. The numbers will be on the walls.");
            afterDialog = this::openKeypad;
        } else {
            // every later look goes straight to the dials
            openKeypad();
        }
    }

    /** Shows the lock. "busy" stops the player walking away while the keys turn the dials. */
    private void openKeypad() {
        keypad = true;
        busy = true;
        showDials();
    }

    /** Hides the lock and lets the player move again. */
    private void closeKeypad() {
        keypad = false;
        busy = false;
        game.hud().setBanner(null, null, null);
    }

    /** Draws the three dials as text, with square brackets around the selected one. */
    private void showDials() {
        // builds a string such as " 4 [1] 7 "
        StringBuilder dials = new StringBuilder();
        for (int i = 0; i < entry.length; i++) {
            dials.append(i == slot ? "[" + entry[i] + "]" : " " + entry[i] + " ");
        }
        game.hud().setBanner(dials.toString(),
                "W / S: turn the dial      A / D: next dial      E: try it      Enter: step back",
                null);
    }

    /** Reads the keys while the lock is on screen. */
    private void updateKeypad() {
        Controls controls = game.controls();
        // W and S turn the selected dial up or down. "% 10" wraps 9 round to 0,
        // and adding 9 is the same as taking away 1 without ever going below 0
        if (controls.wasPressed("forward")) {
            entry[slot] = (entry[slot] + 1) % 10;
        }
        if (controls.wasPressed("back")) {
            entry[slot] = (entry[slot] + 9) % 10;
        }
        // A and D (or the turn keys) pick the dial to the left or right, wrapping
        // round at the ends in the same way
        if (controls.wasPressed("left") || controls.wasPressed("turnLeft")) {
            slot = (slot + entry.length - 1) % entry.length;
        }
        if (controls.wasPressed("right") || controls.wasPressed("turnRight")) {
            slot = (slot + 1) % entry.length;
        }
        showDials();
        if (controls.wasPressed("interact")) {
            // E tries the code. If it is right the door is used again, which this
            // time takes the "unlocked" branch of interact()
            closeKeypad();
            if (java.util.Arrays.equals(entry, CODE)) {
                unlocked = true;
                interact("frontdoor");
            } else {
                game.hud().openDialog("Nothing. That is not the code.");
            }
        } else if (controls.wasPressed("confirm")) {
            // ENTER steps back from the door without trying the code
            closeKeypad();
        }
    }

    /**
     * Runs this chapter's own rules each frame: the lock when it is open, and
     * otherwise two lines of dialog set off by where the player walks.
     *
     * @param tpf seconds since the last frame
     */
    @Override
    protected void chapterUpdate(float tpf) {
        if (keypad) {
            updateKeypad();
            return;
        }
        // z > 4.7 means the player has just walked out of the bedroom door
        if (!entered && body.position.z > 4.7f) {
            entered = true;
            game.hud().openDialog("Was the hallway always this long?");
        }
        // the south-east corner of the maze (column 8 onwards, row 6 onwards) is dark
        // and a long way off the lit route. Getting there earns the hint about the lamps
        if (!lostLine && body.position.x > X0 + 8 * CELL && body.position.z > Z0 + 6 * CELL) {
            lostLine = true;
            game.hud().openDialog("You've been here before.", "...Haven't you?",
                    "Some of the lights are on. "
                            + "Somebody left them on, all the way to the front door.");
        }
    }

    /**
     * Viewpoints for automated test screenshots: the bedroom, the start of the
     * maze, and three places inside it.
     *
     * @return rows of {x, feet y, z, yaw degrees, pitch degrees}
     */
    @Override
    float[][] debugPoses() {
        return new float[][] {{3.65f, 0, 3.0f, 90, 0}, {3.65f, 0, 5.2f, 90, 0},
                {X0 + 5.5f * CELL, 0, Z0 + 1.5f * CELL, 0, 0},
                {X0 + 1.5f * CELL, 0, Z0 + 2.2f * CELL, 90, 0},
                {X0 + 3.5f * CELL, 0, Z0 + 7.3f * CELL, 90, 0}};
    }

    /** Unlocks the door and stands the player in front of it (tests only). */
    @Override
    void debugComplete() {
        unlocked = true;
        body.place(X0 + 3.5f * CELL, 0.02f, Z0 + 8.3f * CELL, FastMath.HALF_PI);
    }
}
