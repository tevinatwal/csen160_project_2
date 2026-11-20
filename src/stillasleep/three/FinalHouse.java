package stillasleep.three;

import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The last house. It is morning, the clock runs forwards, and nothing at all
 * is wrong. The front door is finally unlocked. Whether this is really
 * waking up is left for the player to decide.
 */
final class FinalHouse extends HouseStage {

    /** The things that were wrong in earlier dreams. All of them must be checked. */
    private static final List<String> CHECKS = List.of("clock", "mirror", "chess", "poster",
            "closet", "tv");

    /** The things from {@link #CHECKS} that the player has inspected so far. */
    private final Set<String> checked = new HashSet<>();
    /** True once the player has tried the unlocked front door for the first time. */
    private boolean doorTried;

    /**
     * Sets up the house in daylight with nothing wrong in it.
     *
     * @param game the running game
     */
    FinalHouse(Game3D game) {
        super(game);
        day = true;
    }

    /** Builds the whole house and wakes the player with the instructions. */
    @Override
    protected void buildChapter() {
        buildBedroomAndCloset();
        buildHallwayAndKitchen();
        addAmbient();
        wake("Morning light.", "No humming. No hole. The clock is ticking forwards.",
                "...Check anyway. Everything that has ever been wrong. Then the front door.");
    }

    /**
     * Reassuring text for the things to check, and the front door's three states.
     *
     * @param id what was inspected
     * @return the dialog pages to show for it
     */
    @Override
    protected String[] describe(String id) {
        return switch (id) {
            case "bed" -> new String[] {"You are not tired. For the first time in a while."};
            case "clock" -> new String[] {
                "7:30 AM. Ticking forwards. You watch it for a full minute to be sure."};
            case "mirror" -> new String[] {"There you are. Looking a little tired."};
            case "closet" -> new String[] {"Clothes. A floor. An ordinary floor."};
            case "chess" -> new String[] {"Every piece where it belongs. You count them twice."};
            case "poster" -> new String[] {"\"HANG IN THERE.\" Right way up."};
            case "tv" -> new String[] {"The right way up. Off. Just a TV."};
            // The front door: "not yet" until everything is checked, then "unlocked" on the
            // first try, then it opens on the second try.
            case "frontdoor" -> checked.size() < CHECKS.size()
                    ? new String[] {"Not yet.",
                        "You promised yourself you would check everything first."}
                    : doorTried
                    ? new String[] {"You open the door."}
                    : new String[] {"Unlocked.", "There is sunlight under the door. "
                            + "Press E again to go outside."};
            default -> super.describe(id);
        };
    }

    /**
     * Shows the text as usual, ticks things off the list and handles the front door.
     *
     * @param id what was inspected
     */
    @Override
    protected void interact(String id) {
        super.interact(id);
        // The first time one of the listed things is inspected, tick it off and show the
        // progress.
        if (CHECKS.contains(id) && checked.add(id)) {
            boolean done = checked.size() == CHECKS.size();
            game.hud().setHint(done ? "Everything checks out. The front door."
                    : "Checked: " + checked.size() + " of " + CHECKS.size());
        }
        // Front door with everything checked: the first press only unlocks it, the second press
        // ends the chapter with a fade to white once the dialog is closed.
        if ("frontdoor".equals(id) && checked.size() == CHECKS.size()) {
            if (doorTried) {
                afterDialog = () -> finish(ColorRGBA.White, 1.6f);
            }
            doorTried = true;
        }
    }

    /**
     * Camera positions used for automated test screenshots.
     *
     * @return rows of {x, feet y, z, yaw degrees, pitch degrees}
     */
    @Override
    float[][] debugPoses() {
        return new float[][] {{0.3f, 0, 6.6f, 185, -22}, {0.0f, 0, 7.0f, 0, -16},
            {5.4f, 0, 9.6f, 190, -8}, {4.6f, 0, 7.4f, 20, -12}, {7.0f, 0, 9.2f, 45, -8},
            {1.6f, 0, 2.6f, 135, -18}, {9.3f, 0, 4.95f, 0, 0}};
    }

    /** Test helper: ticks everything off and stands the player at the front door. */
    @Override
    void debugComplete() {
        checked.addAll(CHECKS);
        // Just inside the front door, looking east at it.
        body.place(10.0f, 0.02f, 4.95f, 0);
        body.pitch = -0.1f * FastMath.DEG_TO_RAD;
    }
}
