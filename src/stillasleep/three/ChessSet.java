package stillasleep.three;

import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;

/**
 * A chess board with movable pieces, built from simple shapes. It keeps track
 * of where every piece is so the chess puzzle can check the arrangement.
 *
 * <p>Layouts are eight strings of eight characters, the far (black) side
 * first. Lower-case letters are black pieces, upper-case are white:
 * r/R rook, n/N knight, b/B bishop, q/Q queen, k/K king, p/P pawn, '.' empty.
 */
final class ChessSet {

    /** The standard starting position. */
    static final String[] START = {
        "rnbqkbnr", "pppppppp", "........", "........",
        "........", "........", "PPPPPPPP", "RNBQKBNR",
    };

    /** Width of one square in metres. */
    static final float SQUARE = 0.06f;

    /** Helper used to make the boxes and materials. */
    private final Build build;
    /** Parent of the whole set. Everything below is positioned relative to it. */
    private final Node root = new Node("chess");
    /** What is on each square, as board[row][col]. '.' means empty. */
    private final char[][] board = new char[8][8];
    /** The 3D piece standing on each square, or null if the square is empty. */
    private final Node[][] nodes = new Node[8][8];
    /** The glowing square that shows which square is selected. */
    private final Geometry cursor;
    /** The piece the player is holding, or '.' for none. */
    private char held = '.';
    /** The 3D piece being held, or null. */
    private Node heldNode;
    /** Column of the selected square, 0 (west) to 7 (east). */
    private int cursorCol = 4;
    /** Row of the selected square, 0 (black's back row) to 7 (white's back row). */
    private int cursorRow = 6;

