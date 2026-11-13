package tqq;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The exact system prompts each model was trained with, one per timeline
 * (assets/prompts/<sister>.json, exported from the training pipeline).
 * Training format: <timeline card + facts + rules> + " You are talking with X."
 * X was "Fuutarou" or "a new friend" in training, so those are the two characters you can play.
 */
public final class Prompts {
    public static final List<String> STAGES = List.of("season_1", "season_2", "movie");
    public static final String FUTARO = "futaro";
    public static final String FRIEND = "friend";

    private final Map<String, String> prompts = new LinkedHashMap<>();
    private final Map<String, String> blurbs = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    public Prompts(Sister s) {
        try {
            Path f = Main.HOME.resolve("assets").resolve("prompts").resolve(s.key() + ".json");
            Map<String, Object> root = (Map<String, Object>) Json.parse(Files.readString(f, StandardCharsets.UTF_8));
            ((Map<String, Object>) root.get("prompts")).forEach((k, v) -> prompts.put(k, String.valueOf(v)));
            ((Map<String, Object>) root.get("blurbs")).forEach((k, v) -> blurbs.put(k, String.valueOf(v)));
        } catch (Exception ignored) {
            // no file: the model's own built-in prompt (Season 2, Futaro) is used instead
        }
    }

    public boolean available() { return !prompts.isEmpty(); }

    /** The full system prompt for this timeline and character, or null if prompts are missing. */
    public String system(String stage, String persona, String friendName, String friendStory) {
        String base = prompts.get(stage);
        if (base == null) return null;
        if (!FRIEND.equals(persona)) return base + " You are talking with Fuutarou.";
        StringBuilder b = new StringBuilder(base).append(" You are talking with a new friend.");
        String name = friendName == null ? "" : friendName.strip();
        String story = friendStory == null ? "" : friendStory.strip().replaceAll("\\s+", " ");
        // Not part of training ("a new friend" had no name or story), so it goes after the
        // trained text. This wording was the one the models stuck to best in tests.
        String who = name.isEmpty() ? "your friend" : name;
        if (!name.isEmpty()) b.append(" Your friend's name is ").append(name).append('.');
        if (!story.isEmpty()) b.append(" What you know about ").append(who).append(" and how you met, in ")
                .append(name.isEmpty() ? "their" : name + "'s").append(" own words (this is true, never change ")
                .append("or contradict it): \"").append(story.replace("\"", "'")).append('"');
        return b.toString();
    }

    /** One hand-written line about her at that timeline (from her timeline card). */
    public String blurb(String stage) {
        return blurbs.getOrDefault(stage, "");
    }

    public static String stageLabel(String stage) {
        return switch (stage) {
            case "season_1" -> "Season 1";
            case "movie" -> "Movie";
            default -> "Season 2";
        };
    }

    public static String stageShort(String stage) {
        return switch (stage) {
            case "season_1" -> "S1";
            case "movie" -> "Movie";
            default -> "S2";
        };
    }
}
