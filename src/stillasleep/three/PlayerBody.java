package stillasleep.three;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

import java.util.List;

/**
 * The first-person player: looks with the mouse, walks, jumps, falls, and
 * collides with boxes. The position is the point between the feet.
 *
 * <p>For collision the player is treated as a tall, thin, upright box
 * ({@link #HALF_WIDTH} to each side and {@link #HEIGHT} tall). Distances are
 * in metres, speeds in metres per second and angles in radians. x runs east,
 * y runs up and z runs south.
 */
final class PlayerBody {

    /** Half the body width. */
    static final float HALF_WIDTH = 0.22f;
    /** Body height (a kid). */
    static final float HEIGHT = 1.45f;
    /** Height of the eyes above the feet. */
    static final float EYE_HEIGHT = 1.32f;

    /** How quickly falling speeds up, in metres per second, every second. */
    private static final float GRAVITY = 18f;
    /** Upward speed at the moment of a jump, in metres per second. */
    private static final float JUMP_SPEED = 6.6f;
    /** How strongly mouse movement turns the view. */
    private static final float MOUSE_SENSITIVITY = 1.7f;
    /** Turning speed with the arrow keys, in radians per second. */
    private static final float KEY_TURN_SPEED = 2.2f;
    /**
     * The farthest the body may move in one collision check, in metres.
     * Small steps stop a fast-moving player from passing through a thin wall.
     */
    private static final float MAX_STEP = 0.04f;

    /** Where the feet are. */
    final Vector3f position = new Vector3f();
    /** Current speed along each axis, in metres per second. */
    final Vector3f velocity = new Vector3f();
    /** Facing direction in radians: 0 looks east (+x), increasing turns right. */
    float yaw;
    /** Looking up (positive) or down (negative), in radians. */
    float pitch;
    /** True while the feet are resting on something. */
    private boolean onGround;
    /** Keeps growing while walking; drives the small up-and-down head bob. */
    private float bob;
    /** Seconds left in which a recent jump key press still counts ("jump buffer"). */
    private float jumpMemory;
    /** Seconds left in which the player still counts as on the ground ("coyote time"). */
    private float groundMemory;

    /**
     * Turns, moves and applies gravity for one frame.
     *
     * @param tpf      seconds since the last frame
     * @param controls keyboard and mouse state, or null to stand still
     * @param solids   boxes to collide with
     * @param speed    walking speed in metres per second
     * @param canJump  whether the jump key works here
     */
    void update(float tpf, Controls controls, List<Aabb> solids, float speed, boolean canJump) {
        // Never simulate more than 1/20 of a second at once. If the game
        // freezes for a moment, the player should not suddenly fall a long way.
        tpf = Math.min(tpf, 1 / 20f);

        // How much the player wants to move: +1 forward / right, -1 back / left.
        float forward = 0;
        float strafe = 0;
        if (controls != null) {
            // Turn the view with the mouse, and also with the arrow keys.
            // Mouse movement is already "per frame"; the keys are multiplied
            // by tpf so they turn at the same rate on any computer.
            yaw += controls.lookX() * MOUSE_SENSITIVITY;
            pitch += controls.lookY() * MOUSE_SENSITIVITY;
            yaw += ((controls.isHeld("turnRight") ? 1 : 0) - (controls.isHeld("turnLeft") ? 1 : 0))
                    * KEY_TURN_SPEED * tpf;

            // Stop at about 77 degrees up or down, so the view can never
            // flip over backwards.
            pitch = FastMath.clamp(pitch, -1.35f, 1.35f);

            // Opposite keys cancel each other out.
            forward = (controls.isHeld("forward") ? 1 : 0) - (controls.isHeld("back") ? 1 : 0);
            strafe = (controls.isHeld("right") ? 1 : 0) - (controls.isHeld("left") ? 1 : 0);

            // A jump still counts a moment after walking off an edge, and a press
            // just before landing is remembered, so jumps feel fair.
            // jumpMemory: a press is remembered for 0.14 s ("jump buffer").
            // groundMemory: the ground is remembered for 0.12 s ("coyote time").
            jumpMemory = controls.wasPressed("jump") ? 0.14f : jumpMemory - tpf;
            groundMemory = onGround ? 0.12f : groundMemory - tpf;
            if (canJump && jumpMemory > 0 && groundMemory > 0) {
                // Jump: start moving upward, and forget both memories so one
                // press cannot cause two jumps.
                velocity.y = JUMP_SPEED;
                onGround = false;
                jumpMemory = 0;
                groundMemory = 0;
            }
        }

        // The direction the player faces, flat on the ground. With yaw 0 this
        // is (1, 0): due east.
        float dirX = FastMath.cos(yaw);
        float dirZ = FastMath.sin(yaw);

        // Combine forward/back along the facing direction with left/right
        // along the direction at a right angle to it, which is (-dirZ, dirX).
        float moveX = dirX * forward - dirZ * strafe;
        float moveZ = dirZ * forward + dirX * strafe;
        float length = FastMath.sqrt(moveX * moveX + moveZ * moveZ);
        if (length > 0) {
            // Dividing by the length makes walking diagonally exactly as fast
            // as walking straight.
            velocity.x = moveX / length * speed;
            velocity.z = moveZ / length * speed;
            if (onGround) {
                // Advance the head bob; faster walking bobs faster.
                bob += tpf * speed * 2.6f;
            }
        } else {
            // No keys held: stop at once (there is no sliding).
            velocity.x = 0;
            velocity.z = 0;
        }

        // Gravity pulls down a little more every frame, but falling never
        // gets faster than 30 metres per second.
        velocity.y = Math.max(velocity.y - GRAVITY * tpf, -30f);

        // If something (like a rising platform) has moved into the feet, stand on top of it.
        // This is only done when its top is less than 0.45 m above the feet;
        // anything taller is a wall, not something to be lifted onto.
        Aabb under = firstHit(solids);
        if (under != null && under.maxY() - position.y < 0.45f) {
            position.y = under.maxY();
            velocity.y = Math.max(0, velocity.y);
            onGround = true;
        }

        // Split the frame's movement into pieces no longer than MAX_STEP and
        // check for collisions after each piece.
        int steps = Math.max(1, (int) Math.ceil(velocity.length() * tpf / MAX_STEP));
        float dt = tpf / steps;
        for (int i = 0; i < steps; i++) {
            step(dt, solids);
        }
    }

