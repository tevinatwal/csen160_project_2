package stillasleep.three;

import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;

import java.util.ArrayList;
import java.util.List;

/**
 * The moving parts of a platforming course, shared by every platformer level:
 * fixed and moving platforms, platforms that crumble, bounce pads, spikes,
 * sweeping bars, checkpoints, and putting the player back after a fall.
 * Each level supplies its own {@link Look} so the same obstacles can appear
 * as grassy islands, floorboards or chunks of road.
 *
 * <p>How a level uses it: create one Course, call the "add" methods
 * ({@link #fixed}, {@link #mover}, {@link #crumbler} ...) once while building
 * the level, then call {@link #update(float)} every frame.
 *
 * <p>All positions are in metres: x is east, z is south and y is up.
 */
final class Course {

    /** How a level wants its platforms to look. */
    interface Look {
        /**
         * Builds the visible shape of one platform.
         *
         * @param halfX half the platform's width
         * @param halfZ half the platform's depth
         * @return the platform's shape, with its top surface at the node's origin
         */
        Node make(float halfX, float halfZ);
    }

    /** A platform that glides back and forth between two points, carrying the player. */
    private final class Mover {
        /** The visible platform. */
        final Node node;
        /** The collision box at the platform's current position (replaced every frame). */
        Aabb current;
        /** Centre of the top surface at one end of the journey. */
        final Vector3f from;
        /** Centre of the top surface at the other end of the journey. */
        final Vector3f to;
        /** Half the platform's width (x direction). */
        final float halfX;
        /** Half the platform's depth (z direction). */
        final float halfZ;
        /** Seconds for one full there-and-back trip. */
        final float period;
        /** Where the centre of the top surface is right now. */
        final Vector3f top = new Vector3f();

        /**
         * Creates the mover at its starting point and registers its collision box.
         *
         * @param node   the visible platform
         * @param from   surface centre at one end of its journey
         * @param to     surface centre at the other end
         * @param halfX  half its width
         * @param halfZ  half its depth
         * @param period seconds for a full there-and-back trip
         */
        Mover(Node node, Vector3f from, Vector3f to, float halfX, float halfZ, float period) {
            this.node = node;
            this.from = from;
            this.to = to;
            this.halfX = halfX;
            this.halfZ = halfZ;
            this.period = period;
            // start at the "from" end and make the platform solid there
            top.set(from);
            node.setLocalTranslation(top);
            current = box();
            solids.add(current);
        }

        /**
         * Works out the collision box for where the platform is now.
         *
         * @return a box 0.4 m thick whose top is the platform's surface
         */
        Aabb box() {
            return new Aabb(top.x - halfX, top.y - 0.4f, top.z - halfZ, top.x + halfX, top.y,
                    top.z + halfZ);
        }

        /** Moves the platform to this frame's position, taking the player along if on it. */
        void update() {
            // blend goes smoothly 0 -> 1 -> 0 over one period (a cosine wave), so the
            // platform slows down at each end instead of bouncing off it
            float blend = 0.5f - 0.5f * FastMath.cos(time * FastMath.TWO_PI / period);
            Vector3f next = new Vector3f().interpolateLocal(from, to, blend);
            // if the player is riding it, shift them by the same amount the platform
            // moved and keep their feet on its surface
            if (standingOn(current)) {
                body.position.x += next.x - top.x;
                body.position.z += next.z - top.z;
                body.position.y = next.y;
            }
            top.set(next);
            node.setLocalTranslation(top);
            // collision boxes cannot be moved, so swap the old box for a new one
            solids.remove(current);
            current = box();
            solids.add(current);
        }
    }

    /** A platform that shakes and drops away shortly after being stepped on, then returns. */
    private final class Crumbler {
        /** The visible platform. */
        final Node node;
        /** Its collision box while it is still in place. */
        final Aabb box;
        /** Where the platform belongs before it falls. */
        final Vector3f home;
        /** True while the platform has dropped away and cannot be stood on. */
        boolean gone;
        /** Seconds since the player first stepped on it, or -1 if untouched. */
        float timer = -1;

        /**
         * Creates the crumbler and makes it solid.
         *
         * @param node the visible platform
         * @param box  its collision box
         * @param home where its top surface centre sits
         */
        Crumbler(Node node, Aabb box, Vector3f home) {
            this.node = node;
            this.box = box;
            this.home = home;
            solids.add(box);
        }

