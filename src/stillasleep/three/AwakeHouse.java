package stillasleep.three;

import com.jme3.math.ColorRGBA;

/**
 * The last scene. After lying down in the bed at the end of the street, the
 * player wakes in their own bed, in their own room, in the morning. Nothing
 * says it is a dream. After a little while the bedside clock quietly loses a
 * minute, and the scene fades out.
 */
final class AwakeHouse extends HouseStage {

    /** Seconds since the opening dialog was closed, or -1 while it is still open. */
    private float sinceDialog = -1;

    /**
     * Sets up the house in daylight.
     *
     * @param game the running game
     */
    AwakeHouse(Game3D game) {
        super(game);
        day = true;
    }

    /** Builds the ordinary morning house and wakes the player up in bed. */
    @Override
    protected void buildChapter() {
        buildBedroomAndCloset();
        buildHallwayAndKitchen();
        addAmbient();
        wake("I think I'm awake.", "Everything is fine.", "Probably...");
        // When the player closes that dialog, start the timer that ends the game.
        afterDialog = () -> sinceDialog = 0;
    }

    /**
     * Calm morning text for a few things; everything else is as usual.
     *
     * @param id what was inspected
     * @return the dialog pages to show for it
     */
    @Override
    protected String[] describe(String id) {
        return switch (id) {
            case "bed" -> new String[] {"Your own bed. Still warm."};
            case "clock" -> new String[] {shownClock + " AM."};
            case "closet" -> new String[] {"Clothes. A floor."};
            case "mirror" -> new String[] {"There you are."};
            default -> super.describe(id);
        };
    }

    /**
     * Counts the seconds after the dialog, changes the clock, then fades out.
     *
     * @param tpf seconds since the last frame
     */
    @Override
    protected void chapterUpdate(float tpf) {
        // The timer has not started yet.
        if (sinceDialog < 0) {
            return;
        }
        sinceDialog += tpf;
        // After 9 seconds the clock slips back from 7:30 to 7:29: a hint that this may still be
        // a dream.
        if (sinceDialog > 9f) {
            clockOverride = "7:29";
        }
        // After 14 seconds, fade slowly to black and go to the ending.
        if (sinceDialog > 14f) {
            finish(ColorRGBA.Black, 2.5f);
        }
    }

    /**
     * Camera positions used for automated test screenshots.
     *
     * @return rows of {x, feet y, z, yaw degrees, pitch degrees}
     */
    @Override
    float[][] debugPoses() {
        return new float[][] {{3.1f, 0, 1.6f, 120, -5}};
    }

    /** Test helper: jumps the timer to just before the fade-out. */
    @Override
    void debugComplete() {
        sinceDialog = 13.5f;
    }
}
