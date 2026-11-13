package tqq;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One sister's chat threads (like ChatGPT/Claude "chats"), saved in history/<sister>.json:
 * { "active_id": "...", "threads": [ {id, title, created, updated, messages:[{time, role, text}]} ] }
 */
public final class Threads {
    public static final String USER_ROLE = "FUTARO";
    public static final String NEW_TITLE = "New chat";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** One conversation. */
    public static final class ChatThread {
        public String id;
        public String title;
        public String created;
        public String updated;
        /** Timeline of this chat: season_1 / season_2 / movie. */
        public String stage = "season_2";
        /** Who you play in this chat: "futaro" or "friend". */
        public String persona = Prompts.FUTARO;
        public final List<Map<String, Object>> messages = new ArrayList<>();

        public boolean asFriend() { return Prompts.FRIEND.equals(persona); }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("title", title);
            m.put("created", created);
            m.put("updated", updated);
            m.put("stage", stage);
            m.put("persona", persona);
            m.put("messages", messages);
            return m;
        }

        @SuppressWarnings("unchecked")
        static ChatThread fromMap(Map<String, Object> m) {
            ChatThread t = new ChatThread();
            t.id = String.valueOf(m.get("id"));
            t.title = m.get("title") == null ? NEW_TITLE : String.valueOf(m.get("title"));
            t.created = String.valueOf(m.getOrDefault("created", now()));
            t.updated = String.valueOf(m.getOrDefault("updated", t.created));
            // chats made before timelines existed were Season 2 with Futaro (the model's built-in prompt)
            t.stage = String.valueOf(m.getOrDefault("stage", "season_2"));
            if (!Prompts.STAGES.contains(t.stage)) t.stage = "season_2";
            t.persona = Prompts.FRIEND.equals(m.get("persona")) ? Prompts.FRIEND : Prompts.FUTARO;
            Object msgs = m.get("messages");
            if (msgs instanceof List<?> l) for (Object o : l) t.messages.add((Map<String, Object>) o);
            return t;
        }
    }

    private final Sister sister;
    private final List<ChatThread> threads = new ArrayList<>();
    private String activeId;

    /** Your friend character for this sister (name + who you are and how you met). */
    public String friendName = "";
    public String friendStory = "";
    /** What a new chat starts with (the last settings you picked). */
    public String defaultStage = "season_2";
    public String defaultPersona = Prompts.FUTARO;

    /** The name you go by in a chat: "Futaro", or your friend's name. */
    public String userName(ChatThread t) {
        if (!t.asFriend()) return "Futaro";
        return friendName.isBlank() ? "You" : friendName.strip();
    }

    private Threads(Sister sister) { this.sister = sister; }

    public static Path file(Sister s) {
        return Main.HOME.resolve("history").resolve(s.key() + ".json");
    }

    @SuppressWarnings("unchecked")
    public static Threads load(Sister s) {
        Threads t = new Threads(s);
        try {
            Path f = file(s);
            if (Files.exists(f)) {
                Map<String, Object> root = (Map<String, Object>) Json.parse(Files.readString(f, StandardCharsets.UTF_8));
                for (Object o : (List<Object>) root.get("threads")) t.threads.add(ChatThread.fromMap((Map<String, Object>) o));
                Object a = root.get("active_id");
                t.activeId = a == null ? null : String.valueOf(a);
                if (root.get("friend") instanceof Map<?, ?> fr) {
                    t.friendName = String.valueOf(fr.get("name") == null ? "" : fr.get("name"));
                    t.friendStory = String.valueOf(fr.get("story") == null ? "" : fr.get("story"));
                }
                Object ds = root.get("default_stage");
                if (ds != null && Prompts.STAGES.contains(String.valueOf(ds))) t.defaultStage = String.valueOf(ds);
                if (Prompts.FRIEND.equals(root.get("default_persona"))) t.defaultPersona = Prompts.FRIEND;
            }
        } catch (Exception bad) {
            // unreadable file: keep a copy instead of overwriting it with an empty one on the next save
            try {
                Path f = file(s);
                if (Files.exists(f)) {
                    String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                    Files.copy(f, f.resolveSibling(s.key() + ".unreadable-" + stamp + ".json"));
                }
            } catch (Exception ignored) {}
            t.threads.clear();
        }
        if (t.threads.isEmpty()) t.create();
        if (t.find(t.activeId) == null) t.activeId = t.sorted().get(0).id;
        return t;
    }

    public void save() {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("active_id", activeId);
            Map<String, Object> fr = new LinkedHashMap<>();
            fr.put("name", friendName);
            fr.put("story", friendStory);
            root.put("friend", fr);
            root.put("default_stage", defaultStage);
            root.put("default_persona", defaultPersona);
            List<Object> list = new ArrayList<>();
            for (ChatThread t : threads) list.add(t.toMap());
            root.put("threads", list);
            Path f = file(sister);
            Files.createDirectories(f.getParent());
            // write a temp file, then swap it in: a crash mid-save can't leave a half-written file
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, Json.pretty(root), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {}
    }

    /** Newest first (by last message). */
    public List<ChatThread> sorted() {
        List<ChatThread> out = new ArrayList<>(threads);
        out.sort(Comparator.comparing((ChatThread t) -> t.updated).reversed());
        return out;
    }

    public ChatThread find(String id) {
        if (id == null) return null;
        for (ChatThread t : threads) if (t.id.equals(id)) return t;
        return null;
    }

    public ChatThread active() {
        ChatThread t = find(activeId);
        if (t == null) { t = sorted().get(0); activeId = t.id; }
        return t;
    }

    public void setActive(String id) { if (find(id) != null) activeId = id; }

    /** Makes a new empty thread and selects it. */
    public ChatThread create() {
        ChatThread t = new ChatThread();
        t.id = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        t.title = NEW_TITLE;
        t.created = now();
        t.updated = t.created;
        t.stage = defaultStage;
        t.persona = defaultPersona;
        threads.add(t);
        activeId = t.id;
        return t;
    }

    /** Deletes a thread; there is always at least one left. */
    public void delete(String id) {
        threads.removeIf(t -> t.id.equals(id));
        if (threads.isEmpty()) create();
        if (find(activeId) == null) activeId = sorted().get(0).id;
    }

    public void deleteAll() {
        threads.clear();
        create();
    }

    public List<ChatThread> all() { return sorted(); }

    /** Adds a message, bumps "updated", and names a new thread after the first user line. */
    public Map<String, Object> add(ChatThread t, String role, String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("time", now());
        m.put("role", role);
        m.put("text", text);
        t.messages.add(m);
        t.updated = String.valueOf(m.get("time"));
        if (NEW_TITLE.equals(t.title) && USER_ROLE.equals(role)) {
            String title = text.strip().replaceAll("\\s+", " ");
            t.title = title.length() > 40 ? title.substring(0, 40).strip() + "…" : title;
        }
        return m;
    }

    public static boolean isUser(Map<String, Object> e) { return USER_ROLE.equals(e.get("role")); }

    public static String now() { return LocalDateTime.now().format(FMT); }

    /** "now", "5m", "3h", "yesterday", "Nov 9" */
    public static String ago(String time) {
        try {
            LocalDateTime t = LocalDateTime.parse(time, FMT);
            LocalDateTime n = LocalDateTime.now();
            long min = java.time.Duration.between(t, n).toMinutes();
            if (min < 1) return "now";
            if (min < 60) return min + "m";
            if (t.toLocalDate().equals(n.toLocalDate())) return (min / 60) + "h";
            if (t.toLocalDate().equals(n.toLocalDate().minusDays(1))) return "yesterday";
            return t.format(DateTimeFormatter.ofPattern("MMM d"));
        } catch (Exception e) {
            return time;
        }
    }

    /** Last message of a thread, one line, for the thread list. */
    public static String preview(ChatThread t) {
        if (t.messages.isEmpty()) return "No messages yet";
        Map<String, Object> m = t.messages.get(t.messages.size() - 1);
        String who = isUser(m) ? "You: " : "";
        return who + String.valueOf(m.get("text")).replaceAll("\\s+", " ");
    }

    /** "2026-11-12 18:05" -> "18:05" */
    public static String shortTime(Object time) {
        String t = String.valueOf(time);
        int sp = t.lastIndexOf(' ');
        return sp >= 0 ? t.substring(sp + 1) : t;
    }

    /** Plain-text transcript for "Export this chat". */
    public String transcript(ChatThread t) {
        StringBuilder b = new StringBuilder();
        b.append(sister.fullName()).append(" — ").append(t.title).append('\n');
        b.append("Model: ").append(sister.model()).append(" · ").append(Prompts.stageLabel(t.stage))
         .append(" · you as ").append(userName(t)).append(" · exported ").append(now()).append('\n');
        b.append("=".repeat(50)).append("\n\n");
        for (Map<String, Object> m : t.messages) {
            String who = isUser(m) ? userName(t) : sister.name();
            b.append('[').append(m.get("time")).append("] ").append(who).append(":\n");
            Object act = m.get("action");
            if (act != null && !String.valueOf(act).isBlank()) b.append('*').append(act).append("*\n");
            b.append(m.get("text")).append("\n\n");
        }
        return b.toString();
    }
}
