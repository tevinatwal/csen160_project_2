package stillasleep.three;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Box;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Helpers for building 3D scenes: materials, textures, flat panels, boxes, and
 * the downloaded furniture models.
 *
 * <p>Some jMonkeyEngine words used here: a <em>mesh</em> is a shape made of
 * triangles; a <em>material</em> says how a surface is coloured and whether
 * lights affect it; a <em>texture</em> is a picture stretched over a surface;
 * a <em>geometry</em> is a mesh plus a material, ready to be drawn; and a
 * <em>node</em> is an invisible group that holds other things.
 */
final class Build {

    /** The furniture pack is modelled at half size; this brings it to metres. */
    static final float MODEL_SCALE = 2f;

    /** The engine's loader for models, fonts and material definitions. */
    private final AssetManager assets;
    /** Plain-colour materials made so far, so each colour is only created once. */
    private final Map<String, Material> materials = new HashMap<>();

    /**
     * @param assets the engine's asset manager
     */
    Build(AssetManager assets) {
        this.assets = assets;
    }

    /**
     * Turns generated pixels into a texture.
     *
     * @param pixels 0xRRGGBB pixels, top row first
     * @param width  image width
     * @param height image height
     * @return a repeating texture
     */
    Texture2D texture(int[] pixels, int width, int height) {
        // The graphics card wants 4 bytes per pixel: red, green, blue, alpha.
        ByteBuffer data = BufferUtils.createByteBuffer(width * height * 4);

        // Our pixels start at the top row, but the engine expects the bottom
        // row first, so the rows are copied in reverse order.
        for (int y = height - 1; y >= 0; y--) {
            for (int x = 0; x < width; x++) {
                // Split 0xRRGGBB into three bytes by shifting. The cast to
                // byte keeps only the lowest 8 bits. Alpha 255 = fully solid.
                int rgb = pixels[y * width + x];
                data.put((byte) (rgb >> 16)).put((byte) (rgb >> 8)).put((byte) rgb).put((byte) 255);
            }
        }
        // flip() rewinds the buffer so it can be read from the beginning.
        data.flip();
        Texture2D texture = new Texture2D(new Image(Image.Format.RGBA8, width, height, data,
                ColorSpace.sRGB));

        // Repeat: the picture tiles across a large wall instead of stretching.
        texture.setWrap(Texture.WrapMode.Repeat);
        // These filters smooth the picture when it is seen from far away
        // (min), from close up (mag) and at a slant (anisotropic).
        texture.setMinFilter(Texture.MinFilter.Trilinear);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        texture.setAnisotropicFilter(8);
        return texture;
    }

    /**
     * Cuts a rectangle out of a square generated texture.
     *
     * @param pixels source pixels
     * @param size   source width and height
     * @param x0     left edge
     * @param y0     top edge
     * @param x1     right edge (exclusive)
     * @param y1     bottom edge (exclusive)
     * @return the cropped texture
     */
    Texture2D crop(int[] pixels, int size, int x0, int y0, int x1, int y1) {
        int w = x1 - x0;
        int h = y1 - y0;
        int[] out = new int[w * h];
        // Copy one row at a time. Row y of the result comes from row y0 + y
        // of the source, starting x0 pixels in.
        for (int y = 0; y < h; y++) {
            System.arraycopy(pixels, (y0 + y) * size + x0, out, y * w, w);
        }
        return texture(out, w, h);
    }

