package stillasleep.three;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;

/**
 * Chapter 5: the house is the right shape again, and only one thing is off:
 * somebody has moved the pieces on the chess board. The player has to put
 * every piece back on its starting square. The untouched black side of the
 * board shows what the white side should look like.
 */
final class ChessHouse extends HouseStage {

    /**
     * The starting position with five white pieces out of place. The format is
     * explained in {@link ChessSet}: eight rows, black's side first.
     */
    static final String[] SCRAMBLED = {
        "rnbqkbnr", "pppppppp", "........", "........",
        "....P...", ".P...N..", "P.PP.PPP", "BNRKQB.R",
    };

    /** True while the camera is over the board and the keys move the cursor. */
    private boolean inPuzzle;
    /** True once the "somebody has moved the pieces" dialog has been shown. */
    private boolean introduced;
    /** True once every piece is back where it belongs. */
    private boolean solved;

    /**
     * Sets up an ordinary night house; only the chess board is different.
     *
     * @param game the running game
     */
    ChessHouse(Game3D game) {
        super(game);
    }

    /**
     * Tells the shared house code to set the board up scrambled.
     *
     * @return the scrambled arrangement
     */
    @Override
    protected String[] chessLayout() {
        return SCRAMBLED;
    }

    /** Builds the whole house and wakes the player up in bed. */
    @Override
    protected void buildChapter() {
        buildBedroomAndCloset();
        buildHallwayAndKitchen();
        addAmbient();
        wake("...", "The hallway was never that long. That was a dream. This is real.",
                "Probably.");
    }

    /**
     * The bed and mirror have their own text here; everything else is as usual.
     *
     * @param id what was inspected
     * @return the dialog pages to show for it
     */
    @Override
    protected String[] describe(String id) {
        return switch (id) {
            case "bed" -> new String[] {"Not yet. Something in the house is bothering you."};
            case "mirror" -> new String[] {"Just one mirror. Just one you. Good."};
            default -> super.describe(id);
        };
    }

    /**
     * Starts the chess puzzle when the board is inspected.
     *
     * @param id what was inspected
     */
    @Override
    protected void interact(String id) {
        // Everything except the chess board behaves as usual.
        if (!"chess".equals(id)) {
            super.interact(id);
            return;
        }
        // The board: once solved it only shows a line. The first time, explain what is wrong
        // and start the puzzle when the dialog closes. After that, go straight in.
        if (solved) {
            game.hud().openDialog("Every piece is where it should be.");
        } else if (!introduced) {
            introduced = true;
            game.hud().openDialog("The chess set. Somebody has moved the pieces.",
                    "They were all lined up when you went to bed. Put them back.");
            afterDialog = this::enterPuzzle;
        } else {
            enterPuzzle();
        }
    }

    /** Switches from walking around to moving pieces on the board. */
    private void enterPuzzle() {
        // Stop the player walking and let this chapter aim the camera itself.
        inPuzzle = true;
        busy = true;
        ownCamera = true;
        // Show the selection square. Moving it by (0, 0) just puts it on its square.
        chess.setCursorVisible(true);
        chess.moveCursor(0, 0);
        // Hold the camera 62 cm above the board and 34 cm to the south of it, looking down at
        // the middle.
        Vector3f centre = chess.centre();
        game.getCamera().setLocation(centre.add(0, 0.62f, 0.34f));
        game.getCamera().lookAt(centre.add(0, 0, -0.03f), Vector3f.UNIT_Y);
        // No crosshair while moving pieces. Setting hintSeconds to 0 keeps the list of keys on
        // screen.
        Hud hud = game.hud();
        hud.setCrosshair(false);
        hud.setHint("W A S D: move      E: pick up / put down      Enter: step back");
        hintSeconds = 0;
    }

    /** Goes back from the board to walking around. */
    private void leavePuzzle() {
        inPuzzle = false;
        busy = false;
        ownCamera = false;
        chess.setCursorVisible(false);
        game.hud().setCrosshair(true);
        game.hud().setHint(null);
    }

    /**
     * Reads the keys while the chess puzzle is open.
     *
     * @param tpf seconds since the last frame
     */
    @Override
    protected void chapterUpdate(float tpf) {
        // Outside the puzzle there is nothing to do here.
        if (!inPuzzle) {
            return;
        }
        Controls controls = game.controls();
        // Move the selection square one square per key press. "Forward" is row -1: away from
        // the camera, toward the black pieces.
        if (controls.wasPressed("forward")) {
            chess.moveCursor(0, -1);
        }
        if (controls.wasPressed("back")) {
            chess.moveCursor(0, 1);
        }
        if (controls.wasPressed("left") || controls.wasPressed("turnLeft")) {
            chess.moveCursor(-1, 0);
        }
        if (controls.wasPressed("right") || controls.wasPressed("turnRight")) {
            chess.moveCursor(1, 0);
        }
        // E picks a piece up or puts it down. After that, check whether the board is back in
        // the starting position.
        if (controls.wasPressed("interact")) {
            chess.pickUpOrPutDown();
            if (chess.matches(ChessSet.START)) {
                solve();
                return;
            }
        }
        // Enter steps back from the board, but not while a piece is in the hand.
        if (controls.wasPressed("confirm") && !chess.isHolding()) {
            leavePuzzle();
        }
    }

    /** Called when the puzzle is solved: leaves the board and ends the chapter. */
    private void solve() {
        solved = true;
        leavePuzzle();
        game.hud().openDialog("There. Every piece where it belongs.",
                "...So why is the board humming?");
        // When that dialog is closed, fade to white and move on.
        afterDialog = () -> finish(ColorRGBA.White, 1.0f);
    }

    /**
     * Camera positions used for automated test screenshots.
     *
     * @return rows of {x, feet y, z, yaw degrees, pitch degrees}
     */
    @Override
    float[][] debugPoses() {
        return new float[][] {{8.6f, 0, 3.7f, -90, -42}, {7.4f, 0, 3.3f, -20, -25}};
    }

    /** Test helper: solves the puzzle straight away. */
    @Override
    void debugComplete() {
        if (!solved) {
            solve();
        }
    }
}
