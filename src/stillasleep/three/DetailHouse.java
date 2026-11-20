package stillasleep.three;

import com.jme3.math.ColorRGBA;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The "spot what's wrong" chapters. The house looks right, but one or more
 * details are off. Inspecting every wrong detail breaks the dream.
 * The first version has four fairly obvious mistakes spread around the house;
 * the second has a single subtle one.
 */
final class DetailHouse extends HouseStage {

    /** True for the harder version with a single subtle mistake. */
    private final boolean subtle;
    /** The wrong details the player has already inspected. */
    private final Set<String> found = new HashSet<>();

    /**
     * Chooses which details are wrong before the house is built.
     *
     * @param game   the running game
     * @param subtle false for four obvious mistakes, true for one subtle mistake
     */
    DetailHouse(Game3D game, boolean subtle) {
        super(game);
        this.subtle = subtle;
        if (subtle) {
            // Subtle version: the hallway mirror has no reflection in it.
            odd.add("mirror");
        } else {
            // Obvious version: the chair is on the ceiling, the poster and TV are upside down
            // and the clock runs backwards.
            odd.addAll(List.of("chair", "poster", "clock", "tv"));
            clockBackwards = true;
        }
    }

    /** Builds the whole house and wakes the player with a line that fits the version. */
    @Override
    protected void buildChapter() {
        buildBedroomAndCloset();
        buildHallwayAndKitchen();
        addAmbient();
        if (subtle) {
            wake("Still dreaming. Fine.", "Everything looks right this time. Look closer.");
        } else {
            wake("You sit up. Bed. Bedroom. No chess board humming.",
                    "Dreams get the details wrong. So check the details.");
        }
    }

    /**
     * Gives the wrong details their own text; everything else is as usual.
     *
     * @param id what was inspected
     * @return the dialog pages to show for it
     */
    @Override
    protected String[] describe(String id) {
        // Things that are not wrong keep their normal text.
        if (!odd.contains(id)) {
            return super.describe(id);
        }
        return switch (id) {
            case "chair" -> new String[] {"The chair is on the ceiling.",
                "Chairs do not go on the ceiling."};
            case "poster" -> new String[] {
                "The cat is hanging upside down. Or the whole poster is. Either way: wrong."};
            case "clock" -> new String[] {shownClock + " AM. It is counting backwards."};
            case "tv" -> new String[] {"The TV is upside down.",
                "It was the right way up yesterday. You would have noticed."};
            // The only other wrong detail is the mirror.
            default -> new String[] {"The hallway. The wallpaper. The photos behind you. No you.",
                "Mirrors are supposed to have you in them."};
        };
    }

    /**
     * Shows the text as usual, and also keeps count of the wrong details found.
     *
     * @param id what was inspected
     */
    @Override
    protected void interact(String id) {
        super.interact(id);
        // Stop unless this is a wrong detail that is being found for the first time. (found.add
        // gives false if it was already in the set.)
        if (!odd.contains(id) || !found.add(id)) {
            return;
        }
        // That was the last one. When the dialog closes, show one more line, and when that
        // closes, fade to white and move on.
        if (found.size() == odd.size()) {
            game.hud().setHint(null);
            afterDialog = () -> {
                game.hud().openDialog("...Still dreaming.");
                afterDialog = () -> finish(ColorRGBA.White, 0.9f);
            };
        } else {
            // Otherwise show how many have been found so far.
            game.hud().setHint("Things that are wrong: " + found.size());
        }
    }

    /**
     * Camera positions used for automated test screenshots.
     *
     * @return rows of {x, feet y, z, yaw degrees, pitch degrees}
     */
    @Override
    float[][] debugPoses() {
        return subtle ? new float[][] {{2.0f, 0, 4.6f, 90, 0}}
                : new float[][] {{8.6f, 0, 3.7f, -90, 15}, {3.4f, 0, 1.9f, -80, 5}};
    }

    /** Test helper: inspects every wrong detail as if the player had found them all. */
    @Override
    void debugComplete() {
        for (String id : odd) {
            interact(id);
        }
    }
}