    /**
     * Draws a handwritten paper note as a texture.
     *
     * @param text what the note says
     * @return the note picture
     */
    Texture2D note(String text) {
        // Paint the note with Java 2D on a small 128 x 96 picture in memory.
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(128, 96,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Cream paper, with a slightly darker strip along the top edge.
        g.setColor(new java.awt.Color(0xf4ecd0));
        g.fillRect(0, 0, 128, 96);
        g.setColor(new java.awt.Color(0xd8cfae));
        g.fillRect(0, 0, 128, 6);

        // Dark red, bold italic writing, centred: start half the text's
        // width to the left of the middle (x = 64).
        g.setColor(new java.awt.Color(0x8a2a2a));
        g.setFont(new java.awt.Font(java.awt.Font.SERIF,
                java.awt.Font.BOLD | java.awt.Font.ITALIC, 44));
        int w = g.getFontMetrics().stringWidth(text);
        g.drawString(text, 64 - w / 2, 64);
        g.dispose();

        // Read the painted pixels back out and turn them into a texture.
        return texture(image.getRGB(0, 0, 128, 96, null, 0, 128), 128, 96);
    }

    /**
     * Makes a textured material that is brighter or darker depending on the
     * lights around it.
     *
     * @param texture surface picture
     * @return a material that reacts to the lights in the scene
     */
    Material lit(Texture texture) {
        // "Lighting.j3md" is the engine's standard material for lit surfaces.
        Material material = new Material(assets, "Common/MatDefs/Light/Lighting.j3md");
        material.setTexture("DiffuseMap", texture);
        return material;
    }

    /**
     * Makes (or reuses) a plain-coloured material that reacts to lights.
     *
     * @param rgb colour as 0xRRGGBB
     * @return a plain-coloured material that reacts to lights (cached)
     */
    Material color(int rgb) {
        // computeIfAbsent runs the code below only the first time a colour
        // is asked for; after that the stored material is handed back.
        return materials.computeIfAbsent("lit" + rgb, k -> {
            ColorRGBA c = rgba(rgb);
            Material material = new Material(assets, "Common/MatDefs/Light/Lighting.j3md");
            // Use the colours set here instead of a texture. "Diffuse" is the
            // colour under direct light, "Ambient" the colour in shadow.
            material.setBoolean("UseMaterialColors", true);
            material.setColor("Diffuse", c);
            material.setColor("Ambient", c);
            // Black specular = no shiny highlights, so surfaces look matte.
            material.setColor("Specular", ColorRGBA.Black);
            material.setFloat("Shininess", 1f);
            return material;
        });
    }

    /**
     * Makes (or reuses) a plain-coloured material that always shows at full
     * brightness.
     *
     * @param rgb colour as 0xRRGGBB
     * @return a material that ignores lighting, so it appears to glow
     */
    Material glow(int rgb) {
        return materials.computeIfAbsent("glow" + rgb, k -> {
            // "Unshaded" means lights and shadows have no effect on it.
            Material material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
            material.setColor("Color", rgba(rgb));
            return material;
        });
    }

    /**
     * Converts a 0xRRGGBB number into the engine's colour type.
     *
     * @param rgb colour as 0xRRGGBB
     * @return the engine colour
     */
    static ColorRGBA rgba(int rgb) {
        // Shift and mask to pull out each 0-255 part, then divide by 255
        // because the engine stores colours as numbers from 0 to 1.
        return new ColorRGBA(((rgb >> 16) & 0xff) / 255f, ((rgb >> 8) & 0xff) / 255f,
                (rgb & 0xff) / 255f, 1f);
    }

    /**
     * Builds a flat four-sided panel. It is visible from the side that
     * {@code edgeA x edgeB} points toward.
     *
     * @param corner   first corner
     * @param edgeA    edge from the corner (texture u runs along it)
     * @param edgeB    second edge from the corner (texture v runs along it)
     * @param u0       texture u at the corner
     * @param u1       texture u at the end of edgeA
     * @param v0       texture v at the corner
     * @param v1       texture v at the end of edgeB
     * @param material surface material
     * @return the panel
     */
    Geometry panel(Vector3f corner, Vector3f edgeA, Vector3f edgeB, float u0, float u1, float v0,
            float v1, Material material) {
        // The other three corners, going round the panel in order.
        Vector3f b = corner.add(edgeA);
        Vector3f c = b.add(edgeB);
        Vector3f d = corner.add(edgeB);

        // The "normal" is the direction the panel faces. The cross product
        // of two edges gives a direction at right angles to both of them.
        Vector3f n = edgeA.cross(edgeB).normalizeLocal();

        // A mesh is described by lists of numbers ("buffers"):
        // where each corner is (3 numbers per corner),
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, new float[] {corner.x, corner.y, corner.z,
            b.x, b.y, b.z, c.x, c.y, c.z, d.x, d.y, d.z});
        // which way each corner faces, used for lighting (the same for all four),
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, new float[] {n.x, n.y, n.z, n.x, n.y, n.z,
            n.x, n.y, n.z, n.x, n.y, n.z});
        // which point of the texture is pinned to each corner (2 numbers each),
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, new float[] {u0, v0, u1, v0, u1, v1, u0, v1});
        // and which corners make up the two triangles that fill the rectangle.
        mesh.setBuffer(VertexBuffer.Type.Index, 3, new short[] {0, 1, 2, 0, 2, 3});

        // Work out the mesh's size, which the engine needs in order to know
        // whether the panel is on screen.
        mesh.updateBound();
        Geometry geometry = new Geometry("panel", mesh);
        geometry.setMaterial(material);
        return geometry;
    }

    /**
     * Builds a box.
     *
     * @param cx centre x
     * @param cy centre y
     * @param cz centre z
     * @param hx half width
     * @param hy half height
     * @param hz half depth
     * @param material surface material
     * @return the box
     */
    Geometry box(float cx, float cy, float cz, float hx, float hy, float hz, Material material) {
        // The engine's Box shape is made around (0, 0, 0) from half sizes,
        // and is then moved so its centre is where we want it.
        Geometry geometry = new Geometry("box", new Box(hx, hy, hz));
        geometry.setMaterial(material);
        geometry.setLocalTranslation(cx, cy, cz);
        return geometry;
    }

    /**
     * Loads a furniture model and stands it on a spot. The model is centred on
     * (x, z) with its underside at height y, whatever its own origin is.
     *
     * @param parent  node to attach it to
     * @param name    model file name without extension
     * @param x       centre x
     * @param y       height of its underside
     * @param z       centre z
     * @param degrees rotation about the vertical axis
     * @param scale   extra scale on top of {@link #MODEL_SCALE}
     * @return the space the placed model occupies
     */
    Aabb model(Node parent, String name, float x, float y, float z, float degrees, float scale) {
        return place(parent, name, x, y, z, degrees, scale, false).box();
    }

    /**
     * A model that has been put into a scene: its node and the space it takes up.
     *
     * @param node the node holding the model, for moving or removing it later
     * @param box  the space the model occupies, usable as a collision box
     */
    record Placed(Node node, Aabb box) {
    }

    /**
     * Like {@link #model}, but can turn the model upside down and also returns its node.
     *
     * @param parent  node to attach it to
     * @param name    model file name without extension
     * @param x       centre x
     * @param y       height of its underside
     * @param z       centre z
     * @param degrees rotation about the vertical axis
     * @param scale   extra scale on top of {@link #MODEL_SCALE}
     * @param flipped true to turn it upside down
     * @return the node and the space it occupies
     */
    Placed place(Node parent, String name, float x, float y, float z, float degrees, float scale,
            boolean flipped) {
        Spatial model = assets.loadModel("models/" + name + ".obj");

        // A model is made of several parts. Visit each one and adjust its
        // material so the furniture matches the rest of the game.
        model.depthFirstTraversal(spatial -> {
            if (spatial instanceof Geometry geometry) {
                Material material = geometry.getMaterial();
                MatParam diffuse = material.getParam("Diffuse");
                if (diffuse != null) {
                    // Use the part's own colour in shadow as well (ambient),
                    // otherwise unlit sides would look wrong, and switch off
                    // shiny highlights so it is matte like the walls.
                    material.setBoolean("UseMaterialColors", true);
                    material.setColor("Ambient", (ColorRGBA) diffuse.getValue());
                    material.setColor("Specular", ColorRGBA.Black);
                    material.setFloat("Shininess", 1f);
                }
            }
        });

        // Put the model inside a "holder" node. Scaling, turning and moving
        // the holder then moves the whole model, wherever its own origin is.
        Node holder = new Node(name);
        holder.attachChild(model);
        holder.setLocalScale(MODEL_SCALE * scale);

        // A quaternion is how the engine stores a rotation. This one turns
        // the model about the upright (y) axis.
        Quaternion turn = new Quaternion().fromAngleAxis(degrees * FastMath.DEG_TO_RAD,
                Vector3f.UNIT_Y);
        if (flipped) {
            // Add half a turn (PI radians = 180 degrees) about the x axis,
            // which leaves the model upside down.
            turn = new Quaternion().fromAngleAxis(FastMath.PI, Vector3f.UNIT_X).mult(turn);
        }
        holder.setLocalRotation(turn);

        // Ask the engine to work out the scaled, turned model's bounding box:
        // the smallest upright box that contains all of it.
        holder.updateGeometricState();
        BoundingBox bounds = (BoundingBox) holder.getWorldBound();
        Vector3f centre = bounds.getCenter();

        // Slide the holder so that box is centred on (x, z) and its bottom
        // (centre minus half the height) sits at height y.
        holder.move(x - centre.x, y - (centre.y - bounds.getYExtent()), z - centre.z);
        parent.attachChild(holder);

        // Return a collision box of the same size at the final position.
        // The "extents" are half sizes, so its centre is half a height above y.
        return new Placed(holder, Aabb.centered(x, y + bounds.getYExtent(), z, bounds.getXExtent(),
                bounds.getYExtent(), bounds.getZExtent()));
    }
}
