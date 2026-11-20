package stillasleep.three;

import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Sphere;
import com.jme3.shadow.DirectionalLightShadowRenderer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Chapter 1, "Level 1": a bright, cheerful obstacle course of floating
 * islands. It is meant to look like the whole game, so reaching the flag
 * feels like winning. The course forks twice: each time there is a longer,
 * safer way round and a shorter, riskier way through.
 *
 * <p>The course runs from west to east: the player starts near x = 0 and the
 * flag is at about x = 69. The middle of the course is the line z = 0;
 * negative z is to the player's left and positive z to their right.
 */
final class LevelState extends Stage {

    /** How fast the player runs in this level, in metres per second. */
    private static final float RUN_SPEED = 4.6f;

    /** The stars that have not been collected yet. */
    private final List<Node> stars = new ArrayList<>();
    /** The clouds, kept so they can drift across the sky. */
    private final List<Geometry> clouds = new ArrayList<>();
    /** Helpers for making boxes, colours and materials. */
    private Build build;
    /** The obstacles: platforms, spikes, checkpoints and so on. */
    private Course course;
    /** The chequered grass used on top of every island. */
    private Material grassMaterial;
    /** Draws the sun's shadows; removed again when the level is left. */
    private DirectionalLightShadowRenderer shadows;
    /** The area around the flagpole; touching it wins the level. */
    private Aabb flagZone;
    /** The node the flag's cloth hangs from, so it can be swung to look like waving. */
    private Node flagHinge;
    /** How many stars the level started with. */
    private int totalStars;
    /** Seconds since the level started (shown as the timer). */
    private float time;
    /** Strength of the white flash shown after a death; fades from 1 to 0. */
    private float flash;
    /** Seconds since the flag was reached, or -1 while still playing. */
    private float wonTime = -1;

    /**
     * Creates the level. Nothing is built until the game switches to it.
     *
     * @param game the running game
     */
    LevelState(Game3D game) {
        super(game);
    }

