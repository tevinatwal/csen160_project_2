package stillasleep.three;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Tests that the puzzle data is fair: the chess puzzle can be solved with the
 * pieces on the board, and the lamp sequence only uses lamps that exist.
 */
class PuzzleDataTest {

    /** Joins a board into one string and sorts the letters, to compare which pieces it has. */
    private static char[] sortedPieces(String[] board) {
        char[] pieces = String.join("", board).toCharArray();
        Arrays.sort(pieces);
        return pieces;
    }

    /** Both chess layouts are a full 8 x 8 board. */
    @Test
    void chessBoardsAreEightByEight() {
        for (String[] board : new String[][] {ChessSet.START, ChessHouse.SCRAMBLED}) {
            assertEquals(8, board.length);
            for (String row : board) {
                assertEquals(8, row.length());
            }
        }
    }

    /** The puzzle has to start out wrong, or there is nothing to fix. */
    @Test
    void scrambledBoardIsNotAlreadySolved() {
        assertFalse(Arrays.equals(ChessSet.START, ChessHouse.SCRAMBLED));
    }

    /** The scrambled board has exactly the pieces of a real game, so it can be put right. */
    @Test
    void scrambledBoardHasTheSamePieces() {
        assertArrayEquals(sortedPieces(ChessSet.START), sortedPieces(ChessHouse.SCRAMBLED));
    }

    /** Black's side is untouched: only the white pieces were moved. */
    @Test
    void onlyWhitePiecesAreScrambled() {
        for (int row = 0; row < 4; row++) {
            assertEquals(ChessSet.START[row], ChessHouse.SCRAMBLED[row]);
        }
    }

    /** The lamp sequence only names the four lamps, numbered 0 to 3. */
    @Test
    void lampSequenceUsesOnlyTheFourLamps() {
        assertEquals(5, LightsHouse.SEQUENCE.length);
        for (int lamp : LightsHouse.SEQUENCE) {
            assertTrue(lamp >= 0 && lamp <= 3);
        }
    }

    /** The game has twelve chapters. */
    @Test
    void gameHasTwelveChapters() {
        assertEquals(12, Game3D.CHAPTERS);
    }
}
