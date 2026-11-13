package tqq;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Home: five full-height panels side by side, one per sister.
 * The hovered panel widens (animated) and reveals her greeting + "Talk to her".
 */
public final class HomeView extends StackPane {
    private final Map<String, Label> modelLabels = new HashMap<>();
    private final Label status = new Label("Waking up Ollama…");
    private final Circle statusDot = new Circle(4);
    private final Timeline breath = Ui.breathe(statusDot);

    public HomeView(Consumer<Sister> open, CompletableFuture<Boolean> ollama) {
        getStyleClass().add("home");

        Quint quint = new Quint();
        for (int k = 0; k < Sister.ALL.size(); k++) {
            Sister s = Sister.ALL.get(k);
            Node p = panel(s, k, quint, open);
            quint.add(p);
            Ui.rise(p, 70, 650, 120 + k * 90);
        }
        quint.setOnMouseExited(e -> quint.focus(-1));

        // ---- floating top bar (mouse-transparent so hover reaches the panels)
        Label logo = new Label("TQQ");
        logo.getStyleClass().add("logo");
        Label logoSub = new Label("the quintessential chatbox");
        logoSub.getStyleClass().add("logo-sub");
        VBox brand = new VBox(-4, logo, logoSub);

        statusDot.getStyleClass().addAll("dot", "dot-busy");
        breath.play();
        // stop the endless animation when the home screen is left (it used to keep running)
        sceneProperty().addListener((o, a, sc) -> { if (sc == null) breath.stop(); });
        status.getStyleClass().add("home-status-text");
        HBox pill = new HBox(8, statusDot, status);
        pill.setAlignment(Pos.CENTER);
        pill.getStyleClass().add("glass-pill");

        HBox top = new HBox(brand, Ui.grow(), pill);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(22, 28, 0, 28));
        top.setMaxHeight(Region.USE_PREF_SIZE);
        top.setMouseTransparent(true);
        StackPane.setAlignment(top, Pos.TOP_CENTER);

        Label hint = new Label("hover to meet them  ·  click to talk  ·  you are Futaro");
        hint.getStyleClass().add("home-hint");
        hint.setMouseTransparent(true);
        StackPane.setAlignment(hint, Pos.BOTTOM_CENTER);
        StackPane.setMargin(hint, new Insets(0, 0, 18, 0));

        getChildren().addAll(quint, top, hint);
        Ui.rise(top, -20, 600, 50);

