package tqq;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/** Small shared UI pieces: icons, animations, per-sister colour variables. */
public final class Ui {
    private Ui() {}

    // Segoe Fluent Icons (built into Windows 11)
    public static final String I_HOME = "";
    public static final String I_ADD = "";
    public static final String I_SEARCH = "";
    public static final String I_DELETE = "";
    public static final String I_EDIT = "";
    public static final String I_SEND = "";
    public static final String I_STOP = "";
    public static final String I_HISTORY = "";
    public static final String I_CLOSE = "";
    public static final String I_EXPORT = "";
    public static final String I_RESET = "";

    public static Label icon(String glyph) {
        Label l = new Label(glyph);
        l.getStyleClass().add("icon");
        return l;
    }

    /** Round icon-only button with a tooltip. */
    public static Button iconButton(String glyph, String tip) {
        Button b = new Button();
        b.setGraphic(icon(glyph));
        b.getStyleClass().add("icon-btn");
        if (tip != null) b.setTooltip(new Tooltip(tip));
        return b;
    }

    /** Button with an icon and text. */
    public static Button button(String glyph, String text, String... classes) {
        Button b = new Button(text);
        if (glyph != null) b.setGraphic(icon(glyph));
        b.getStyleClass().addAll(classes);
        return b;
    }

    public static Region grow() {
        Region r = new Region();
        HBox.setHgrow(r, javafx.scene.layout.Priority.ALWAYS);
        return r;
    }

    /** CSS looked-up colours for one sister: -accent, -accent-soft, -accent-glow, -accent-line, -accent-deep. */
    public static String accentVars(Sister s) {
        Color c = Color.web(s.accent());
        Color deep = c.deriveColor(0, 1.0, 0.45, 1.0);
        return "-accent: " + s.accent() + ";"
             + "-accent-soft: " + rgba(c, 0.14) + ";"
             + "-accent-glow: " + rgba(c, 0.38) + ";"
             + "-accent-line: " + rgba(c, 0.55) + ";"
             + "-accent-deep: " + rgba(deep, 1.0) + ";";
    }

    public static String rgba(Color c, double a) {
        return String.format("rgba(%d,%d,%d,%.2f)",
                (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255), a);
    }

    /** Fade + slide up. */
    public static void rise(Node n, double dy, int ms, int delayMs) {
        n.setOpacity(0);
        n.setTranslateY(dy);
        FadeTransition f = new FadeTransition(Duration.millis(ms), n);
        f.setToValue(1);
        TranslateTransition t = new TranslateTransition(Duration.millis(ms), n);
        t.setToY(0);
        t.setInterpolator(Interpolator.SPLINE(0.2, 0.8, 0.2, 1.0));
        ParallelTransition p = new ParallelTransition(f, t);
        p.setDelay(Duration.millis(delayMs));
        p.play();
    }

    /** Animated "typing" dots. */
    public static HBox typingDots() {
        HBox box = new HBox(5);
        box.getStyleClass().add("typing");
        Timeline tl = new Timeline();
        for (int k = 0; k < 3; k++) {
            Circle c = new Circle(3.6);
            c.getStyleClass().add("typing-dot");
            c.setOpacity(0.25);
            box.getChildren().add(c);
            double off = k * 160;
            tl.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(off), new KeyValue(c.opacityProperty(), 0.25)),
                    new KeyFrame(Duration.millis(off + 260), new KeyValue(c.opacityProperty(), 1.0)),
                    new KeyFrame(Duration.millis(off + 560), new KeyValue(c.opacityProperty(), 0.25)));
        }
        tl.getKeyFrames().add(new KeyFrame(Duration.millis(1100)));
        tl.setCycleCount(Timeline.INDEFINITE);
        // animate only while shown: every reply bubble has dots, most of them hidden forever
        box.visibleProperty().addListener((o, a, v) -> { if (v && box.getScene() != null) tl.play(); else tl.stop(); });
        box.sceneProperty().addListener((o, a, sc) -> { if (sc == null) tl.stop(); else if (box.isVisible()) tl.play(); });
        return box;
    }

    /** Slow breathing glow for status dots. */
    public static Timeline breathe(Node n) {
        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(n.opacityProperty(), 1.0)),
                new KeyFrame(Duration.millis(900), new KeyValue(n.opacityProperty(), 0.3, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(1800), new KeyValue(n.opacityProperty(), 1.0, Interpolator.EASE_BOTH)));
        tl.setCycleCount(Timeline.INDEFINITE);
        return tl;
    }
}
