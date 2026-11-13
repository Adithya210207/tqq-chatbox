package tqq;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Chat screen for one sister.
 * Left: sister rail (jump between the five) + her chat threads.
 * Middle: the open thread. Right: her portrait "stage".
 */
public final class ChatView extends StackPane {
    /** How many past turns of the open thread she remembers (1 turn = your line + her reply). */
    private static final int MEMORY_TURNS = 20;
    /** Cap on reply length; the models were trained on short replies. */
    private static final int MAX_TOKENS = 256;
    private static final double COLUMN = 800;
    /** Letters per second when her words appear after the visual cue. */
    private static final double TYPE_SPEED = 70;

    private final Sister sister;
    private final CompletableFuture<Boolean> ollamaReady;
    private final Consumer<Sister> onSwitch;
    private final Runnable onHome;
    private final Threads threads;

    private final BorderPane layout = new BorderPane();
    private final VBox messages = new VBox(18);
    private final ScrollPane scroll = new ScrollPane();
    private final TextField input = new TextField();
    private final Button sendBtn = new Button();
    private final VBox threadBox = new VBox(4);
    private final Label threadCount = new Label();
    private final Label threadTitle = new Label();
    private final Label modelText = new Label();
    private final Circle modelDot = new Circle(4);
    private final Label stageStatus = new Label();
    private final Circle stageDot = new Circle(4);
    private final Label toast = new Label();
    private final Timeline modelBreath = Ui.breathe(modelDot);
    private final Timeline stageBreath = Ui.breathe(stageDot);

    private boolean busy;
    private boolean ready;
    private boolean disposed;
    private volatile Ollama.Handle current;
    private Node historyOverlay;
    private Node setupOverlay;
    private SequentialTransition toastAnim;
    private final Prompts prompts;
    private final Button contextChip = new Button();
    private final Label withLabel = new Label();

