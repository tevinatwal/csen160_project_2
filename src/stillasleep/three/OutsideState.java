package stillasleep.three;

import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Sphere;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.texture.Texture2D;

/**
 * The last level. The front door opens onto the street at dawn, and for a
 * moment it is just the street. Then it turns out the road only goes so far
 * before it breaks into pieces hanging in the sky. There are three ways down
 * the street: the broken road, the crumbling pavement beside it, and over the
 * rooftops. At the far end, in the middle of the road, is a bed.
 *
 * <p>Layout: the player steps out of their front door at about x = 1, z = 0,
 * facing east. Going east they cross the lawn (x 0 to 6), the pavement
 * (6 to 7.6) and the road (7.6 to 13.6); the neighbours' houses are beyond
 * that. The street runs south (z gets bigger). It is whole up to z = 6 and
 * broken after that, and the bed is at about z = 68.
 */
final class OutsideState extends Stage {

    /** How fast the player runs in this level, in metres per second. */
    private static final float RUN_SPEED = 4.6f;

    /** Helpers for making boxes, colours, materials and furniture models. */
    private Build build;
    /** The obstacles: platforms, spikes, checkpoints and so on. */
    private Course course;
    /** Draws the sun's shadows; removed again when the level is left. */
    private DirectionalLightShadowRenderer shadows;
    /** The area around the bed; touching it ends the level. */
    private Aabb bedZone;
    /** True once the player has seen that the street is broken (and been told so). */
    private boolean noticed;
    /** Seconds since the level started. */
    private float time;
    /** Strength of the white flash shown after a death; fades from 1 to 0. */
    private float flash;

    /**
     * Creates the level. Nothing is built until the game switches to it.
     *
     * @param game the running game
     */
    OutsideState(Game3D game) {
        super(game);
    }

