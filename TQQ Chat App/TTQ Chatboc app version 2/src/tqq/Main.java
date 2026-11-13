package tqq;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** App shell: swaps between the home screen and a sister's chat. */
public final class Main extends Application {

    /** App folder (run.bat starts the app from there). */
    public static final Path HOME =
            Path.of(System.getProperty("tqq.home", System.getProperty("user.dir")));

    /** Shared by every sister's chat: show the narrator "action line" or not. */
    public static boolean actionsOn = true;

    private static final Map<String, Image> IMAGES = new HashMap<>();

    /** Starts (or finds) Ollama in the background as soon as the app opens. */
    private final CompletableFuture<Boolean> ollama =
            CompletableFuture.supplyAsync(Ollama::ensureRunning);

    private final StackPane content = new StackPane();
    private ChatView chat;

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        content.getStyleClass().add("app-root");
        Scene scene = new Scene(content, 1320, 820);
        scene.setFill(javafx.scene.paint.Color.web("#0b0b10"));
        scene.getStylesheets().add(css());
        stage.setScene(scene);
        Path icon = HOME.resolve("assets").resolve("app.png");
        if (Files.isRegularFile(icon)) stage.getIcons().add(new Image(icon.toUri().toString()));
        stage.setTitle("TQQ Chatbox — the Nakano sisters");
        stage.setMinWidth(1000);
        stage.setMinHeight(660);
        stage.setOnCloseRequest(e -> shutdown());
        showHome();
        stage.show();
    }

    // ------------------------------------------------------------ screens

    void showHome() {
        closeChat();
        content.getChildren().setAll(new HomeView(this::openChat, ollama));
    }

    void openChat(Sister s) {
        if (chat != null && chat.sister() == s) return;
        closeChat();
        chat = new ChatView(s, ollama, this::openChat, this::showHome);
        content.getChildren().setAll(chat);
        Ui.rise(chat, 0, 380, 0);
    }

    private void closeChat() {
        if (chat != null) {
            chat.dispose(true);
            chat = null;
        }
    }

    private void shutdown() {
        if (chat != null) {
            chat.dispose(false);
            // Free her memory before quitting (unless we started Ollama: stopping it frees everything).
            if (!Ollama.startedByUs()) {
                Ollama.unload(chat.sister().model());
                if (InventionCheck.appliesTo(chat.sister())) Ollama.unload(InventionCheck.JUDGE_MODEL);
            }
        }
        Ollama.stopIfStartedByUs();
        Platform.exit();
        System.exit(0);
    }

    // ------------------------------------------------------------ shared helpers

    public static String css() {
        return HOME.resolve("assets").resolve("style.css").toUri().toString();
    }

    /** assets/images/<sister>.png (or .jpg/.jpeg/.gif/.bmp). Null if there is none. */
    public static Image image(Sister s) {
        if (IMAGES.containsKey(s.key())) return IMAGES.get(s.key());
        Image img = null;
        for (String ext : new String[] {".png", ".jpg", ".jpeg", ".gif", ".bmp"}) {
            Path p = HOME.resolve("assets").resolve("images").resolve(s.key() + ext);
            if (Files.isRegularFile(p)) {
                img = new Image(p.toUri().toString(), 1100, 0, true, true);
                if (img.isError()) img = null; else break;
            }
        }
        IMAGES.put(s.key(), img);
        return img;
    }

    /** Round avatar (falls back to her kanji on her colour). */
    public static Node avatar(Sister s, double size) {
        StackPane box = new StackPane();
        box.setMinSize(size, size);
        box.setMaxSize(size, size);
        box.setPrefSize(size, size);
        box.setStyle(Ui.accentVars(s));
        Image img = image(s);
        if (img == null) {
            Label k = new Label(s.kanji().substring(0, 1));
            k.getStyleClass().add("avatar-kanji");
            k.setStyle("-fx-font-size: " + Math.round(size * 0.45) + "px;");
            box.getChildren().add(k);
            box.getStyleClass().add("avatar-fallback");
            return box;
        }
        ImageView iv = new ImageView(img);
        iv.setSmooth(true);
        // tight square around the face (portraits keep the face in the upper third)
        double side = Math.min(img.getWidth(), img.getHeight()) * 0.56;
        double y = Math.min(img.getHeight() * 0.10, img.getHeight() - side);
        iv.setViewport(new javafx.geometry.Rectangle2D((img.getWidth() - side) / 2, y, side, side));
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        iv.setClip(new Circle(size / 2, size / 2, size / 2));
        box.getChildren().add(iv);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("avatar");
        return box;
    }
}