        ollama.thenAcceptAsync(ok -> {
            List<String> have = ok ? Ollama.models() : List.of();
            Platform.runLater(() -> showStatus(ok, have));
        });
    }

    private void showStatus(boolean ok, List<String> have) {
        breath.stop();
        statusDot.setOpacity(1);
        statusDot.getStyleClass().removeAll("dot-busy", "dot-ok", "dot-error");
        statusDot.getStyleClass().add(ok ? "dot-ok" : "dot-error");
        status.setText(ok ? "TQQ_CB ready" : "Ollama not running");
        if (!ok) return;
        for (Sister s : Sister.ALL) {
            boolean found = have.stream().anyMatch(n -> n.equalsIgnoreCase(s.model())
                    || n.equalsIgnoreCase(s.model() + ":latest"));
            Label ml = modelLabels.get(s.key());
            if (!found && ml != null) {
                ml.setText(s.model() + "  ·  not installed");
                ml.getStyleClass().add("text-error");
            }
        }
    }

    // ------------------------------------------------------------ one panel

    private Node panel(Sister s, int index, Quint quint, Consumer<Sister> open) {
        StackPane p = new StackPane();
        p.getStyleClass().add("panel");
        p.setStyle(Ui.accentVars(s));
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(p.widthProperty());
        clip.heightProperty().bind(p.heightProperty());
        p.setClip(clip);

        Cover photo = new Cover(Main.image(s), 0.12);
        Region shade = new Region();
        shade.getStyleClass().add("panel-shade");
        Region wash = new Region();
        wash.getStyleClass().add("panel-wash");
        wash.setOpacity(0.45);

        // big vertical kanji: hidden until hover (it would cover her face in a narrow panel)
        Label kanji = new Label(s.kanjiVertical());
        kanji.getStyleClass().add("panel-kanji");
        kanji.setOpacity(0);
        kanji.setMouseTransparent(true);
        StackPane.setAlignment(kanji, Pos.TOP_RIGHT);
        StackPane.setMargin(kanji, new Insets(96, 22, 0, 0));

        Label numText = new Label(s.num());
        numText.getStyleClass().add("panel-num");
        Label smallKanji = new Label(s.kanji());
        smallKanji.getStyleClass().add("panel-kanji-small");
        HBox num = new HBox(10, numText, smallKanji);
        num.setAlignment(Pos.BASELINE_LEFT);
        Region bar = new Region();
        bar.getStyleClass().add("panel-bar");
        Label name = new Label(s.name());
        name.getStyleClass().add("panel-name");
        name.setMinWidth(Region.USE_PREF_SIZE);
        // the name grows with the panel; sized for the longest name so all five match when closed
        p.widthProperty().addListener((o, a, w) -> {
            double size = Math.max(30, Math.min(64, (w.doubleValue() - 52) / (0.6 * 7)));
            name.setStyle("-fx-font-size: " + Math.round(size) + "px;");
        });
        Label tag = new Label(s.tagline());
        tag.getStyleClass().add("panel-tag");
        tag.setWrapText(true);
        tag.setMinWidth(0);
        tag.setMinHeight(36);   // room for 2 lines in every panel, so all five line up
        tag.setPrefHeight(36);
        tag.setMaxHeight(36);
        tag.setAlignment(Pos.TOP_LEFT);
        Label model = new Label(s.model());
        model.getStyleClass().add("panel-model");
        modelLabels.put(s.key(), model);

        Label quote = new Label("“" + s.greeting() + "”");
        quote.getStyleClass().add("panel-quote");
        quote.setWrapText(true);
        // fixed size so every panel's text block sits at the same height
        quote.setMinWidth(0);
        quote.setPrefWidth(320);
        quote.setMaxWidth(320);
        quote.setMinHeight(54);
        quote.setPrefHeight(54);
        quote.setMaxHeight(54);
        quote.setAlignment(Pos.BOTTOM_LEFT);
        Label talk = new Label("Talk to her   →");
        talk.getStyleClass().add("panel-talk");
        VBox reveal = new VBox(14, quote, talk);
        reveal.setOpacity(0);
        reveal.setTranslateY(14);

        VBox info = new VBox(6, num, bar, name, tag, model, reveal);
        info.setAlignment(Pos.BOTTOM_LEFT);
        info.setPadding(new Insets(0, 26, 64, 26));
        info.setMaxHeight(Double.MAX_VALUE);
        VBox.setMargin(reveal, new Insets(16, 0, 0, 0));
        StackPane.setAlignment(info, Pos.BOTTOM_LEFT);

        p.getChildren().addAll(photo, shade, wash, kanji, info);

        // Quint opens this panel and closes every other one, so only one is ever revealed
        ParallelTransition[] running = new ParallelTransition[1];
        quint.revealer(index, in -> {
            if (running[0] != null) running[0].stop();
            running[0] = animate(reveal, wash, kanji, in);
        });
        p.setOnMouseEntered(e -> quint.focus(index));
        p.setOnMouseClicked(e -> open.accept(s));
        return p;
    }

    private static ParallelTransition animate(Node reveal, Node wash, Node kanji, boolean in) {
        Duration d = Duration.millis(in ? 340 : 220);
        FadeTransition f = new FadeTransition(d, reveal);
        f.setToValue(in ? 1 : 0);
        TranslateTransition t = new TranslateTransition(d, reveal);
        t.setToY(in ? 0 : 14);
        FadeTransition w = new FadeTransition(d, wash);
        w.setToValue(in ? 1.0 : 0.45);
        FadeTransition k = new FadeTransition(d, kanji);
        k.setToValue(in ? 0.92 : 0.0);
        ParallelTransition all = new ParallelTransition(f, t, w, k);
        all.play();
        return all;
    }

    // ------------------------------------------------------------ accordion layout

    /** Lays panels out side by side; widths follow animated weights. */
    private static final class Quint extends Pane {
        private final List<Node> panels = new ArrayList<>();
        private final List<DoubleProperty> weights = new ArrayList<>();
        private final Map<Integer, java.util.function.Consumer<Boolean>> revealers = new HashMap<>();
        private int revealed = -1;
        private Timeline anim;

        void revealer(int idx, java.util.function.Consumer<Boolean> r) { revealers.put(idx, r); }

        void add(Node n) {
            DoubleProperty w = new SimpleDoubleProperty(1);
            w.addListener((o, a, b) -> requestLayout());
            panels.add(n);
            weights.add(w);
            getChildren().add(n);
        }

        void focus(int idx) {
            if (idx != revealed) {
                if (revealed >= 0 && revealers.containsKey(revealed)) revealers.get(revealed).accept(false);
                if (idx >= 0 && revealers.containsKey(idx)) revealers.get(idx).accept(true);
                revealed = idx;
            }
            if (anim != null) anim.stop();
            List<KeyValue> kv = new ArrayList<>();
            for (int k = 0; k < weights.size(); k++) {
                double target = idx < 0 ? 1.0 : (k == idx ? 2.3 : 0.84);
                kv.add(new KeyValue(weights.get(k), target, Interpolator.SPLINE(0.25, 0.8, 0.25, 1.0)));
            }
            anim = new Timeline(new KeyFrame(Duration.millis(520), kv.toArray(new KeyValue[0])));
            anim.play();
        }

        @Override
        protected void layoutChildren() {
            double gap = 2;
            double total = 0;
            for (DoubleProperty w : weights) total += w.get();
            double avail = getWidth() - gap * (panels.size() - 1);
            double x = 0;
            for (int k = 0; k < panels.size(); k++) {
                double w = avail * weights.get(k).get() / total;
                panels.get(k).resizeRelocate(Math.round(x), 0, Math.round(w), getHeight());
                x += w + gap;
            }
        }
    }
}