    /** Builds the whole level: dawn sky, the real street, the three routes and the bed. */
    @Override
    protected void build() {
        build = game.build();
        // a warm orange dawn sky, with a low sun (its direction is more sideways
        // than downwards, which gives long shadows)
        game.getViewPort().setBackgroundColor(new ColorRGBA(0.98f, 0.8f, 0.62f, 1f));
        DirectionalLight sun = new DirectionalLight(new Vector3f(-0.35f, -0.55f, -0.75f)
                .normalizeLocal(), new ColorRGBA(1f, 0.82f, 0.62f, 1f));
        scene.addLight(sun);
        scene.addLight(new AmbientLight(new ColorRGBA(0.5f, 0.46f, 0.52f, 1f)));
        // shadows from the sun: 2048 is the sharpness of the shadow picture and 3 is
        // how many of them are used to cover near, middle and far distances
        shadows = new DirectionalLightShadowRenderer(game.getAssetManager(), 2048, 3);
        shadows.setLight(sun);
        shadows.setShadowIntensity(0.4f);
        shadows.setShadowZExtend(80f);
        game.getViewPort().addProcessor(shadows);

        // set up the obstacle kit. Falling below y = -12 counts as a death and the
        // player starts just outside the front door, facing east
        course = new Course(build, scene, solids, body, -12f);
        course.setSpawn(1.2f, 0f, 0f, 0f);
        course.showLandingMarker(0x000000, 0.3f);
        // the four kinds of platform in this level. Lawn and pavement are the same
        // slab shape with different colours and thickness
        Course.Look road = this::road;
        Course.Look lawn = (hx, hz) -> slab(hx, hz, 0x5fb04e, 0x8a623a, 0.8f);
        Course.Look pavement = (hx, hz) -> slab(hx, hz, 0xc9c6bd, 0x9a968c, 0.3f);
        Course.Look roof = this::rooftop;

        // the part of the street that is really there
        // (four strips side by side, west to east: front lawn, pavement, road,
        // and the lawn on the far side with two neighbours' houses on it)
        homeFront();
        course.fixed(lawn, 0f, -14f, 6f, 6f, 0f);
        course.fixed(pavement, 6f, -14f, 7.6f, 6f, 0f);
        course.fixed(road, 7.6f, -14f, 13.6f, 6f, 0f);
        course.fixed(lawn, 13.6f, -14f, 20f, 6f, 0f);
        house(17f, 0f, -9f, 0xd9c7a8, 0x8a4a3a);
        house(17f, 0f, 1f, 0xb9c9d6, 0x4a5a6a);
        streetLamp(6.8f, 0f, -6f);
        streetLamp(6.8f, 0f, 4.6f);
        tree(3.5f, 0f, -9f);
        tree(2.2f, 0f, 3.8f);
        // the mailbox: a wooden post with a blue box on top
        scene.attachChild(build.box(5.4f, 0.55f, 2.4f, 0.05f, 0.55f, 0.05f, build.color(0x6b4a32)));
        scene.attachChild(build.box(5.4f, 1.18f, 2.4f, 0.14f, 0.12f, 0.22f, build.color(0x3a6ea8)));

        // road route: broken chunks, a moving chunk, a lift and a narrow strip
        // (two chunks, each a little higher, then a chunk that glides south)
        course.fixed(road, 8f, 7.8f, 13f, 12f, 0.2f);
        course.fixed(road, 8.4f, 13.8f, 12.6f, 17f, 0.5f);
        course.mover(road, new Vector3f(10.5f, 0.6f, 19f), new Vector3f(10.5f, 0.6f, 24f), 1.2f,
                0.9f, 7f);
        // (a big chunk with a checkpoint and a sweeping bar; the pavement route
        // joins here, and the rooftop route starts from the lawn beside it)
        course.fixed(road, 8f, 25.6f, 13f, 30f, 0.8f);
        course.checkpoint(9f, 0.8f, 26.4f, FastMath.HALF_PI, true);
        course.sweeper(10.5f, 0.8f, 28.2f, 2.2f, 1.4f);
        // (a lift from height 1.0 up to 2.4, then a strip only 1 m wide with a
        // faster bar that has to be jumped)
        course.fixed(road, 8.5f, 31.8f, 12.5f, 34.6f, 1.0f);
        course.mover(pavement, new Vector3f(10.5f, 1.0f, 36.4f), new Vector3f(10.5f, 2.4f, 36.4f),
                1f, 0.9f, 5f);
        course.fixed(road, 10f, 38.2f, 11f, 43f, 2.4f);
        course.sweeper(10.5f, 2.4f, 40.6f, 1.3f, 1.9f);

        // pavement route: narrow slabs that fall away underfoot
        // (five in a row going south, each 10 cm higher than the last)
        float[] slabs = {7.4f, 9.4f, 11.4f, 13.4f, 15.4f};
        for (int i = 0; i < slabs.length; i++) {
            course.crumbler(pavement, 6.8f, 0.1f + i * 0.1f, slabs[i], 0.7f);
        }
        // (a solid slab to rest on, then two more crumblers leading to the big road chunk)
        course.fixed(pavement, 6f, 16.6f, 7.6f, 19.6f, 0.6f);
        course.crumbler(pavement, 6.8f, 0.7f, 20.9f, 0.7f);
        course.crumbler(pavement, 6.9f, 0.8f, 23.0f, 0.7f);

        // rooftop route: a bounce pad on a lawn, then long jumps from roof to roof
        course.fixed(lawn, 14.2f, 26f, 17.8f, 30f, 0.8f);
        course.bouncer(17f, 0.8f, 29.2f);
        course.fixed(roof, 13.6f, 32.5f, 17.6f, 36.5f, 3.2f);
        course.fixed(roof, 13.6f, 39f, 17.6f, 43f, 3.4f);

        // the three routes meet; the last stretch
        // (two strips of spikes across the road with a gap in the middle to go through)
        course.fixed(road, 8f, 44.8f, 13.5f, 49f, 2.4f);
        course.checkpoint(9f, 2.4f, 45.6f, FastMath.HALF_PI, true);
        course.spikes(8f, 47.2f, 10.4f, 47.9f, 2.4f);
        course.spikes(11.2f, 47.2f, 13.5f, 47.9f, 2.4f);
        // (three crumbling steps going up, a chunk that glides south, then the
        // final piece of road where the bed is)
        course.crumbler(pavement, 10.5f, 2.6f, 50.6f, 0.7f);
        course.crumbler(pavement, 10.9f, 2.9f, 52.8f, 0.7f);
        course.crumbler(pavement, 10.1f, 3.2f, 55.0f, 0.7f);
        course.mover(road, new Vector3f(10.5f, 3.4f, 57.4f), new Vector3f(10.5f, 3.4f, 62f), 1f,
                1f, 7f);
        course.fixed(road, 7.5f, 63.6f, 13.5f, 70f, 3.6f);
        streetLamp(8f, 3.6f, 64.4f);

        // the bed, in the middle of the road
        // (with a rug, a bedside cabinet and a lit lamp, like the bedroom's)
        build.model(scene, "bedSingle", 10.5f, 3.6f, 67.6f, 180, 1);
        build.model(scene, "rugRectangle", 10.5f, 3.605f, 66.2f, 90, 0.7f);
        build.model(scene, "cabinetBedDrawer", 9.4f, 3.6f, 68.4f, 0, 1);
        build.model(scene, "lampRoundTable", 9.4f, 4.12f, 68.4f, 0, 1);
        Geometry bulb = new Geometry("bulb", new Sphere(10, 10, 0.07f));
        bulb.setMaterial(build.glow(0xfff0c0));
        bulb.setLocalTranslation(9.4f, 4.54f, 68.4f);
        scene.attachChild(bulb);
        // touching this box (the space on and just above the bed) finishes the level
        bedZone = Aabb.centered(10.5f, 4.3f, 67.6f, 0.75f, 0.8f, 1.2f);

        // houses and trees drifting where the street used to be
        // (decoration at odd heights on both sides of the course)
        house(2f, -1.5f, 24f, 0xe0d2b8, 0x7a4a3a);
        house(21f, -3f, 16f, 0xc9d6c0, 0x4a6a5a);
        house(1f, 1f, 44f, 0xd6c0c0, 0x6a4a4a);
        house(22f, 2f, 54f, 0xb9c9d6, 0x4a5a6a);
        house(2.5f, 3f, 66f, 0xd9c7a8, 0x8a4a3a);
        tree(20.5f, -1f, 38f);
        tree(4f, 0.5f, 34f);
        tree(19f, 2f, 64f);

        // put the player just outside the front door, looking east across the street
        body.place(1.2f, 0.05f, 0f, 0f);
        game.hud().openDialog("The door opens.",
                "The lawn. The mailbox. The neighbours' houses. All of it, exactly right.");
    }

