package stillasleep.three;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link PlayerBody}: gravity, landing, bouncing and where the
 * player is looking. No window is needed, because the player only needs a
 * list of collision boxes to move around in.
 */
class PlayerBodyTest {

    /** One frame at 60 frames per second. */
    private static final float FRAME = 1 / 60f;
    /** Walking speed passed to update (unused here because no keys are held). */
    private static final float SPEED = 3f;

    private PlayerBody body;
    private List<Aabb> solids;

    /** Starts every test with a fresh player above a big floor whose top is at y = 0. */
    @BeforeEach
    void setUp() {
        body = new PlayerBody();
        solids = new ArrayList<>();
        solids.add(new Aabb(-10, -1, -10, 10, 0, 10));
    }

    /** Runs the game for a number of seconds with no keys pressed. */
    private void simulate(float seconds) {
        for (int i = 0; i < Math.round(seconds / FRAME); i++) {
            body.update(FRAME, null, solids, SPEED, true);
        }
    }

    /** A player dropped from one metre ends up standing on the floor. */
    @Test
    void fallsAndLandsOnTheFloor() {
        body.place(0, 1, 0, 0);
        simulate(2);
        assertEquals(0f, body.position.y, 0.001f);
        assertTrue(body.isOnGround());
    }

    /** With nothing underneath, the player keeps falling. */
    @Test
    void keepsFallingWhenThereIsNoFloor() {
        solids.clear();
        body.place(0, 1, 0, 0);
        simulate(2);
        assertTrue(body.position.y < -10f);
        assertFalse(body.isOnGround());
    }

    /** A bounce pad throws the player up, and gravity brings them back down. */
    @Test
    void launchGoesUpThenComesBackDown() {
        body.place(0, 0, 0, 0);
        simulate(0.5f);
        body.launch(8);
        simulate(0.3f);
        assertTrue(body.position.y > 1f, "should be in the air");
        assertFalse(body.isOnGround());
        simulate(3);
        assertEquals(0f, body.position.y, 0.001f);
        assertTrue(body.isOnGround());
    }

    /** A low ceiling stops the player's head instead of letting them through. */
    @Test
    void ceilingStopsAnUpwardLaunch() {
        solids.add(new Aabb(-10, 2, -10, 10, 3, 10));
        body.place(0, 0, 0, 0);
        simulate(0.5f);
        body.launch(12);
        float highest = 0;
        for (int i = 0; i < 120; i++) {
            body.update(FRAME, null, solids, SPEED, true);
            highest = Math.max(highest, body.position.y);
        }
        assertTrue(highest <= 2 - PlayerBody.HEIGHT + 0.001f, "head went through the ceiling");
    }

    /** Standing still with no keys pressed does not slide the player sideways. */
    @Test
    void doesNotDriftWithoutInput() {
        body.place(2, 0, -3, 1f);
        simulate(1);
        assertEquals(2f, body.position.x, 0.0001f);
        assertEquals(-3f, body.position.z, 0.0001f);
    }

    /** Teleporting resets the position, speed and view. */
    @Test
    void placeResetsEverything() {
        body.launch(5);
        body.pitch = 0.7f;
        body.place(1, 2, 3, FastMath.HALF_PI);
        assertEquals(new Vector3f(1, 2, 3), body.position);
        assertEquals(Vector3f.ZERO, body.velocity);
        assertEquals(FastMath.HALF_PI, body.yaw);
        assertEquals(0f, body.pitch);
    }

    /** Yaw 0 looks east (+x) and a quarter turn to the right looks south (+z). */
    @Test
    void lookDirectionFollowsYaw() {
        body.place(0, 0, 0, 0);
        Vector3f east = body.lookDirection();
        assertEquals(1f, east.x, 0.001f);
        assertEquals(0f, east.z, 0.001f);

        body.place(0, 0, 0, FastMath.HALF_PI);
        Vector3f south = body.lookDirection();
        assertEquals(0f, south.x, 0.001f);
        assertEquals(1f, south.z, 0.001f);
    }

    /** The eyes sit at eye height above the feet. */
    @Test
    void eyeIsAboveTheFeet() {
        body.place(4, 1, 5, 0);
        Vector3f eye = body.eye();
        assertEquals(4f, eye.x);
        assertEquals(1 + PlayerBody.EYE_HEIGHT, eye.y, 0.03f);
        assertEquals(5f, eye.z);
    }

    /** touches() is true only for boxes the body actually reaches. */
    @Test
    void touchesOnlyNearbyBoxes() {
        body.place(0, 0, 0, 0);
        assertTrue(body.touches(Aabb.centered(0, 0.5f, 0, 0.5f, 0.5f, 0.5f)));
        assertFalse(body.touches(Aabb.centered(5, 0.5f, 0, 0.5f, 0.5f, 0.5f)));
    }
}
