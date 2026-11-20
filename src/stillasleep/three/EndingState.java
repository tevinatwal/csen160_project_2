package stillasleep.three;

import com.jme3.math.ColorRGBA;

/**
 * The closing card: a dark screen with the game's name as a question, and
 * one option, which starts the night over again.
 */
final class EndingState extends Stage {

    /** The big "STILL ASLEEP?" text. */
    private Label title;
    /** The small blinking "Press ENTER to wake up" text. */
    private Label prompt;
    /** Seconds since the card appeared; decides when each line of text shows. */
    private float time;

    /**
     * Creates the ending card. Nothing is shown until the game switches to it.
     *
     * @param game the running game
     */
    EndingState(Game3D game) {
        super(game);
    }

    /** Sets up the dark background and the two pieces of text, both hidden to begin with. */
    @Override
    protected void build() {
        // almost black, with a hint of blue. There is no 3D scenery in this stage
        game.getViewPort().setBackgroundColor(new ColorRGBA(0.02f, 0.02f, 0.05f, 1f));
        body.place(0, 0, 0, 0);
        Hud hud = game.hud();
        // no aiming dot in the middle of the screen: there is nothing to look at
        hud.setCrosshair(false);
        // createLabel takes a text size and a colour (0xRRGGBB): large cream
        // lettering for the title, small pale blue for the prompt
        title = hud.createLabel(96, 0xf4f1dc);
        prompt = hud.createLabel(22, 0xaab3d6);
        title.setText("STILL ASLEEP?");
        prompt.setText("Press ENTER to wake up");
        // update() makes them appear one after the other
        title.setVisible(false);
        prompt.setVisible(false);
    }

    /**
     * Removes the two labels and turns the crosshair back on for the next stage.
     *
     * @param app the running application
     */
    @Override
    protected void cleanup(com.jme3.app.Application app) {
        title.remove();
        prompt.remove();
        game.hud().setCrosshair(true);
        super.cleanup(app);
    }

    /**
     * Shows the text in stages and waits for ENTER.
     *
     * @param tpf seconds since the last frame
     */
    @Override
    public void update(float tpf) {
        time += tpf;
        // work out the middle of the screen, to centre both lines of text on it
        float centre = game.hud().width() / 2;
        body.applyTo(game.getCamera());
        // the title appears after 1.5 seconds of darkness
        title.setCentred(centre, 470, 3);
        title.setVisible(time >= 1.5f);
        // the prompt appears after 3.5 seconds and then blinks: (int) (time * 1.6)
        // goes up by one 1.6 times a second, and the text shows while it is even
        prompt.setCentred(centre, 250, 3);
        prompt.setVisible(time >= 3.5f && (int) (time * 1.6f) % 2 == 0);
        // ENTER only works once the prompt is on screen; it restarts the game
        if (time >= 3.5f && game.controls().wasPressed("confirm")) {
            game.playAgain();
        }
    }

    /** Skips the wait so the prompt is showing and ENTER works straight away (tests only). */
    @Override
    void debugComplete() {
        time = 5f;
    }
}