    /**
     * Tidies up when the level is left. The shadow renderer belongs to the
     * whole window, not to this scene, so it has to be removed by hand.
     *
     * @param app the running application
     */
    @Override
    protected void cleanup(com.jme3.app.Application app) {
        game.getViewPort().removeProcessor(shadows);
        super.cleanup(app);
    }

    /**
     * A flat-topped block: one colour on top, another for the sides.
     *
     * @param hx        half its width
     * @param hz        half its depth
     * @param topColor  colour of the top surface, as 0xRRGGBB
     * @param sideColor colour of the sides and underneath, as 0xRRGGBB
     * @param thickness how deep the block is below its top, in metres
     * @return the block, with its top surface at the node's origin
     */
    private Node slab(float hx, float hz, int topColor, int sideColor, float thickness) {
        Node node = new Node("slab");
        // a thin top layer, and a slightly narrower block below it for the sides
        Geometry top = build.box(0, -0.04f, 0, hx, 0.04f, hz, build.color(topColor));
        Geometry sides = build.box(0, -0.04f - thickness / 2, 0, hx - 0.03f, thickness / 2,
                hz - 0.03f, build.color(sideColor));
        top.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        sides.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        node.attachChild(top);
        node.attachChild(sides);
        return node;
    }

    /**
     * A chunk of road with a dashed centre line.
     *
     * @param hx half its width
     * @param hz half its length
     * @return the chunk, with its top surface at the node's origin
     */
    private Node road(float hx, float hz) {
        Node node = slab(hx, hz, 0x41434b, 0x2c2d33, 0.6f);
        // yellow dashes down the middle: one 0.7 m dash every 1.6 m
        Material paint = build.color(0xe8d36a);
        for (float z = -hz + 0.5f; z < hz - 0.4f; z += 1.6f) {
            node.attachChild(build.box(0, 0.006f, z + 0.3f, 0.07f, 0.006f, 0.35f, paint));
        }
        return node;
    }

    /**
     * A roof to land on, with the top floor of its house hanging underneath.
     *
     * @param hx half its width
     * @param hz half its depth
     * @return the roof, with its top surface at the node's origin
     */
    private Node rooftop(float hx, float hz) {
        // the roof itself overhangs the walls a little
        Node node = slab(hx + 0.2f, hz + 0.2f, 0x8a4a3a, 0x6a382c, 0.25f);
        Geometry walls = build.box(0, -1.9f, 0, hx - 0.2f, 1.6f, hz - 0.2f, build.color(0xd9c7a8));
        walls.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        node.attachChild(walls);
        // two dark blue windows on the north wall, the side the player comes from
        node.attachChild(build.box(-hx * 0.4f, -1.5f, -hz + 0.19f, 0.45f, 0.5f, 0.02f,
                build.color(0x34427a)));
        node.attachChild(build.box(hx * 0.4f, -1.5f, -hz + 0.19f, 0.45f, 0.5f, 0.02f,
                build.color(0x34427a)));
        return node;
    }

