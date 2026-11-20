package stillasleep.three;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

/**
 * Everything drawn flat on top of the 3D view: the dialog box, the interact
 * prompt, level information, big banners and the fade between scenes.
 * Laid out for a 720-pixel-tall screen and scaled to fit the real window.
 *
 * <p>"HUD" is short for "heads-up display". Positions here are in screen
 * units: (0, 0) is the bottom-left corner of the window, x runs right and y
 * runs up. The third number given when placing something is its layer:
 * things with a higher layer are drawn in front of things with a lower one.
 */
final class Hud {

    /** Height the layout is designed for; the real window is scaled to match. */
    private static final float VIRTUAL_HEIGHT = 720f;
    /** Cream colour of the interact prompt's square, as an engine colour. */
    private static final ColorRGBA CREAM = new ColorRGBA(0.95f, 0.93f, 0.88f, 1f);
    /** Cream text colour as 0xRRGGBB. */
    private static final int CREAM_RGB = 0xf3efe4;
    /** Gold text colour as 0xRRGGBB, used for headlines. */
    private static final int GOLD_RGB = 0xffd23f;
    /** White text colour as 0xRRGGBB. */
    private static final int WHITE_RGB = 0xffffff;

    /** The engine's asset manager, kept for making materials and labels later. */
    private final AssetManager assets;
    /** The engine's built-in font, offered to scenes for text inside the 3D world. */
    private final BitmapFont font;
    /** Parent of everything on the HUD; scaling it scales the whole layout. */
    private final Node root = new Node("hud");
    /** Screen width in the same units as {@link #VIRTUAL_HEIGHT}. */
    private final float width;

    /** Dark box behind the dialog text. */
    private final Geometry dialogPanel;
    /** The dialog text itself (wraps onto several lines). */
    private final Label dialogText;
    /** Small "E  >" reminder in the corner of the dialog box. */
    private final Label dialogHint;
    /** Cream square behind the "E" interact prompt. */
    private final Geometry promptPanel;
    /** The letter "E" of the interact prompt. */
    private final Label promptText;
    /** Small dot in the middle of the screen that shows where the player is aiming. */
    private final Geometry crosshair;
    /** Dark bar across the top of the screen. */
    private final Geometry topBar;
    /** Text at the left of the top bar. */
    private final Label topLeft;
    /** Text in the middle of the top bar. */
    private final Label topCenter;
    /** Text at the right of the top bar. */
    private final Label topRight;
    /** Huge banner headline. */
    private final Label bigTitle;
    /** Line under the banner headline. */
    private final Label bigSub;
    /** Blinking line near the bottom of a banner. */
    private final Label bigBlink;
    /** Small help line at the very bottom of the screen. */
    private final Label hint;
    /** Rectangle that covers the whole screen during a fade. */
    private final Geometry fade;
    /** The fade rectangle's material; changing its colour changes the fade. */
    private final Material fadeMaterial;

    /** Pages of the dialog being shown; empty when no dialog is open. */
    private String[] pages = new String[0];
    /** Index of the dialog page on screen. */
    private int page;
    /** Seconds since the HUD was created; used to time the blinking. */
    private float time;

