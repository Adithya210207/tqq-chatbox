package tqq;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Talks to the local Ollama server (http://localhost:11434). */
public final class Ollama {
    public static final String URL = "http://localhost:11434";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    /** Set only if this app started "ollama serve" itself. */
    private static volatile Process startedByUs;

    private Ollama() {}

    /** Lets the UI stop a reply while it is streaming. */
    public static final class Handle {
        volatile InputStream in;
        volatile boolean cancelled;
        /** True when the reply hit the length limit (num_predict). */
        public volatile boolean truncated;

        public void cancel() {
            cancelled = true;
            try { if (in != null) in.close(); } catch (IOException ignored) {}
        }

        public boolean isCancelled() { return cancelled; }
    }

    // ------------------------------------------------------------ server

    public static boolean ping() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(URL + "/api/tags"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            return HTTP.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /** Starts "ollama serve" if nothing answers. Returns true when the server is up. */
    public static boolean ensureRunning() {
        if (ping()) return true;
        Path exe = findExe();
        if (exe == null) return false;
        try {
            startedByUs = new ProcessBuilder(exe.toString(), "serve")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            return false;
        }
        for (int k = 0; k < 20; k++) {
            try { Thread.sleep(1000); } catch (InterruptedException e) { return false; }
            if (ping()) return true;
        }
        return false;
    }

    public static boolean startedByUs() { return startedByUs != null; }

    /** Stops the server, but only if this app started it. */
    public static void stopIfStartedByUs() {
        Process p = startedByUs;
        if (p == null) return;
        p.descendants().forEach(ProcessHandle::destroyForcibly);
        p.destroyForcibly();
        startedByUs = null;
    }

    private static Path findExe() {
        List<Path> candidates = new ArrayList<>();
        String local = System.getenv("LOCALAPPDATA");
        String pf = System.getenv("ProgramFiles");
        if (local != null) candidates.add(Path.of(local, "Programs", "Ollama", "ollama.exe"));
        if (pf != null) candidates.add(Path.of(pf, "Ollama", "ollama.exe"));
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                if (!dir.isBlank()) candidates.add(Path.of(dir.trim(), "ollama.exe"));
            }
        }
        for (Path c : candidates) if (Files.isRegularFile(c)) return c;
        return null;
    }

    // ------------------------------------------------------------ models

    @SuppressWarnings("unchecked")
    public static List<String> models() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(URL + "/api/tags"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            String body = HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body();
            List<String> out = new ArrayList<>();
            Map<String, Object> m = (Map<String, Object>) Json.parse(body);
            for (Object o : (List<Object>) m.get("models")) {
                out.add(String.valueOf(((Map<String, Object>) o).get("name")));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    public static boolean hasModel(String model) {
        String want = model.toLowerCase();
        for (String n : models()) {
            String have = n.toLowerCase();
            if (have.equals(want) || have.equals(want + ":latest")) return true;
        }
        return false;
    }

    /** Loads the model into memory (so the first reply is not slow). Blocks until loaded. */
    public static void load(String model) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("keep_alive", "30m");
        post("/api/generate", body, Duration.ofMinutes(5));
    }

    /** Frees the model's memory (only one 8.5 GB model fits at a time). */
    public static void unload(String model) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("keep_alive", 0);
            post("/api/generate", body, Duration.ofSeconds(20));
        } catch (Exception ignored) {}
    }

    private static String post(String path, Map<String, Object> body, Duration timeout)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(URL + path))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body)))
                .build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IOException("Ollama " + r.statusCode() + ": " + r.body());
        return r.body();
    }

    /** Names of the models currently in memory (/api/ps). */
    @SuppressWarnings("unchecked")
    public static List<String> loaded() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(URL + "/api/ps"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            String body = HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body();
            List<String> out = new ArrayList<>();
            Map<String, Object> m = (Map<String, Object>) Json.parse(body);
            for (Object o : (List<Object>) m.get("models")) {
                out.add(String.valueOf(((Map<String, Object>) o).get("name")));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    // ------------------------------------------------------------ narrator

    // "just the two of them": without it the narrator kept inventing sisters in the room (tested: 1/12 -> 0/12)
    private static final String NARRATOR =
            "You are a narrator for a visual novel based on The Quintessential Quintuplets. "
          + "It is just %1$s and %2$s talking; nobody else is there. "
          + "Write ONE short sentence (8-18 words) in third person, present tense, describing only "
          + "%1$s's body language or action as she says her line: gestures, expression, eyes, posture. "
          + "Base it only on what is said. Do not mention other people, glasses, or things that aren't in the lines. "
          + "No dialogue, no quotes, no thoughts. Output the sentence only.";

    /**
     * Pre-reads a chat's prompt (system + history) so the first reply after opening a chat or
     * changing its timeline/character is fast (tested: 11.8 s -> 3.7 s). Errors are ignored.
     */
    public static void prime(String model, List<Map<String, Object>> messages) {
        try {
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("num_predict", 1);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("stream", false);
            body.put("keep_alive", "30m");
            body.put("options", options);
            post("/api/chat", body, Duration.ofMinutes(3));
        } catch (Exception ignored) {}
    }

    /**
     * The "action line": the same sister model (already in memory) writes one short
     * narrator sentence about what she does while saying her reply. Uses its own
     * system prompt, so it never touches her chat memory.
     */
    @SuppressWarnings("unchecked")
    public static String narrate(String model, String name, String userLine, String reply)
            throws IOException, InterruptedException {
        return narrate(model, name, "Futaro", userLine, reply);
    }

    /** Same, with the name of the person she's talking to (Futaro or your friend). */
    @SuppressWarnings("unchecked")
    public static String narrate(String model, String name, String partner, String userLine, String reply)
            throws IOException, InterruptedException {
        Map<String, Object> sys = new LinkedHashMap<>();
        sys.put("role", "system");
        sys.put("content", String.format(NARRATOR, name, partner));
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("role", "user");
        String said = userLine == null || userLine.isBlank() ? "" : partner + " says: \"" + userLine + "\"\n";
        user.put("content", said + name + " replies: \"" + reply + "\"\n\nNarrate what " + name
                + " does as she replies.");
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", 0.7);
        options.put("num_predict", 48);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(sys, user));
        body.put("stream", false);
        body.put("keep_alive", "30m");
        body.put("options", options);
        Map<String, Object> r = (Map<String, Object>) Json.parse(post("/api/chat", body, Duration.ofMinutes(2)));
        Map<String, Object> msg = (Map<String, Object>) r.get("message");
        return cleanAction(msg == null ? "" : String.valueOf(msg.get("content")));
    }

    /** One clean sentence: no asterisks/quotes, cut after the first sentence end. */
    static String cleanAction(String s) {
        String t = s.replace("*", "").replaceAll("[\"“”]", "").strip();
        int nl = t.indexOf('\n');
        if (nl > 0) t = t.substring(0, nl).strip();
        for (int k = 0; k < t.length(); k++) {
            char c = t.charAt(k);
            if ((c == '.' || c == '!' || c == '?') && k > 10) { t = t.substring(0, k + 1); break; }
        }
        if (t.length() > 160) t = t.substring(0, 160).strip() + "…";
        // cut off before the sentence ended: finish it with "..." instead of a dangling word
        if (!t.isEmpty() && !endsSentence(t)) t = t.replaceAll("[\\s,;:\\-]+$", "") + "…";
        return t;
    }

    // ------------------------------------------------------------ chat

    /**
     * Streams one reply. No system message is sent: the fine-tuned model's Modelfile
     * SYSTEM (= its training prompt) is used automatically.
     */
    @SuppressWarnings("unchecked")
    public static String chat(String model, List<Map<String, Object>> messages,
                              Map<String, Object> options, Consumer<String> onToken,
                              Handle handle) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", true);
        body.put("keep_alive", "30m");
        if (options != null && !options.isEmpty()) body.put("options", options);

        HttpRequest req = HttpRequest.newBuilder(URI.create(URL + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body)))
                .build();
        HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) {
            String err = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new IOException("Ollama " + resp.statusCode() + ": " + err);
        }
        handle.in = resp.body();
        if (handle.cancelled) handle.cancel();

        StringBuilder all = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                Map<String, Object> m = (Map<String, Object>) Json.parse(line);
                if (m.containsKey("error")) throw new IOException(String.valueOf(m.get("error")));
                Map<String, Object> msg = (Map<String, Object>) m.get("message");
                if (msg != null) {
                    Object t = msg.get("content");
                    if (t instanceof String tok && !tok.isEmpty()) {
                        all.append(tok);
                        onToken.accept(tok);
                    }
                }
                if (Boolean.TRUE.equals(m.get("done"))) {
                    // "length" = she hit the num_predict limit and was cut off mid-sentence
                    if ("length".equals(m.get("done_reason"))) handle.truncated = true;
                    break;
                }
            }
        } catch (IOException e) {
            if (!handle.cancelled) throw e;
        }
        return all.toString();
    }

    /**
     * A reply that was cut off: keep it up to its last finished sentence
     * (only if that keeps at least a third of it; otherwise end it with "...").
     */
    public static String trimToSentence(String s) {
        String t = s.strip();
        if (t.isEmpty() || endsSentence(t)) return t;
        int cut = -1;
        for (int k = t.length() - 1; k >= 0; k--) {
            char c = t.charAt(k);
            if (c == '.' || c == '!' || c == '?' || c == '…') { cut = k; break; }
        }
        if (cut >= t.length() / 3) {
            int end = cut + 1;
            while (end < t.length() && "\"'”’)".indexOf(t.charAt(end)) >= 0) end++;
            return t.substring(0, end).strip();
        }
        return t.replaceAll("[\\s,;:\\-]+$", "") + "…";
    }

    static boolean endsSentence(String t) {
        char c = t.charAt(t.length() - 1);
        return ".!?…~\"'”’)".indexOf(c) >= 0;
    }
}