    /** Builds the whole level: sky, sun, islands, obstacles, stars and scenery. */
    @Override
    protected void build() {
        build = game.build();
        // a clear blue sky
        game.getViewPort().setBackgroundColor(new ColorRGBA(0.42f, 0.72f, 1f, 1f));

        // the sun (a light shining in one direction, here down and slightly sideways)
        // plus a soft all-round light so the shaded sides are not black
        DirectionalLight sun = new DirectionalLight(
                new Vector3f(-0.45f, -1f, -0.35f).normalizeLocal(),
                new ColorRGBA(0.82f, 0.8f, 0.72f, 1f));
        scene.addLight(sun);
        scene.addLight(new AmbientLight(new ColorRGBA(0.46f, 0.5f, 0.58f, 1f)));
        // shadows from the sun: 2048 is the sharpness of the shadow picture and 3 is
        // how many of them are used to cover near, middle and far distances
        shadows = new DirectionalLightShadowRenderer(game.getAssetManager(), 2048, 3);
        shadows.setLight(sun);
        shadows.setShadowIntensity(0.38f);
        shadows.setShadowZExtend(80f);
        game.getViewPort().addProcessor(shadows);

        // set up the obstacle kit. Falling below y = -10 counts as a death, the
        // player starts on the first island facing east, and a dark disc on the
        // ground shows where they will land
        grassMaterial = build.lit(build.texture(grassPixels(), 64, 64));
        course = new Course(build, scene, solids, body, -10f);
        course.setSpawn(-1.5f, 0f, 0f, 0f);
        course.showLandingMarker(0x000000, 0.35f);
        // every platform in this level is drawn by island()
        Course.Look island = this::island;

        // opening: stepping stones up to the first big island
        course.fixed(island, -3f, -2.5f, 4f, 2.5f, 0f);
        course.fixed(island, 5.6f, -0.8f, 7.0f, 0.8f, 0.3f);
        course.fixed(island, 8.6f, -1.6f, 9.8f, -0.4f, 0.7f);
        course.fixed(island, 11.2f, 0.2f, 12.4f, 1.4f, 1.0f);
        course.fixed(island, 14.0f, -2f, 18f, 2f, 0.8f);
        course.checkpoint(14.8f, 0.8f, -1.4f, 0, true);

        // first fork. Straight on: three crumbling stones. To the right: the long way round.
        // (short, risky route: the three crumblers along z = 0)
        course.crumbler(island, 19.6f, 0.8f, 0f, 0.6f);
        course.crumbler(island, 21.8f, 1.0f, 0.3f, 0.6f);
        course.crumbler(island, 24.0f, 1.2f, -0.3f, 0.6f);
        // (long, safe route: a side island, a platform gliding east and back every
        // 7 seconds, and another side island, all along z = 4.8)
        course.fixed(island, 15f, 3.6f, 17.5f, 6f, 0.8f);
        course.mover(island, new Vector3f(19.4f, 0.8f, 4.8f), new Vector3f(23.6f, 0.8f, 4.8f),
                0.9f, 0.9f, 7f);
        course.fixed(island, 25.2f, 3.6f, 27.6f, 6f, 1.0f);

        // both ways meet here; a strip of spikes that has to be jumped
        course.fixed(island, 25.8f, -2f, 30.5f, 2f, 1.2f);
        course.checkpoint(26.6f, 1.2f, -1.4f, 0, true);
        course.spikes(28.2f, -2f, 28.9f, 2f, 1.2f);

        // a narrow beam with a bar sweeping across it
        course.fixed(island, 30.5f, -0.4f, 35.5f, 0.4f, 1.2f);
        course.sweeper(33f, 1.2f, 0f, 1.5f, 1.7f);
        course.fixed(island, 35.5f, -2.2f, 39.5f, 2.2f, 1.2f);
        course.checkpoint(36.2f, 1.2f, 1.6f, 0, true);

        // second fork. Left: bounce pads over the top. Right: platforms and a lift.
        // (left, high route: a bounce pad up to two islands, then a second pad up
        // to an island at height 6, from which the player drops down to the meeting point)
        course.bouncer(38.8f, 1.2f, -1.5f);
        course.fixed(island, 41.5f, -2.6f, 43.5f, -0.6f, 3.6f);
        course.fixed(island, 45.5f, -2.4f, 47.5f, -0.8f, 3.8f);
        course.bouncer(47f, 3.8f, -1.6f);
        course.fixed(island, 50f, -2.6f, 52.5f, -0.4f, 6.0f);
        // (right, low route: a platform gliding east, a small island, then a lift
        // that rises from height 1.4 to 3.0)
        course.mover(island, new Vector3f(41.2f, 1.2f, 1.2f), new Vector3f(45.5f, 1.2f, 1.2f),
                0.9f, 0.9f, 6.5f);
        course.fixed(island, 47.2f, 0.4f, 49.2f, 2.0f, 1.6f);
        course.mover(island, new Vector3f(50.8f, 1.4f, 1.2f), new Vector3f(50.8f, 3.0f, 1.2f),
                0.8f, 0.8f, 5f);

        // both ways meet again; a zigzag between spikes
        // (the first strip leaves a gap on the right, the second a gap on the left)
        course.fixed(island, 53.4f, -2f, 57.4f, 2f, 3.0f);
        course.checkpoint(54.0f, 3.0f, -1.5f, 0, true);
        course.spikes(55.2f, -2f, 55.9f, 0.4f, 3.0f);
        course.spikes(56.4f, -0.5f, 57.1f, 2f, 3.0f);

        // the last climb: crumbling steps, then a sweeper guarding the flag
        course.crumbler(island, 58.8f, 3.3f, 0f, 0.6f);
        course.crumbler(island, 60.8f, 3.7f, 0.6f, 0.6f);
        course.crumbler(island, 62.8f, 4.1f, -0.4f, 0.6f);
        course.fixed(island, 64.6f, -2.5f, 70f, 2.5f, 4.2f);
        course.sweeper(66.6f, 4.2f, 0f, 1.9f, 1.3f);
        flag(69.3f, 4.2f, 0f);

        // the stars to collect, each one {x, y, z}. They hang about a metre above
        // the platforms, and every route has some
        for (float[] s : new float[][] {{1.5f, 1.1f, 0f}, {6.3f, 1.5f, 0f}, {9.2f, 1.9f, -1f},
                {11.8f, 2.2f, 0.8f}, {16f, 2f, 0f}, {21.8f, 2.2f, 0.3f}, {24f, 2.4f, -0.3f},
                {16.2f, 2f, 4.8f}, {21.5f, 2f, 4.8f}, {26.4f, 2.2f, 4.8f}, {28.5f, 2.6f, 0f},
                {33f, 2.5f, 0f}, {37.5f, 2.4f, 0f}, {42.5f, 4.8f, -1.6f}, {46.5f, 5f, -1.6f},
                {51.2f, 7.2f, -1.5f}, {43.3f, 2.4f, 1.2f}, {48.2f, 2.8f, 1.2f}, {50.8f, 4.3f, 1.2f},
                {55.4f, 4.2f, 1.2f}, {60.8f, 4.9f, 0.6f}, {65.4f, 5.4f, 0f}}) {
            star(s[0], s[1], s[2]);
        }
        totalStars = stars.size();
        scenery();

        // put the player at the start (5 cm above the island), looking east
        body.place(-1.5f, 0.05f, 0f, 0f);
        game.hud().setHint("Mouse: look      W A S D: run      Space: jump");
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
     * A two-tone chequered grass picture; the squares help judge distance when jumping.
     *
     * @return 64 x 64 pixel colours, each as 0xRRGGBB, row by row
     */
    private static int[] grassPixels() {
        int[] pixels = new int[64 * 64];
        // a fixed seed, so the "random" speckles are the same every time
        Random random = new Random(2);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                // the picture is four 32-pixel squares; opposite corners share a shade
                boolean light = (x / 32 + y / 32) % 2 == 0;
                // nudge each pixel a little lighter or darker so it is not flat
                int shade = random.nextInt(9) - 4;
                int r = (light ? 0x6f : 0x62) + shade;
                int g = (light ? 0xc8 : 0xb8) + shade;
                int b = (light ? 0x58 : 0x4c) + shade;
                // pack red, green and blue into a single number
                pixels[y * 64 + x] = (r << 16) | (g << 8) | b;
            }
        }
        return pixels;
    }

    /**
     * The dirt-and-grass shape of an island, with its top surface at the node's origin.
     *
     * @param hx half the island's width
     * @param hz half the island's depth
     * @return the island, ready to be positioned
     */
    private Node island(float hx, float hz) {
        Node node = new Node("island");
        // three stacked boxes: a block of dirt, a smaller darker lump under it so
        // the island tapers, and a thin layer of grass that overhangs slightly
        Geometry dirt = build.box(0, -0.62f, 0, hx, 0.5f, hz, build.color(0x9a6a3a));
        Geometry under = build.box(0, -1.35f, 0, hx * 0.72f, 0.25f, hz * 0.72f,
                build.color(0x7d5530));
        Geometry grass = build.box(0, -0.06f, 0, hx + 0.04f, 0.06f, hz + 0.04f, grassMaterial);
        // repeat the grass picture once per two metres, so the squares stay the
        // same size on big and small islands
        grass.getMesh().scaleTextureCoordinates(new Vector2f(Math.max(1, hx), Math.max(1, hz)));
        for (Geometry part : new Geometry[] {dirt, under, grass}) {
            part.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
            node.attachChild(part);
        }
        return node;
    }

    /**
     * Adds a collectable star.
     *
     * @param x x in metres
     * @param y height of its centre
     * @param z z in metres
     */
    private void star(float x, float y, float z) {
        Node star = new Node("star");
        // a sphere made of very few faces looks like a cut gem
        Geometry gem = new Geometry("gem", new Sphere(3, 4, 0.26f));
        gem.setMaterial(build.color(0xffd23f));
        gem.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X));
        gem.setShadowMode(RenderQueue.ShadowMode.Cast);
        star.attachChild(gem);
        star.setLocalTranslation(x, y, z);
        // remember its resting height; animate() bobs it up and down around this
        star.setUserData("baseY", y);
        scene.attachChild(star);
        stars.add(star);
    }

    /**
     * Adds the flag at the end of the level.
     *
     * @param x x in metres
     * @param y height of the surface it stands on
     * @param z z in metres
     */
    private void flag(float x, float y, float z) {
        // a white pole 3.2 m tall with a gold ball on top
        Geometry pole = build.box(x, y + 1.6f, z, 0.05f, 1.6f, 0.05f, build.color(0xf2f2f2));
        pole.setShadowMode(RenderQueue.ShadowMode.Cast);
        scene.attachChild(pole);
        Geometry ball = new Geometry("ball", new Sphere(10, 10, 0.12f));
        ball.setMaterial(build.color(0xffd23f));
        ball.setLocalTranslation(x, y + 3.3f, z);
        scene.attachChild(ball);
        // the red cloth is attached off to one side of a hinge node placed on the
        // pole, so turning the hinge swings the cloth about the pole
        flagHinge = new Node("flag");
        flagHinge.attachChild(build.box(0, 0, 0.5f, 0.02f, 0.32f, 0.5f, build.color(0xe84a4a)));
        flagHinge.setLocalTranslation(x, y + 2.8f, z);
        scene.attachChild(flagHinge);
        // touching anywhere near the pole counts as reaching the flag
        flagZone = Aabb.centered(x, y + 1.5f, z, 0.6f, 1.5f, 0.6f);
    }

    /** Clouds and far-off islands, so the sky doesn't feel empty. */
    private void scenery() {
        Material white = build.glow(0xffffff);
        // each cloud is {x, y, z, size}: a white ball squashed flat and stretched
        float[][] cloudSpots = {{-10, 9, -22, 5}, {8, 12, -30, 7}, {26, 10, -24, 6},
                {48, 13, -16, 7}, {62, 11, 24, 6}, {14, 11, 34, 7}, {-12, 12, 18, 5},
                {36, 16, 40, 8}, {-20, 7, -4, 4}, {78, 14, -8, 7}};
        for (float[] c : cloudSpots) {
            Geometry cloud = new Geometry("cloud", new Sphere(10, 14, 1f));
            cloud.setMaterial(white);
            cloud.setLocalScale(c[3], c[3] * 0.32f, c[3] * 0.6f);
            cloud.setLocalTranslation(c[0], c[1], c[2]);
            scene.attachChild(cloud);
            clouds.add(cloud);
        }
        // each background island is {x, y, z, size}. They are only decoration:
        // they have no collision boxes and are too far away to reach
        float[][] islands = {{-14, -3, -16, 5}, {10, -6, -20, 7}, {36, -4, -16, 4},
                {60, -5, -18, 6}, {8, -4, 22, 5}, {-10, -7, 12, 6}, {44, -8, 24, 8},
                {70, -3, 18, 5}, {84, 0, 0, 6}};
        for (float[] i : islands) {
            // dirt block, then a grass layer on top
            scene.attachChild(build.box(i[0], i[1], i[2], i[3], 0.8f, i[3] * 0.8f,
                    build.color(0x9a6a3a)));
            scene.attachChild(build.box(i[0], i[1] + 0.9f, i[2], i[3] + 0.1f, 0.12f,
                    i[3] * 0.8f + 0.1f, build.color(0x5cc04a)));
            // a simple tree: a brown trunk and a green ball of leaves
            scene.attachChild(build.box(i[0] + i[3] * 0.3f, i[1] + 1.9f, i[2], 0.25f, 0.9f, 0.25f,
                    build.color(0x7d5530)));
            Geometry leaves = new Geometry("leaves", new Sphere(6, 8, 1.1f));
            leaves.setMaterial(build.color(0x3f9a45));
            leaves.setLocalTranslation(i[0] + i[3] * 0.3f, i[1] + 3.3f, i[2]);
            scene.attachChild(leaves);
        }
    }

    /**
     * Runs one frame of the level: obstacles, the player, stars, the flag and
     * the on-screen text.
     *
     * @param tpf seconds since the last frame
     */
    @Override
    public void update(float tpf) {
        time += tpf;
        Hud hud = game.hud();
        Controls controls = game.controls();
        animate(tpf);

        // after winning: the player can no longer move (controls are passed as
        // null) and the game waits for ENTER. The 2.5 s delay stops a key that was
        // already being pressed from skipping the "YOU WIN!" screen
        if (wonTime >= 0) {
            wonTime += tpf;
            body.update(tpf, null, solids, RUN_SPEED, false);
            body.applyTo(game.getCamera());
            if (wonTime > 2.5f && controls.wasPressed("confirm")) {
                game.nextChapter(ColorRGBA.Black, 1.3f);
            }
            return;
        }

        // move the obstacles; if the player died this frame, start a white flash
        if (course.update(tpf)) {
            flash = 1;
        }
        // move the player (ignoring the controls while the screen is fading to
        // another chapter) and point the camera where they are looking
        body.update(tpf, game.isChanging() ? null : controls, solids, RUN_SPEED, true);
        body.applyTo(game.getCamera());
        course.updateLandingMarker();
        collectStars();
        if (body.touches(flagZone)) {
            win();
            return;
        }

        // top of the screen: level name, star count and a minutes:seconds timer
        int collected = totalStars - stars.size();
        hud.setTop("LEVEL 1", "STARS " + collected + "/" + totalStars,
                String.format("TIME %02d:%02d", (int) time / 60, (int) time % 60));
        // the title banner shows for the first 3.2 seconds only
        if (time < 3.2f) {
            hud.setBanner("LEVEL 1", "Reach the flag!", null);
        } else {
            hud.setBanner(null, null, null);
        }
        // the controls hint goes away after 10 seconds
        if (time > 10) {
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
    }

    /**
     * Moves the things that are only decoration: stars, clouds and the flag.
     *
     * @param tpf seconds since the last frame
     */
    private void animate(float tpf) {
        // stars spin and bob 10 cm up and down. Adding p.x to the wave means
        // neighbouring stars are out of step with each other
        for (Node star : stars) {
            star.rotate(0, tpf * 2.2f, 0);
            float baseY = star.getUserData("baseY");
            Vector3f p = star.getLocalTranslation();
            star.setLocalTranslation(p.x, baseY + FastMath.sin(time * 3f + p.x) * 0.1f, p.z);
        }
        // clouds drift slowly east; one that goes past x = 100 jumps back 140 m
        // to the west and comes round again
        for (Geometry cloud : clouds) {
            cloud.move(tpf * 0.35f, 0, 0);
            if (cloud.getLocalTranslation().x > 100) {
                cloud.move(-140, 0, 0);
            }
        }
        // swing the flag a little from side to side, as if in a breeze
        flagHinge.setLocalRotation(new Quaternion().fromAngleAxis(
                FastMath.sin(time * 3f) * 0.25f, Vector3f.UNIT_Y));
    }

    /** Removes every star the player is close enough to pick up. */
    private void collectStars() {
        // measure from the middle of the player's body, not from their feet
        Vector3f centre = body.position.add(0, PlayerBody.HEIGHT / 2, 0);
        // an Iterator is used because it can remove from the list while looping over it
        Iterator<Node> iterator = stars.iterator();
        while (iterator.hasNext()) {
            Node star = iterator.next();
            if (star.getLocalTranslation().distance(centre) < 1.0f) {
                star.removeFromParent();
                iterator.remove();
            }
        }
    }

    /** Called when the flag is touched: stops the timer and shows the "YOU WIN!" screen. */
    private void win() {
        // from now on update() takes its "after winning" branch
        wonTime = 0;
        Hud hud = game.hud();
        // clear anything else that might be on screen
        hud.setFade(ColorRGBA.White, 0);
        hud.setHint(null);
        hud.setPrompt(false);
        // write the final star count and time once; they are not updated again
        int collected = totalStars - stars.size();
        hud.setTop("LEVEL 1", "STARS " + collected + "/" + totalStars,
                String.format("TIME %02d:%02d", (int) time / 60, (int) time % 60));
        // a fake ending: "exit" really leads to the next chapter
        hud.setBanner("YOU WIN!", "Thanks for playing!", "Press ENTER to exit");
    }

    /**
     * Viewpoints for automated test screenshots: the start, the first fork, the
     * first meeting point, the second fork, the second meeting point, and an
     * overview from high up.
     *
     * @return rows of {x, feet y, z, yaw degrees, pitch degrees}
     */
    @Override
    float[][] debugPoses() {
        return new float[][] {{-1.5f, 0, 0, 0, -6}, {16.5f, 0.8f, 0.8f, 20, -12},
                {26.4f, 1.2f, 0, 0, -18}, {36f, 1.2f, 0, 0, 6}, {54f, 3.0f, 0, 0, -10},
                {30f, 14f, -18f, 55, -30}};
    }

    /** Puts the player right beside the flag, one step from winning (tests only). */
    @Override
    void debugComplete() {
        body.place(69.3f, 4.25f, 0.5f, -FastMath.HALF_PI);
    }
}
