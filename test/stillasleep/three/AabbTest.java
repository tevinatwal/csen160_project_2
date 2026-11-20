package stillasleep.three;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for {@link Aabb}, the collision box every wall and platform is made of. */
class AabbTest {

    /** A 2 x 2 x 2 metre box centred on the origin, used by most tests. */
    private final Aabb box = Aabb.centered(0, 0, 0, 1, 1, 1);

    /** The centre-and-half-size helper should produce the right edges. */
    @Test
    void centeredBuildsBoxAroundTheCentre() {
        Aabb made = Aabb.centered(5, 2, -3, 1, 0.5f, 2);
        assertEquals(4f, made.minX());
        assertEquals(6f, made.maxX());
        assertEquals(1.5f, made.minY());
        assertEquals(2.5f, made.maxY());
        assertEquals(-5f, made.minZ());
        assertEquals(-1f, made.maxZ());
    }

    /** Two boxes that share some space overlap. */
    @Test
    void overlappingBoxesOverlap() {
        assertTrue(box.overlaps(0.5f, 0.5f, 0.5f, 3, 3, 3));
    }

    /** A box completely inside another one overlaps it. */
    @Test
    void boxInsideAnotherOverlaps() {
        assertTrue(box.overlaps(-0.2f, -0.2f, -0.2f, 0.2f, 0.2f, 0.2f));
    }

    /** Boxes that are apart on just one axis do not overlap. */
    @Test
    void boxesApartOnOneAxisDoNotOverlap() {
        assertFalse(box.overlaps(2, -1, -1, 3, 1, 1), "apart on x");
        assertFalse(box.overlaps(-1, 2, -1, 1, 3, 1), "apart on y");
        assertFalse(box.overlaps(-1, -1, 2, 1, 1, 3), "apart on z");
    }

    /** Standing exactly on top of a box is touching, not overlapping. */
    @Test
    void touchingFacesDoNotCountAsOverlap() {
        assertFalse(box.overlaps(-1, 1, -1, 1, 2, 1));
    }
}