    /**
     * Creates every HUD element once. Apart from the crosshair they all start
     * hidden, and are shown and hidden later by the methods below.
     *
     * @param assets       asset manager for the font and materials
     * @param guiNode      the engine's 2D layer
     * @param screenWidth  real window width in pixels
     * @param screenHeight real window height in pixels
     */
    Hud(AssetManager assets, Node guiNode, int screenWidth, int screenHeight) {
        this.assets = assets;
        font = assets.loadFont("Interface/Fonts/Default.fnt");

        // Everything below is laid out as if the window were 720 units tall.
        // Scaling the root node stretches that layout to the real window, and
        // "width" is how many of those units fit across it.
        float scale = screenHeight / VIRTUAL_HEIGHT;
        width = screenWidth / scale;
        root.setLocalScale(scale);
        guiNode.attachChild(root);

        // Crosshair: a 4 x 4 slightly see-through white dot. It is moved back
        // by half its size (2) so that it sits exactly on the screen centre.
        crosshair = panel(4, 4, new ColorRGBA(1, 1, 1, 0.6f));
        crosshair.setLocalTranslation(width / 2 - 2, VIRTUAL_HEIGHT / 2 - 2, 0);

        // Dialog box: a 900 x 130 dark panel centred near the bottom, text
        // that wraps at 830 units, and a small "E  >" reminder in the corner.
        dialogPanel = panel(900, 130, new ColorRGBA(0.05f, 0.05f, 0.11f, 0.9f));
        dialogPanel.setLocalTranslation(width / 2 - 450, 36, 1);
        dialogText = new Label(assets, root, 25, CREAM_RGB, 830);
        dialogHint = new Label(assets, root, 15, 0xbdb7a6, 0);
        dialogHint.setText("E  >");
        dialogHint.setTopLeft(width / 2 + 390, 68, 2);

        // Interact prompt: a cream square with a dark "E" on it, just below
        // the crosshair.
        promptPanel = panel(34, 34, CREAM);
        promptPanel.setLocalTranslation(width / 2 - 17, VIRTUAL_HEIGHT / 2 - 70, 1);
        promptText = new Label(assets, root, 20, 0x282232, 0);
        promptText.setText("E");
        promptText.setCentred(width / 2, VIRTUAL_HEIGHT / 2 - 36, 2);

        // Top bar: a strip 46 units tall across the top, with three texts.
        // The texts are positioned later, in setTop, once their words are known.
        topBar = panel(width, 46, new ColorRGBA(0.11f, 0.17f, 0.23f, 0.92f));
        topBar.setLocalTranslation(0, VIRTUAL_HEIGHT - 46, 1);
        topLeft = new Label(assets, root, 24, WHITE_RGB, 0);
        topCenter = new Label(assets, root, 24, GOLD_RGB, 0);
        topRight = new Label(assets, root, 24, WHITE_RGB, 0);

        // Banner texts and the bottom help line; also positioned later.
        bigTitle = new Label(assets, root, 104, GOLD_RGB, 0);
        bigSub = new Label(assets, root, 32, WHITE_RGB, 0);
        bigBlink = new Label(assets, root, 24, GOLD_RGB, 0);
        hint = new Label(assets, root, 18, CREAM_RGB, 0);

        // Fade overlay: a rectangle the size of the whole screen. It starts
        // fully see-through (alpha 0) and sits on layer 9, in front of
        // everything else, so a fade hides the text as well as the 3D view.
        fadeMaterial = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        fadeMaterial.setColor("Color", new ColorRGBA(0, 0, 0, 0));
        fadeMaterial.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        fade = new Geometry("fade", new Quad(width, VIRTUAL_HEIGHT));
        fade.setMaterial(fadeMaterial);
        fade.setLocalTranslation(0, 0, 9);
        root.attachChild(fade);

        // Begin with everything hidden except the crosshair.
        clear();
    }

    /**
     * Makes a flat coloured rectangle and adds it to the HUD.
     *
     * @param w     width in screen units
     * @param h     height in screen units
     * @param color fill colour; an alpha below 1 makes it see-through
     * @return the rectangle, with its bottom-left corner at (0, 0)
     */
    private Geometry panel(float w, float h, ColorRGBA color) {
        // "Unshaded" = a flat colour that lights do not affect. Alpha
        // blending is what makes a partly see-through colour actually show
        // the scene behind it.
        Material material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", color);
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        Geometry geometry = new Geometry("panel", new Quad(w, h));
        geometry.setMaterial(material);
        root.attachChild(geometry);
        return geometry;
    }

    /**
     * Shows or hides one HUD element.
     *
     * @param spatial the element
     * @param visible true to draw it, false to hide it
     */
    private static void show(com.jme3.scene.Spatial spatial, boolean visible) {
        // A "cull hint" tells the engine whether to draw something:
        // Never = never skip it (always draw), Always = always skip it (hidden).
        spatial.setCullHint(visible ? com.jme3.scene.Spatial.CullHint.Never
                : com.jme3.scene.Spatial.CullHint.Always);
    }

    /** Hides everything except the fade. Called when a new scene starts. */
    void clear() {
        // An empty page list means "no dialog open".
        pages = new String[0];
        show(dialogPanel, false);
        dialogText.setVisible(false);
        dialogHint.setVisible(false);
        // Passing false / null to these hides the matching element.
        setPrompt(false);
        setTop(null, null, null);
        setBanner(null, null, null);
        setHint(null);
        // The crosshair is on by default; a scene can switch it off.
        show(crosshair, true);
    }

    /**
     * Opens the dialog box.
     *
     * @param text pages to show in order
     */
    void openDialog(String... text) {
        pages = text;
        page = 0;
        showPage();
    }