    /**
     * Moves along each axis in turn, backing out of anything solid.
     *
     * <p>Handling x, z and y separately is what lets the player slide along a
     * wall: the axis that runs into the wall is stopped, while the other axis
     * keeps moving.
     *
     * @param dt     length of this small step in seconds
     * @param solids boxes to collide with
     */
    private void step(float dt, List<Aabb> solids) {
        Aabb hit;

        // East-west. If the move ends inside a box, put the body flush against
        // the side it came from: the west face when moving east, else the east face.
        if (velocity.x != 0) {
            position.x += velocity.x * dt;
            hit = firstHit(solids);
            if (hit != null) {
                position.x = velocity.x > 0 ? hit.minX() - HALF_WIDTH : hit.maxX() + HALF_WIDTH;
            }
        }

        // North-south, in exactly the same way.
        if (velocity.z != 0) {
            position.z += velocity.z * dt;
            hit = firstHit(solids);
            if (hit != null) {
                position.z = velocity.z > 0 ? hit.minZ() - HALF_WIDTH : hit.maxZ() + HALF_WIDTH;
            }
        }

        // Up-down. This runs every step, because gravity is always acting.
        position.y += velocity.y * dt;
        hit = firstHit(solids);
        if (hit != null) {
            if (velocity.y <= 0) {
                // Falling onto something: stand on its top.
                position.y = hit.maxY();
                onGround = true;
            } else {
                // Rising into something: the head stops just under it.
                position.y = hit.minY() - HEIGHT;
            }
            velocity.y = 0;
        } else if (velocity.y < -1f) {
            // Nothing underneath and clearly falling: no longer on the ground.
            // (Waiting for -1 m/s avoids flickering on and off while standing.)
            onGround = false;
        }
    }

    /**
     * Looks for a box the body is currently inside.
     *
     * @param solids boxes to test
     * @return the first overlapping box, or null if the body is clear of them all
     */
    private Aabb firstHit(List<Aabb> solids) {
        for (Aabb box : solids) {
            // The body's own box: HALF_WIDTH to every side of the feet, and
            // from the feet up to HEIGHT.
            if (box.overlaps(position.x - HALF_WIDTH, position.y, position.z - HALF_WIDTH,
                    position.x + HALF_WIDTH, position.y + HEIGHT, position.z + HALF_WIDTH)) {
                return box;
            }
        }
        return null;
    }

    /**
     * Tests the body against a box.
     *
     * @param box box to test
     * @return true if the player is touching it
     */
    boolean touches(Aabb box) {
        return box.overlaps(position.x - HALF_WIDTH, position.y, position.z - HALF_WIDTH,
                position.x + HALF_WIDTH, position.y + HEIGHT, position.z + HALF_WIDTH);
    }

    /** @return the direction the player is looking */
    Vector3f lookDirection() {
        // Looking up or down tilts the direction: its upward part is
        // sin(pitch), and the flat part shrinks by cos(pitch) so the whole
        // arrow keeps a length of exactly 1.
        float cosPitch = FastMath.cos(pitch);
        return new Vector3f(FastMath.cos(yaw) * cosPitch, FastMath.sin(pitch),
                FastMath.sin(yaw) * cosPitch);
    }

    /** @return the position of the eyes, with a slight bob while walking */
    Vector3f eye() {
        // The sine wave moves the eyes up and down by at most 2.5 centimetres.
        return new Vector3f(position.x, position.y + EYE_HEIGHT + FastMath.sin(bob) * 0.025f,
                position.z);
    }

    /**
     * Points a camera out of the player's eyes.
     *
     * @param camera camera to move
     */
    void applyTo(Camera camera) {
        camera.setLocation(eye());
        // The second value tells the camera which way is "up" (the y axis),
        // so the picture is never tilted sideways.
        camera.lookAtDirection(lookDirection(), Vector3f.UNIT_Y);
    }

    /**
     * Teleports the player and stops all movement.
     *
     * @param x     x in metres
     * @param y     height of the feet
     * @param z     z in metres
     * @param facing yaw in radians
     */
    void place(float x, float y, float z, float facing) {
        position.set(x, y, z);
        velocity.set(0, 0, 0);
        yaw = facing;
        pitch = 0;
        // Treated as in the air; the next update lands the body if there is
        // a floor under it.
        onGround = false;
    }

    /**
     * Throws the player upward, as a bounce pad does.
     *
     * @param speed upward speed in metres per second
     */
    void launch(float speed) {
        velocity.y = speed;
        onGround = false;
        // Forget the ground, so a jump cannot be added on top of the bounce.
        groundMemory = 0;
    }

    /** @return true while standing on something */
    boolean isOnGround() {
        return onGround;
    }
}
