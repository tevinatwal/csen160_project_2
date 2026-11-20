package stillasleep.three;

import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;

import java.util.HashSet;
import java.util.Set;

/**
 * Keyboard and mouse state for the 3D game: which keys are held, which were
 * pressed this frame, and how far the mouse moved.
 *
 * <p>The rest of the game never asks about real keys. It asks about named
 * actions such as "forward" or "jump". This class connects keys and mouse
 * buttons to those names, so a key can be changed in one place.
 *
 * <p>The engine tells this class about input in two ways: on/off events
 * (a key went down or up) arrive in {@link #onAction}, and "how much" events
 * (the mouse moved this far) arrive in {@link #onAnalog}.
 */
final class Controls implements ActionListener, AnalogListener {

    /** Names of all the on/off actions (keys and mouse buttons) the game listens for. */
    private static final String[] ACTIONS = {"forward", "back", "left", "right", "turnLeft",
        "turnRight", "jump", "interact", "confirm", "skip"};

    /** Actions whose key is down right now. */
    private final Set<String> held = new HashSet<>();
    /** Actions whose key went down during the current frame (emptied every frame). */
    private final Set<String> pressed = new HashSet<>();
    /** Mouse movement to the right during the current frame (negative = left). */
    private float lookX;
    /** Mouse movement upward during the current frame (negative = down). */
    private float lookY;

    /**
     * Connects keys and mouse movement to action names and starts listening.
     *
     * @param input the engine's input manager to register with
     */
    Controls(InputManager input) {
        // Walking: W/S or the up/down arrows move, A/D step sideways.
        input.addMapping("forward", new KeyTrigger(KeyInput.KEY_W),
                new KeyTrigger(KeyInput.KEY_UP));
        input.addMapping("back", new KeyTrigger(KeyInput.KEY_S), new KeyTrigger(KeyInput.KEY_DOWN));
        input.addMapping("left", new KeyTrigger(KeyInput.KEY_A));
        input.addMapping("right", new KeyTrigger(KeyInput.KEY_D));

        // The left/right arrows turn the view, for playing without a mouse.
        input.addMapping("turnLeft", new KeyTrigger(KeyInput.KEY_LEFT));
        input.addMapping("turnRight", new KeyTrigger(KeyInput.KEY_RIGHT));

        // Space jumps; E or a left click uses whatever the player looks at;
        // Enter confirms; the "]" key skips to the next chapter.
        input.addMapping("jump", new KeyTrigger(KeyInput.KEY_SPACE));
        input.addMapping("interact", new KeyTrigger(KeyInput.KEY_E),
                new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        input.addMapping("confirm", new KeyTrigger(KeyInput.KEY_RETURN));
        input.addMapping("skip", new KeyTrigger(KeyInput.KEY_RBRACKET));

        // Mouse movement. The engine reports each direction separately and
        // always as a positive amount: "false" is the positive direction of
        // the axis (right / up) and "true" is the negative one (left / down).
        input.addMapping("lookRight", new MouseAxisTrigger(MouseInput.AXIS_X, false));
        input.addMapping("lookLeft", new MouseAxisTrigger(MouseInput.AXIS_X, true));
        input.addMapping("lookUp", new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        input.addMapping("lookDown", new MouseAxisTrigger(MouseInput.AXIS_Y, true));

        // Ask the engine to call onAction / onAnalog below for these names.
        input.addListener(this, ACTIONS);
        input.addListener(this, "lookRight", "lookLeft", "lookUp", "lookDown");
    }

    /**
     * Called by the engine when a key or button for an action goes down or up.
     *
     * @param name      action name, for example "jump"
     * @param isPressed true when the key went down, false when it was let go
     * @param tpf       seconds since the last frame (not used)
     */
    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (isPressed) {
            // "held" stays until the key is released; "pressed" lasts only
            // until the end of this frame, so one tap triggers things once.
            held.add(name);
            pressed.add(name);
        } else {
            held.remove(name);
        }
    }

    /**
     * Called by the engine whenever the mouse moves. Adds the movement to
     * this frame's totals.
     *
     * @param name  which direction the mouse moved in
     * @param value how far it moved (always positive)
     * @param tpf   seconds since the last frame (not used)
     */
    @Override
    public void onAnalog(String name, float value, float tpf) {
        // Opposite directions cancel out, leaving one number per axis.
        switch (name) {
            case "lookRight" -> lookX += value;
            case "lookLeft" -> lookX -= value;
            case "lookUp" -> lookY += value;
            case "lookDown" -> lookY -= value;
            default -> { }
        }
    }

    /**
     * @param action action name such as "forward" or "jump"
     * @return true while its key is held
     */
    boolean isHeld(String action) {
        return held.contains(action);
    }

    /**
     * @param action action name
     * @return true only on the frame its key went down
     */
    boolean wasPressed(String action) {
        return pressed.contains(action);
    }

    /** @return mouse movement to the right since last frame */
    float lookX() {
        return lookX;
    }

    /** @return mouse movement upward since last frame */
    float lookY() {
        return lookY;
    }

    /** Clears per-frame state. Called once at the end of every frame. */
    void endFrame() {
        // Held keys are kept; only the "just happened" information is reset.
        pressed.clear();
        lookX = 0;
        lookY = 0;
    }
}