    /**
     * Builds the board and stands the pieces on it.
     *
     * @param build  scene helpers
     * @param parent node to attach the board to
     * @param x      board centre x
     * @param y      height of the table top
     * @param z      board centre z
     * @param layout starting arrangement
     */
    ChessSet(Build build, Node parent, float x, float y, float z, String[] layout) {
        this.build = build;
        // "root" sits on the table top at the middle of the board. Everything else is attached
        // to it, so the positions below are measured from the board centre.
        root.setLocalTranslation(x, y, z);
        parent.attachChild(root);
        // The wooden base, a little bigger than the 8 by 8 squares.
        root.attachChild(build.box(0, 0.006f, 0, SQUARE * 4.25f, 0.006f, SQUARE * 4.25f,
                build.color(0x5a3b28)));
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                // One thin tile per square, alternating cream and dark.
                root.attachChild(build.box(squareX(col), 0.014f, squareZ(row), SQUARE / 2, 0.003f,
                        SQUARE / 2, build.color((row + col) % 2 == 0 ? 0xf1e6c8 : 0x3a302c)));
                // Remember what is on this square and build its piece, if there is one.
                board[row][col] = layout[row].charAt(col);
                if (board[row][col] != '.') {
                    nodes[row][col] = piece(board[row][col]);
                    nodes[row][col].setLocalTranslation(squareX(col), 0.017f, squareZ(row));
                    root.attachChild(nodes[row][col]);
                }
            }
        }
        // The light blue selection square, hidden until the puzzle starts.
        cursor = build.box(0, 0.019f, 0, SQUARE / 2, 0.002f, SQUARE / 2, build.glow(0x6fe0ff));
        root.attachChild(cursor);
        setCursorVisible(false);
    }

    /**
     * @param col column 0-7
     * @return x of the middle of that column, measured from the board centre
     */
    private static float squareX(int col) {
        // The board centre lies between columns 3 and 4, which is why 3.5 is subtracted.
        return (col - 3.5f) * SQUARE;
    }

    /**
     * @param row row 0-7
     * @return z of the middle of that row, measured from the board centre
     */
    private static float squareZ(int row) {
        return (row - 3.5f) * SQUARE;
    }

    /**
     * Builds one chess piece out of cylinders, balls and boxes. Each type has
     * its own outline so they can be told apart.
     *
     * @param type the piece letter; upper-case is white, lower-case is black
     * @return a node holding the parts of the piece
     */
    private Node piece(char type) {
        // Cream for white pieces, near-black for black pieces.
        Material material = build.color(Character.isUpperCase(type) ? 0xf7f1e1 : 0x262220);
        Node node = new Node("piece");
        // "r" is the basic radius of a piece. Every piece starts with a flat round base. The
        // numbers after each shape are sizes and heights in metres.
        float r = SQUARE * 0.3f;
        node.attachChild(upright(new Cylinder(2, 12, r * 1.15f, 0.008f, true), 0.004f, material));
        switch (Character.toLowerCase(type)) {
            // Pawn: a short stem with a ball on top.
            case 'p' -> {
                node.attachChild(upright(new Cylinder(2, 12, r * 0.6f, 0.03f, true), 0.023f,
                        material));
                node.attachChild(ball(r * 0.75f, 0.046f, material));
            }
            // Rook: a thick tower with a square top.
            case 'r' -> {
                node.attachChild(upright(new Cylinder(2, 12, r * 0.8f, 0.045f, true), 0.03f,
                        material));
                node.attachChild(shape(new Box(r * 0.95f, 0.008f, r * 0.95f), 0.058f, material));
            }
            // Knight: a stem with a tilted block for the horse's head.
            case 'n' -> {
                node.attachChild(upright(new Cylinder(2, 12, r * 0.7f, 0.03f, true), 0.023f,
                        material));
                Geometry head = shape(new Box(r * 0.45f, 0.018f, r * 0.95f), 0.052f, material);
                head.setLocalRotation(new Quaternion().fromAngleAxis(-0.5f, Vector3f.UNIT_X));
                node.attachChild(head);
            }
            // Bishop: a tapered stem with a small ball on top.
            case 'b' -> {
                node.attachChild(upright(new Cylinder(2, 12, r * 0.3f, r * 0.8f, 0.055f, true,
                        false), 0.035f, material));
                node.attachChild(ball(r * 0.5f, 0.068f, material));
            }
            // Queen: a tall stem, a large ball and a small ball on top.
            case 'q' -> {
                node.attachChild(upright(new Cylinder(2, 12, r * 0.75f, 0.065f, true), 0.04f,
                        material));
                node.attachChild(ball(r * 0.85f, 0.078f, material));
                node.attachChild(ball(r * 0.35f, 0.094f, material));
            }
            // King: the tallest stem with a cross on top (an upright bar and a crossbar).
            default -> {
                node.attachChild(upright(new Cylinder(2, 12, r * 0.8f, 0.07f, true), 0.043f,
                        material));
                node.attachChild(shape(new Box(r * 0.25f, 0.016f, r * 0.25f), 0.094f, material));
                node.attachChild(shape(new Box(r * 0.75f, 0.005f, r * 0.25f), 0.098f, material));
            }
        }
        return node;
    }

    /**
     * Turns a mesh into something that can be drawn.
     *
     * @param mesh     the shape
     * @param y        height of its centre above the board
     * @param material surface material
     * @return the finished part
     */
    private static Geometry shape(com.jme3.scene.Mesh mesh, float y, Material material) {
        Geometry geometry = new Geometry("part", mesh);
        geometry.setMaterial(material);
        geometry.setLocalTranslation(0, y, 0);
        return geometry;
    }

    /**
     * Makes a cylinder that stands on its end. The engine builds cylinders
     * lying on their side, so this turns it a quarter turn.
     *
     * @param cylinder the shape
     * @param y        height of its centre above the board
     * @param material surface material
     * @return the finished part
     */
    private static Geometry upright(Cylinder cylinder, float y, Material material) {
        Geometry geometry = shape(cylinder, y, material);
        geometry.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI,
                Vector3f.UNIT_X));
        return geometry;
    }

    /**
     * @param radius   size of the ball
     * @param y        height of its centre above the board
     * @param material surface material
     * @return a ball-shaped part
     */
    private static Geometry ball(float radius, float y, Material material) {
        return shape(new Sphere(10, 10, radius), y, material);
    }

    /**
     * @param visible whether the glowing selection square is shown
     */
    void setCursorVisible(boolean visible) {
        // A cull hint of Always means "never draw this".
        cursor.setCullHint(visible ? Spatial.CullHint.Never : Spatial.CullHint.Always);
    }

    /**
     * Moves the selection square, staying on the board.
     *
     * @param dCol columns to move (positive is east)
     * @param dRow rows to move (positive is toward the white side)
     */
    void moveCursor(int dCol, int dRow) {
        // Keep the column and row between 0 and 7 so the square stays on the board.
        cursorCol = Math.max(0, Math.min(7, cursorCol + dCol));
        cursorRow = Math.max(0, Math.min(7, cursorRow + dRow));
        cursor.setLocalTranslation(squareX(cursorCol), 0.019f, squareZ(cursorRow));
        // A piece that is being held floats above the selected square.
        if (heldNode != null) {
            heldNode.setLocalTranslation(squareX(cursorCol), 0.06f, squareZ(cursorRow));
        }
    }

    /**
     * Picks up the piece under the selection square, or puts the held piece
     * down. Putting a piece onto an occupied square swaps the two.
     */
    void pickUpOrPutDown() {
        // Swap what is on the square with what is in the hand. With an empty hand this is a
        // pick-up; onto an empty square it is a put-down.
        char there = board[cursorRow][cursorCol];
        Node thereNode = nodes[cursorRow][cursorCol];
        board[cursorRow][cursorCol] = held;
        nodes[cursorRow][cursorCol] = heldNode;
        // The piece that was in the hand now rests on the board.
        if (heldNode != null) {
            heldNode.setLocalTranslation(squareX(cursorCol), 0.017f, squareZ(cursorRow));
        }
        held = there;
        heldNode = thereNode;
        // The piece that was on the square is lifted above it.
        if (heldNode != null) {
            heldNode.setLocalTranslation(squareX(cursorCol), 0.06f, squareZ(cursorRow));
        }
    }

    /** @return true while a piece is lifted off the board */
    boolean isHolding() {
        return held != '.';
    }

    /**
     * Compares the board with a layout.
     *
     * @param layout arrangement to compare against
     * @return true if every square matches and nothing is held
     */
    boolean matches(String[] layout) {
        // A piece in the hand is not on its square yet.
        if (isHolding()) {
            return false;
        }
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if (board[row][col] != layout[row].charAt(col)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * @param col column 0-7
     * @param row row 0-7
     * @return the piece on that square, or '.'
     */
    char at(int col, int row) {
        return board[row][col];
    }

    /** @return the world position of the middle of the board */
    Vector3f centre() {
        return root.getWorldTranslation().clone();
    }
}