        /**
         * Runs the shake / fall / come back sequence.
         *
         * @param tpf seconds since the last frame
         */
        void update(float tpf) {
            // untouched: wait until the player steps on it, then start the clock
            if (timer < 0) {
                if (standingOn(box)) {
                    timer = 0;
                }
                return;
            }
            timer += tpf;
            if (timer < 0.55f) {
                // first 0.55 s: a fast little wobble (2.5 cm) as a warning
                node.setLocalTranslation(home.x + FastMath.sin(timer * 70) * 0.025f, home.y,
                        home.z + FastMath.cos(timer * 55) * 0.025f);
            } else if (timer < 3.6f) {
                // then it stops being solid and drops faster and faster
                if (!gone) {
                    gone = true;
                    solids.remove(box);
                }
                float fall = timer - 0.55f;
                node.setLocalTranslation(home.x, home.y - fall * fall * 9f, home.z);
            } else {
                // 3.6 s after being touched it reappears, ready to be used again
                reset();
            }
        }

        /** Puts the platform back in place, solid and untouched. */
        void reset() {
            timer = -1;
            node.setLocalTranslation(home);
            if (gone) {
                gone = false;
                solids.add(box);
            }
        }
    }

    /** A bar that swings round and round just above a platform; touching it is fatal. */
    private final class Sweeper {
        /** The node the bar hangs from; turning it turns the bar. */
        final Node pivot;
        /** The point on the ground the bar turns around. */
        final Vector3f centre;
        /** How far the bar reaches from the centre, each way. */
        final float length;
        /** Turning speed in radians per second. */
        final float speed;

        /**
         * @param pivot  node holding the bar
         * @param centre point the bar turns around
         * @param length how far the bar reaches each way
         * @param speed  turning speed in radians per second
         */
        Sweeper(Node pivot, Vector3f centre, float length, float speed) {
            this.pivot = pivot;
            this.centre = centre;
            this.length = length;
            this.speed = speed;
        }

