package stillasleep.three;

import com.jme3.font.BitmapText;
import com.jme3.light.AmbientLight;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Sphere;
import com.jme3.texture.Texture2D;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared code for every chapter that takes place in the house. It knows how
 * to build the rooms and furniture, lets the player walk around and inspect
 * things, and leaves the story of each chapter to its subclass.
 *
 * <p>All positions are in metres: x grows to the east, z grows to the south
 * and y is height above the floor. Seen from above the house looks like this:
 * the closet is west of the bedroom, the kitchen is east of it, a long
 * hallway runs along the south side of those rooms, and the bathroom and
 * living room are south of the hallway.
 *
 * <p>A subclass sets flags such as {@link #day} or {@link #closetHole} in its
 * constructor, builds the house in {@link #buildChapter()}, and overrides
 * hooks such as {@link #chapterUpdate}, {@link #interact} and
 * {@link #describe} to tell its part of the story.
 */
abstract class HouseStage extends Stage {

    /** Height of every ceiling, in metres. */
    protected static final float CEILING = 2.6f;
    /** Height of the top of every doorway, in metres. */
    protected static final float DOOR_HEIGHT = 2.05f;
    /** Wall side code: the north wall of a room (its smallest z). */
    protected static final int NORTH = 0;
    /** Wall side code: the south wall of a room (its largest z). */
    protected static final int SOUTH = 1;
    /** Wall side code: the west wall of a room (its smallest x). */
    protected static final int WEST = 2;
    /** Wall side code: the east wall of a room (its largest x). */
    protected static final int EAST = 3;
    /** How far away, in metres, the player can be and still inspect something. */
    private static final float REACH = 2.3f;

    /**
     * Something the player can look at and inspect.
     *
     * @param id       short name used to decide what happens, such as "bed"
     * @param position the point in the world the player has to look at
     */
    protected record Spot(String id, Vector3f position) {
    }

    /** Everything in this house that the player can inspect. */
    protected final List<Spot> spots = new ArrayList<>();
    /** Ids of things that are deliberately wrong in this chapter. */
    protected final Set<String> odd = new HashSet<>();
    /** Helper that makes materials, flat panels, boxes and furniture models. */
    protected Build build;
    /** Where new walls and floors are attached; a chapter may redirect this. */
    protected Node into = scene;
    /** Where new collision boxes go; a chapter may redirect this. */
    protected List<Aabb> intoSolids = solids;

    /** True for a daylight house, false for the usual night. */
    protected boolean day;
    /** True if there is a hole in the closet floor. */
    protected boolean closetHole;
    /** True if the electricity is off: no lamps are lit anywhere. */
    protected boolean powerOut;
    /** The glowing front of the television, so a chapter can change its colour. */
    protected Material tvScreen;
    /** If set, the bedside clock shows exactly this. */
    protected String clockOverride;
    /** True if the bedside clock runs backwards. */
    protected boolean clockBackwards;
    /** Walking speed in metres per second. */
    protected float walkSpeed = 3.1f;
    /** True if the player is allowed to jump in this chapter. */
    protected boolean canJump;
    /** While true the player cannot move or interact (a chapter is doing something). */
    protected boolean busy;
    /** While true the chapter controls the camera itself. */
    protected boolean ownCamera;
    /** Runs once when the open dialog box is closed. */
    protected Runnable afterDialog;
    /** The chess board on the kitchen table. */
    protected ChessSet chess;
    /** Seconds since this chapter started. */
    protected float time;
    /**
     * The moment, measured on {@link #time}, when the hint text at the bottom of
     * the screen is hidden. 0 means the hint is never hidden automatically.
     */
    protected float hintSeconds;

    /** Wall, floor and ceiling materials made so far, so each is only made once. */
    private final Map<String, Material> surfaces = new HashMap<>();
    /** The closet door (slab and knob). It turns around its hinge when opened. */
    private Node closetDoor;
    /** The collision box that blocks the closet doorway while the door is shut. */
    private Aabb closetDoorBox;
    /** True once the player has opened the closet. */
    protected boolean closetOpen;
    /** How far the closet door has swung: 0 is shut, 1 is fully open. */
    private float doorSwing;
    /** The purple light inside the closet hole, or null if there is no hole. */
    private PointLight holeLight;
    /** The red digits of the bedside clock, drawn as text inside the 3D world. */
    private BitmapText clock;
    /** The time the bedside clock is showing right now. */
    protected String shownClock = "";

    /**
     * Creates the stage. Nothing is built yet; that happens in {@link #build()}.
     *
     * @param game the running game
     */
    protected HouseStage(Game3D game) {
        super(game);
    }

    /** Builds this chapter's version of the house and places the player. */
    protected abstract void buildChapter();

    /**
     * Per-frame chapter logic, run after the shared movement and interaction.
     *
     * @param tpf seconds since the last frame
     */
    protected void chapterUpdate(float tpf) {
    }

    /**
     * Per-frame animation that keeps running even while a dialog is open.
     *
     * @param tpf seconds since the last frame
     */
    protected void animate(float tpf) {
    }

    /**
     * Called once when the chapter starts. Sets the background colour and then
     * lets the chapter build its own version of the house.
     */
    @Override
    protected final void build() {
        build = game.build();
        // The background is what shows where there is no wall: pale blue sky in the day, almost
        // black at night.
        game.getViewPort().setBackgroundColor(day ? new ColorRGBA(0.6f, 0.8f, 1f, 1f)
                : new ColorRGBA(0.02f, 0.02f, 0.05f, 1f));
        buildChapter();
    }

    // ------------------------------------------------------------- building

    /**
     * The bedroom and closet, which appear in every house chapter. Builds the
     * walls, the furniture and the bedroom lights.
     */
    protected void buildBedroomAndCloset() {
        // Bedroom: x 0 to 4.6, z 0 to 4. Its south wall has a doorway to the hallway (x 3.2 to
        // 4.1) and its west wall has a doorway to the closet (z 1.0 to 1.9).
        room('b', 0f, 0f, 4.6f, 4f, true, new float[] {SOUTH, 3.2f, 4.1f},
                new float[] {WEST, 1.0f, 1.9f});
        // Closet: a small room west of the bedroom. No floor is laid here because buildCloset()
        // does it (the floor may have a hole in it).
        room('c', -1.55f, 0.2f, -0.15f, 2.2f, false, new float[] {EAST, 1.0f, 1.9f});
        // Door frames that fill the gap between the rooms at those two doorways.
        doorFrame(true, 3.2f, 4.1f, 4.0f, 4.15f);
        doorFrame(false, 1.0f, 1.9f, -0.15f, 0f);
        buildCloset();
        furnishBedroom();
        // Lights. A PointLight is a bulb: (position, colour, how many metres it reaches).
        // Day: sunlight coming in above the desk.
        if (day) {
            scene.addLight(new PointLight(new Vector3f(1.85f, 1.6f, 1.0f),
                    new ColorRGBA(1f, 0.97f, 0.88f, 1f).mult(1.1f), 8f));
        } else if (powerOut) {
            // Power cut: only a faint blue glow of moonlight by the window.
            scene.addLight(new PointLight(new Vector3f(1.85f, 1.7f, 0.5f),
                    new ColorRGBA(0.35f, 0.45f, 0.9f, 1f), 4f));
        } else {
            // Normal night: the warm bedside lamp plus the same blue moonlight.
            scene.addLight(new PointLight(new Vector3f(3.2f, 1.05f, 0.45f),
                    new ColorRGBA(1f, 0.82f, 0.56f, 1f).mult(1.05f), 6f));
            scene.addLight(new PointLight(new Vector3f(1.85f, 1.7f, 0.5f),
                    new ColorRGBA(0.35f, 0.45f, 0.9f, 1f), 4f));
        }
    }

    /**
     * The rest of the house: hallway, kitchen, bathroom and living room, with
     * their furniture and lights.
     */
    protected void buildHallwayAndKitchen() {
        // Hallway: the long corridor south of the bedroom (x -1.55 to 11, z 4.15 to 5.75).
        // Doorways go north to the bedroom and kitchen, south to the bathroom and living room.
        room('h', -1.55f, 4.15f, 11f, 5.75f, true, new float[] {NORTH, 3.2f, 4.1f},
                new float[] {NORTH, 6.5f, 7.4f}, new float[] {SOUTH, 0.2f, 1.1f},
                new float[] {SOUTH, 7.2f, 8.1f});
        // Kitchen: the north-east room, east of the bedroom.
        room('k', 6f, 0f, 10.6f, 4f, true, new float[] {SOUTH, 6.5f, 7.4f});
        // Bathroom: the south-west room.
        room('t', -1.55f, 5.9f, 1.6f, 8.6f, true, new float[] {NORTH, 0.2f, 1.1f});
        // Living room: the big south-east room.
        room('l', 3.2f, 5.9f, 10.6f, 10.4f, true, new float[] {NORTH, 7.2f, 8.1f});
        // Door frames for the kitchen, bathroom and living room doorways.
        doorFrame(true, 6.5f, 7.4f, 4.0f, 4.15f);
        doorFrame(true, 0.2f, 1.1f, 5.75f, 5.9f);
        doorFrame(true, 7.2f, 8.1f, 5.75f, 5.9f);
        // Put the furniture in each room.
        furnishHallway();
        furnishKitchen();
        furnishBathroom();
        furnishLivingRoom();
        // Lights. "warm" is the orange-white colour of an ordinary light bulb.
        ColorRGBA warm = new ColorRGBA(1f, 0.82f, 0.56f, 1f);
        // Day: sunlight in the kitchen, a soft light in the hallway, sunlight in the living
        // room and a dim light in the bathroom.
        if (day) {
            scene.addLight(new PointLight(new Vector3f(7.3f, 1.6f, 1.0f),
                    new ColorRGBA(1f, 0.97f, 0.88f, 1f).mult(1.1f), 8f));
            scene.addLight(new PointLight(new Vector3f(4.7f, 2.0f, 4.95f),
                    new ColorRGBA(1f, 0.96f, 0.9f, 1f).mult(0.7f), 9f));
            scene.addLight(new PointLight(new Vector3f(6.5f, 1.7f, 9.2f),
                    new ColorRGBA(1f, 0.97f, 0.88f, 1f).mult(1.1f), 9f));
            scene.addLight(new PointLight(new Vector3f(0f, 1.8f, 7.6f),
                    new ColorRGBA(1f, 0.98f, 0.95f, 1f).mult(0.35f), 5f));
        } else if (!powerOut) {
            // Night with the power on: one light under each ceiling lamp (living room,
            // bathroom, two in the hallway, kitchen). In a power cut no lights are added at
            // all.
            scene.addLight(new PointLight(new Vector3f(6.6f, 2.1f, 8.1f), warm, 7.5f));
            scene.addLight(new PointLight(new Vector3f(0f, 2.1f, 7.2f),
                    new ColorRGBA(0.9f, 0.95f, 1f, 1f).mult(0.9f), 5f));
            scene.addLight(new PointLight(new Vector3f(2.0f, 2.1f, 4.95f), warm, 7f));
            scene.addLight(new PointLight(new Vector3f(8.0f, 2.1f, 4.95f), warm, 7f));
            scene.addLight(new PointLight(new Vector3f(8.3f, 2.1f, 2.0f),
                    new ColorRGBA(1f, 0.93f, 0.78f, 1f).mult(1.2f), 7.5f));
        }
    }

    /** Adds the dim overall light every chapter needs so nothing is pitch black. */
    protected void addAmbient() {
        // A pale grey in the day, a dark blue at night.
        scene.addLight(new AmbientLight(day ? new ColorRGBA(0.6f, 0.6f, 0.56f, 1f)
                : new ColorRGBA(0.17f, 0.18f, 0.27f, 1f)));
    }

    /**
     * Stands the player by the bed and opens the "just woke up" dialog.
     *
     * @param lines dialog pages
     */
    protected void wake(String... lines) {
        // Stand next to the bed with the feet just above the floor. A yaw of a quarter turn
        // (HALF_PI) looks south; the extra 0.5 radians turns a little further, toward the room.
        body.place(3.1f, 0.02f, 1.6f, FastMath.HALF_PI + 0.5f);
        game.hud().openDialog(lines);
    }

    /**
     * Gets the wallpaper for a room. It is made the first time it is asked for
     * and then reused.
     *
     * @param room letter of the room (b, c, h, k, t or l)
     * @return the wall material
     */
    protected Material wallpaper(char room) {
        return surfaces.computeIfAbsent("wall" + room, k -> build.lit(
                build.texture(Textures.wall(room, null), Textures.SIZE, Textures.SIZE)));
    }

    /**
     * Gets the floor surface for a room, making it only once.
     *
     * @param room letter of the room (b, c, h, k, t or l)
     * @return the floor material
     */
    private Material floorMaterial(char room) {
        return surfaces.computeIfAbsent("floor" + room, k -> build.lit(
                build.texture(Textures.floor(room), Textures.SIZE, Textures.SIZE)));
    }

    /**
     * Gets the ceiling surface, which is the same in every room.
     *
     * @return the ceiling material
     */
    private Material ceilingMaterial() {
        return surfaces.computeIfAbsent("ceiling", k -> build.lit(
                build.texture(Textures.ceiling(), Textures.SIZE, Textures.SIZE)));
    }

    /**
     * Builds the four walls, floor and ceiling of a rectangular room.
     *
     * @param tex   letter that picks the wallpaper and floor style: b bedroom,
     *              c closet, h hallway, k kitchen, t bathroom, l living room
     * @param x0    west edge
     * @param z0    north edge
     * @param x1    east edge
     * @param z1    south edge
     * @param floor whether to lay a full floor
     * @param doors openings as {side, from, to}: which wall, and where along that
     *              wall the doorway starts and ends. List them in order along the wall.
     */
    protected void room(char tex, float x0, float z0, float x1, float z1, boolean floor,
            float[]... doors) {
        Material wall = wallpaper(tex);
        // Build the four walls one after another.
        for (int side = 0; side < 4; side++) {
            // North and south walls run along x; west and east walls run along z. "fixed" is
            // the coordinate that is the same along the whole wall.
            boolean alongX = side == NORTH || side == SOUTH;
            float start = alongX ? x0 : z0;
            float end = alongX ? x1 : z1;
            float fixed = side == NORTH ? z0 : side == SOUTH ? z1 : side == WEST ? x0 : x1;
            // Leave a gap for every doorway that is on this wall.
            for (float[] door : doors) {
                if ((int) door[0] != side) {
                    continue;
                }
                // Full-height wall up to the doorway, then a short piece above the doorway.
                wallPiece(wall, side, fixed, start, door[1], 0, CEILING);
                wallPiece(wall, side, fixed, door[1], door[2], DOOR_HEIGHT, CEILING);
                start = door[2];
            }
            // The rest of the wall after the last doorway.
            wallPiece(wall, side, fixed, start, end, 0, CEILING);
        }
        if (floor) {
            floorPiece(tex, x0, z0, x1, z1);
        }
        ceilingPiece(x0, z0, x1, z1);
    }

    /**
     * A rectangle of ceiling, visible from below. It has no collision box.
     *
     * @param x0 west edge
     * @param z0 north edge
     * @param x1 east edge
     * @param z1 south edge
     */
    protected void ceilingPiece(float x0, float z0, float x1, float z1) {
        Material ceiling = ceilingMaterial();
        // Dividing by 1.3 makes the ceiling picture repeat every 1.3 metres.
        into.attachChild(build.panel(new Vector3f(x0, CEILING, z0), new Vector3f(x1 - x0, 0, 0),
                new Vector3f(0, 0, z1 - z0), x0 / 1.3f, x1 / 1.3f, z0 / 1.3f, z1 / 1.3f, ceiling));
    }

    /**
     * One flat piece of wall facing into its room, plus its collision box.
     *
     * @param material wallpaper to use
     * @param side     which wall of the room: NORTH, SOUTH, WEST or EAST
     * @param fixed    where the wall is: its z for north/south, its x for west/east
     * @param a        where the piece starts along the wall
     * @param b        where the piece ends along the wall
     * @param y0       height of its bottom edge
     * @param y1       height of its top edge
     */
    protected void wallPiece(Material material, int side, float fixed, float a, float b, float y0,
            float y1) {
        // A piece with no width (a doorway right at a corner) is skipped.
        if (b - a < 0.001f) {
            return;
        }
        // "up" is the vertical edge of the panel. v0 and v1 pick the matching strip of the
        // wallpaper picture so the pattern lines up from piece to piece.
        Vector3f up = new Vector3f(0, y1 - y0, 0);
        float v0 = y0 / CEILING;
        float v1 = y1 / CEILING;
        // Thickness of the invisible collision box behind the wall.
        float t = 0.075f;
        Geometry panel;
        Aabb box;
        // Each side draws its panel in a different direction so the visible face points into
        // the room, and puts its collision box just outside the room.
        switch (side) {
            case NORTH -> {
                panel = build.panel(new Vector3f(a, y0, fixed), new Vector3f(b - a, 0, 0), up,
                        a / CEILING, b / CEILING, v0, v1, material);
                box = new Aabb(a, 0, fixed - t, b, CEILING, fixed);
            }
            case SOUTH -> {
                panel = build.panel(new Vector3f(b, y0, fixed), new Vector3f(a - b, 0, 0), up,
                        -b / CEILING, -a / CEILING, v0, v1, material);
                box = new Aabb(a, 0, fixed, b, CEILING, fixed + t);
            }
            case WEST -> {
                panel = build.panel(new Vector3f(fixed, y0, b), new Vector3f(0, 0, a - b), up,
                        -b / CEILING, -a / CEILING, v0, v1, material);
                box = new Aabb(fixed - t, 0, a, fixed, CEILING, b);
            }
            default -> {
                panel = build.panel(new Vector3f(fixed, y0, a), new Vector3f(0, 0, b - a), up,
                        a / CEILING, b / CEILING, v0, v1, material);
                box = new Aabb(fixed, 0, a, fixed + t, CEILING, b);
            }
        }
        into.attachChild(panel);
        // Only pieces that reach the floor block the player. The piece above a doorway must
        // not, or nobody could walk through.
        if (y0 == 0) {
            intoSolids.add(box);
        }
    }

    /**
     * A rectangle of floor with a collision slab to stand on.
     *
     * @param tex letter of the room whose floor style to use
     * @param x0  west edge
     * @param z0  north edge
     * @param x1  east edge
     * @param z1  south edge
     */
    protected void floorPiece(char tex, float x0, float z0, float x1, float z1) {
        Material material = floorMaterial(tex);
        // The visible floor, facing up, with its picture repeating every 1.3 metres.
        into.attachChild(build.panel(new Vector3f(x0, 0, z0), new Vector3f(0, 0, z1 - z0),
                new Vector3f(x1 - x0, 0, 0), z0 / 1.3f, z1 / 1.3f, x0 / 1.3f, x1 / 1.3f, material));
        // A 30 cm thick solid slab just under it.
        intoSolids.add(new Aabb(x0, -0.3f, z0, x1, 0, z1));
    }

    /**
     * The wooden frame that joins two rooms at a doorway.
     *
     * @param alongX true if the opening runs east-west (a north/south doorway)
     * @param from   start of the opening
     * @param to     end of the opening
     * @param gap0   near edge of the gap between the two rooms
     * @param gap1   far edge of the gap
     */
    protected void doorFrame(boolean alongX, float from, float to, float gap0, float gap1) {
        // Cream paint for the frame and brown wood for the strip on the floor.
        Material trim = build.color(0xe8e4d8);
        Material wood = build.color(0x7a5236);
        // Middle of the gap and half its thickness (a little extra so the frame sticks out of
        // the wall).
        float mid = (gap0 + gap1) / 2;
        float depth = (gap1 - gap0) / 2 + 0.03f;
        // Middle and half-width of the opening itself.
        float centre = (from + to) / 2;
        float half = (to - from) / 2;
        if (alongX) {
            // Doorway in a north/south wall: the two side posts and the beam across the top.
            scene.attachChild(build.box(from - 0.04f, DOOR_HEIGHT / 2, mid, 0.04f, DOOR_HEIGHT / 2,
                    depth, trim));
            scene.attachChild(build.box(to + 0.04f, DOOR_HEIGHT / 2, mid, 0.04f, DOOR_HEIGHT / 2,
                    depth, trim));
            scene.attachChild(build.box(centre, DOOR_HEIGHT + 0.04f, mid, half + 0.08f, 0.04f,
                    depth, trim));
            // The wooden strip on the floor, and a slab so the player can walk across the gap.
            scene.attachChild(build.box(centre, -0.05f, mid, half, 0.05f, depth - 0.03f, wood));
            solids.add(new Aabb(from, -0.3f, gap0, to, 0, gap1));
        } else {
            // Doorway in a west/east wall: the same parts, turned a quarter turn.
            scene.attachChild(build.box(mid, DOOR_HEIGHT / 2, from - 0.04f, depth, DOOR_HEIGHT / 2,
                    0.04f, trim));
            scene.attachChild(build.box(mid, DOOR_HEIGHT / 2, to + 0.04f, depth, DOOR_HEIGHT / 2,
                    0.04f, trim));
            scene.attachChild(build.box(mid, DOOR_HEIGHT + 0.04f, centre, depth, 0.04f,
                    half + 0.08f, trim));
            scene.attachChild(build.box(mid, -0.05f, centre, depth - 0.03f, 0.05f, half, wood));
            solids.add(new Aabb(gap0, -0.3f, from, gap1, 0, to));
        }
    }

    /**
     * Hangs a flat picture (window, poster, mirror) on a wall.
     *
     * @param side       which wall of the room it hangs on
     * @param fixed      the wall's position
     * @param centre     position along the wall
     * @param top        height of the top edge
     * @param width      width in metres
     * @param height     height in metres
     * @param texture    the picture
     * @param upsideDown true to hang it the wrong way up
     */
    protected void hang(int side, float fixed, float centre, float top, float width, float height,
            Texture2D texture, boolean upsideDown) {
        Material material = build.lit(texture);
        // "up" is the picture's vertical edge, "y" the height of its bottom edge and "w" half
        // its width.
        Vector3f up = new Vector3f(0, height, 0);
        float y = top - height;
        float w = width / 2;
        // Hang it 1.2 cm in front of the wall so the picture and the wall do not flicker
        // through each other.
        float off = 0.012f;
        // Swapping 0 and 1 in the texture coordinates turns the picture upside down.
        float u0 = upsideDown ? 1 : 0;
        float u1 = upsideDown ? 0 : 1;
        // The starting corner and direction depend on the wall, so the picture always faces
        // into the room.
        Geometry panel = switch (side) {
            case NORTH -> build.panel(new Vector3f(centre - w, y, fixed + off),
                    new Vector3f(width, 0, 0), up, u0, u1, u0, u1, material);
            case SOUTH -> build.panel(new Vector3f(centre + w, y, fixed - off),
                    new Vector3f(-width, 0, 0), up, u0, u1, u0, u1, material);
            case WEST -> build.panel(new Vector3f(fixed + off, y, centre + w),
                    new Vector3f(0, 0, -width), up, u0, u1, u0, u1, material);
            default -> build.panel(new Vector3f(fixed - off, y, centre - w),
                    new Vector3f(0, 0, width), up, u0, u1, u0, u1, material);
        };
        into.attachChild(panel);
    }

    /**
     * Cuts part of a generated wall picture out and hangs it at its true size.
     *
     * @param room       letter of the room whose wall picture to cut from
     * @param decor      what is painted on that wall picture, such as "window"
     * @param px0        left edge of the cut-out, in pixels
     * @param py0        top edge of the cut-out, in pixels
     * @param px1        right edge of the cut-out, in pixels
     * @param py1        bottom edge of the cut-out, in pixels
     * @param side       which wall of the room it hangs on
     * @param fixed      the wall's position
     * @param centre     position along the wall
     * @param upsideDown true to hang it the wrong way up
     */
    protected void hangCrop(char room, String decor, int px0, int py0, int px1, int py1, int side,
            float fixed, float centre, boolean upsideDown) {
        // The wall picture covers a wall from floor to ceiling, so this is the size of one
        // pixel in metres.
        int size = Textures.SIZE;
        float metresPerPixel = CEILING / size;
        Texture2D texture = build.crop(Textures.wall(room, decor), size, px0, py0, px1, py1);
        // Pixel rows are counted from the top, so the top edge is (size - py0) pixels above the
        // floor.
        hang(side, fixed, centre, (size - py0) * metresPerPixel, (px1 - px0) * metresPerPixel,
                (py1 - py0) * metresPerPixel, texture, upsideDown);
    }

    /**
     * Hangs the front door picture on a wall.
     *
     * @param side   which wall of the room it hangs on
     * @param fixed  the wall's position
     * @param centre position along the wall
     */
    protected void hangFrontDoor(int side, float fixed, float centre) {
        hangCrop('h', "frontdoor", 34, 20, 94, 128, side, fixed, centre, false);
    }

    /**
     * Builds the inside of the closet: the floor (with or without the hole), the
     * clothes rail, the boxes and the door.
     */
    private void buildCloset() {
        // With a hole: four strips of floor around a 1.2 m by 0.65 m opening (south, north,
        // west and east of it).
        if (closetHole) {
            floorPiece('c', -1.55f, 0.95f, -0.15f, 2.2f);
            floorPiece('c', -1.55f, 0.2f, -0.15f, 0.3f);
            floorPiece('c', -1.55f, 0.3f, -1.45f, 0.95f);
            floorPiece('c', -0.25f, 0.3f, -0.15f, 0.95f);
            // The pit under the opening: four dark purple walls going 6 metres down.
            Material pit = build.color(0x1a0a2a);
            scene.attachChild(build.panel(new Vector3f(-1.45f, -6, 0.3f), new Vector3f(1.2f, 0, 0),
                    new Vector3f(0, 6, 0), 0, 1, 0, 1, pit));
            scene.attachChild(build.panel(new Vector3f(-0.25f, -6, 0.95f),
                    new Vector3f(-1.2f, 0, 0), new Vector3f(0, 6, 0), 0, 1, 0, 1, pit));
            scene.attachChild(build.panel(new Vector3f(-1.45f, -6, 0.95f),
                    new Vector3f(0, 0, -0.65f), new Vector3f(0, 6, 0), 0, 1, 0, 1, pit));
            scene.attachChild(build.panel(new Vector3f(-0.25f, -6, 0.3f), new Vector3f(0, 0, 0.65f),
                    new Vector3f(0, 6, 0), 0, 1, 0, 1, pit));
            // A glowing purple bottom, and a purple light just under the floor. update() makes
            // that light pulse.
            scene.attachChild(build.panel(new Vector3f(-1.45f, -5.9f, 0.3f),
                    new Vector3f(0, 0, 0.65f), new Vector3f(1.2f, 0, 0), 0, 1, 0, 1,
                    build.glow(0xb060ff)));
            holeLight = new PointLight(new Vector3f(-0.85f, -0.6f, 0.62f),
                    new ColorRGBA(0.75f, 0.3f, 1f, 1f).mult(2.2f), 4.5f);
            scene.addLight(holeLight);
        } else {
            // No hole: one ordinary floor.
            floorPiece('c', -1.55f, 0.2f, -0.15f, 2.2f);
        }

        // A clothes rail along the west wall with four coloured shirts hanging from it.
        scene.attachChild(build.box(-1.4f, 1.75f, 1.6f, 0.015f, 0.015f, 0.6f,
                build.color(0xb9b9c4)));
        int[] shirts = {0xc45a5a, 0x5a7fc4, 0xd9b44a, 0x6fae6f};
        for (int i = 0; i < shirts.length; i++) {
            scene.attachChild(build.box(-1.4f, 1.36f, 1.12f + i * 0.3f, 0.04f, 0.36f, 0.13f,
                    build.color(shirts[i])));
        }
        // Two cardboard boxes on the floor at the south end.
        build.model(scene, "cardboardBoxClosed", -1.2f, 0, 1.95f, 15, 1);
        build.model(scene, "cardboardBoxClosed", -0.55f, 0, 2.0f, -20, 0.8f);

        // The closet door: a thin slab with the door picture on it, and a brass knob.
        Texture2D doorTexture = build.crop(Textures.wall('b', "closet"), Textures.SIZE, 38, 24, 90,
                103);
        Geometry slab = build.box(0, DOOR_HEIGHT / 2, 0.45f, 0.025f, DOOR_HEIGHT / 2, 0.45f,
                build.lit(doorTexture));
        Geometry knob = new Geometry("knob", new Sphere(8, 8, 0.04f));
        knob.setMaterial(build.color(0xe3c26a));
        knob.setLocalTranslation(0.05f, 1.0f, 0.8f);
        // Both go in one node whose starting point is the hinge, so turning the node swings the
        // whole door open.
        closetDoor = new Node("closetDoor");
        closetDoor.attachChild(slab);
        closetDoor.attachChild(knob);
        closetDoor.setLocalTranslation(-0.075f, 0, 1.0f);
        scene.attachChild(closetDoor);
        // While the door is shut this box stops the player walking through it.
        closetDoorBox = new Aabb(-0.15f, 0, 1.0f, 0f, CEILING, 1.9f);
        solids.add(closetDoorBox);
        // A strip of purple light under the door hints that something is behind it.
        if (closetHole) {
            scene.attachChild(build.box(-0.01f, 0.012f, 1.45f, 0.1f, 0.012f, 0.42f,
                    build.glow(0xb070ff)));
            scene.addLight(new PointLight(new Vector3f(0.25f, 0.15f, 1.45f),
                    new ColorRGBA(0.7f, 0.35f, 1f, 1f).mult(0.9f), 1.6f));
        }
        // Looking at the door lets the player open it.
        spots.add(new Spot("closet", new Vector3f(-0.05f, 1.1f, 1.45f)));
    }

    /** Puts the furniture, the clock, the pictures and the inspectable spots in the bedroom. */
    private void furnishBedroom() {
        // Bed by the east wall, and a bedside cabinet with a lamp on top of it. stand.maxY() is
        // the height of the cabinet's top.
        solids.add(build.model(scene, "bedSingle", 4.0f, 0, 1.25f, 180, 1));
        Aabb stand = build.model(scene, "cabinetBedDrawer", 3.05f, 0, 0.3f, 180, 1);
        solids.add(stand);
        build.model(scene, "lampRoundTable", 3.18f, stand.maxY(), 0.26f, 0, 1);
        // The lamp only glows at night when the power is on.
        if (!day && !powerOut) {
            glowBall(3.18f, stand.maxY() + 0.42f, 0.26f, 0.07f);
        }

        // The alarm clock: a small black box with red digits floating just in front of it.
        scene.attachChild(build.box(2.88f, stand.maxY() + 0.05f, 0.36f, 0.12f, 0.05f, 0.04f,
                build.color(0x15151c)));
        clock = new BitmapText(game.hud().font());
        clock.setSize(0.085f);
        clock.setColor(new ColorRGBA(1f, 0.25f, 0.2f, 1f));
        clock.setQueueBucket(RenderQueue.Bucket.Transparent);
        clock.setLocalTranslation(2.79f, stand.maxY() + 0.098f, 0.402f);
        scene.attachChild(clock);

        // Desk under the window with a computer on it, and a chair facing the desk.
        Aabb desk = build.model(scene, "desk", 1.85f, 0, 0.42f, 180, 1);
        solids.add(desk);
        build.model(scene, "computerScreen", 1.85f, desk.maxY(), 0.28f, 180, 1);
        solids.add(build.model(scene, "chairDesk", 1.85f, 0, 1.2f, 0, 1));
        // Bookcase with books by the west wall, a rug, and a teddy bear in the corner.
        solids.add(build.model(scene, "bookcaseOpen", 0.3f, 0, 3.1f, 90, 1));
        build.model(scene, "books", 0.3f, 1.76f, 3.0f, 90, 1);
        build.model(scene, "rugRectangle", 2.2f, 0.004f, 2.7f, 0, 0.8f);
        build.model(scene, "bear", 0.6f, 0, 3.65f, -45, 0.55f);

        // On the north wall: the window above the desk and the cat poster. The poster hangs
        // upside down in the chapter where it is one of the wrong details.
        hangCrop('b', day ? "windowDay" : "window", 24, 13, 104, 90, NORTH, 0f, 1.85f, false);
        hangCrop('b', "poster", 42, 22, 86, 80, NORTH, 0f, 3.7f, odd.contains("poster"));

        // The things the player can inspect, each at about the middle of the object.
        spots.add(new Spot("poster", new Vector3f(3.7f, 1.55f, 0.05f)));
        spots.add(new Spot("window", new Vector3f(1.85f, 1.7f, 0.05f)));
        spots.add(new Spot("computer", new Vector3f(1.85f, 1.0f, 0.35f)));
        spots.add(new Spot("bed", new Vector3f(4.0f, 0.6f, 1.6f)));
        spots.add(new Spot("clock", new Vector3f(2.9f, 0.62f, 0.36f)));
        spots.add(new Spot("bookshelf", new Vector3f(0.3f, 1.0f, 3.1f)));
        spots.add(new Spot("bear", new Vector3f(0.6f, 0.25f, 3.65f)));
    }

    /** Puts the pictures, furniture, lamps and inspectable spots in the hallway. */
    private void furnishHallway() {
        // On the south wall: a mirror (with nobody in it when it is a wrong detail) and three
        // family photos.
        hangCrop('h', odd.contains("mirror") ? "mirrorEmpty" : "mirror", 44, 22, 84, 88, SOUTH,
                5.75f, 2.0f, false);
        hangCrop('h', "photos", 19, 31, 51, 69, SOUTH, 5.75f, 4.7f, false);
        hangCrop('h', "photos", 53, 37, 91, 67, SOUTH, 5.75f, 5.45f, false);
        hangCrop('h', "photos", 91, 29, 121, 65, SOUTH, 5.75f, 6.2f, false);
        // The front door is at the east end of the hallway.
        hangFrontDoor(EAST, 11f, 4.95f);

        // Coat rack, doormat and side table near the front door; a plant and a floor lamp at
        // the west end.
        build.model(scene, "coatRackStanding", 10.5f, 0, 4.5f, 0, 1);
        build.model(scene, "rugDoormat", 10.3f, 0.004f, 4.95f, 90, 1);
        build.model(scene, "pottedPlant", -1.2f, 0, 4.5f, 0, 1);
        solids.add(build.model(scene, "sideTable", 9.7f, 0, 5.52f, 0, 1));
        build.model(scene, "lampRoundFloor", -1.2f, 0, 5.4f, 0, 1);
        // Two ceiling lamps, lit only at night with the power on.
        ceilingLamp(2.0f, 4.95f, !day && !powerOut);
        ceilingLamp(8.0f, 4.95f, !day && !powerOut);

        spots.add(new Spot("mirror", new Vector3f(2.0f, 1.4f, 5.72f)));
        spots.add(new Spot("photos", new Vector3f(5.45f, 1.55f, 5.72f)));
        spots.add(new Spot("frontdoor", new Vector3f(10.97f, 1.2f, 4.95f)));
    }

    /** Puts the counter, table, chess board and inspectable spots in the kitchen. */
    private void furnishKitchen() {
        // A row of counter units along the north wall, 0.86 m apart, facing south into the room
        // (rotation 180).
        String[] row = {"kitchenCabinetDrawer", "kitchenSink", "kitchenCabinet", "kitchenStove"};
        for (int i = 0; i < row.length; i++) {
            solids.add(build.model(scene, row[i], 6.43f + i * 0.86f, 0, 0.45f, 180, 1));
        }
        // The fridge in the corner, cupboards on the wall above the counter, and a window.
        solids.add(build.model(scene, "kitchenFridge", 9.87f, 0, 0.3f, 180, 1));
        build.model(scene, "kitchenCabinetUpperDouble", 6.43f, 1.55f, 0.22f, 180, 1);
        build.model(scene, "kitchenCabinetUpperDouble", 8.15f, 1.55f, 0.22f, 180, 1);
        build.model(scene, "kitchenCabinetUpper", 9.01f, 1.55f, 0.22f, 180, 1);
        hangCrop('k', day ? "windowDay" : "window", 24, 13, 104, 90, NORTH, 0f, 7.29f, false);

        // The table with a chair on its south side.
        Aabb table = build.model(scene, "table", 8.6f, 0, 2.8f, 0, 1.15f);
        solids.add(table);
        build.model(scene, "chair", 8.6f, 0, 3.55f, 0, 1);
        // The chair on the north side. When it is a wrong detail it hangs upside down from the
        // ceiling instead and can be inspected.
        if (odd.contains("chair")) {
            build.place(scene, "chair", 8.6f, CEILING - 0.94f, 2.0f, 0, 1, true);
            spots.add(new Spot("chair", new Vector3f(8.6f, 2.15f, 2.0f)));
        } else {
            build.model(scene, "chair", 8.6f, 0, 2.05f, 180, 1);
        }
        // The chess board sits on the table top.
        chess = new ChessSet(build, scene, 8.6f, table.maxY(), 2.8f, chessLayout());
        build.model(scene, "trashcan", 10.25f, 0, 3.6f, 0, 1);
        ceilingLamp(8.3f, 2.0f, !day && !powerOut);

        spots.add(new Spot("chess", new Vector3f(8.6f, table.maxY() + 0.05f, 2.8f)));
        spots.add(new Spot("fridge", new Vector3f(9.87f, 1.1f, 0.55f)));
        spots.add(new Spot("cabinets", new Vector3f(7.3f, 1.0f, 0.6f)));
    }

    /** Puts the bath, toilet, sink, washer and inspectable spots in the bathroom. */
    private void furnishBathroom() {
        // Bathtub at the south end, toilet on the west wall, sink and washing machine on the
        // east wall.
        solids.add(build.model(scene, "bathtub", 0.2f, 0, 8.0f, 0, 1));
        solids.add(build.model(scene, "toilet", -1.15f, 0, 6.5f, -90, 1));
        Aabb sink = build.model(scene, "bathroomSink", 1.28f, 0, 7.0f, 90, 1);
        solids.add(sink);
        solids.add(build.model(scene, "washer", 1.18f, 0, 6.35f, 90, 1));
        // A mirror above the sink (empty when it is a wrong detail) and a window on the south
        // wall.
        hangCrop('t', odd.contains("bathmirror") ? "mirrorEmpty" : "mirror", 44, 22, 84, 88, EAST,
                1.6f, 7.0f, false);
        hangCrop('t', day ? "windowDay" : "window", 24, 13, 104, 90, SOUTH, 8.6f, -0.6f, false);
        ceilingLamp(0f, 7.2f, !day && !powerOut);
        spots.add(new Spot("bathtub", new Vector3f(0.2f, 0.6f, 8.0f)));
        spots.add(new Spot("toilet", new Vector3f(-1.15f, 0.5f, 6.5f)));
        spots.add(new Spot("sink", new Vector3f(1.4f, 1.3f, 7.0f)));
    }

    /** Puts the television, sofa, shelves and inspectable spots in the living room. */
    private void furnishLivingRoom() {
        // TV cabinet by the west wall with the television on top, facing east toward the sofa
        // (rotation -90). The TV is upside down when it is a wrong detail.
        Aabb cabinet = build.model(scene, "cabinetTelevision", 3.5f, 0, 8.2f, -90, 1);
        solids.add(cabinet);
        Build.Placed tv = build.place(scene, "televisionModern", 3.5f, cabinet.maxY(), 8.2f,
                -90, 1, odd.contains("tv"));
        // The screen is a separate glowing panel just in front of the TV so a chapter can
        // change its colour. clone() gives this screen its own copy of the material.
        Aabb tvBox = tv.box();
        tvScreen = build.glow(0x0b0d12).clone();
        float screenY = (tvBox.minY() + tvBox.maxY()) / 2 + (odd.contains("tv") ? -0.06f : 0.06f);
        scene.attachChild(build.panel(new Vector3f(tvBox.maxX() + 0.012f, screenY - 0.33f,
                8.2f - 0.6f), new Vector3f(0, 0, 1.2f), new Vector3f(0, 0.66f, 0), 0, 1, 0, 1,
                tvScreen));
        // Sofa, coffee table and rug in front of the TV, and an armchair to the north.
        solids.add(build.model(scene, "loungeSofa", 6.9f, 0, 8.2f, 90, 1));
        solids.add(build.model(scene, "tableCoffee", 5.4f, 0, 8.2f, 90, 1));
        build.model(scene, "rugRounded", 5.5f, 0.004f, 8.2f, 90, 1);
        solids.add(build.model(scene, "loungeChair", 5.3f, 0, 6.5f, 180, 1));
        // Bookcase on the south wall, a floor lamp, a speaker and two plants.
        solids.add(build.model(scene, "bookcaseClosedWide", 9.2f, 0, 10.12f, 0, 1));
        build.model(scene, "lampSquareFloor", 10.2f, 0, 6.3f, 0, 1);
        build.model(scene, "speaker", 3.45f, 0, 9.6f, -90, 1);
        build.model(scene, "pottedPlant", 3.6f, 0, 6.3f, 0, 1);
        build.model(scene, "plantSmall1", 5.4f, 0.46f, 8.2f, 0, 1);
        // Window on the south wall, photos on the east wall, and the ceiling lamp.
        hangCrop('l', day ? "windowDay" : "window", 24, 13, 104, 90, SOUTH, 10.4f, 5.6f, false);
        hangCrop('l', "photos", 19, 31, 51, 69, EAST, 10.6f, 8.6f, false);
        ceilingLamp(6.6f, 8.1f, !day && !powerOut);
        spots.add(new Spot("tv", new Vector3f(3.75f, 1.05f, 8.2f)));
        spots.add(new Spot("sofa", new Vector3f(6.9f, 0.6f, 8.2f)));
        spots.add(new Spot("livingbooks", new Vector3f(9.2f, 1.0f, 10.0f)));
    }

    /** @return how the chess pieces are arranged in this chapter */
    protected String[] chessLayout() {
        return ChessSet.START;
    }

    /**
     * Hangs a lamp from the ceiling.
     *
     * @param x   lamp centre x
     * @param z   lamp centre z
     * @param lit true to add a glowing bulb to it
     */
    protected void ceilingLamp(float x, float z, boolean lit) {
        // The lamp model hangs down 0.46 m, so this puts its top at the ceiling.
        build.model(into, "lampSquareCeiling", x, CEILING - 0.46f, z, 0, 1);
        if (lit) {
            glowBall(x, CEILING - 0.4f, z, 0.08f);
        }
    }

    /**
     * Adds a small glowing ball that looks like a lit bulb. It only looks
     * bright; it does not light up anything around it.
     *
     * @param x      centre x
     * @param y      centre height
     * @param z      centre z
     * @param radius size of the ball in metres
     */
    protected void glowBall(float x, float y, float z, float radius) {
        Geometry bulb = new Geometry("bulb", new Sphere(10, 10, radius));
        bulb.setMaterial(build.glow(0xfff0c0));
        bulb.setLocalTranslation(x, y, z);
        into.attachChild(bulb);
    }

    // ------------------------------------------------------------- gameplay

    /**
     * Runs every frame. Does everything the house chapters share (the clock,
     * the closet door, walking, dialogs and inspecting things) and then calls
     * the chapter's own {@link #chapterUpdate}.
     *
     * @param tpf seconds since the last frame
     */
    @Override
    public final void update(float tpf) {
        // Things that keep running even while a dialog is open.
        time += tpf;
        Hud hud = game.hud();
        Controls controls = game.controls();
        updateClock();
        animate(tpf);
        // Swing the closet door open in about 0.6 seconds. "eased" turns the straight 0-to-1
        // count into a curve that starts and stops gently. Fully open is 1.75 radians (about
        // 100 degrees).
        if (closetOpen && doorSwing < 1) {
            doorSwing = Math.min(1, doorSwing + tpf * 1.6f);
            float eased = doorSwing * doorSwing * (3 - 2 * doorSwing);
            closetDoor.setLocalRotation(new Quaternion().fromAngleAxis(eased * 1.75f,
                    Vector3f.UNIT_Y));
        }
        // Make the purple light in the closet hole pulse.
        if (holeLight != null) {
            holeLight.setColor(new ColorRGBA(0.75f, 0.3f, 1f, 1f)
                    .mult(2.0f + 0.5f * FastMath.sin(time * 3f)));
        }

        // The player stands still while the screen is fading, a dialog is open or the chapter
        // is busy. Passing null instead of the controls means no keys are read.
        boolean dialog = hud.isDialogOpen();
        boolean frozen = game.isChanging() || dialog || busy;
        body.update(tpf, frozen ? null : controls, solids, walkSpeed, canJump);
        // Normally the camera looks out of the player's eyes.
        if (!ownCamera) {
            body.applyTo(game.getCamera());
        }

        // While a dialog is open, E or Enter shows the next page and nothing else happens.
        if (dialog) {
            hud.setPrompt(false);
            if (controls.wasPressed("interact") || controls.wasPressed("confirm")) {
                hud.advanceDialog();
                // The last page was just closed: run the chapter's follow-up once. It is
                // cleared first so that the follow-up can set a new one.
                if (!hud.isDialogOpen() && afterDialog != null) {
                    Runnable then = afterDialog;
                    afterDialog = null;
                    then.run();
                }
            }
            return;
        }
        // Hide the hint text when its time is up.
        if (hintSeconds > 0 && time > hintSeconds) {
            hud.setHint(null);
            hintSeconds = 0;
        }
        // Find what the player is looking at, show the "press E" prompt, and react to E.
        if (busy || game.isChanging()) {
            hud.setPrompt(false);
        } else {
            Spot target = findTarget();
            hud.setPrompt(target != null);
            if (target != null && controls.wasPressed("interact")) {
                interact(target.id());
                return;
            }
        }
        // Last of all, the chapter's own logic.
        chapterUpdate(tpf);
    }

    /**
     * Finds the inspectable spot closest to the middle of the screen.
     *
     * @return what the player is looking at within reach, or null
     */
    private Spot findTarget() {
        Vector3f eye = body.eye();
        Vector3f look = body.lookDirection();
        Spot best = null;
        // 0.86 is about the cosine of 30 degrees: a spot only counts if it is within about 30
        // degrees of where the player is looking.
        float bestDot = 0.86f;
        for (Spot spot : spots) {
            // An open closet cannot be opened again.
            if ("closet".equals(spot.id()) && closetOpen) {
                continue;
            }
            // Skip anything too far away.
            Vector3f to = spot.position().subtract(eye);
            float distance = to.length();
            if (distance > REACH) {
                continue;
            }
            // "dot" is the cosine of the angle between the look direction and the direction to
            // the spot. The biggest value is the spot nearest the middle of the screen.
            float dot = to.dot(look) / distance;
            if (dot > bestDot) {
                bestDot = dot;
                best = spot;
            }
        }
        return best;
    }

    /**
     * Called when the player inspects something. Opens the closet or shows text.
     *
     * @param id what was inspected
     */
    protected void interact(String id) {
        // Opening the closet also removes the box that blocked its doorway.
        if ("closet".equals(id)) {
            closetOpen = true;
            solids.remove(closetDoorBox);
        }
        game.hud().openDialog(describe(id));
    }

    /**
     * The normal text for everything in the house. Chapters override this to change
     * some of it.
     *
     * @param id what was inspected
     * @return the dialog pages to show for it
     */
    protected String[] describe(String id) {
        // One line per inspectable thing. Each string is one page of the dialog box.
        return switch (id) {
            case "closet" -> new String[] {"Just clothes.", "No hole. No humming. Of course not."};
            case "poster" -> new String[] {
                "\"HANG IN THERE.\" That cat has been hanging in there since fourth grade."};
            case "bookshelf" -> new String[] {
                "Mostly comics. And one library book, eleven years overdue."};
            case "window" -> new String[] {day
                    ? "Morning. A neighbour is mowing the lawn. Birds. Ordinary things."
                    : "The middle of the night. Every other house on the street is dark."};
            case "computer" -> new String[] {"Homework. Due yesterday."};
            case "bear" -> new String[] {"Mr. Buttons. He has seen things."};
            case "bed" -> new String[] {"You JUST woke up."};
            case "clock" -> new String[] {clockBackwards
                    ? shownClock + " AM. ...Wasn't it later than that a minute ago?"
                    : shownClock + " AM."};
            case "mirror" -> new String[] {"Yep. That's you. Pajamas and all."};
            case "photos" -> new String[] {"Family photos. Everybody's smiling."};
            case "frontdoor" -> new String[] {"Locked. The key is... somewhere."};
            case "chess" -> new String[] {
                "The chess set, ready for a new game. Every piece is where it should be."};
            case "fridge" -> new String[] {"Leftover pizza. Tempting. Later."};
            case "tv" -> new String[] {"The TV. Off. Your own shape looks back from the glass."};
            case "sofa" -> new String[] {
                "The sofa. There is a you-shaped dent in the middle cushion."};
            case "livingbooks" -> new String[] {
                "Board games, photo albums, and a jigsaw with one piece missing since forever."};
            case "bathtub" -> new String[] {"The tap drips. It has always dripped."};
            case "toilet" -> new String[] {"Not now."};
            case "sink" -> new String[] {
                "You splash cold water on your face. It feels real. It always does."};
            case "cabinets" -> new String[] {
                "Plates, mugs, and the good cookies on the top shelf."};
            default -> new String[] {"Nothing unusual."};
        };
    }

    /** The bedside clock: fixed at 3:07, running backwards, or showing the morning. */
    private void updateClock() {
        // There is no clock until the bedroom has been built.
        if (clock == null) {
            return;
        }
        // Normally the clock is stuck: 3:07 at night, 7:30 in the day.
        String text = day ? "7:30" : "3:07";
        if (clockOverride != null) {
            text = clockOverride;
        }
        // Running backwards: start at 3:07 (counted in minutes) and lose one minute every 3
        // seconds, wrapping around a 12-hour clock.
        if (clockBackwards && clockOverride == null) {
            int minutes = Math.floorMod(3 * 60 + 7 - (int) (time / 3), 12 * 60);
            int hour = minutes / 60 == 0 ? 12 : minutes / 60;
            text = hour + ":" + String.format("%02d", minutes % 60);
        }
        // Only change the 3D text when the time really changes.
        if (!text.equals(shownClock)) {
            shownClock = text;
            clock.setText(text);
        }
    }

    /**
     * Moves on to the next chapter with a fade.
     *
     * @param color   colour to fade through
     * @param seconds length of each half of the fade
     */
    protected void finish(ColorRGBA color, float seconds) {
        game.nextChapter(color, seconds);
    }
}
