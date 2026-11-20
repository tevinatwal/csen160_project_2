package stillasleep.three;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.scene.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * One playable 3D scene (the house, a level). Owns its own scene graph,
 * collision boxes and player, and is swapped in and out by {@link Game3D}.
 *
 * <p>A stage is a jMonkeyEngine "app state": a piece of the game that the
 * engine can switch on and off. When {@link Game3D} attaches a stage, the
 * engine calls {@link #initialize}; when it detaches the stage, the engine
 * calls {@link #cleanup}. Every chapter of the game is a subclass of this
 * class.
 */
abstract class Stage extends BaseAppState {

    /** The running game, used to reach the shared helpers (HUD, controls, builder). */
    protected final Game3D game;
    /** Parent of everything this stage draws. Removing it removes the whole scene. */
    protected final Node scene = new Node("stage");
    /** Every box the player cannot walk through (walls, floors, furniture). */
    protected final List<Aabb> solids = new ArrayList<>();
    /** The player: position, view direction and movement. */
    protected final PlayerBody body = new PlayerBody();

    /**
     * Remembers the game. The scene itself is not built until the engine
     * calls {@link #initialize}.
     *
     * @param game the running game
     */
    protected Stage(Game3D game) {
        this.game = game;
    }

    /** Creates the scene's geometry, lights and collision boxes. */
    protected abstract void build();

    /**
     * Called once by the engine when this stage is attached. Builds the scene
     * and shows it.
     *
     * @param app the running application (not used; {@link #game} is the same object)
     */
    @Override
    protected void initialize(Application app) {
        // Hide any text left on screen by the previous stage.
        game.hud().clear();

        // Let the subclass create its rooms, lights and collision boxes.
        build();

        // Nothing is drawn until it hangs somewhere under the engine's root
        // node, so attach the finished scene there.
        game.getRootNode().attachChild(scene);

        // Put the camera at the player's eyes right away, so the first frame
        // is not drawn from the wrong place.
        body.applyTo(game.getCamera());
    }

    /**
     * Called once by the engine when this stage is detached. Takes the scene
     * off the screen so the next stage starts with an empty world.
     *
     * @param app the running application (not used)
     */
    @Override
    protected void cleanup(Application app) {
        scene.removeFromParent();
    }

    /**
     * Called by the engine when the stage is switched on. Nothing to do here,
     * but the engine requires the method to exist.
     */
    @Override
    protected void onEnable() {
    }

    /**
     * Called by the engine when the stage is switched off. Nothing to do
     * here, but the engine requires the method to exist.
     */
    @Override
    protected void onDisable() {
    }

    /**
     * Viewpoints for automated test screenshots. The default is an empty
     * list; a stage overrides this to say where the test camera should stand.
     *
     * @return rows of {x, feet y, z, yaw degrees, pitch degrees}
     */
    float[][] debugPoses() {
        return new float[0][];
    }

    /** Puts the scene into the state it is in just before the player completes it (tests only). */
    void debugComplete() {
    }

    /** @return the player in this scene */
    PlayerBody body() {
        return body;
    }
}
