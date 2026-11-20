package stillasleep.three;

import com.jme3.light.DirectionalLight;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.texture.Texture2D;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Chapter 2: the first "wake up". The bedroom is right, and through the door
 * the hallway looks right too. But the hallway is only a stage set: step out
 * and it falls away, showing that the bedroom is floating in an empty night
 * sky. The way out is a long platforming course made of pieces of the house.
 *
 * <p>Layout: the bedroom covers x 0 to 4.6 and z 0 to 4. Its door is in the
 * south wall, between x = 3.2 and x = 4.1, and the hallway is the strip from
 * z = 4.15 to z = 5.75 just south of it.
 */
final class VoidHouse extends HouseStage {

    /** The fake hallway: every piece that falls away is attached to this one node. */
    private final Node facade = new Node("facade");
    /** The fake hallway's collision boxes, kept apart so they can all be removed at once. */
    private final List<Aabb> facadeSolids = new ArrayList<>();
    /** Furniture floating in the sky, kept so it can slowly tumble. */
    private final List<Node> drifting = new ArrayList<>();
    /** The obstacles: platforms, checkpoints and so on. */
    private Course course;
    /** The hallway floorboards, used on every floating platform. */
    private Material boardMaterial;
    /** The hallway's warm ceiling light; switched off when the hallway falls. */
    private PointLight hallLight;
    /** The area in front of the exit door; touching it finishes the chapter. */
    private Aabb goal;
    /** True once the player has stepped out and the hallway has started to fall. */
    private boolean revealed;
    /** Seconds since the hallway started to fall. */
    private float revealTime;

    /**
     * Creates the chapter. The bedside clock runs backwards here, as a first
     * sign that this is still a dream.
     *
     * @param game the running game
     */
    VoidHouse(Game3D game) {
        super(game);
        clockBackwards = true;
    }

    /** Builds the bedroom, the fake hallway, the platform course and the night sky. */
    @Override
    protected void buildChapter() {
        buildBedroomAndCloset();
        addAmbient();
        outsideShell();

        // the one real strip of hallway floor, just outside the bedroom door
        floorPiece('h', 2.9f, 4.15f, 4.4f, 5.75f);
        boardMaterial = build.lit(build.texture(Textures.floor('h'), Textures.SIZE, Textures.SIZE));

        // the stage-set hallway that will fall away
        // ("into" and "intoSolids" are where the wall and floor helpers put what
        // they make. Pointing them at the facade collects the whole fake hallway
        // in one node and one list)
        into = facade;
        intoSolids = facadeSolids;
        // floor to the west and east of the real strip
        floorPiece('h', 0.4f, 4.15f, 2.9f, 5.75f);
        floorPiece('h', 4.4f, 4.15f, 6.9f, 5.75f);
        // four walls. The north wall is in three parts: the middle part only
        // covers the space above the bedroom door, leaving the doorway open
        Material hall = wallpaper('h');
        wallPiece(hall, NORTH, 4.15f, 0.4f, 3.2f, 0, CEILING);
        wallPiece(hall, NORTH, 4.15f, 3.2f, 4.1f, DOOR_HEIGHT, CEILING);
        wallPiece(hall, NORTH, 4.15f, 4.1f, 6.9f, 0, CEILING);
        wallPiece(hall, SOUTH, 5.75f, 0.4f, 6.9f, 0, CEILING);
        wallPiece(hall, WEST, 0.4f, 4.15f, 5.75f, 0, CEILING);
        wallPiece(hall, EAST, 6.9f, 4.15f, 5.75f, 0, CEILING);
        // a ceiling, family photos facing the door, and a ceiling lamp
        ceilingPiece(0.4f, 4.15f, 6.9f, 5.75f);
        hangCrop('h', "photos", 53, 37, 91, 67, SOUTH, 5.75f, 3.65f, false);
        ceilingLamp(3.65f, 4.95f, true);
        // point the helpers back at the real scene, then add the finished facade to it
        into = scene;
        intoSolids = solids;
        scene.attachChild(facade);
        solids.addAll(facadeSolids);
        // the hallway's light is kept in a field so it can be removed at the reveal
        hallLight = new PointLight(new Vector3f(3.65f, 2.1f, 4.95f),
                new ColorRGBA(1f, 0.82f, 0.56f, 1f), 7f);
        scene.addLight(hallLight);

        course();
        stars();
        // the lines shown as the player wakes up in bed
        wake("...", "Huh. What a weird dream.");
    }