    public ChatView(Sister sister, CompletableFuture<Boolean> ollamaReady,
                    Consumer<Sister> onSwitch, Runnable onHome) {
        this.sister = sister;
        this.ollamaReady = ollamaReady;
        this.onSwitch = onSwitch;
        this.onHome = onHome;
        this.threads = Threads.load(sister);
        this.prompts = new Prompts(sister);
        setStyle(Ui.accentVars(sister));
        getStyleClass().add("chat-view");
        // never ask for more room than the window has (it would push the sister rail off-screen)
        setMinWidth(0);

        // ---- backdrop: her picture, huge and blurred, under a tinted veil
        Cover backdrop = new Cover(Main.image(sister), 0.15);
        backdrop.setEffect(new GaussianBlur(63));
        backdrop.setOpacity(0.32);
        backdrop.setScaleX(1.2);
        backdrop.setScaleY(1.2);
        Region veil = new Region();
        veil.getStyleClass().add("chat-veil");
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        HBox left = new HBox(buildRail(), buildDrawer());
        layout.setLeft(left);
        layout.setCenter(buildCenter());
        layout.setRight(buildStage());

        toast.getStyleClass().add("toast");
        toast.setOpacity(0);
        toast.setMouseTransparent(true);
        StackPane.setAlignment(toast, Pos.TOP_CENTER);
        StackPane.setMargin(toast, new Insets(84, 0, 0, 0));

        getChildren().addAll(backdrop, veil, layout, toast);

        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE && historyOverlay != null) closeHistory();
            else if (e.getCode() == KeyCode.ESCAPE && setupOverlay != null) closeSetup();
        });

        showThread(threads.active(), false);
        loadModel();
    }

    public Sister sister() { return sister; }

    // ================================================================ left: rail + drawer

    private Node buildRail() {
        Button home = Ui.iconButton(Ui.I_HOME, "Home");
        home.getStyleClass().add("rail-home");
        home.setOnAction(e -> onHome.run());

        VBox sisters = new VBox(14);
        sisters.setAlignment(Pos.TOP_CENTER);
        for (Sister o : Sister.ALL) {
            Node av = Main.avatar(o, 40);
            Label num = new Label(String.valueOf(o.number()));
            num.getStyleClass().add("rail-num");
            StackPane wrap = new StackPane(av);
            wrap.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            wrap.getStyleClass().add("rail-sis");
            wrap.setStyle(Ui.accentVars(o));
            if (o == sister) wrap.getStyleClass().add("rail-sis-active");
            VBox cell = new VBox(4, wrap, num);
            cell.setAlignment(Pos.CENTER);
            Tooltip.install(wrap, new Tooltip(o.fullName() + "  ·  " + o.tagline()));
            wrap.setOnMouseClicked(e -> { if (o != sister) onSwitch.accept(o); });
            sisters.getChildren().add(cell);
        }

        Region div = new Region();
        div.getStyleClass().add("rail-div");
        Region push = new Region();
        VBox.setVgrow(push, Priority.ALWAYS);
        Label brand = new Label("TQQ");
        brand.getStyleClass().add("rail-brand");

        VBox rail = new VBox(18, home, div, sisters, push, brand);
        rail.setAlignment(Pos.TOP_CENTER);
        rail.getStyleClass().add("rail");
        return rail;
    }

    private Node buildDrawer() {
        Label head = new Label("Chats");
        head.getStyleClass().add("drawer-title");
        threadCount.getStyleClass().add("drawer-count");
        HBox top = new HBox(8, head, threadCount);
        top.setAlignment(Pos.BASELINE_LEFT);

        Button newBtn = Ui.button(Ui.I_ADD, "New chat", "new-chat");
        newBtn.setMaxWidth(Double.MAX_VALUE);
        newBtn.setOnAction(e -> newThread());

        ScrollPane sp = new ScrollPane(threadBox);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("clear-scroll");
        VBox.setVgrow(sp, Priority.ALWAYS);

        Button rename = Ui.button(Ui.I_EDIT, "Rename", "mini");
        rename.setOnAction(e -> renameThread());
        Button delete = Ui.button(Ui.I_DELETE, "Delete", "mini", "mini-danger");
        delete.setOnAction(e -> deleteThread());
        Button export = Ui.button(Ui.I_EXPORT, "Export", "mini");
        export.setOnAction(e -> exportThread());
        for (Button b : new Button[] {rename, delete, export}) {
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
        }
        HBox foot = new HBox(6, rename, delete, export);

        VBox drawer = new VBox(14, top, newBtn, sp, foot);
        drawer.getStyleClass().add("drawer");
        return drawer;
    }

    private void refreshThreadList() {
        threadBox.getChildren().clear();
        List<Threads.ChatThread> all = threads.sorted();
        threadCount.setText(String.valueOf(all.size()));
        String activeId = threads.active().id;
        for (Threads.ChatThread t : all) {
            Label title = new Label(t.title);
            title.getStyleClass().add("thread-title");
            title.setMaxWidth(170);
            Label when = new Label(Threads.ago(t.updated));
            when.getStyleClass().add("thread-when");
            when.setMinWidth(Region.USE_PREF_SIZE);   // the date never gets cut ("yeste..."); the title shortens instead
            title.setMinWidth(0);
            HBox row1 = new HBox(6, title, Ui.grow(), when);
            row1.setAlignment(Pos.CENTER_LEFT);
            Label prev = new Label(Threads.preview(t));
            prev.getStyleClass().add("thread-preview");
            prev.setMaxWidth(210);
            Label ctx = new Label(Prompts.stageShort(t.stage) + "  ·  as " + threads.userName(t));
            ctx.getStyleClass().add("thread-ctx");
            VBox card = new VBox(3, row1, prev, ctx);
            card.getStyleClass().add("thread-card");
            if (t.id.equals(activeId)) card.getStyleClass().add("thread-card-active");
            card.setOnMouseClicked(e -> { if (!t.id.equals(threads.active().id)) switchThread(t); });
            threadBox.getChildren().add(card);
        }
        threadTitle.setText(threads.active().title);
        refreshContext();
    }

    /** Top-bar chip + sub-line + input hint for the open chat's timeline and character. */
    private void refreshContext() {
        Threads.ChatThread t = threads.active();
        contextChip.setText(Prompts.stageShort(t.stage) + "  ·  " + threads.userName(t) + "  ✎");
        withLabel.setText("with " + sister.fullName() + "   ·   " + Prompts.stageLabel(t.stage)
                + "   ·   you are " + (t.asFriend() ? threads.userName(t) + " (a friend)" : "Futaro"));
    }

    /** Her opening line for an empty chat (shown only, never sent to the model). */
    private String greetingFor(Threads.ChatThread t) {
        if (t.asFriend()) {
            String n = threads.friendName.isBlank() ? "" : threads.friendName.strip();
            String c = n.isEmpty() ? "" : ", " + n;
            return switch (sister.key()) {
                case "ichika" -> "Oh" + c + ". Did you come to see me?";
                case "nino" -> "Oh, it's you" + c + ". What's up?";
                case "miku" -> "..." + (n.isEmpty() ? "Hi" : n) + ". Hi.";
                case "yotsuba" -> (n.isEmpty() ? "Hi" : n) + "! Hi hi!";
                case "itsuki" -> "Good afternoon" + c + ".";
                default -> "Hi" + c + ".";
            };
        }
        if (sister.key().equals("nino") && t.stage.equals("movie")) return "Hmph. You're late, Fu-kun.";
        return sister.greeting();
    }

    // ================================================================ middle

    private Node buildCenter() {
        // top bar
        threadTitle.getStyleClass().add("thread-heading");
        withLabel.getStyleClass().add("thread-sub");
        VBox titles = new VBox(1, threadTitle, withLabel);

        // timeline + who you are, for this chat (click to change)
        contextChip.getStyleClass().add("context-chip");
        contextChip.setTooltip(new Tooltip("Timeline and who you are in this chat"));
        contextChip.setOnAction(e -> openSetup(false));

        Button actions = new Button();
        actions.getStyleClass().add("toggle");
        Runnable paint = () -> {
            actions.setText(Main.actionsOn ? "✦  Actions on" : "✧  Actions off");
            actions.getStyleClass().removeAll("toggle-on");
            if (Main.actionsOn) actions.getStyleClass().add("toggle-on");
        };
        paint.run();
        actions.setTooltip(new Tooltip("Line 1 of her reply: a short narrator line about what she does"));
        actions.setOnAction(e -> {
            Main.actionsOn = !Main.actionsOn;
            paint.run();
            flash(Main.actionsOn ? "Action lines on" : "Action lines off · only her words");
        });

        Button hist = Ui.iconButton(Ui.I_SEARCH, "Search all chats (History)");
        hist.setOnAction(e -> openHistory());
        Button clear = Ui.iconButton(Ui.I_RESET, "Clear this chat");
        clear.setOnAction(e -> clearThread());

        modelDot.getStyleClass().addAll("dot", "dot-busy");
        modelText.getStyleClass().add("model-text");
        HBox model = new HBox(8, modelDot, modelText);
        model.setAlignment(Pos.CENTER);
        model.getStyleClass().add("glass-pill");
        Tooltip.install(model, new Tooltip(sister.model() + "  ·  click to retry if it failed"));
        // after an error (Ollama down, model missing): click the pill to try again
        model.setOnMouseClicked(e -> { if (!ready && !loading) loadModel(); });

        // buttons keep their size; the chat title shortens with "..." instead
        for (Region r : new Region[] {contextChip, actions, hist, clear, model}) r.setMinWidth(Region.USE_PREF_SIZE);
        titles.setMinWidth(0);
        HBox.setHgrow(titles, Priority.SOMETIMES);
        HBox topbar = new HBox(10, titles, Ui.grow(), contextChip, actions, hist, clear, model);
        topbar.setAlignment(Pos.CENTER_LEFT);
        topbar.getStyleClass().add("topbar");

        // messages column
        messages.getStyleClass().add("messages");
        messages.setMaxWidth(COLUMN);
        messages.setFillWidth(true);
        VBox column = new VBox(messages);
        column.setAlignment(Pos.TOP_CENTER);
        column.getStyleClass().add("column");
        scroll.setContent(column);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("clear-scroll");
        messages.heightProperty().addListener((o, a, b) -> scroll.setVvalue(1.0));
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // composer
        input.setPromptText("Say something to " + sister.name() + "…");
        input.getStyleClass().add("composer-input");
        input.setOnAction(e -> send());
        HBox.setHgrow(input, Priority.ALWAYS);
        sendBtn.setGraphic(Ui.icon(Ui.I_SEND));
        sendBtn.getStyleClass().add("send");
        sendBtn.setOnAction(e -> { if (busy) stop(); else send(); });
        sendBtn.setDisable(true);
        HBox box = new HBox(8, input, sendBtn);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("composer");
        box.setMaxWidth(COLUMN);
        input.focusedProperty().addListener((o, a, f) -> {
            if (f) box.getStyleClass().add("composer-focus"); else box.getStyleClass().remove("composer-focus");
        });
        Label hint = new Label("Enter to send  ·  she remembers the last " + MEMORY_TURNS
                + " turns of this chat  ·  Esc closes search");
        hint.getStyleClass().add("composer-hint");
        VBox composer = new VBox(8, box, hint);
        composer.setAlignment(Pos.CENTER);
        composer.getStyleClass().add("composer-wrap");

        VBox main = new VBox(topbar, scroll, composer);

        // giant kanji watermark behind the messages
        Label mark = new Label(sister.kanjiVertical());
        mark.getStyleClass().add("watermark");
        mark.setMouseTransparent(true);
        StackPane.setAlignment(mark, Pos.CENTER_RIGHT);
        StackPane.setMargin(mark, new Insets(0, 30, 0, 0));

        return new StackPane(mark, main);
    }

    // ================================================================ right: stage

    private Node buildStage() {
        Cover portrait = new Cover(Main.image(sister), 0.05);
        Region fade = new Region();
        fade.getStyleClass().add("stage-fade");

        Label num = new Label(sister.num());
        num.getStyleClass().add("stage-num");
        Label kanji = new Label(sister.kanji());
        kanji.getStyleClass().add("stage-kanji");
        HBox numRow = new HBox(12, num, kanji);
        numRow.setAlignment(Pos.BASELINE_LEFT);
        Label name = new Label(sister.name());
        name.getStyleClass().add("stage-name");
        Label tag = new Label(sister.tagline());
        tag.getStyleClass().add("stage-tag");
        stageDot.getStyleClass().addAll("dot", "dot-accent");
        stageStatus.getStyleClass().add("stage-status");
        HBox status = new HBox(8, stageDot, stageStatus);
        status.setAlignment(Pos.CENTER_LEFT);

        VBox info = new VBox(4, numRow, name, tag, status);
        VBox.setMargin(status, new Insets(10, 0, 0, 0));
        info.setPadding(new Insets(0, 26, 34, 30));
        info.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(info, Pos.BOTTOM_LEFT);

        StackPane stage = new StackPane(portrait, fade, info);
        stage.getStyleClass().add("stage");
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(stage.widthProperty());
        clip.heightProperty().bind(stage.heightProperty());
        stage.setClip(clip);
        // hide the stage when the WINDOW is too narrow for it (the view's own width can't be
        // used: it never gets smaller than what its contents need)
        sceneProperty().addListener((o, a, sc) -> {
            stage.visibleProperty().unbind();
            if (sc != null) stage.visibleProperty().bind(sc.widthProperty().greaterThan(1180));
        });
        stage.managedProperty().bind(stage.visibleProperty());
        return stage;
    }

    private void setStageStatus(String text, boolean breathing) {
        stageStatus.setText(text);
        if (breathing) stageBreath.play(); else { stageBreath.stop(); stageDot.setOpacity(1); }
    }

    // ================================================================ threads

    /** Repaints the chat with a thread's messages; her memory is rebuilt from it on each send. */
    private void showThread(Threads.ChatThread t, boolean animate) {
        threads.setActive(t.id);
        threads.save();
        messages.getChildren().clear();
        if (t.messages.isEmpty()) {
            // just her greeting (no suggestion buttons: the user didn't want them)
            addMsg(true, greetingFor(t), Threads.shortTime(Threads.now()), null, animate);
        } else {
            for (Map<String, Object> m : t.messages) {
                addMsg(!Threads.isUser(m), String.valueOf(m.get("text")),
                        Threads.shortTime(m.get("time")), m.get("action"), false);
            }
        }
        refreshThreadList();
        warmUp(t);
    }

    private String warmedKey = "";

    /** Pre-read this chat's prompt in the background, so her first reply comes much faster. */
    private void warmUp(Threads.ChatThread t) {
        if (!ready || busy || disposed) return;
        String key = t.id + "|" + t.stage + "|" + t.persona + "|" + threads.friendName + "|"
                + threads.friendStory.hashCode() + "|" + t.messages.size();
        if (key.equals(warmedKey)) return;
        warmedKey = key;
        List<Map<String, Object>> msgs = memory(t);
        Thread th = new Thread(() -> Ollama.prime(sister.model(), msgs), "warm-up");
        th.setDaemon(true);
        th.start();
    }

    private void switchThread(Threads.ChatThread t) {
        if (busy) stop();
        showThread(t, true);
    }

    private void newThread() {
        if (busy) stop();
        if (threads.active().messages.isEmpty()) { input.requestFocus(); return; }
        // reuse an empty chat if there already is one, instead of piling up "New chat"s
        Threads.ChatThread empty = threads.sorted().stream().filter(x -> x.messages.isEmpty()).findFirst().orElse(null);
        if (empty != null) {
            empty.stage = threads.defaultStage;
            empty.persona = threads.defaultPersona;
            empty.updated = Threads.now();
            showThread(empty, true);
            input.requestFocus();
            return;
        }
        showThread(threads.create(), true);
        flash("New chat · " + sister.name() + " only remembers this one");
        input.requestFocus();
    }

    private void renameThread() {
        Threads.ChatThread t = threads.active();
        TextInputDialog d = new TextInputDialog(t.title);
        d.setTitle("Rename chat");
        d.setHeaderText(null);
        d.setContentText("New name:");
        style(d);
        Optional<String> r = d.showAndWait();
        if (r.isPresent() && !r.get().isBlank()) {
            String v = r.get().strip();
            t.title = v.length() > 60 ? v.substring(0, 60) : v;
            threads.save();
            refreshThreadList();
            flash("Chat renamed");
        }
    }

    private void deleteThread() {
        Threads.ChatThread t = threads.active();
        if (!confirm("Delete chat", "Delete “" + t.title + "”? This cannot be undone.")) return;
        if (busy) stop();
        threads.delete(t.id);
        threads.save();
        showThread(threads.active(), true);
        flash("Chat deleted");
    }

    private void exportThread() {
        Threads.ChatThread t = threads.active();
        if (t.messages.isEmpty()) { flash("This chat is empty"); return; }
        FileChooser fc = new FileChooser();
        fc.setTitle("Export chat");
        String safe = (sister.name() + " - " + t.title).replaceAll("[\\\\/:*?\"<>|…]", "").strip();
        fc.setInitialFileName(safe + ".txt");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text transcript", "*.txt"),
                new FileChooser.ExtensionFilter("JSON", "*.json"));
        File f = fc.showSaveDialog(getScene().getWindow());
        if (f == null) return;
        try {
            String body = f.getName().toLowerCase().endsWith(".json")
                    ? Json.pretty(t.messages) : threads.transcript(t);
            Files.writeString(f.toPath(), body, StandardCharsets.UTF_8);
            flash("Exported · " + f.getName());
        } catch (Exception ex) {
            flash("Export failed: " + ex.getMessage());
        }
    }

    /** Clears the open thread (other threads stay). */
    private void clearThread() {
        Threads.ChatThread t = threads.active();
        if (t.messages.isEmpty()) return;
        if (!confirm("Clear chat", "Clear every message in “" + t.title + "”?\n"
                + sister.name() + " will forget this chat. Your other chats stay.")) return;
        if (busy) stop();
        t.messages.clear();
        t.title = Threads.NEW_TITLE;
        threads.save();
        showThread(t, true);
        flash("Chat cleared");
    }

    // ================================================================ model + chatting

    private volatile boolean loading;

    private void loadModel() {
        if (loading) return;
        loading = true;
        setReady(false, "waking up Ollama…", "dot-busy");
        Thread th = new Thread(() -> {
            try { loadModelNow(); } finally { loading = false; }
        }, "load-model");
        th.setDaemon(true);
        th.start();
    }

    private void loadModelNow() {
        {
            boolean ok;
            try { ok = ollamaReady.get(); } catch (Exception e) { ok = false; }
            // Ollama wasn't up when the app started: try (again) now
            if (!ok && !disposed) ok = Ollama.ensureRunning();
            if (disposed) return;
            if (!ok) {
                Platform.runLater(() -> setReady(false, "Ollama is not running", "dot-error"));
                return;
            }
            if (!Ollama.hasModel(sister.model())) {
                Platform.runLater(() -> setReady(false, sister.model() + " is not installed", "dot-error"));
                return;
            }
            Platform.runLater(() -> setReady(false, "loading " + sister.name() + "…", "dot-busy"));
            // Only one 8.5 GB model fits: free any other sister still in memory first.
            for (String loaded : Ollama.loaded()) {
                String base = loaded.toLowerCase().replace(":latest", "");
                for (Sister other : Sister.ALL) {
                    if (other != sister && base.equals(other.model().toLowerCase())) Ollama.unload(loaded);
                }
            }
            try {
                Ollama.load(sister.model());
                if (disposed) { Ollama.unload(sister.model()); return; }
                Platform.runLater(() -> setReady(true, "ready", "dot-ok"));
            } catch (Exception e) {
                Platform.runLater(() -> setReady(false, "could not load: " + e.getMessage(), "dot-error"));
            }
        }
    }

    private void setReady(boolean r, String text, String dotClass) {
        ready = r;
        modelText.setText(text);
        modelDot.getStyleClass().removeAll("dot-busy", "dot-ok", "dot-error");
        modelDot.getStyleClass().add(dotClass);
        if ("dot-busy".equals(dotClass)) modelBreath.play(); else { modelBreath.stop(); modelDot.setOpacity(1); }
        sendBtn.setDisable(!r && !busy);
        if (r) {
            setStageStatus("listening", false);
            input.requestFocus();
            warmUp(threads.active());
        } else {
            setStageStatus(text, "dot-busy".equals(dotClass));
            if ("dot-error".equals(dotClass)) flash(text);
        }
    }

    /**
     * What the model gets: the exact training system prompt for this chat's timeline and
     * character, then the last MEMORY_TURNS turns of the open chat.
     */
    private List<Map<String, Object>> memory(Threads.ChatThread t) {
        List<Map<String, Object>> out = new ArrayList<>();
        String sys = prompts.system(t.stage, t.persona, threads.friendName, threads.friendStory);
        if (sys != null) {   // (without the prompt files the model's built-in Season 2 / Futaro prompt is used)
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("role", "system");
            s.put("content", sys);
            out.add(s);
        }
        List<Map<String, Object>> src = t.messages;
        int from = Math.max(0, src.size() - MEMORY_TURNS * 2);
        List<Map<String, Object>> turns = new ArrayList<>();
        for (int k = from; k < src.size(); k++) {
            Map<String, Object> m = src.get(k);
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", Threads.isUser(m) ? "user" : "assistant");
            msg.put("content", String.valueOf(m.get("text")));
            turns.add(msg);
        }
        // Training chats always start with the other person speaking.
        while (!turns.isEmpty() && "assistant".equals(turns.get(0).get("role"))) turns.remove(0);
        out.addAll(turns);
        return out;
    }

    /** The name the narrator uses for you. */
    private String partnerName(Threads.ChatThread t) {
        if (!t.asFriend()) return "Futaro";
        return threads.friendName.isBlank() ? "Her friend" : threads.friendName.strip();
    }

    /** How many times the safety check may ask her again for one message. */
    private static final int MAX_RETRIES = 2;

    private void send() {
        String text = input.getText().strip();
        if (text.isEmpty() || busy) return;
        if (!ready) {   // keep the text, just say why nothing happens yet
            flash(sister.name() + " isn't ready yet · " + modelText.getText());
            return;
        }
        input.clear();

        Threads.ChatThread t = threads.active();
        if (t.messages.isEmpty()) messages.getChildren().clear(); // drop the greeting + starters
        // her previous reply (to catch her repeating herself)
        String prev = null;
        for (int k = t.messages.size() - 1; k >= 0; k--)
            if (!Threads.isUser(t.messages.get(k))) { prev = String.valueOf(t.messages.get(k).get("text")); break; }
        String prevReply = prev;
        Map<String, Object> userMsg = threads.add(t, Threads.USER_ROLE, text);
        threads.save();
        refreshThreadList();
        addMsg(false, text, Threads.shortTime(userMsg.get("time")), null, true);

        Msg her = addMsg(true, "", "", null, true);
        her.typing(true);
        List<Map<String, Object>> context = memory(t);
        boolean withCue = Main.actionsOn;
        String partner = partnerName(t);
        String stage = t.stage;
        boolean friend = t.asFriend();
        String friendName = threads.friendName;
        setBusy(true);

        Ollama.Handle h = new Ollama.Handle();
        current = h;
        Thread th = new Thread(() -> {
            try {
                // ---- 1) her words, checked; asked again if something is wrong
                String best = null;
                List<SafetyCheck.Issue> bestIssues = List.of();
                List<SafetyCheck.Issue> caught = new ArrayList<>();
                List<SafetyCheck.Issue> last = List.of();
                int retries = 0;
                for (int attempt = 0; attempt <= MAX_RETRIES && !h.isCancelled(); attempt++) {
                    h.truncated = false;
                    // last try: add a one-line reminder of what was wrong (e.g. "Your birthday is May 5.")
                    List<Map<String, Object>> msgs = attempt == MAX_RETRIES ? withHint(context, SafetyCheck.hint(last)) : context;
                    Map<String, Object> opts = new LinkedHashMap<>();
                    opts.put("num_predict", MAX_TOKENS);
                    if (attempt == MAX_RETRIES) opts.put("temperature", 0.6);
                    StringBuilder sb = new StringBuilder();
                    boolean stream = !withCue && attempt == 0;   // words-only mode: show the first try live
                    String raw = Ollama.chat(sister.model(), msgs, opts, tok -> {
                        String now = sb.append(tok).toString();
                        if (stream) Platform.runLater(() -> { her.typing(false); her.body.setText(now + "▌"); });
                    }, h).strip();
                    String reply = h.truncated ? Ollama.trimToSentence(raw) : raw;
                    if (reply.isEmpty()) { if (h.isCancelled()) break; else continue; }
                    List<SafetyCheck.Issue> issues = SafetyCheck.check(sister, stage, friend, friendName, text, reply, prevReply);
                    if (best == null || issues.size() < bestIssues.size()) { best = reply; bestIssues = issues; }
                    if (issues.isEmpty() || h.isCancelled()) break;
                    caught.addAll(issues);
                    last = issues;
                    if (attempt < MAX_RETRIES) {
                        retries++;
                        // invisible to the user: she just keeps "thinking" (typing dots) while she's asked again
                        Platform.runLater(() -> her.typing(true));
                    }
                }
                if (best == null) {
                    Platform.runLater(() -> finish(t, her, "", null, h, false, List.of(), List.of(), 0));
                    return;
                }
                // ---- 2) safe last-resort fixes (her name for you, *actions*, stray tokens)
                String finalReply = best;
                List<SafetyCheck.Issue> left = bestIssues;
                if (left.stream().anyMatch(SafetyCheck.Issue::autoFixable)) {
                    finalReply = SafetyCheck.autofix(sister, stage, friend, finalReply);
                    left = SafetyCheck.check(sister, stage, friend, friendName, text, finalReply, prevReply);
                }
                String shown = finalReply;
                List<SafetyCheck.Issue> unresolved = left;
                int tries = retries;
                if (!withCue || h.isCancelled()) {
                    Platform.runLater(() -> finish(t, her, shown, null, h, false, caught, unresolved, tries));
                    return;
                }
                // ---- 3) the visual cue for those words, checked too (one more try if it invents people)
                String cue;
                try {
                    cue = Ollama.narrate(sister.model(), sister.name(), partner, text, shown);
                    if (!SafetyCheck.checkCue(sister, friend, cue).isEmpty()) {
                        String again = Ollama.narrate(sister.model(), sister.name(), partner, text, shown);
                        if (SafetyCheck.checkCue(sister, friend, again).size() <= SafetyCheck.checkCue(sister, friend, cue).size()) cue = again;
                    }
                } catch (Exception e) { cue = ""; }
                String action = cue;
                Platform.runLater(() -> finish(t, her, shown, action, h, !h.isCancelled(), caught, unresolved, tries));
            } catch (Exception e) {
                Platform.runLater(() -> fail(her, e));
            }
        }, "chat");
        th.setDaemon(true);
        th.start();
    }

    /** Same messages, with a one-line reminder added to the end of the system prompt (last retry only). */
    private static List<Map<String, Object>> withHint(List<Map<String, Object>> context, String hint) {
        if (hint == null || hint.isBlank() || context.isEmpty() || !"system".equals(context.get(0).get("role"))) return context;
        List<Map<String, Object>> out = new ArrayList<>(context);
        Map<String, Object> sys = new LinkedHashMap<>(context.get(0));
        sys.put("content", sys.get("content") + "\n\n(Important for this reply: " + hint + ")");
        out.set(0, sys);
        return out;
    }

    private Timeline typer;
    private Runnable typerDone;

    /** Shows her reply: visual cue on line 1 (if any), then her words. Saves it first. */
    private void finish(Threads.ChatThread t, Msg her, String reply, String action, Ollama.Handle h, boolean typeOut,
                        List<SafetyCheck.Issue> caught, List<SafetyCheck.Issue> unresolved, int retries) {
        if (current == h) current = null;
        her.typing(false);
        if (reply.isEmpty()) {
            messages.getChildren().remove(her.row);
            setBusy(false);
            flash(h.isCancelled() ? "Stopped" : sister.name() + " didn't answer · try again");
            return;
        }
        Map<String, Object> m = threads.add(t, sister.name().toUpperCase(), reply);
        if (action != null && !action.isBlank()) m.put("action", action);
        // what the safety check did: saved quietly in the chat file only, never shown in the app
        if (!caught.isEmpty() || !unresolved.isEmpty()) {
            Map<String, Object> chk = new LinkedHashMap<>();
            chk.put("caught", SafetyCheck.summary(caught));
            chk.put("retries", retries);
            if (!unresolved.isEmpty()) chk.put("unresolved", SafetyCheck.summary(unresolved));
            m.put("check", chk);
        }
        threads.save();
        refreshThreadList();
        her.time.setText(Threads.shortTime(m.get("time")));

        Runnable done = () -> {
            her.body.setText(reply);
            typer = null;
            typerDone = null;
            setBusy(false);
            if (h.isCancelled()) flash("Stopped · what she said is kept");
        };
        if (action == null || action.isBlank() || !typeOut) {
            her.setAction(action);
            done.run();
            return;
        }
        // 1) the cue rises in, 2) a short beat, 3) her words type out under it
        her.setAction(action);
        Ui.rise(her.action, 8, 380, 0);
        her.body.setText("");
        her.body.setVisible(true);
        her.body.setManaged(true);
        int n = reply.length();
        // normal speed, but a long reply never takes more than ~3.5 s to appear
        double perChar = Math.min(1000.0 / TYPE_SPEED, 3500.0 / Math.max(1, n));
        Timeline tl = new Timeline();
        for (int k = 1; k <= n; k++) {
            String part = reply.substring(0, k);
            tl.getKeyFrames().add(new javafx.animation.KeyFrame(
                    Duration.millis(450 + k * perChar), e -> her.body.setText(part)));
        }
        tl.setOnFinished(e -> done.run());
        typer = tl;
        typerDone = done;
        tl.play();
    }

    private void fail(Msg her, Exception e) {
        current = null;
        her.typing(false);
        her.body.setText("Couldn't get a reply: " + e.getMessage());
        her.card.getStyleClass().add("card-error");
        setBusy(false);
        flash("Error talking to Ollama · your message is saved, try again");
    }

    private void stop() {
        Ollama.Handle h = current;
        if (h != null) h.cancel();
        // words still typing out: show them all at once
        if (typer != null) {
            typer.stop();
            if (typerDone != null) typerDone.run();
        }
    }

    private void setBusy(boolean b) {
        busy = b;
        sendBtn.setGraphic(Ui.icon(b ? Ui.I_STOP : Ui.I_SEND));
        sendBtn.setTooltip(new Tooltip(b ? "Stop" : "Send"));
        if (b) sendBtn.getStyleClass().add("send-stop"); else sendBtn.getStyleClass().remove("send-stop");
        sendBtn.setDisable(!b && !ready);
        if (b) setStageStatus("thinking…", true);
        else if (ready) setStageStatus("listening", false);
        if (!b) input.requestFocus();
    }

    /** Short message that fades in and out at the top. */
    private void flash(String text) {
        toast.setText(text);
        if (toastAnim != null) toastAnim.stop();
        FadeTransition in = new FadeTransition(Duration.millis(180), toast);
        in.setToValue(1);
        FadeTransition out = new FadeTransition(Duration.millis(400), toast);
        out.setToValue(0);
        toastAnim = new SequentialTransition(in, new PauseTransition(Duration.millis(2400)), out);
        toastAnim.play();
    }

    /** Called when leaving this screen: stops any reply and frees the model's memory. */
    public void dispose(boolean unload) {
        disposed = true;
        stop();
        modelBreath.stop();
        stageBreath.stop();
        threads.save();
        if (unload) {
            Thread th = new Thread(() -> Ollama.unload(sister.model()), "unload");
            th.setDaemon(false);
            th.start();
        }
    }

    // ================================================================ messages

    /** One message on screen. For her: line 1 = action (italic), line 2 = her words. */
    private final class Msg {
        HBox row;
        VBox card;
        Label action;
        Label body;
        Label time;
        HBox dots;

        void setAction(String a) {
            boolean show = a != null && !a.isBlank();
            action.setText(show ? "✦  " + a : "");
            action.setVisible(show);
            action.setManaged(show);
        }

        void typing(boolean on) {
            if (dots == null) return;
            dots.setVisible(on);
            dots.setManaged(on);
            body.setVisible(!on);
            body.setManaged(!on);
        }
    }

    private Msg addMsg(boolean hers, String text, String time, Object action, boolean animate) {
        Msg m = new Msg();
        m.action = new Label();
        m.action.setWrapText(true);
        m.action.setMinHeight(Region.USE_PREF_SIZE);
        m.action.getStyleClass().add("action");
        m.body = new Label(text);
        m.body.setWrapText(true);
        m.body.setMinHeight(Region.USE_PREF_SIZE);
        m.body.getStyleClass().add(hers ? "her-text" : "my-text");
        m.time = new Label(time);
        m.time.getStyleClass().add("msg-time");

        if (hers) {
            m.dots = Ui.typingDots();
            m.dots.setVisible(false);
            m.dots.setManaged(false);
            m.card = new VBox(6, m.action, m.body, m.dots);
            m.card.getStyleClass().add("her-card");
            m.card.maxWidthProperty().bind(messages.widthProperty().multiply(0.84));

            Label name = new Label(sister.name());
            name.getStyleClass().add("her-name");
            HBox meta = new HBox(8, name, m.time);
            meta.setAlignment(Pos.BASELINE_LEFT);
            VBox col = new VBox(6, meta, m.card);
            Node av = Main.avatar(sister, 34);
            m.row = new HBox(12, av, col);
            m.row.setAlignment(Pos.TOP_LEFT);
        } else {
            m.card = new VBox(m.body);
            m.card.getStyleClass().add("my-card");
            m.card.maxWidthProperty().bind(messages.widthProperty().multiply(0.72));
            VBox col = new VBox(5, m.card, m.time);
            col.setAlignment(Pos.TOP_RIGHT);
            m.row = new HBox(col);
            m.row.setAlignment(Pos.TOP_RIGHT);
        }
        m.setAction(action == null ? null : String.valueOf(action));
        messages.getChildren().add(m.row);
        if (animate) Ui.rise(m.row, 16, 320, 0);
        return m;
    }

    // ================================================================ chat setup (timeline + who you are)

    /** Panel to pick the timeline and your character for this chat (or a new one). */
    private void openSetup(boolean unused) {
        if (setupOverlay != null) closeSetup();
        Threads.ChatThread t = threads.active();
        String[] stage = {t.stage};
        String[] persona = {t.persona};

        Label title = new Label("Set up the chat");
        title.getStyleClass().add("hist-title");
        Label sub = new Label("with " + sister.fullName());
        sub.getStyleClass().add("setup-sub");
        Button close = Ui.iconButton(Ui.I_CLOSE, "Close (Esc)");
        close.setOnAction(e -> closeSetup());
        HBox head = new HBox(new VBox(2, title, sub), Ui.grow(), close);
        head.setAlignment(Pos.TOP_LEFT);

        // ---- when
        Label whenL = new Label("WHEN");
        whenL.getStyleClass().add("setup-label");
        HBox when = new HBox(12);
        List<VBox> stageCards = new ArrayList<>();
        for (String st : Prompts.STAGES) {
            Label big = new Label(Prompts.stageLabel(st));
            big.getStyleClass().add("opt-title");
            Label blurb = new Label(prompts.blurb(st));
            blurb.getStyleClass().add("opt-text");
            blurb.setWrapText(true);
            VBox card = new VBox(6, big, blurb);
            card.getStyleClass().add("opt-card");
            card.setUserData(st);
            // three equal columns (a tiny pref width made the wrapped text very tall)
            card.prefWidthProperty().bind(when.widthProperty().subtract(24).divide(3));
            card.setMinWidth(0);
            stageCards.add(card);
            when.getChildren().add(card);
        }
        Runnable paintStage = () -> stageCards.forEach(c -> {
            c.getStyleClass().remove("opt-selected");
            if (c.getUserData().equals(stage[0])) c.getStyleClass().add("opt-selected");
        });
        stageCards.forEach(c -> c.setOnMouseClicked(e -> { stage[0] = (String) c.getUserData(); paintStage.run(); }));
        paintStage.run();

        // ---- who
        Label whoL = new Label("WHO ARE YOU");
        whoL.getStyleClass().add("setup-label");
        VBox futaro = optionCard("Futaro", "Fuutarou Uesugi, the tutor. The one she knows best.");
        futaro.setUserData(Prompts.FUTARO);
        VBox friend = optionCard("A friend", "Your own character. Pick a name and write how you two met.");
        friend.setUserData(Prompts.FRIEND);
        HBox who = new HBox(12, futaro, friend);
        futaro.prefWidthProperty().bind(who.widthProperty().subtract(12).divide(2));
        friend.prefWidthProperty().bind(who.widthProperty().subtract(12).divide(2));

        TextField name = new TextField(threads.friendName);
        name.setPromptText("Your name");
        name.getStyleClass().add("setup-field");
        javafx.scene.control.TextArea story = new javafx.scene.control.TextArea(threads.friendStory);
        story.setPromptText("Who you are and how you met " + sister.name() + ".\n"
                + "e.g. I'm a transfer student in her class. We met at the library on my first day.");
        story.setWrapText(true);
        story.setPrefRowCount(4);
        story.getStyleClass().add("setup-area");
        Label note = new Label("Saved for " + sister.name() + " and used in every friend chat with her. "
                + "She was trained to talk with \"a new friend\"; your name and story are added to that.");
        note.getStyleClass().add("setup-note");
        note.setWrapText(true);
        VBox friendBox = new VBox(8, name, story, note);
        friendBox.getStyleClass().add("setup-friend");

        Runnable paintWho = () -> {
            for (VBox c : List.of(futaro, friend)) {
                c.getStyleClass().remove("opt-selected");
                if (c.getUserData().equals(persona[0])) c.getStyleClass().add("opt-selected");
            }
            boolean f = Prompts.FRIEND.equals(persona[0]);
            friendBox.setVisible(f);
            friendBox.setManaged(f);
        };
        futaro.setOnMouseClicked(e -> { persona[0] = Prompts.FUTARO; paintWho.run(); });
        friend.setOnMouseClicked(e -> { persona[0] = Prompts.FRIEND; paintWho.run(); name.requestFocus(); });
        paintWho.run();

        // ---- buttons
        boolean empty = t.messages.isEmpty();
        Button primary = Ui.button(Ui.I_ADD, empty ? "Use for this chat" : "Start a new chat", "new-chat");
        Button secondary = Ui.button(null, "Change this chat", "mini");
        Label later = new Label("This chat keeps its messages; the change applies from your next message.");
        later.getStyleClass().add("setup-note");
        later.setWrapText(true);
        HBox buttons = empty ? new HBox(10, primary) : new HBox(10, primary, secondary);
        buttons.setAlignment(Pos.CENTER_LEFT);

        java.util.function.Consumer<Boolean> apply = newChat -> {
            if (Prompts.FRIEND.equals(persona[0]) && name.getText().isBlank()) {
                flash("Give your friend character a name first");
                name.requestFocus();
                return;
            }
            threads.friendName = name.getText().strip();
            threads.friendStory = story.getText().strip();
            threads.defaultStage = stage[0];
            threads.defaultPersona = persona[0];
            if (busy) stop();
            Threads.ChatThread target = newChat ? threads.create() : threads.active();
            target.stage = stage[0];
            target.persona = persona[0];
            threads.save();
            closeSetup();
            showThread(target, true);
            flash(Prompts.stageLabel(stage[0]) + " · you are " + threads.userName(target));
        };
        primary.setOnAction(e -> apply.accept(!empty));
        secondary.setOnAction(e -> apply.accept(false));

        VBox sheet = new VBox(16, head, whenL, when, whoL, who, friendBox, buttons);
        if (!empty) sheet.getChildren().add(later);
        sheet.getStyleClass().add("hist-sheet");
        sheet.setMaxWidth(820);
        sheet.setMaxHeight(Region.USE_PREF_SIZE);
        ScrollPane sp = new ScrollPane(sheet);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("clear-scroll");
        sp.setMaxWidth(860);
        StackPane overlay = new StackPane(sp);
        overlay.getStyleClass().add("hist-overlay");
        overlay.setOnMouseClicked(e -> { if (e.getTarget() == overlay) closeSetup(); });

        layout.setEffect(new GaussianBlur(16));
        setupOverlay = overlay;
        getChildren().add(overlay);
        Ui.rise(sheet, 24, 300, 0);
    }

    private VBox optionCard(String title, String text) {
        Label big = new Label(title);
        big.getStyleClass().add("opt-title");
        Label small = new Label(text);
        small.getStyleClass().add("opt-text");
        small.setWrapText(true);
        VBox card = new VBox(6, big, small);
        card.getStyleClass().add("opt-card");
        card.setMinWidth(0);
        return card;
    }

    private void closeSetup() {
        if (setupOverlay != null) getChildren().remove(setupOverlay);
        setupOverlay = null;
        layout.setEffect(null);
        input.requestFocus();
    }

    // ================================================================ history overlay

    /** Search across all of this sister's chats; click a chat to open it. */
    private void openHistory() {
        if (historyOverlay != null) closeHistory();

        Label title = new Label("Search " + sister.name() + "'s chats");
        title.getStyleClass().add("hist-title");
        Button close = Ui.iconButton(Ui.I_CLOSE, "Close (Esc)");
        close.setOnAction(e -> closeHistory());
        HBox head = new HBox(title, Ui.grow(), close);
        head.setAlignment(Pos.CENTER_LEFT);

        TextField search = new TextField();
        search.setPromptText("Type a word and press Enter…");
        search.getStyleClass().add("hist-search");
        HBox.setHgrow(search, Priority.ALWAYS);
        HBox searchBox = new HBox(10, Ui.icon(Ui.I_SEARCH), search);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.getStyleClass().add("hist-search-box");

        Button wipe = Ui.button(Ui.I_DELETE, "Delete all chats", "mini", "mini-danger");
        Label count = new Label();
        count.getStyleClass().add("hist-count");
        HBox tools = new HBox(10, count, Ui.grow(), wipe);
        tools.setAlignment(Pos.CENTER_LEFT);

        VBox list = new VBox(14);
        ScrollPane sp = new ScrollPane(list);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("clear-scroll");
        VBox.setVgrow(sp, Priority.ALWAYS);

        Runnable populate = () -> fillHistory(list, count, search.getText().strip().toLowerCase());
        search.setOnAction(e -> populate.run());
        search.textProperty().addListener((o, a, b) -> populate.run());
        wipe.setOnAction(e -> {
            if (!confirm("Delete all chats", "Permanently delete every chat with " + sister.name()
                    + "?\nShe won't remember any of them.")) return;
            if (busy) stop();
            threads.deleteAll();
            threads.save();
            showThread(threads.active(), false);
            populate.run();
            flash("All chats deleted");
        });

        VBox sheet = new VBox(16, head, searchBox, tools, sp);
        sheet.getStyleClass().add("hist-sheet");
        sheet.setMaxWidth(880);
        StackPane overlay = new StackPane(sheet);
        overlay.getStyleClass().add("hist-overlay");
        overlay.setOnMouseClicked(e -> { if (e.getTarget() == overlay) closeHistory(); });

        layout.setEffect(new GaussianBlur(16));
        historyOverlay = overlay;
        getChildren().add(overlay);
        Ui.rise(sheet, 24, 300, 0);
        populate.run();
        search.requestFocus();
    }

    private void fillHistory(VBox list, Label count, String query) {
        list.getChildren().clear();
        int threadsShown = 0;
        int hits = 0;
        for (Threads.ChatThread t : threads.sorted()) {
            List<Map<String, Object>> shown = new ArrayList<>();
            boolean titleHit = query.isEmpty() || t.title.toLowerCase().contains(query);
            for (Map<String, Object> m : t.messages) {
                if (titleHit || String.valueOf(m.get("text")).toLowerCase().contains(query)
                        || String.valueOf(m.getOrDefault("action", "")).toLowerCase().contains(query)) {
                    shown.add(m);
                }
            }
            if (t.messages.isEmpty() || (!titleHit && shown.isEmpty())) continue;
            threadsShown++;
            hits += shown.size();

            Label tt = new Label(t.title);
            tt.getStyleClass().add("hist-thread-title");
            Label when = new Label(t.updated + "  ·  " + t.messages.size() + " messages");
            when.getStyleClass().add("hist-thread-when");
            Label open = new Label("Open  →");
            open.getStyleClass().add("hist-open");
            HBox head = new HBox(10, new VBox(2, tt, when), Ui.grow(), open);
            head.setAlignment(Pos.CENTER_LEFT);

            VBox lines = new VBox(8);
            int limit = query.isEmpty() ? 4 : 50;
            for (int k = 0; k < shown.size() && k < limit; k++) {
                Map<String, Object> m = shown.get(k);
                boolean hers = !Threads.isUser(m);
                Label who = new Label(hers ? sister.name() : threads.userName(t));
                who.getStyleClass().add(hers ? "hist-who-her" : "hist-who-me");
                who.setMinWidth(64);
                VBox txt = new VBox(2);
                Object act = m.get("action");
                if (hers && act != null && !String.valueOf(act).isBlank()) {
                    Label a = new Label(String.valueOf(act));
                    a.setWrapText(true);
                    a.getStyleClass().add("hist-action");
                    txt.getChildren().add(a);
                }
                Label body = new Label(String.valueOf(m.get("text")));
                body.setWrapText(true);
                body.getStyleClass().add("hist-text");
                txt.getChildren().add(body);
                HBox line = new HBox(10, who, txt);
                lines.getChildren().add(line);
            }
            if (shown.size() > limit) {
                Label more = new Label("+ " + (shown.size() - limit) + " more");
                more.getStyleClass().add("hist-thread-when");
                lines.getChildren().add(more);
            }

            VBox card = new VBox(12, head, lines);
            card.getStyleClass().add("hist-card");
            card.setOnMouseClicked(e -> { closeHistory(); switchThread(t); });
            list.getChildren().add(card);
        }
        if (threadsShown == 0) {
            Label none = new Label(query.isEmpty() ? "No chats yet." : "Nothing found for “" + query + "”.");
            none.getStyleClass().add("hist-empty");
            list.getChildren().add(none);
        }
        count.setText(query.isEmpty()
                ? threadsShown + " chats"
                : hits + " matching messages in " + threadsShown + " chats");
    }

    private void closeHistory() {
        if (historyOverlay != null) getChildren().remove(historyOverlay);
        historyOverlay = null;
        layout.setEffect(null);
        input.requestFocus();
    }

    // ================================================================ dialogs

    private boolean confirm(String title, String text) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.YES, ButtonType.NO);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setGraphic(null);
        style(a);
        return a.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void style(Dialog<?> d) {
        d.initOwner(getScene().getWindow());
        d.getDialogPane().getStylesheets().add(Main.css());
        d.getDialogPane().setStyle(Ui.accentVars(sister));
        d.getDialogPane().getStyleClass().add("tqq-dialog");
    }
}