    /** The front of the player's own house, behind them as they step out. */
    private void homeFront() {
        // the front wall and the edge of the roof above it, plus one collision box
        // so the player cannot walk back through the house
        scene.attachChild(build.box(-0.25f, 2.2f, 0f, 0.2f, 2.2f, 5.5f, build.color(0xd9c7a8)));
        scene.attachChild(build.box(-0.6f, 4.7f, 0f, 1.0f, 0.3f, 6f, build.color(0x8a4a3a)));
        solids.add(new Aabb(-0.6f, 0, -6f, -0.05f, 5f, 6f));
        // the front door: cut the door out of the hallway wall picture (the four
        // numbers are the pixel rectangle) and stick it on a flat panel on the wall
        Texture2D door = build.crop(Textures.wall('h', "frontdoor"), Textures.SIZE, 34, 20, 94,
                128);
        scene.attachChild(build.panel(new Vector3f(-0.04f, 0, -0.61f), new Vector3f(0, 0, 1.22f),
                new Vector3f(0, 2.19f, 0), 0, 1, 0, 1, build.lit(door)));
        // a window on each side of the door: a blue pane with a pale bar down the middle
        for (float z : new float[] {-3.4f, 3.4f}) {
            scene.attachChild(build.box(-0.04f, 1.6f, z, 0.02f, 0.6f, 0.8f, build.color(0x34427a)));
            scene.attachChild(build.box(-0.03f, 1.6f, z, 0.02f, 0.66f, 0.05f,
                    build.color(0xe8e4d8)));
        }
        // the garden path from the door to the pavement, in two paving slabs
        scene.attachChild(build.box(1.6f, 0.012f, 0f, 1.6f, 0.012f, 0.5f, build.color(0xc9c6bd)));
        scene.attachChild(build.box(4.6f, 0.012f, 0f, 1.4f, 0.012f, 0.5f, build.color(0xc9c6bd)));
    }

    /**
     * A simple house: walls, a roof, a door and two windows.
     *
     * @param x         centre x
     * @param y         height of the ground it stands on
     * @param z         centre z
     * @param wallColor colour of the walls, as 0xRRGGBB
     * @param roofColor colour of the roof, as 0xRRGGBB
     */
    private void house(float x, float y, float z, int wallColor, int roofColor) {
        // the roof is two flat boxes, a wide one and a narrower one on top, which
        // from a distance looks like a sloping roof
        Geometry walls = build.box(x, y + 1.6f, z, 2.4f, 1.6f, 3f, build.color(wallColor));
        Geometry roof = build.box(x, y + 3.45f, z, 2.7f, 0.25f, 3.3f, build.color(roofColor));
        Geometry ridge = build.box(x, y + 3.95f, z, 1.5f, 0.25f, 3.3f, build.color(roofColor));
        for (Geometry part : new Geometry[] {walls, roof, ridge}) {
            part.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
            scene.attachChild(part);
        }
        // a door and two windows on the west wall, the side that faces the road
        scene.attachChild(build.box(x - 2.41f, y + 1.0f, z, 0.02f, 1.0f, 0.45f,
                build.color(0x6a3a2c)));
        scene.attachChild(build.box(x - 2.41f, y + 1.7f, z - 1.7f, 0.02f, 0.5f, 0.6f,
                build.color(0x34427a)));
        scene.attachChild(build.box(x - 2.41f, y + 1.7f, z + 1.7f, 0.02f, 0.5f, 0.6f,
                build.color(0x34427a)));
        // one collision box for the whole house
        solids.add(new Aabb(x - 2.4f, y, z - 3f, x + 2.4f, y + 4.2f, z + 3f));
    }

