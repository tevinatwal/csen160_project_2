package stillasleep.three;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Paints every wall, floor and ceiling picture used in the house, along with
 * the things that hang on the walls (windows, posters, mirrors, doors).
 * Everything is generated in code, so the game ships no image files.
 * Wall textures are {@link #SIZE} pixels square, with y = 0 at the ceiling.
 *
 * <p>The pictures are painted with Java 2D (the {@code Graphics2D} class).
 * In Java 2D, x runs right and y runs <em>down</em>, and
 * {@code fillRect(x, y, width, height)} fills a rectangle whose top-left
 * corner is (x, y). Colours are written as 0xRRGGBB; a {@code Color} made
 * from four numbers has an alpha value last (0 = see-through, 255 = solid).
 *
 * <p>This class only produces arrays of pixels. {@link Build#texture} turns
 * such an array into a texture the engine can draw.
 */
public final class Textures {

    /** Width and height of wall, floor and ceiling textures. */
    public static final int SIZE = 128;

    /** Mid-brown wood. */
    private static final Color WOOD = new Color(0x7a5236);
    /** Dark wood, used for frames, shelves and rails. */
    private static final Color WOOD_DARK = new Color(0x5a3b28);
    /** Light wood, used for the closet door. */
    private static final Color WOOD_LIGHT = new Color(0x9a6b45);
    /** Off-white, used for window and door frames. */
    private static final Color CREAM = new Color(0xe8e4d8);
    /** Pictures painted so far, by name, so each one is only painted once. */
    private static final Map<String, int[]> CACHE = new HashMap<>();

    /** Not used: this class only has static methods and is never created. */
    private Textures() {
    }

    /**
     * Paints one square piece of wall: wallpaper with an optional decoration.
     *
     * @param room  letter of the room whose wallpaper to use: b, c, h, k, l or t
     * @param decor decoration painted on top, or null
     * @return texture pixels, cached after first use
     */
    public static int[] wall(char room, String decor) {
        // The cache key combines the room and decoration, so every different
        // combination gets its own picture. The code inside only runs the
        // first time a combination is asked for.
        return CACHE.computeIfAbsent("wall" + room + decor, k -> {
            BufferedImage image = blank(SIZE, SIZE, false);
            Graphics2D g = graphics(image);
            // Wallpaper first, then the decoration on top of it.
            wallpaper(g, room);
            if (decor != null) {
                decorate(g, decor);
            }
            g.dispose();
            // Speckle the result a little. The seed depends on the room and
            // decoration, so the same wall always gets the same speckles.
            grain(image, 7, room * 31 + (decor == null ? 0 : decor.hashCode()));
            return pixels(image);
        });
    }

    /**
     * Paints a floor: light and dark tiles for rooms k and t, wooden boards
     * for every other room.
     *
     * @param room room the floor belongs to
     * @return floor texture pixels
     */
    public static int[] floor(char room) {
        // There are only two kinds of floor, so only two cache entries.
        boolean tiled = room == 'k' || room == 't';
        return CACHE.computeIfAbsent("floor" + (tiled ? 'k' : 'w'), k -> {
            BufferedImage image = blank(SIZE, SIZE, false);
            Graphics2D g = graphics(image);
            if (tiled) {
                // A 4 x 4 checkerboard of 32-pixel tiles. (tx + ty) is even
                // on one colour of square and odd on the other.
                for (int ty = 0; ty < 4; ty++) {
                    for (int tx = 0; tx < 4; tx++) {
                        g.setColor((tx + ty) % 2 == 0 ? new Color(0xe4dfd0) : new Color(0x4e5560));
                        g.fillRect(tx * 32, ty * 32, 32, 32);
                    }
                }
                // Dark 2-pixel lines between the tiles, across and down.
                g.setColor(new Color(0x2c2f36));
                for (int i = 0; i <= SIZE; i += 32) {
                    g.fillRect(i - 1, 0, 2, SIZE);
                    g.fillRect(0, i - 1, SIZE, 2);
                }
            } else {
                // Wooden boards: 8 rows, each 16 pixels tall. A fixed seed
                // makes the "random" boards the same every time.
                Random random = new Random(5);
                for (int row = 0; row < 8; row++) {
                    // Start each row a random distance off the left edge, so
                    // the ends of the boards do not line up between rows.
                    int x = -random.nextInt(48);
                    while (x < SIZE) {
                        // One board: a random length and a slightly lighter
                        // or darker shade of brown.
                        int length = 40 + random.nextInt(40);
                        int tone = random.nextInt(26) - 13;
                        g.setColor(new Color(0x8a + tone, 0x5c + tone, 0x3c + tone));
                        g.fillRect(x, row * 16, length, 16);
                        // A dark line where this board ends.
                        g.setColor(new Color(0x4a2f1e));
                        g.fillRect(x + length - 1, row * 16, 1, 16);
                        x += length;
                    }
                    // A dark line along the top of the row.
                    g.setColor(new Color(0x4a2f1e));
                    g.fillRect(0, row * 16, SIZE, 1);
                }
            }
            g.dispose();
            grain(image, 8, 99);
            return pixels(image);
        });
    }

    /** @return ceiling texture pixels (plain plaster) */
    public static int[] ceiling() {
        return CACHE.computeIfAbsent("ceiling", k -> {
            // One flat colour; the speckle added by grain() does the rest.
            BufferedImage image = blank(SIZE, SIZE, false);
            Graphics2D g = graphics(image);
            g.setColor(new Color(0xcfc8b8));
            g.fillRect(0, 0, SIZE, SIZE);
            g.dispose();
            grain(image, 9, 4);
            return pixels(image);
        });
    }

    /**
     * Fills a wall with a room's wallpaper, panelling and skirting board.
     *
     * @param g    where to paint
     * @param room letter that picks the style; unknown letters get the
     *             default beige stripes
     */
    private static void wallpaper(Graphics2D g, char room) {
        switch (room) {
            // 'b': blue stripes.  'l': warm brown stripes.
            case 'b' -> striped(g, 0x5b7bb0, 0x6789bf, 0x48628f, 0xdfe6f2);
            case 'l' -> striped(g, 0xb8876a, 0xc39478, 0x7d5a48, 0xf1e6d6);
            case 't' -> {
                // 't': pale blue tiles from top to bottom, drawn as a grid
                // of lines 16 pixels apart, with a darker band half-way up.
                g.setColor(new Color(0xdfeef2));
                g.fillRect(0, 0, SIZE, SIZE);
                g.setColor(new Color(0xa9c4cc));
                for (int i = 0; i <= SIZE; i += 16) {
                    g.fillRect(i, 0, 1, SIZE);
                    g.fillRect(0, i, SIZE, 1);
                }
                g.setColor(new Color(0x6fa6b8));
                g.fillRect(0, 62, SIZE, 5);
            }
            case 'k' -> {
                // 'k': green paint above, cream tiles on the lower part of
                // the wall (from y = 80 down), with a dark green rail between.
                g.setColor(new Color(0x8fb59a));
                g.fillRect(0, 0, SIZE, SIZE);
                g.setColor(new Color(0xe8e4d8));
                g.fillRect(0, 80, SIZE, 48);
                // Grid lines over the tiled part only. "i % 48" keeps the
                // across lines inside the 48-pixel-tall tiled strip.
                g.setColor(new Color(0xb9b4a6));
                for (int i = 0; i <= SIZE; i += 16) {
                    g.fillRect(i, 80, 1, 48);
                    g.fillRect(0, 80 + i % 48, SIZE, 1);
                }
                g.setColor(new Color(0x5e8a70));
                g.fillRect(0, 77, SIZE, 4);
            }
            case 'c' -> {
                // 'c': dark brown wooden planks with a gap every 16 pixels.
                g.setColor(new Color(0x5a4038));
                g.fillRect(0, 0, SIZE, SIZE);
                g.setColor(new Color(0x45302a));
                for (int x = 0; x < SIZE; x += 16) {
                    g.fillRect(x, 0, 2, SIZE);
                }
            }
            // Any other letter (such as 'h'): beige stripes.
            default -> striped(g, 0xc9a878, 0xd5b687, 0x8f6f4a, 0xefe6d2);
        }
        // Every style gets a faint light strip where the wall meets the
        // ceiling, and a dark strip (the skirting board) along the floor.
        g.setColor(new Color(255, 255, 255, 50));
        g.fillRect(0, 0, SIZE, 4);
        g.setColor(new Color(0, 0, 0, 110));
        g.fillRect(0, SIZE - 6, SIZE, 6);
    }

    /**
     * Paints striped wallpaper above wooden panelling, the style most rooms use.
     *
     * @param g      where to paint
     * @param base   wallpaper colour as 0xRRGGBB
     * @param stripe colour of the upright stripes
     * @param panel  colour of the panelling on the lower part of the wall
     * @param rail   colour of the thin rail between wallpaper and panelling
     */
    private static void striped(Graphics2D g, int base, int stripe, int panel, int rail) {
        // Background colour, then a 5-pixel-wide stripe every 16 pixels.
        g.setColor(new Color(base));
        g.fillRect(0, 0, SIZE, SIZE);
        g.setColor(new Color(stripe));
        for (int x = 4; x < SIZE; x += 16) {
            g.fillRect(x, 0, 5, SIZE);
        }
        // Panelling covers the wall from y = 84 down to the floor.
        g.setColor(new Color(panel));
        g.fillRect(0, 84, SIZE, 44);
        // Faint outlined rectangles make it look like separate panels.
        g.setColor(new Color(0, 0, 0, 40));
        for (int x = 0; x < SIZE; x += 32) {
            g.drawRect(x + 4, 92, 24, 26);
        }
        // The rail hides the join between wallpaper and panelling.
        g.setColor(new Color(rail));
        g.fillRect(0, 81, SIZE, 4);
    }

    /**
     * Paints one decoration over the wallpaper.
     *
     * @param g     where to paint
     * @param decor name of the decoration; unknown names paint nothing
     */
    private static void decorate(Graphics2D g, String decor) {
        switch (decor) {
            case "window" -> window(g, false);
            case "windowDay" -> window(g, true);
            case "poster" -> poster(g);
            case "bookshelf" -> bookshelf(g);
            case "clothes" -> clothes(g);
            case "mirror" -> mirror(g, true);
            case "mirrorEmpty" -> mirror(g, false);
            case "photos" -> photos(g);
            case "frontdoor" -> door(g, new Color(0x7a3f38), new Color(0x64322c), true);
            case "closet" -> {
                // A plain wooden door, plus a purple glow along the bottom
                // that fades from see-through (alpha 0) at y = 104 to almost
                // solid at the floor, as if light were leaking from under it.
                door(g, WOOD_LIGHT, WOOD, false);
                g.setPaint(new GradientPaint(0, 104, new Color(176, 112, 255, 0), 0, 128,
                        new Color(190, 120, 255, 230)));
                g.fillRect(34, 104, 60, 24);
            }
            case "cabinets" -> cabinets(g);
            case "fridge" -> fridge(g);
            default -> { }
        }
    }

    /**
     * Paints a window with curtains.
     *
     * @param g   where to paint
     * @param day true for a daytime view (blue sky, clouds, grass), false for
     *            a night view (dark sky, moon, stars)
     */
    private static void window(Graphics2D g, boolean day) {
        // Cream frame; the view is painted inside it, 4 pixels smaller all round.
        g.setColor(CREAM);
        g.fillRect(28, 20, 72, 66);
        if (day) {
            // Sky that fades from blue at the top to nearly white lower down.
            // A GradientPaint blends between two colours along a line.
            g.setPaint(new GradientPaint(0, 24, new Color(0x7fc4ff), 0, 82, new Color(0xe8f6ff)));
            g.fillRect(32, 24, 64, 58);
            // Three white ovals as clouds, and a strip of green grass.
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Double(40, 34, 22, 9));
            g.fill(new Ellipse2D.Double(50, 30, 16, 10));
            g.fill(new Ellipse2D.Double(70, 50, 20, 8));
            g.setColor(new Color(0x7cc47a));
            g.fillRect(32, 72, 64, 10);
        } else {
            // Night sky, from very dark blue down to a lighter blue.
            g.setPaint(new GradientPaint(0, 24, new Color(0x0d1330), 0, 82, new Color(0x3a4a86)));
            g.fillRect(32, 24, 64, 58);
            // A pale round moon, and five single-pixel stars.
            g.setColor(new Color(0xf4f1dc));
            g.fill(new Ellipse2D.Double(72, 30, 12, 12));
            g.setColor(Color.WHITE);
            g.fillRect(40, 34, 1, 1);
            g.fillRect(52, 46, 1, 1);
            g.fillRect(60, 30, 1, 1);
            g.fillRect(86, 58, 1, 1);
            g.fillRect(44, 64, 1, 1);
        }
        // The bars that split the glass into four panes, and the sill below.
        g.setColor(CREAM);
        g.fillRect(63, 24, 3, 58);
        g.fillRect(32, 51, 64, 3);
        g.fillRect(24, 86, 80, 4);

        // Red curtains. Each one is a closed outline: along the top, then a
        // curve (quadTo) sweeping down and outward, then back up the outer
        // edge. The right curtain is a mirror image of the left.
        g.setColor(new Color(0xb85c5c));
        Path2D left = new Path2D.Double();
        left.moveTo(24, 16);
        left.lineTo(46, 16);
        left.quadTo(42, 60, 30, 90);
        left.lineTo(24, 90);
        left.closePath();
        g.fill(left);
        Path2D right = new Path2D.Double();
        right.moveTo(104, 16);
        right.lineTo(82, 16);
        right.quadTo(86, 60, 98, 90);
        right.lineTo(104, 90);
        right.closePath();
        g.fill(right);

        // The curtain rail across the top.
        g.setColor(WOOD_DARK);
        g.fillRect(20, 13, 88, 4);
    }

    /**
     * Paints a "HANG IN THERE" poster of a cat hanging from a branch.
     *
     * @param g where to paint
     */
    private static void poster(Graphics2D g) {
        // Paper, with a sky-blue picture area inside it.
        g.setColor(new Color(0xf1e6c8));
        g.fillRect(42, 22, 44, 58);
        g.setColor(new Color(0x8ecae6));
        g.fillRect(45, 25, 38, 42);
        // The branch: a thin brown line across the picture.
        g.setColor(new Color(0x5a4636));
        g.fillRect(45, 34, 38, 2);
        // The orange cat: a tall oval for the body and a rounder one for the head.
        g.setColor(new Color(0xe08a3c));
        g.fill(new Ellipse2D.Double(57, 36, 14, 24));
        g.fill(new Ellipse2D.Double(56, 28, 16, 13));
        // The caption, in tiny bold letters under the picture.
        g.setColor(new Color(0x3a2a22));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 5));
        g.drawString("HANG IN THERE", 44, 76);
    }

    /**
     * Paints a bookshelf full of books.
     *
     * @param g where to paint
     */
    private static void bookshelf(Graphics2D g) {
        // The wooden case, and its darker inside.
        g.setColor(WOOD_DARK);
        g.fillRect(28, 30, 72, 98);
        g.setColor(new Color(0x33221a));
        g.fillRect(32, 34, 64, 90);
        // A fixed seed gives the same "random" books every time.
        Random random = new Random(11);
        int[] colors = {0xc45a5a, 0x5a7fc4, 0xd9b44a, 0x6fae6f, 0x9a6ac4, 0xe0e0d0, 0xd9773a};
        for (int shelf = 0; shelf < 4; shelf++) {
            // shelfY is the top of the shelf board, 23 pixels below the last.
            int shelfY = 56 + shelf * 23;
            int x = 33;
            // Fill the shelf from left to right with books of random width,
            // height and colour, leaving a 1-pixel gap between them.
            while (x < 92) {
                int w = 3 + random.nextInt(4);
                int h = 12 + random.nextInt(8);
                g.setColor(new Color(colors[random.nextInt(colors.length)]));
                // A book stands on the shelf, so its top is shelfY minus its height.
                g.fillRect(x, shelfY - h, w, h);
                x += w + 1;
            }
            // The shelf board itself.
            g.setColor(WOOD_DARK);
            g.fillRect(32, shelfY, 64, 3);
        }
    }

    /**
     * Paints a row of shirts hanging from a rail, for inside the closet.
     *
     * @param g where to paint
     */
    private static void clothes(Graphics2D g) {
        // A wooden shelf, with a grey metal rail under it.
        g.setColor(WOOD_DARK);
        g.fillRect(0, 22, SIZE, 4);
        g.setColor(new Color(0xb9b9c4));
        g.fillRect(0, 34, SIZE, 2);
        int[] colors = {0xc45a5a, 0x5a7fc4, 0xd9b44a, 0x6fae6f, 0x9a6ac4};
        for (int i = 0; i < colors.length; i++) {
            // One shirt every 24 pixels. Its outline starts at the hanger
            // point on the rail, goes out to a shoulder, down to the hem,
            // across, and back up the other side.
            int x = 16 + i * 24;
            Path2D shirt = new Path2D.Double();
            shirt.moveTo(x, 36);
            shirt.lineTo(x - 11, 46);
            // (i % 2) * 10 makes every second shirt 10 pixels longer.
            shirt.lineTo(x - 9, 88 + (i % 2) * 10);
            shirt.lineTo(x + 9, 88 + (i % 2) * 10);
            shirt.lineTo(x + 11, 46);
            shirt.closePath();
            g.setColor(new Color(colors[i]));
            g.fill(shirt);
        }
    }

    /**
     * Paints an oval mirror in a gold frame.
     *
     * @param g          where to paint
     * @param reflection true to show the kid in the glass, false for a
     *                   mirror with nobody in it
     */
    private static void mirror(Graphics2D g, boolean reflection) {
        // Gold frame, then a slightly smaller oval of glass on top. The
        // glass fades from light to darker blue, corner to corner.
        g.setColor(new Color(0xc9a24a));
        g.fill(new Ellipse2D.Double(44, 22, 40, 66));
        Ellipse2D glass = new Ellipse2D.Double(48, 26, 32, 58);
        g.setPaint(new GradientPaint(48, 26, new Color(0xcfe3ee), 80, 84, new Color(0x7fa2b8)));
        g.fill(glass);
        if (!reflection) {
            // Empty mirror: just a short white streak as a glint of light.
            g.setColor(new Color(255, 255, 255, 110));
            g.setStroke(new BasicStroke(2f));
            g.drawLine(54, 44, 60, 34);
            return;
        }
        // the kid looking back
        // setClip limits painting to the glass, so the body is cut off
        // neatly at the edge of the oval instead of covering the frame.
        g.setClip(glass);
        // Teal shirt, then the face, hair and two eyes.
        g.setColor(new Color(0x3fa7a0));
        g.fill(new RoundRectangle2D.Double(52, 66, 24, 24, 10, 10));
        g.setColor(new Color(0xf2c9a1));
        g.fill(new Ellipse2D.Double(55, 46, 18, 20));
        g.setColor(new Color(0x4a2f24));
        g.fill(new Ellipse2D.Double(54, 43, 20, 11));
        g.setColor(new Color(0x2a1c18));
        g.fill(new Ellipse2D.Double(59, 55, 2, 3));
        g.fill(new Ellipse2D.Double(67, 55, 2, 3));
        // Turn the clip off again, and add the same glint on top.
        g.setClip(null);
        g.setColor(new Color(255, 255, 255, 110));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(54, 44, 60, 34);
    }

    /**
     * Paints three framed family photos.
     *
     * @param g where to paint
     */
    private static void photos(Graphics2D g) {
        // One row per photo: {left, top, width, height, background colour}.
        int[][] frames = {{22, 34, 26, 32, 0x7fa8c9}, {56, 40, 32, 24, 0xc9a07f},
            {94, 32, 24, 30, 0x9ac98a}};
        for (int[] f : frames) {
            // The wooden frame is 3 pixels bigger than the photo on every side.
            g.setColor(WOOD_DARK);
            g.fillRect(f[0] - 3, f[1] - 3, f[2] + 6, f[3] + 6);
            g.setColor(new Color(f[4]));
            g.fillRect(f[0], f[1], f[2], f[3]);
            // Two simple people: a round head each, then a body each. Their
            // places are fractions of the photo's size, so the same drawing
            // fits all three photos.
            g.setColor(new Color(0xf2c9a1));
            g.fill(new Ellipse2D.Double(f[0] + f[2] * 0.2, f[1] + f[3] * 0.3, 6, 6));
            g.fill(new Ellipse2D.Double(f[0] + f[2] * 0.55, f[1] + f[3] * 0.22, 7, 7));
            g.setColor(new Color(0x4a5a8a));
            g.fillRect((int) (f[0] + f[2] * 0.16), (int) (f[1] + f[3] * 0.55), 9,
                    (int) (f[3] * 0.45));
            g.setColor(new Color(0x8a4a5a));
            g.fillRect((int) (f[0] + f[2] * 0.52), (int) (f[1] + f[3] * 0.5), 10,
                    (int) (f[3] * 0.5));
        }
    }

    /**
     * Paints a door in its frame.
     *
     * @param g     where to paint
     * @param main  colour of the door
     * @param panel colour of the sunken panels on the door
     * @param glass true for a front door with a dark window at the top,
     *              false for an inside door with two more panels there
     */
    private static void door(Graphics2D g, Color main, Color panel, boolean glass) {
        // Cream frame, with the door itself inside it. Both reach the floor.
        g.setColor(CREAM);
        g.fillRect(34, 20, 60, 108);
        g.setColor(main);
        g.fillRect(38, 24, 52, 104);
        // Two tall panels on the lower part of the door.
        g.setColor(panel);
        g.fillRect(44, 62, 17, 56);
        g.fillRect(67, 62, 17, 56);
        if (glass) {
            // A rounded window showing the night sky outside.
            g.setPaint(new GradientPaint(0, 30, new Color(0x121a3a), 0, 54, new Color(0x2a3560)));
            g.fill(new RoundRectangle2D.Double(46, 30, 36, 24, 6, 6));
        } else {
            // Two short panels at the top (still in the panel colour).
            g.fillRect(44, 30, 17, 24);
            g.fillRect(67, 30, 17, 24);
        }
        // A round brass door knob.
        g.setColor(new Color(0xe3c26a));
        g.fill(new Ellipse2D.Double(82, 76, 6, 6));
    }

    /**
     * Paints kitchen cabinets: a row high on the wall and a row at the floor.
     *
     * @param g where to paint
     */
    private static void cabinets(Graphics2D g) {
        // The two rows of cabinets, as teal strips right across the wall.
        g.setColor(new Color(0x4f7f7a));
        g.fillRect(0, 14, SIZE, 38);
        g.fillRect(0, 86, SIZE, 42);
        // A darker door every 32 pixels, on both rows.
        g.setColor(new Color(0x3f6a66));
        for (int x = 4; x < SIZE; x += 32) {
            g.fillRect(x, 18, 24, 30);
            g.fillRect(x, 92, 24, 30);
        }
        // A small cream handle on each door.
        g.setColor(CREAM);
        for (int x = 4; x < SIZE; x += 32) {
            g.fillRect(x + 20, 38, 2, 7);
            g.fillRect(x + 11, 95, 3, 5);
        }
        // The worktop on the lower row, and a shadow where it meets the floor.
        g.fillRect(0, 81, SIZE, 6);
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRect(0, 122, SIZE, 6);
    }

    /**
     * Paints a fridge with magnets on its door.
     *
     * @param g where to paint
     */
    private static void fridge(Graphics2D g) {
        // The body: a pale rectangle with rounded corners.
        g.setColor(new Color(0xdfe6ea));
        g.fill(new RoundRectangle2D.Double(28, 18, 72, 110, 8, 8));
        // Grey details: the line between the freezer and fridge doors, and
        // a handle on each door.
        g.setColor(new Color(0xaab6bd));
        g.fillRect(28, 58, 72, 3);
        g.fillRect(36, 32, 4, 18);
        g.fillRect(36, 68, 4, 34);
        // Three little square magnets: red, yellow and green.
        g.setColor(new Color(0xd9695f));
        g.fillRect(70, 28, 8, 8);
        g.setColor(new Color(0xd9b44a));
        g.fillRect(82, 38, 8, 8);
        g.setColor(new Color(0x6fae6f));
        g.fillRect(64, 42, 7, 7);
    }

    /**
     * Makes an empty picture to paint on.
     *
     * @param width       width in pixels
     * @param height      height in pixels
     * @param transparent true if the picture needs see-through pixels
     * @return the new picture
     */
    private static BufferedImage blank(int width, int height, boolean transparent) {
        // ARGB stores an alpha (see-through) value for each pixel; RGB does not.
        return new BufferedImage(width, height,
                transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    }

    /**
     * Gets the "paintbrush" for a picture, set up for smooth results.
     *
     * @param image picture to paint on
     * @return a Graphics2D that paints onto the picture
     */
    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        // Anti-aliasing softens the edges of shapes and letters, so curves
        // and slanted lines do not look like staircases.
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    /**
     * Adds slight random brightness variation so surfaces don't look flat.
     *
     * @param image  picture to change
     * @param amount the most that a pixel may be brightened or darkened (0-255 scale)
     * @param seed   starting number for the random generator; the same seed
     *               always gives the same pattern
     */
    private static void grain(BufferedImage image, int amount, long seed) {
        Random random = new Random(seed);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                // A random change between -amount and +amount.
                int delta = random.nextInt(amount * 2 + 1) - amount;
                // Take the pixel apart into red, green and blue, add the same
                // change to each (so the shade changes, not the colour), and
                // keep every result inside 0-255.
                int r = clamp(((rgb >> 16) & 0xff) + delta);
                int gr = clamp(((rgb >> 8) & 0xff) + delta);
                int b = clamp((rgb & 0xff) + delta);
                // Put the three parts back together into one 0xRRGGBB number.
                image.setRGB(x, y, (r << 16) | (gr << 8) | b);
            }
        }
    }

    /**
     * Keeps a colour value inside the allowed range.
     *
     * @param value any number
     * @return the value, but no lower than 0 and no higher than 255
     */
    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    /**
     * Reads every pixel out of a picture.
     *
     * @param image picture to read
     * @return its pixels row by row, top row first
     */
    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0,
                image.getWidth());
    }
}