    /** Plain outer walls, so the bedroom looks like a solid box from outside. */
    private void outsideShell() {
        // dark boxes just outside the bedroom and closet: north wall, east wall,
        // west wall, roof and underside
        Material plaster = build.color(0x35303d);
        scene.attachChild(build.box(1.5f, 1.2f, -0.13f, 3.35f, 1.6f, 0.05f, plaster));
        scene.attachChild(build.box(4.73f, 1.2f, 2.0f, 0.05f, 1.6f, 2.2f, plaster));
        scene.attachChild(build.box(-1.68f, 1.2f, 2.0f, 0.05f, 1.6f, 2.2f, plaster));
        scene.attachChild(build.box(1.5f, 2.74f, 2.0f, 3.35f, 0.06f, 2.2f, plaster));
        scene.attachChild(build.box(1.5f, -0.37f, 2.0f, 3.35f, 0.06f, 2.2f, plaster));
        // the south wall, in three pieces that leave the doorway open: west of
        // the door, east of the door, and the strip above it
        scene.attachChild(build.box(0.72f, 1.2f, 4.07f, 2.4f, 1.6f, 0.05f, plaster));
        scene.attachChild(build.box(4.48f, 1.2f, 4.07f, 0.3f, 1.6f, 0.05f, plaster));
        scene.attachChild(build.box(3.65f, 2.45f, 4.07f, 0.55f, 0.35f, 0.05f, plaster));
    }

