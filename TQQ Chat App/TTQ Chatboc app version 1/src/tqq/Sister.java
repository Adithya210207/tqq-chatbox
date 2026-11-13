package tqq;

import java.util.List;

/**
 * One sister = one fine-tuned Ollama model.
 * The model already carries its own training prompt (Modelfile SYSTEM), so the app
 * never sends a system prompt. The user always plays Futaro, like in training.
 */
public record Sister(String key, String name, String kanji, int number, String model,
                     String accent, String tagline, String greeting) {

    public static final List<Sister> ALL = List.of(
        new Sister("ichika", "Ichika", "一花", 1, "Ichika_ryuu_v1", "#f5b942",
                   "The eldest · actress",
                   "Oh, Futaro-kun. Did you come all this way just to see Big Sis?"),
        new Sister("nino", "Nino", "二乃", 2, "Nino_ryuu_v1", "#c58cff",
                   "The second · the cook",
                   "Hmph. You're late, Futaro."),
        new Sister("miku", "Miku", "三玖", 3, "Miku_ryuu_v1", "#5aa9ff",
                   "The third · loves Sengoku history",
                   "Futaro... I was waiting."),
        new Sister("yotsuba", "Yotsuba", "四葉", 4, "Yotsuba_ryuu_v1", "#4fd88f",
                   "The fourth · sporty and cheerful",
                   "Uesugi-san! You're here!"),
        new Sister("itsuki", "Itsuki", "五月", 5, "Itsuki_ryuu_v1", "#ff6f7d",
                   "The fifth · wants to be a teacher",
                   "Uesugi-kun. Shall we start studying?")
    );

    public String fullName() { return name + " Nakano"; }

    /** "01" .. "05" */
    public String num() { return String.format("%02d", number); }

    /** The kanji written top-to-bottom (one character per line). */
    public String kanjiVertical() { return String.join("\n", kanji.split("")); }
}
