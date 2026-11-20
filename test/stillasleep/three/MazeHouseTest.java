package stillasleep.three;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for the maze chapter's map and door code in {@link MazeHouse}. */
class MazeHouseTest {

    /** Counts how many times a character appears in the whole maze. */
    private static int count(char wanted) {
        int total = 0;
        for (String row : MazeHouse.MAZE) {
            for (char c : row.toCharArray()) {
                if (c == wanted) {
                    total++;
                }
            }
        }
        return total;
    }

    /** Every row must be the same width, or the walls would not line up. */
    @Test
    void mazeIsARectangle() {
        int width = MazeHouse.MAZE[0].length();
        for (String row : MazeHouse.MAZE) {
            assertEquals(width, row.length());
        }
    }

    /** There is exactly one way in and one way out. */
    @Test
    void mazeHasOneStartAndOneExit() {
        assertEquals(1, count('S'));
        assertEquals(1, count('X'));
    }

    /** The player must be able to get out, or the game could never be finished. */
    @Test
    void mazeCanBeSolved() {
        assertTrue(MazeHouse.shortestRoute() > 0);
    }

    /** The shortest way out is long enough to feel like a maze, not a corridor. */
    @Test
    void mazeIsNotTrivial() {
        assertTrue(MazeHouse.shortestRoute() >= 10);
    }

    /** Walls, and anything off the edge of the map, cannot be walked through. */
    @Test
    void wallsAndOutsideAreClosed() {
        assertFalse(MazeHouse.isOpen(0, 0), "corner wall");
        assertFalse(MazeHouse.isOpen(-1, 3), "west of the map");
        assertFalse(MazeHouse.isOpen(3, -1), "north of the map");
        assertFalse(MazeHouse.isOpen(99, 3), "east of the map");
        assertFalse(MazeHouse.isOpen(3, 99), "south of the map");
    }

    /** The start cell and a corridor cell can be walked through. */
    @Test
    void corridorsAreOpen() {
        assertTrue(MazeHouse.isOpen(5, 0), "start cell");
        assertTrue(MazeHouse.isOpen(1, 1), "corridor");
    }

    /** The lock has three dials and each shows a single digit. */
    @Test
    void doorCodeIsThreeDigits() {
        assertEquals(3, MazeHouse.CODE.length);
        for (int digit : MazeHouse.CODE) {
            assertTrue(digit >= 0 && digit <= 9);
        }
    }
}