        /**
         * Turns the bar and checks whether it is hitting the player.
         *
         * @return true if the player is touching the bar
         */
        boolean update() {
            // the bar's angle depends only on the clock, so it turns at a steady rate
            float angle = time * speed;
            pivot.setLocalRotation(new Quaternion().fromAngleAxis(angle, Vector3f.UNIT_Y));
            // (dx, dz) is the direction the bar points in along the ground
            float dx = FastMath.cos(angle);
            float dz = -FastMath.sin(angle);
            // collision boxes cannot be rotated, so test a row of small boxes placed
            // every 0.3 m along the bar, on both sides of the centre (sign -1 and +1)
            for (float along = 0.3f; along <= length; along += 0.3f) {
                for (int sign = -1; sign <= 1; sign += 2) {
                    Aabb part = Aabb.centered(centre.x + dx * along * sign, centre.y + 0.3f,
                            centre.z + dz * along * sign, 0.14f, 0.16f, 0.14f);
                    if (body.touches(part)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /** Helpers for making boxes, colours and materials. */
    private final Build build;
    /** The level's scene node; everything visible is attached to it. */
    private final Node scene;
    /** The level's collision boxes. Platforms are added here so the player can stand on them. */
    private final List<Aabb> solids;
    /** The player. */
    private final PlayerBody body;
    /** Falling below this height counts as a death. */
    private final float killHeight;
    /** Every moving platform in the course. */
    private final List<Mover> movers = new ArrayList<>();
    /** Every crumbling platform in the course. */
    private final List<Crumbler> crumblers = new ArrayList<>();
    /** Every sweeping bar in the course. */
    private final List<Sweeper> sweepers = new ArrayList<>();
    /** The collision boxes of the bounce pads. */
    private final List<Aabb> bouncers = new ArrayList<>();
    /** Areas that kill the player on touch (the spikes). */
    private final List<Aabb> hazards = new ArrayList<>();
    /** One entry per checkpoint: {x, y, z, yaw}. */
    private final List<float[]> checkpoints = new ArrayList<>();
    /** The lamp on top of each checkpoint post (null for an invisible checkpoint). */
    private final List<Geometry> checkpointLamps = new ArrayList<>();
    /** Where the player is put back after dying. */
    private final Vector3f spawn = new Vector3f();
    /** Which way the player faces after being put back, in radians. */
    private float spawnYaw;
    /** Seconds since the course started; drives the movers and sweepers. */
    private float time;
    /** How many times the player has died. */
    private int deaths;
    /** The disc drawn on the ground under the player, or null if not shown. */
    private Geometry marker;

    /**
     * Creates an empty course. Obstacles are added afterwards with the other methods.
     *
     * @param build      scene helpers
     * @param scene      node the obstacles are attached to
     * @param solids     the stage's collision boxes
     * @param body       the player
     * @param killHeight falling below this height sends the player back
     */
    Course(Build build, Node scene, List<Aabb> solids, PlayerBody body, float killHeight) {
        this.build = build;
        this.scene = scene;
        this.solids = solids;
        this.body = body;
        this.killHeight = killHeight;
    }

    /**
     * Shows a disc on the ground under the player, so they can see where a
     * jump will land.
     *
     * @param rgb   colour as 0xRRGGBB (dark for bright levels, pale for dark ones)
     * @param alpha how solid it looks, 0 to 1
     */
    void showLandingMarker(int rgb, float alpha) {
        // a very flat cylinder: 0.3 m radius, 1 cm tall
        marker = new Geometry("marker", new Cylinder(2, 20, 0.3f, 0.01f, true));
        // copy the material so making it see-through doesn't affect anything else
        Material material = build.glow(rgb).clone();
        com.jme3.math.ColorRGBA color = Build.rgba(rgb);
        color.a = alpha;
        material.setColor("Color", color);
        material.getAdditionalRenderState().setBlendMode(
                com.jme3.material.RenderState.BlendMode.Alpha);
        marker.setMaterial(material);
        // see-through things must be drawn after everything solid
        marker.setQueueBucket(RenderQueue.Bucket.Transparent);
        // cylinders are made lying on their side; tip it 90 degrees so it lies flat
        marker.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X));
        scene.attachChild(marker);
    }

    /** Moves the landing marker onto the highest surface directly below the player. */
    void updateLandingMarker() {
        if (marker == null) {
            return;
        }
        // look through every box that is under the player's feet (the player's x and z
        // are inside it and its top is not above them) and remember the highest top
        float best = Float.NEGATIVE_INFINITY;
        Vector3f p = body.position;
        for (Aabb box : solids) {
            if (p.x > box.minX() && p.x < box.maxX() && p.z > box.minZ() && p.z < box.maxZ()
                    && box.maxY() <= p.y + 0.05f && box.maxY() > best) {
                best = box.maxY();
            }
        }
        // nothing below (over a gap): hide the marker; otherwise draw it just above
        // the surface so the two don't flicker
        marker.setCullHint(best == Float.NEGATIVE_INFINITY
                ? com.jme3.scene.Spatial.CullHint.Always : com.jme3.scene.Spatial.CullHint.Never);
        marker.setLocalTranslation(p.x, best + 0.014f, p.z);
    }

    /**
     * Sets where the player returns to after a fall.
     *
     * @param x   x in metres
     * @param y   height of the surface
     * @param z   z in metres
     * @param yaw facing direction in radians
     */
    void setSpawn(float x, float y, float z, float yaw) {
        // 5 cm above the surface, so the player drops onto it rather than starting inside it
        spawn.set(x, y + 0.05f, z);
        spawnYaw = yaw;
    }

    /**
     * Checks whether the player's feet are resting on a box.
     *
     * @param box the box to test
     * @return true if the player is standing on top of the box
     */
    private boolean standingOn(Aabb box) {
        Vector3f p = body.position;
        float w = PlayerBody.HALF_WIDTH;
        // on the ground, feet within 12 cm of the box's top, and overlapping it from above
        // (the player's own half width is added so standing on the very edge still counts)
        return body.isOnGround() && Math.abs(p.y - box.maxY()) < 0.12f
                && p.x > box.minX() - w && p.x < box.maxX() + w
                && p.z > box.minZ() - w && p.z < box.maxZ() + w;
    }

    /**
     * Adds a platform that never moves.
     *
     * @param look how it looks
     * @param x0   west edge
     * @param z0   north edge
     * @param x1   east edge
     * @param z1   south edge
     * @param top  height of its surface
     */
    void fixed(Look look, float x0, float z0, float x1, float z1, float top) {
        // the look is built around its own centre, so place it at the middle of the rectangle
        Node node = look.make((x1 - x0) / 2, (z1 - z0) / 2);
        node.setLocalTranslation((x0 + x1) / 2, top, (z0 + z1) / 2);
        scene.attachChild(node);
        // the solid part is 1.2 m thick, so the player cannot slip in underneath the edge
        solids.add(new Aabb(x0, top - 1.2f, z0, x1, top, z1));
    }

    /**
     * Adds a platform that glides between two points and carries the player.
     *
     * @param look   how it looks
     * @param from   surface centre at one end of its journey
     * @param to     surface centre at the other end
     * @param halfX  half its width
     * @param halfZ  half its depth
     * @param period seconds for a full there-and-back trip
     */
    void mover(Look look, Vector3f from, Vector3f to, float halfX, float halfZ, float period) {
        Node node = look.make(halfX, halfZ);
        scene.attachChild(node);
        movers.add(new Mover(node, from, to, halfX, halfZ, period));
    }

    /**
     * Adds a small platform that drops away shortly after being stepped on.
     *
     * @param look how it looks
     * @param x    centre x
     * @param top  height of its surface
     * @param z    centre z
     * @param half half its width and depth
     */
    void crumbler(Look look, float x, float top, float z, float half) {
        Node node = look.make(half, half);
        Vector3f home = new Vector3f(x, top, z);
        node.setLocalTranslation(home);
        scene.attachChild(node);
        // its collision box is a square, 0.4 m thick, with its top at the surface
        crumblers.add(new Crumbler(node, new Aabb(x - half, top - 0.4f, z - half, x + half, top,
                z + half), home));
    }

    /**
     * Adds a bounce pad that throws the player high into the air.
     *
     * @param x centre x
     * @param y height of the surface it sits on
     * @param z centre z
     */
    void bouncer(float x, float y, float z) {
        // the red base: a short, wide cylinder tipped over to lie flat
        Geometry pad = new Geometry("bouncer", new Cylinder(2, 20, 0.45f, 0.16f, true));
        pad.setMaterial(build.color(0xe8483f));
        pad.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X));
        pad.setLocalTranslation(x, y + 0.08f, z);
        pad.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        scene.attachChild(pad);
        // a smaller yellow disc on top, so it reads as a target to jump on
        Geometry ring = new Geometry("ring", new Cylinder(2, 20, 0.3f, 0.18f, true));
        ring.setMaterial(build.color(0xffd23f));
        ring.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X));
        ring.setLocalTranslation(x, y + 0.09f, z);
        scene.attachChild(ring);
        // the pad is solid (so the player can land on it) and also listed as a
        // bouncer, which is what update() checks to launch the player
        Aabb box = new Aabb(x - 0.45f, y, z - 0.45f, x + 0.45f, y + 0.16f, z + 0.45f);
        solids.add(box);
        bouncers.add(box);
    }

    /**
     * Covers a rectangle with spikes.
     *
     * @param x0 west edge
     * @param z0 north edge
     * @param x1 east edge
     * @param z1 south edge
     * @param y  height of the surface they stand on
     */
    void spikes(float x0, float z0, float x1, float z1, float y) {
        Material metal = build.color(0xe6ebf0);
        // fill the rectangle with a grid of cones, one every 34 cm
        for (float x = x0 + 0.17f; x < x1; x += 0.34f) {
            for (float z = z0 + 0.17f; z < z1; z += 0.34f) {
                // a cylinder with one end shrunk to almost nothing is a cone (0.4 m tall)
                Geometry cone = new Geometry("spike", new Cylinder(2, 8, 0.001f, 0.15f, 0.4f, true,
                        false));
                cone.setMaterial(metal);
                // tip it from lying on its side to standing upright
                cone.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI,
                        Vector3f.UNIT_X));
                cone.setLocalTranslation(x, y + 0.2f, z);
                cone.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
                scene.attachChild(cone);
            }
        }
        // one deadly box covers the whole patch. It is slightly smaller and lower than
        // the spikes look, so a near miss is forgiven
        hazards.add(new Aabb(x0 + 0.05f, y, z0 + 0.05f, x1 - 0.05f, y + 0.32f, z1 - 0.05f));
    }

    /**
     * Adds a bar that sweeps round in a circle just above the ground.
     *
     * @param x      centre x
     * @param y      height of the surface it stands on
     * @param z      centre z
     * @param length how far the bar reaches each way
     * @param speed  turning speed in radians per second
     */
    void sweeper(float x, float y, float z, float length, float speed) {
        // the red bar hangs from a pivot node 0.3 m above the ground: low enough to
        // hit a standing player, low enough to jump over
        Node pivot = new Node("sweeper");
        pivot.setLocalTranslation(x, y, z);
        pivot.attachChild(build.box(0, 0.3f, 0, length, 0.07f, 0.07f, build.color(0xe84a4a)));
        scene.attachChild(pivot);
        // the grey post in the middle that the bar appears to spin on (decoration only)
        scene.attachChild(build.box(x, y + 0.2f, z, 0.12f, 0.2f, 0.12f, build.color(0x4a4a55)));
        sweepers.add(new Sweeper(pivot, new Vector3f(x, y, z), length, speed));
    }

    /**
     * Adds a checkpoint. Walking near it makes it the place the player returns to.
     *
     * @param x       x in metres
     * @param y       height of the surface
     * @param z       z in metres
     * @param yaw     direction to face after returning, in radians
     * @param visible false for a checkpoint with no post
     */
    void checkpoint(float x, float y, float z, float yaw, boolean visible) {
        Geometry lamp = null;
        if (visible) {
            // a thin wooden post, 1 m tall, with a grey ball on top (turns green when reached)
            scene.attachChild(build.box(x, y + 0.5f, z, 0.04f, 0.5f, 0.04f, build.color(0x6b4a32)));
            lamp = new Geometry("checkpoint", new Sphere(10, 10, 0.16f));
            lamp.setMaterial(build.color(0xc9ced4));
            lamp.setLocalTranslation(x, y + 1.1f, z);
            scene.attachChild(lamp);
        }
        // the two lists stay in step: entry i of one belongs to entry i of the other
        checkpointLamps.add(lamp);
        checkpoints.add(new float[] {x, y, z, yaw});
    }

    /**
     * Moves everything and checks whether the player has died. Call once per
     * frame, before the player is updated.
     *
     * @param tpf seconds since the last frame
     * @return true if the player was sent back to a checkpoint this frame
     */
    boolean update(float tpf) {
        time += tpf;
        // move the platforms first, so the player is carried before they move themselves
        for (Mover mover : movers) {
            mover.update();
        }
        for (Crumbler crumbler : crumblers) {
            crumbler.update(tpf);
        }
        // bounce pads: launch upwards at 11 m/s (a normal jump is 6.6), which is
        // roughly 3.4 m of height instead of 1.2 m
        for (Aabb pad : bouncers) {
            if (standingOn(pad)) {
                body.launch(11f);
            }
        }
        // checkpoints: reached when within about 1.8 m sideways (3.2 is that distance
        // squared) and 1.2 m in height. The last test skips the checkpoint that is
        // already the spawn point, so it is not set again every frame
        for (int i = 0; i < checkpoints.size(); i++) {
            float[] c = checkpoints.get(i);
            float dx = body.position.x - c[0];
            float dz = body.position.z - c[2];
            if (dx * dx + dz * dz < 3.2f && Math.abs(body.position.y - c[1]) < 1.2f
                    && (Math.abs(spawn.x - c[0]) > 0.01f || Math.abs(spawn.z - c[2]) > 0.01f)) {
                setSpawn(c[0], c[1], c[2], c[3]);
                if (checkpointLamps.get(i) != null) {
                    checkpointLamps.get(i).setMaterial(build.glow(0x7ed957));
                }
            }
        }
        // three ways to die: falling too far, touching spikes, or being hit by a bar.
        // The sweepers are updated here too, so their bars keep turning
        boolean dead = body.position.y < killHeight;
        for (Aabb hazard : hazards) {
            dead |= body.touches(hazard);
        }
        for (Sweeper sweeper : sweepers) {
            dead |= sweeper.update();
        }
        // on death: back to the last checkpoint, with every crumbled platform restored
        if (dead) {
            deaths++;
            body.place(spawn.x, spawn.y, spawn.z, spawnYaw);
            for (Crumbler crumbler : crumblers) {
                crumbler.reset();
            }
        }
        return dead;
    }

    /**
     * Reports the death count.
     *
     * @return how many times the player has been sent back
     */
    int deaths() {
        return deaths;
    }
}