    /**
     * The floating pieces of house that lead to the exit door. The way out is
     * not a straight line: it runs south from the bedroom door, splits east
     * (over the furniture) or west (crumbling boards and drifting floor),
     * climbs back north, crosses over the bedroom's own roof, splits again,
     * and ends at a door hanging in the dark behind the house.
     */
    private void course() {
        // set up the obstacle kit. Falling below y = -12 counts as a death. The
        // player comes back just outside the bedroom door, facing south, and a
        // pale disc on the ground shows where they will land
        course = new Course(build, scene, solids, body, -12f);
        course.setSpawn(3.65f, 0f, 5.0f, FastMath.HALF_PI);
        course.showLandingMarker(0xdfe6ff, 0.55f);
        course.checkpoint(3.65f, 0f, 5.0f, FastMath.HALF_PI, false);
        // every platform in this chapter is drawn by board()
        Course.Look wood = this::board;
        // the first board, one jump south of the hallway strip. The course forks here
        course.fixed(wood, 3.0f, 7.2f, 4.6f, 9.0f, 0f);
        course.checkpoint(3.8f, 0f, 8.1f, FastMath.HALF_PI, true);

        // east: bed, floor, desk, washing machine
        // (the bed gets its own collision box, only 0.45 m tall, so the player
        // lands on the mattress instead of bumping into the headboard)
        Aabb bed = build.model(scene, "bedSingle", 3.8f, -0.25f, 11.2f, 0, 1);
        solids.add(new Aabb(bed.minX(), bed.minY(), bed.minZ(), bed.maxX(), bed.minY() + 0.45f,
                bed.maxZ()));
        course.fixed(wood, 5.6f, 10.9f, 8.6f, 12.7f, 0.2f);
        course.checkpoint(6.4f, 0.2f, 11.8f, 0, true);
        // (the desk and the washing machine are stepping stones: the furniture
        // models' own boxes are added to the solids)
        solids.add(build.model(scene, "desk", 10.4f, -0.2f, 11.8f, 0, 1));
        solids.add(build.model(scene, "washer", 12.5f, -0.1f, 11.8f, 0, 1.3f));

        // west and round: crumbling boards, two drifting pieces of floor
        // (two crumblers lead west to a safe board)
        course.crumbler(wood, 1.5f, 0.1f, 8.1f, 0.6f);
        course.crumbler(wood, -0.7f, 0.3f, 8.4f, 0.6f);
        course.fixed(wood, -4.2f, 7.4f, -2.2f, 9.6f, 0.5f);
        course.checkpoint(-3.2f, 0.5f, 8.3f, FastMath.HALF_PI, true);
        // (a board that glides south to a second safe board with an armchair on it)
        course.mover(wood, new Vector3f(-3.2f, 0.6f, 11.4f), new Vector3f(-3.2f, 0.6f, 17.0f),
                0.9f, 0.9f, 8f);
        course.fixed(wood, -4.4f, 18.6f, -2f, 20.6f, 0.8f);
        course.checkpoint(-3.6f, 0.8f, 19.2f, 0, true);
        build.model(scene, "loungeChair", -3.7f, 0.8f, 20.1f, 150, 1);
        // (a board that glides east, a third safe board, then three crumblers
        // curving north-east up to the lamp)
        course.mover(wood, new Vector3f(-0.2f, 0.9f, 19.6f), new Vector3f(6.0f, 0.9f, 19.6f), 0.9f,
                0.9f, 9f);
        course.fixed(wood, 7.6f, 18.4f, 10f, 20.8f, 1.0f);
        course.checkpoint(8.2f, 1.0f, 19.0f, 0, true);
        course.crumbler(wood, 11.6f, 1.1f, 19.0f, 0.6f);
        course.crumbler(wood, 13.4f, 1.2f, 17.4f, 0.6f);
        course.crumbler(wood, 15.2f, 1.2f, 15.2f, 0.6f);

        // both ways meet at the lamp
        course.fixed(wood, 14.0f, 10.6f, 16.6f, 13.0f, 1.0f);
        course.checkpoint(14.6f, 1.0f, 12.4f, -FastMath.HALF_PI, true);
        lamp(16.2f, 1.0f, 12.6f);

        // climb north, then turn west past a sweeping bar toward the bedroom's roof
        // (three small steps going north, each 0.4 m higher; the middle one crumbles)
        course.fixed(wood, 14.8f, 8.2f, 15.9f, 9.3f, 1.4f);
        course.crumbler(wood, 15.35f, 1.8f, 6.6f, 0.55f);
        course.fixed(wood, 14.8f, 3.8f, 15.9f, 4.9f, 2.2f);
        // (a board with the sweeper on it, then a board gliding west to the roof)
        course.fixed(wood, 10.6f, 3.4f, 13.4f, 5.4f, 2.4f);
        course.sweeper(12f, 2.4f, 4.4f, 1.15f, 1.5f);
        course.mover(wood, new Vector3f(9.6f, 2.6f, 4.4f), new Vector3f(6.5f, 2.6f, 4.4f), 0.8f,
                0.8f, 7f);

        // the roof of the bedroom itself
        // (the roof is already drawn by outsideShell(); this box makes it solid.
        // A chimney and a lamp are added on top; the chimney is decoration only)
        solids.add(new Aabb(-1.85f, 2.6f, -0.2f, 4.85f, 2.8f, 4.2f));
        course.checkpoint(3.6f, 2.8f, 3.2f, FastMath.PI, true);
        scene.attachChild(build.box(0.2f, 3.3f, 1.2f, 0.35f, 0.5f, 0.35f, build.color(0x4a3038)));
        lamp(-1.2f, 2.8f, 3.4f);

        // from the roof: crumbling boards to the north-west, or up the lift and across
        // (short, risky route: three crumblers in a diagonal line)
        course.crumbler(wood, -0.5f, 3.0f, -1.5f, 0.6f);
        course.crumbler(wood, -2.3f, 3.3f, -2.9f, 0.6f);
        course.crumbler(wood, -4.2f, 3.6f, -4.1f, 0.6f);
        // (long, safe route: a board north of the roof, a lift from height 3.0 to
        // 4.6, a high board, then a board gliding west)
        course.fixed(wood, 3.0f, -2.9f, 4.6f, -1.3f, 3.0f);
        course.mover(wood, new Vector3f(3.8f, 3.0f, -4.6f), new Vector3f(3.8f, 4.6f, -4.6f), 0.8f,
                0.8f, 5f);
        course.fixed(wood, 0.4f, -5.6f, 2.2f, -3.6f, 4.6f);
        course.mover(wood, new Vector3f(-1.0f, 4.6f, -4.6f), new Vector3f(-4.2f, 4.4f, -4.6f), 0.8f,
                0.8f, 7f);

        // both ways meet again; two last crumbling boards to the door
        course.fixed(wood, -7.4f, -5.8f, -5.4f, -3.4f, 3.8f);
        course.checkpoint(-6.0f, 3.8f, -5.2f, FastMath.PI, true);
        course.crumbler(wood, -8.6f, 4.0f, -4.6f, 0.55f);
        course.crumbler(wood, -10.4f, 4.3f, -4.0f, 0.55f);
        course.fixed(wood, -14.4f, -6.2f, -11.6f, -3.0f, 4.5f);

        // the way out: a front door standing on its own, glowing
        // (the door picture is cut out of the hallway wall texture; a slightly
        // bigger glowing box behind it makes a bright outline, and a light in
        // front makes the last platform easy to see)
        Texture2D door = build.crop(Textures.wall('h', "frontdoor"), Textures.SIZE, 34, 20, 94,
                128);
        scene.attachChild(build.box(-13.6f, 4.5f + 1.1f, -4.6f, 0.04f, 1.1f, 0.61f,
                build.lit(door)));
        scene.attachChild(build.box(-13.67f, 4.5f + 1.1f, -4.6f, 0.02f, 1.2f, 0.75f,
                build.glow(0xfff6d8)));
        scene.addLight(new PointLight(new Vector3f(-12.6f, 5.6f, -4.6f),
                new ColorRGBA(1f, 0.95f, 0.8f, 1f).mult(1.4f), 6f));
        // walking into the space just in front of the door finishes the chapter
        goal = Aabb.centered(-13.3f, 5.5f, -4.6f, 0.3f, 1f, 0.6f);

        // furniture adrift in the dark
        // (position i in "adrift" goes with model i in "models". Each piece hangs
        // from its own pivot node, which animate() slowly turns)
        float[][] adrift = {{8f, 4.5f, 8f}, {0f, 2f, 13f}, {11f, -2f, 15f}, {20f, 4f, 8f},
                {8f, 6f, -2f}, {6f, -3f, 15.5f}, {22f, 0f, 16f}, {-7f, 3f, 14f}, {3f, 4f, 23f},
                {-9f, 7f, -9f}};
        String[] models = {"chair", "lampRoundTable", "books", "chairDesk", "televisionVintage",
                "pillow", "sideTable", "bear", "plantSmall1", "cardboardBoxClosed"};
        for (int i = 0; i < adrift.length; i++) {
            Node pivot = new Node("adrift");
            pivot.setLocalTranslation(adrift[i][0], adrift[i][1], adrift[i][2]);
            build.place(pivot, models[i], 0, -0.3f, 0, i * 50, 1, i % 2 == 1);
            scene.attachChild(pivot);
            drifting.add(pivot);
        }
    }

