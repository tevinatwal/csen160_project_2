package stillasleep.three;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * A piece of on-screen text. The text is drawn with Java 2D into a picture at
 * high resolution, so it stays sharp at any size, and shown on a flat panel.
 *
 * <p>In other words, the engine never draws letters. Each time the text
 * changes, this class paints the words into an image with a see-through
 * background, turns that image into a texture, and puts the texture on a
 * rectangle (a "quad") of the same shape.
 */
final class Label {

    /** Pictures are drawn this many times larger than shown, for sharp edges. */
    private static final int DETAIL = 2;

    /** The rectangle the text picture is shown on. */
    private final Geometry geometry;
    /** The rectangle's material; it holds the text picture and the opacity. */
    private final Material material;
    /** Font used to paint the text (already enlarged by {@link #DETAIL}). */
    private final Font font;
    /** Colour of the letters. */
    private final Color color;
    /** Widest allowed line in picture pixels, or 0 for no wrapping. */
    private final int wrapWidth;
    /** The text currently shown; null until the first call to {@link #setText}. */
    private String text;
    /** Width of the label in screen units. */
    private float width;
    /** Height of the label in screen units. */
    private float height;

    /**
     * Creates an empty label and attaches it to a node.
     *
     * @param assets    asset manager for the material
     * @param parent    node to attach to
     * @param size      text height in screen units
     * @param rgb       text colour as 0xRRGGBB
     * @param wrapWidth widest line before wrapping, or 0 for a single line
     */
    Label(AssetManager assets, Node parent, float size, int rgb, int wrapWidth) {
        // The font and wrap width are multiplied by DETAIL because the
        // picture is painted larger than it is shown.
        font = new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size * DETAIL));
        color = new Color(rgb);
        this.wrapWidth = wrapWidth * DETAIL;

        // "Unshaded" ignores lights, and alpha blending lets the see-through
        // parts of the picture show whatever is behind the text.
        material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);

        // Start with a 1 x 1 rectangle; setText gives it the right size.
        geometry = new Geometry("label", new Quad(1, 1));
        geometry.setMaterial(material);
        parent.attachChild(geometry);
        setText("");
    }

    /**
     * Changes the text. Does nothing if it is the same as before.
     *
     * @param newText text to show
     */
    void setText(String newText) {
        // Painting a picture is slow, so skip it when nothing has changed.
        if (newText.equals(text)) {
            return;
        }
        text = newText;

        // Measure the text first. FontMetrics can only be obtained from an
        // image, so a throwaway 1 x 1 "probe" image is made just to ask.
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        FontMetrics metrics = probe.createGraphics().getFontMetrics(font);
        List<String> lines = wrap(newText, metrics);

        // Picture size: as wide as the longest line and as tall as all the
        // lines together, plus a little empty padding on every side.
        int pad = 4 * DETAIL;
        int w = pad * 2;
        for (String line : lines) {
            w = Math.max(w, metrics.stringWidth(line) + pad * 2);
        }
        // 1.08 adds a little extra space between lines.
        int lineHeight = Math.round(metrics.getHeight() * 1.08f);
        int h = Math.max(1, lineHeight * lines.size()) + pad * 2;

        // Paint the text. ARGB means every pixel also stores how see-through
        // it is, and a new image starts out fully see-through.
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(font);

        // Each line is drawn twice: first a half-clear black copy, moved a
        // little down and to the right, as a shadow; then the real text on
        // top. The shadow keeps the text readable over bright backgrounds.
        int shadow = Math.max(1, font.getSize() / 14);
        for (int i = 0; i < lines.size(); i++) {
            // The baseline is the line the letters sit on.
            int baseline = pad + metrics.getAscent() + i * lineHeight;
            g.setColor(new Color(0, 0, 0, 150));
            g.drawString(lines.get(i), pad + shadow, baseline + shadow);
            g.setColor(color);
            g.drawString(lines.get(i), pad, baseline);
        }
        g.dispose();

        // Copy the pixels into the form the engine wants. Java stores a pixel
        // as alpha-red-green-blue in one int; the engine wants four bytes in
        // the order red, green, blue, alpha. Rows are copied bottom first,
        // because the engine's pictures start at the bottom row.
        ByteBuffer data = BufferUtils.createByteBuffer(w * h * 4);
        for (int y = h - 1; y >= 0; y--) {
            for (int x = 0; x < w; x++) {
                int argb = image.getRGB(x, y);
                data.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb)
                        .put((byte) (argb >>> 24));
            }
        }
        // flip() rewinds the buffer so it can be read from the beginning.
        data.flip();
        Texture2D texture = new Texture2D(new Image(Image.Format.RGBA8, w, h, data,
                ColorSpace.sRGB));
        // Smooth the picture when it is scaled. No mipmaps (smaller copies
        // for far-away use) are needed, since text is always shown near full size.
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        texture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        material.setTexture("ColorMap", texture);

        // Resize the rectangle to fit. Dividing by DETAIL turns picture
        // pixels back into screen units.
        width = w / (float) DETAIL;
        height = h / (float) DETAIL;
        geometry.setMesh(new Quad(width, height));
    }

    /**
     * Splits text into lines that fit inside the wrap width.
     *
     * @param value   text to split
     * @param metrics measures how wide a piece of text is in this label's font
     * @return the lines, top to bottom (always at least one)
     */
    private List<String> wrap(String value, FontMetrics metrics) {
        List<String> lines = new ArrayList<>();
        // Wrapping switched off: everything stays on one line.
        if (wrapWidth <= 0) {
            lines.add(value);
            return lines;
        }
        // Add words to the current line one at a time. When the next word
        // would make the line too wide, finish the line and start a new one
        // with that word.
        StringBuilder line = new StringBuilder();
        for (String word : value.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (metrics.stringWidth(candidate) > wrapWidth && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        // Whatever is left over is the last line.
        lines.add(line.toString());
        return lines;
    }

    /**
     * Places the label by its top-left corner.
     *
     * @param x     left edge
     * @param top   top edge
     * @param layer draw order; higher is in front
     */
    void setTopLeft(float x, float top, float layer) {
        // The engine positions a rectangle by its bottom-left corner and y
        // points up the screen, so the bottom is "top minus height".
        geometry.setLocalTranslation(x, top - height, layer);
    }

    /**
     * Centres the label horizontally.
     *
     * @param centreX x to centre on
     * @param top     top edge
     * @param layer   draw order; higher is in front
     */
    void setCentred(float centreX, float top, float layer) {
        setTopLeft(centreX - width / 2, top, layer);
    }

    /**
     * Shows or hides the label.
     *
     * @param visible whether the label is drawn
     */
    void setVisible(boolean visible) {
        // A "cull hint" tells the engine whether to draw something:
        // Never = never skip it (always draw), Always = always skip it (hidden).
        geometry.setCullHint(visible ? Spatial.CullHint.Never : Spatial.CullHint.Always);
    }

    /**
     * Makes the label more or less see-through.
     *
     * @param alpha opacity from 0 to 1
     */
    void setAlpha(float alpha) {
        // The material multiplies the picture by this colour. White leaves
        // the text colour alone, so only the opacity changes.
        material.setColor("Color", new ColorRGBA(1, 1, 1, alpha));
    }

    /** Takes the label off the screen for good. */
    void remove() {
        geometry.removeFromParent();
    }

    /** @return width in screen units */
    float width() {
        return width;
    }
}