    /**
     * A street lamp: a tall post, an arm, and a glowing bulb. Decoration only.
     *
     * @param x x of the post
     * @param y height of the ground it stands on
     * @param z z of the post
     */
    private void streetLamp(float x, float y, float z) {
        // a 3.4 m post with a short arm reaching east over the road
        scene.attachChild(build.box(x, y + 1.7f, z, 0.05f, 1.7f, 0.05f, build.color(0x3a3f4a)));
        scene.attachChild(build.box(x + 0.3f, y + 3.4f, z, 0.35f, 0.04f, 0.05f,
                build.color(0x3a3f4a)));
        // the bulb hangs under the end of the arm
        Geometry bulb = new Geometry("bulb", new Sphere(10, 10, 0.12f));
        bulb.setMaterial(build.glow(0xfff0c0));
        bulb.setLocalTranslation(x + 0.6f, y + 3.32f, z);
        scene.attachChild(bulb);
    }

    /**
     * A tree: a brown trunk with a green ball of leaves. Decoration only.
     *
     * @param x x of the trunk
     * @param y height of the ground it stands on
     * @param z z of the trunk
     */
    private void tree(float x, float y, float z) {
        Geometry trunk = build.box(x, y + 1.0f, z, 0.18f, 1.0f, 0.18f, build.color(0x7d5530));
        trunk.setShadowMode(RenderQueue.ShadowMode.Cast);
        scene.attachChild(trunk);
        Geometry leaves = new Geometry("leaves", new Sphere(7, 9, 1.2f));
        leaves.setMaterial(build.color(0x3f9a45));
        leaves.setLocalTranslation(x, y + 2.7f, z);
        leaves.setShadowMode(RenderQueue.ShadowMode.Cast);
        scene.attachChild(leaves);
    }

    /**
     * Runs one frame of the level: obstacles, the player, the dialog boxes and
     * the check for reaching the bed.
     *
     * @param tpf seconds since the last frame
     */
    @Override
    public void update(float tpf) {
        time += tpf;
        Hud hud = game.hud();
        Controls controls = game.controls();
        boolean dialog = hud.isDialogOpen();
        // move the obstacles; if the player died this frame, start a white flash
        if (course.update(tpf)) {
            flash = 1;
        }
        // move the player. While a dialog box is open or the screen is fading to
        // another chapter the controls are passed as null, so they cannot walk
        body.update(tpf, dialog || game.isChanging() ? null : controls, solids, RUN_SPEED, true);
        body.applyTo(game.getCamera());
        course.updateLandingMarker();
        // while a dialog is open, E or ENTER moves to its next line and nothing
        // else in the level reacts
        if (dialog) {
            if (controls.wasPressed("interact") || controls.wasPressed("confirm")) {
                hud.advanceDialog();
            }
            return;
        }
        // the first time the player walks toward the south end of the real street
        // or out onto the road, they notice it is broken and get the jump hint
        if (!noticed && (body.position.z > 4.5f || body.position.x > 9f)) {
            noticed = true;
            hud.openDialog("...Where is the rest of the street?",
                    "There is something down there, in the middle of the road. "
                            + "It looks like a bed.");
            hud.setHint("Space: jump      Road, pavement or rooftops: your choice");
        }
        // the hint goes away 40 seconds into the level
        if (noticed && time > 40) {
            hud.setHint(null);
        }
        // fade the death flash out over 0.4 s (unless a chapter change is already
        // using the screen fade)
        if (flash > 0) {
            flash = Math.max(0, flash - tpf * 2.5f);
            if (!game.isChanging()) {
                hud.setFade(ColorRGBA.White, flash * 0.7f);
            }
        }
        // reaching the bed ends the level with a slow fade to white
        if (body.touches(bedZone)) {
            game.nextChapter(ColorRGBA.White, 1.8f);
        }
    }

    /**
     * Viewpoints for automated test screenshots: the front door, the road, the
     * first checkpoint, a rooftop, the meeting point, the bed, and an overview.
     *
     * @return rows of {x, feet y, z, yaw degrees, pitch degrees}
     */
    @Override
    float[][] debugPoses() {
        return new float[][] {{1.2f, 0, 0, 0, 0}, {10.5f, 0, 2f, 90, -6},
                {10.5f, 0.8f, 26.5f, 90, -8}, {15.6f, 3.2f, 34f, 90, -12},
                {10.5f, 2.4f, 45.5f, 90, 0}, {10.5f, 3.6f, 64.5f, 90, -8},
                {-8f, 14f, 30f, 10, -28}};
    }

    /** Puts the player right beside the bed, one step from finishing (tests only). */
    @Override
    void debugComplete() {
        body.place(10.5f, 3.65f, 66.9f, FastMath.HALF_PI);
    }
}