    /** Shows the current dialog page, or hides the box if there are no pages left. */
    private void showPage() {
        boolean open = page < pages.length;
        show(dialogPanel, open);
        dialogText.setVisible(open);
        dialogHint.setVisible(open);
        if (open) {
            // The text has to be placed again each time, because a label is
            // positioned by its top edge and its height changes with the text.
            dialogText.setText(pages[page]);
            dialogText.setTopLeft(width / 2 - 424, 156, 2);
        }
    }

    /** @return true while the dialog box is on screen */
    boolean isDialogOpen() {
        return page < pages.length;
    }

    /** Moves to the next dialog page, closing the box after the last one. */
    void advanceDialog() {
        page++;
        showPage();
    }

    /**
     * Shows or hides the interact prompt.
     *
     * @param visible whether to show the "E" prompt under the crosshair
     */
    void setPrompt(boolean visible) {
        show(promptPanel, visible);
        promptText.setVisible(visible);
    }

    /**
     * Sets the bar across the top of the screen; all null hides it.
     *
     * @param left   text on the left
     * @param center text in the middle
     * @param right  text on the right
     */
    void setTop(String left, String center, String right) {
        // The left text decides whether the whole bar is shown.
        boolean visible = left != null;
        show(topBar, visible);
        topLeft.setVisible(visible);
        topCenter.setVisible(visible);
        topRight.setVisible(visible);
        if (visible) {
            topLeft.setText(left);
            topCenter.setText(center);
            topRight.setText(right);
            // Left text 20 units from the left edge, middle text centred, and
            // right text ending 20 units from the right edge (so its starting
            // point depends on how wide it is).
            topLeft.setTopLeft(20, VIRTUAL_HEIGHT - 4, 2);
            topCenter.setCentred(width / 2, VIRTUAL_HEIGHT - 4, 2);
            topRight.setTopLeft(width - 20 - topRight.width(), VIRTUAL_HEIGHT - 4, 2);
        }
    }

    /**
     * Sets the large centred banner; a null title hides it.
     *
     * @param title huge headline
     * @param sub   line under the headline, may be null
     * @param blink blinking line near the bottom, may be null
     */
    void setBanner(String title, String sub, String blink) {
        // The two smaller lines only show when there is a title as well.
        bigTitle.setVisible(title != null);
        bigSub.setVisible(title != null && sub != null);
        bigBlink.setVisible(title != null && blink != null);
        if (title == null) {
            return;
        }
        // Each line is centred across the screen at its own fixed height
        // (590, 440 and 220 units up from the bottom).
        bigTitle.setText(title);
        bigTitle.setCentred(width / 2, 590, 3);
        if (sub != null) {
            bigSub.setText(sub);
            bigSub.setCentred(width / 2, 440, 3);
        }
        if (blink != null) {
            bigBlink.setText(blink);
            bigBlink.setCentred(width / 2, 220, 3);
        }
    }

    /**
     * Shows or hides the help line.
     *
     * @param text small help line at the bottom of the screen, or null to hide it
     */
    void setHint(String text) {
        hint.setVisible(text != null);
        if (text != null) {
            hint.setText(text);
            hint.setCentred(width / 2, 34, 3);
        }
    }

    /**
     * Covers the screen with a colour.
     *
     * @param color fade colour
     * @param alpha 0 for invisible, 1 for fully covered
     */
    void setFade(ColorRGBA color, float alpha) {
        // Keep the colour but replace its alpha (how solid it is).
        fadeMaterial.setColor("Color", new ColorRGBA(color.r, color.g, color.b, alpha));
        // When it is practically invisible, do not draw the rectangle at all.
        show(fade, alpha > 0.001f);
    }

    /**
     * Animates blinking text.
     *
     * @param tpf seconds since the last frame
     */
    void update(float tpf) {
        time += tpf;
        // (int) (time * 2) goes up by one every half second. When it is even
        // the text is solid, when it is odd the text is faint: a slow blink.
        bigBlink.setAlpha((int) (time * 2) % 2 == 0 ? 1f : 0.15f);
    }

    /**
     * Creates an extra piece of text for a scene to position itself.
     *
     * @param size text height
     * @param rgb  colour as 0xRRGGBB
     * @return the new label; the scene must {@link Label#remove()} it when done
     */
    Label createLabel(float size, int rgb) {
        return new Label(assets, root, size, rgb, 0);
    }

    /**
     * Shows or hides the crosshair.
     *
     * @param visible whether to draw the dot in the middle of the screen
     */
    void setCrosshair(boolean visible) {
        show(crosshair, visible);
    }

    /** @return screen width in the same units labels are placed in */
    float width() {
        return width;
    }

    /** @return the font, for text placed in the 3D world */
    BitmapFont font() {
        return font;
    }
}
