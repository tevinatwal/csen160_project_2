package stillasleep.three;

import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;

/**
 * Chapter 0: the real house at 3:07 AM. Something is humming in the closet,
 * and the hole in its floor is where the dream begins.
 */
final class RealHouse extends HouseStage {

    /**
     * Sets up the house with a hole in the closet floor.
     *
     * @param game the running game
     */
    RealHouse(Game3D game) {
        super(game);
        closetHole = true;
    }

    /** Builds the whole house at night and shows the opening lines. */
    @Override
    protected void buildChapter() {
        buildBedroomAndCloset();
        buildHallwayAndKitchen();
        addAmbient();
        // Start in the middle of the bedroom. A yaw of PI looks west; the extra 0.45 radians
        // turns toward the closet door.
        body.place(2.5f, 0.02f, 2.9f, FastMath.PI + 0.45f);
        // The opening lines are different if the game has already been played through.
        if (game.timesPlayed() == 0) {
            game.hud().openDialog("Can't sleep.",
                    "There's that humming again... coming from the closet.");
        } else {
            game.hud().openDialog("Can't sleep.", "...Haven't you done this already?",
                    "The humming. The closet. Of course.");
        }
        // Show the controls for the first 14 seconds.
        game.hud().setHint("Mouse: look      W A S D: walk      E: interact      Esc: quit");
        hintSeconds = 14;
    }

    /**
     * The closet and bed have their own text here; everything else is as usual.
     *
     * @param id what was inspected
     * @return the dialog pages to show for it
     */
    @Override
    protected String[] describe(String id) {
        return switch (id) {
            case "closet" -> new String[] {"The closet door is humming. Closets shouldn't hum.",
                "...There's a hole in the floor. That was definitely not there before."};
            case "bed" -> new String[] {"Not happening. Not with that noise."};
            default -> super.describe(id);
        };
    }

    /**
     * Ends the chapter once the player has dropped into the hole.
     *
     * @param tpf seconds since the last frame
     */
    @Override
    protected void chapterUpdate(float tpf) {
        // 3 metres below the floor: fade through dark purple into the dream.
        if (body.position.y < -3f) {
            finish(new ColorRGBA(0.1f, 0.03f, 0.19f, 1f), 0.7f);
        }
    }

    /**
     * Camera positions used for automated test screenshots.
     *
     * @return rows of {x, feet y, z, yaw degrees, pitch degrees}
     */
    @Override
    float[][] debugPoses() {
        return new float[][] {{2.4f, 0, 3.2f, -60, -5}, {3.4f, 0, 2.6f, 190, -8},
            {-0.5f, 0, 4.95f, 0, 0}, {6.4f, 0, 2.2f, 10, -12}, {8.6f, 0, 3.7f, -90, -40}};
    }

    /** Test helper: drops the player straight into the closet hole. */
    @Override
    void debugComplete() {
        // The middle of the hole in the closet floor.
        body.place(-0.85f, 0.02f, 0.62f, 0);
    }
}