    /**
     * A lit floor lamp, so the next stretch of the course can be seen.
     *
     * @param x x in metres
     * @param y height of the surface it stands on
     * @param z z in metres
     */
    private void lamp(float x, float y, float z) {
        // the lamp model, a glowing ball for its bulb, and a real light beside it
        build.model(scene, "lampRoundFloor", x, y, z, 0, 1);
        glowBall(x, y + 1.55f, z, 0.1f);
        scene.addLight(new PointLight(new Vector3f(x, y + 1.4f, z),
                new ColorRGBA(1f, 0.82f, 0.56f, 1f), 6f));
    }

    /**
     * A floating slab of hallway floor, with its top surface at the node's
     * origin. Pale strips along its edges make it readable against the dark.
     *
     * @param hx half its width
     * @param hz half its depth
     * @return the slab, ready to be positioned
     */
    private Node board(float hx, float hz) {
        Node node = new Node("board");
        // the board itself, 20 cm thick
        node.attachChild(build.box(0, -0.1f, 0, hx, 0.1f, hz, boardMaterial));
        // four thin glowing strips, one along each edge: south, north, east, west
        Material edge = build.glow(0x8fa3e0);
        node.attachChild(build.box(0, -0.01f, hz, hx, 0.012f, 0.02f, edge));
        node.attachChild(build.box(0, -0.01f, -hz, hx, 0.012f, 0.02f, edge));
        node.attachChild(build.box(hx, -0.01f, 0, 0.02f, 0.012f, hz, edge));
        node.attachChild(build.box(-hx, -0.01f, 0, 0.02f, 0.012f, hz, edge));
        return node;
    }

    /** Scatters 260 stars across the night sky. */
    private void stars() {
        // a fixed seed, so the stars are in the same places every time
        Random random = new Random(12);
        Material white = build.glow(0xffffff);
        for (int i = 0; i < 260; i++) {
            // pick a random direction (yaw = compass direction, pitch = up or down)
            // and a random size
            float yaw = random.nextFloat() * FastMath.TWO_PI;
            float pitch = (random.nextFloat() - 0.5f) * FastMath.PI;
            float size = 0.12f + random.nextFloat() * 0.22f;
            // put a small glowing cube 130 m away in that direction, measured
            // from roughly the middle of the course (x = 8, z = 8)
            scene.attachChild(build.box(8 + 130 * FastMath.cos(yaw) * FastMath.cos(pitch),
                    130 * FastMath.sin(pitch), 8 + 130 * FastMath.sin(yaw) * FastMath.cos(pitch),
                    size, size, size, white));
        }
    }

