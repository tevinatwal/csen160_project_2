package stillasleep.three;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.jme3.math.ColorRGBA;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Tests for the painted wall and floor pictures in {@link Textures}, and colour conversion. */
class TexturesTest {

    /** Number of pixels in one square texture. */
    private static final int PIXELS = Textures.SIZE * Textures.SIZE;

    /** Lets Java 2D paint into memory without opening a window. */
    @BeforeAll
    static void noWindow() {
        System.setProperty("java.awt.headless", "true");
    }

    /** Every kind of texture is a full square of pixels. */
    @Test
    void texturesAreFullSquares() {
        assertEquals(PIXELS, Textures.wall('b', null).length);
        assertEquals(PIXELS, Textures.floor('b').length);
        assertEquals(PIXELS, Textures.ceiling().length);
    }

    /** Asking for the same wall twice gives back the saved picture, not a new one. */
    @Test
    void sameWallIsPaintedOnlyOnce() {
        assertSame(Textures.wall('h', "mirror"), Textures.wall('h', "mirror"));
    }

    /** A wall with a poster on it looks different from a bare wall. */
    @Test
    void decorationChangesTheWall() {
        assertFalse(Arrays.equals(Textures.wall('b', null), Textures.wall('b', "poster")));
    }

    /** Different rooms have different wallpaper. */
    @Test
    void roomsHaveDifferentWallpaper() {
        assertFalse(Arrays.equals(Textures.wall('b', null), Textures.wall('k', null)));
    }

    /** The kitchen is tiled and the bedroom has floorboards. */
    @Test
    void kitchenAndBedroomFloorsDiffer() {
        assertFalse(Arrays.equals(Textures.floor('k'), Textures.floor('b')));
    }

    /** A 0xRRGGBB number is split into red, green and blue from 0 to 1. */
    @Test
    void rgbaConvertsHexColours() {
        ColorRGBA red = Build.rgba(0xff0000);
        assertEquals(1f, red.r);
        assertEquals(0f, red.g);
        assertEquals(0f, red.b);
        assertEquals(1f, red.a);

        ColorRGBA white = Build.rgba(0xffffff);
        assertEquals(new ColorRGBA(1, 1, 1, 1), white);
    }
}