    /**
     * Moves everything that moves by itself: the obstacles, the drifting
     * furniture and the falling hallway.
     *
     * @param tpf seconds since the last frame
     */
    @Override
    protected void animate(float tpf) {
        course.update(tpf);
        course.updateLandingMarker();
        // each piece of furniture tumbles at a slightly different speed
        for (int i = 0; i < drifting.size(); i++) {
            drifting.get(i).rotate(tpf * 0.15f, tpf * (0.2f + i * 0.04f), 0);
        }
        // for 4 seconds after the reveal the fake hallway drops, faster and
        // faster, tilting as it goes. After that it is out of sight and is deleted
        if (revealed && revealTime < 4f) {
            revealTime += tpf;
            facade.move(0, -tpf * revealTime * 7f, 0);
            facade.rotate(tpf * 0.05f, 0, tpf * 0.03f);
            if (revealTime >= 4f) {
                facade.removeFromParent();
            }
        }
    }

    /**
     * Checks for the two things that matter in this chapter: stepping out of
     * the bedroom (which starts the reveal) and reaching the exit door.
     *
     * @param tpf seconds since the last frame
     */
    @Override
    protected void chapterUpdate(float tpf) {
        // z > 4.6 means the player has walked through the bedroom door. This
        // happens only once
        if (!revealed && body.position.z > 4.6f) {
            revealed = true;
            // the hallway stops being solid and its lamp goes out
            solids.removeAll(facadeSolids);
            scene.removeLight(hallLight);
            // cold blue moonlight and a dim all-round light replace it, so the
            // platforms can be seen
            DirectionalLight moon = new DirectionalLight(
                    new Vector3f(-0.35f, -1f, 0.25f).normalizeLocal(),
                    new ColorRGBA(0.6f, 0.66f, 0.92f, 1f));
            scene.addLight(moon);
            scene.addLight(new com.jme3.light.AmbientLight(new ColorRGBA(0.14f, 0.15f, 0.24f, 1f)));
            // the house chapters are walking only; from here on the player can
            // jump and moves faster
            canJump = true;
            walkSpeed = 4.2f;
            game.hud().openDialog("...That is not the hallway.",
                    "The bedroom was never in the house.");
            // show the hint for the next 10 seconds
            game.hud().setHint("Space: jump      There is more than one way across");
            hintSeconds = time + 10;
        }
        // reaching the door ends the chapter with a fade to white
        if (body.touches(goal)) {
            finish(ColorRGBA.White, 1.0f);
        }
    }

    /**
     * Gives the text shown when the player examines something. Only the bed
     * has its own line here; everything else uses the usual text.
     *
     * @param id name of the thing being looked at
     * @return the lines to show, one dialog box each
     */
    @Override
    protected String[] describe(String id) {
        if ("bed".equals(id)) {
            return new String[] {"You JUST woke up. Didn't you?"};
        }
        return super.describe(id);
    }

    /**
     * Viewpoints for automated test screenshots: the bedroom, the hallway, the
     * first board, the lamp, the sweeper, the roof, the last meeting point, and
     * an overview.
     *
     * @return rows of {x, feet y, z, yaw degrees, pitch degrees}
     */
    @Override
    float[][] debugPoses() {
        return new float[][] {{3.65f, 0, 2.6f, 90, -3}, {3.65f, 0, 5.2f, 90, -12},
                {3.8f, 0, 8.6f, 60, -10}, {15.3f, 1.0f, 12.6f, -90, 6}, {12f, 2.4f, 4.4f, 180, -4},
                {1.5f, 2.8f, 2.0f, 225, 0}, {-6.4f, 3.8f, -4.6f, 180, 0},
                {10f, 14f, 30f, -105, -30}};
    }

    /** Puts the player on the last platform, right in front of the exit door (tests only). */
    @Override
    void debugComplete() {
        body.place(-12.9f, 4.55f, -4.6f, FastMath.PI);
    }
}
