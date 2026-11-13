package tqq;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The "step 4" safety check: looks at a reply BEFORE it is shown. If it breaks her fact sheet
 * or her training rules, the app asks the model again (see ChatView.send).
 * All rules come from facts/<Sister>.txt and the training rules in common.py.
 */
public final class SafetyCheck {
    private SafetyCheck() {}

    /** One problem found in a reply. */
    public record Issue(String code, String message, String hint, boolean autoFixable) {}

    // ------------------------------------------------------------ her facts (from facts/<Sister>.txt)

    private record Facts(int number, String subject, List<String> subjectWords) {}

    private static final Map<String, Facts> FACTS = Map.of(
            "ichika", new Facts(1, "math", List.of("math", "maths", "mathematics")),
            "nino", new Facts(2, "English", List.of("english")),
            "miku", new Facts(3, "social studies (Sengoku history)", List.of("social studies", "history")),
            "yotsuba", new Facts(4, "Japanese", List.of("japanese")),
            "itsuki", new Facts(5, "science", List.of("science")));
    private static final List<String> SUBJECTS = List.of("math", "maths", "mathematics", "english", "social studies",
            "history", "japanese", "science", "chemistry", "physics", "biology", "geography", "art", "music", "p.e.", "gym");
    private static final String[] ORDINAL = {"", "first", "second", "third", "fourth", "fifth"};
    private static final String[] NAMES = {"", "Ichika", "Nino", "Miku", "Yotsuba", "Itsuki"};

    /** How she calls Futaro, per timeline (common.ADDRESS + the user's name rules). */
    static Set<String> allowedNames(Sister s, String stage) {
        return switch (s.key()) {
            case "ichika" -> Set.of("futaro-kun");
            case "nino" -> stage.equals("season_1") ? Set.of("futaro") : Set.of("futaro", "fu-kun");
            case "miku" -> Set.of("futaro", "uesugi");         // "Uesugi" = the Sengoku warlord in her history talk
            case "yotsuba" -> Set.of("uesugi-san");
            case "itsuki" -> Set.of("uesugi-kun");
            default -> Set.of();
        };
    }

    static String mainName(Sister s, String stage) {
        return switch (s.key()) {
            case "ichika" -> "Futaro-kun";
            case "nino" -> stage.equals("movie") ? "Fu-kun" : "Futaro";
            case "miku" -> "Futaro";
            case "yotsuba" -> "Uesugi-san";
            case "itsuki" -> "Uesugi-kun";
            default -> "Futaro";
        };
    }

    // ------------------------------------------------------------ patterns

    private static final String NAME_FORMS = "fu-kun|fuu?tarou?(?:-kun|-san)?|uesugi(?:-kun|-san)?";
    private static final Pattern FUTARO_NAME = Pattern.compile(
            "\\b(" + NAME_FORMS + ")(?![\\w-])(?!\\s+(?:kenshin|kagekatsu|clan|family|household|army|forces))",
            Pattern.CASE_INSENSITIVE);
    /** Talking ABOUT him while he is the one she's talking to ("Futaro is...", "Fu-kun's..."). */
    private static final Pattern ABOUT_HIM = Pattern.compile(
            "\\b(fu-kun|fuu?tarou?(?:-kun)?|uesugi-(?:kun|san))(?:'s\\b|\\s+(?:is|was|has|had|does|did|will|would|can|could|"
            + "always|never|really|doesn't|didn't|isn't|wasn't|seems|looks|said|says|thinks|likes|loves|knows|wants|"
            + "might|should|must|told|tells|got|gets|went|came|made|makes|needs|tried|tries)\\b)",
            Pattern.CASE_INSENSITIVE);
    /** Calling the person by a Futaro name (start of a sentence + comma, or at the end). */
    private static final Pattern CALLS_FUTARO = Pattern.compile(
            // "Futaro, ..." / "Oh, Futaro." at a sentence start, "..., Futaro?" at a sentence end, "you're Futaro"
            "(?:^|[.!?]\\s+)(?:(?:oh|hey|hi|hmph|um+|uh+|ah+|so|well|look|listen|wait|huh|geez|jeez)\\s*,?\\s*)?(?:" + NAME_FORMS + ")\\s*[,!?.]"
            + "|,\\s*(?:" + NAME_FORMS + ")\\s*[.!?]"
            + "|\\byou(?:'re| are)\\s+(?:" + NAME_FORMS + ")\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AI_TALK = Pattern.compile(
            "\\b(as an ai|an ai (?:language )?model|language model|ai assistant|i'?m an ai\\b|i am an ai\\b|"
            + "i'?m (?:just )?a (?:chat ?bot|bot|program)|virtual assistant|i'?m an assistant)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE = Pattern.compile("```|(?m)^\\s*(?:def |function |public |import |#include|for \\w+ in )");
    private static final Pattern LIST_LINE = Pattern.compile("(?m)^\\s*(?:[-•]|\\d+[.)])\\s+\\S");
    private static final Pattern SCRIPT = Pattern.compile(
            "(?im)^\\s*(?:ichika|nino|miku|yotsuba|itsuki|futaro|fuutarou|raiha|narrator|user|assistant)\\s*:");
    private static final Pattern WRONG_SISTERS = Pattern.compile(
            "\\bi(?:'ve| have)?(?:\\s+got)?\\s+(one|two|three|five|six|seven|1|2|3|5|6|7)\\s+(?:little |older |younger |other )?sisters?\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MONTH = Pattern.compile(
            "\\b(january|february|march|april|june|july|august|september|october|november|december)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAY_DAY = Pattern.compile("\\bmay\\s+(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAY_5 = Pattern.compile(
            "\\bmay\\s+(?:the\\s+)?(?:5(?:th)?|fifth)\\b|\\b(?:5th|fifth)\\s+of\\s+may\\b|\\b5/5\\b|children'?s day", Pattern.CASE_INSENSITIVE);
    private static final Pattern IS_ELDEST = Pattern.compile(
            "\\b(nino|miku|yotsuba|itsuki)(?:'s|\\s+is)\\s+(?:the\\s+)?(?:eldest|oldest)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern IS_YOUNGEST = Pattern.compile(
            "\\b(ichika|nino|miku|yotsuba)(?:'s|\\s+is)\\s+(?:the\\s+)?youngest\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern I_AM_ORDINAL = Pattern.compile(
            "\\bi'?(?:m| am)\\s+the\\s+(eldest|oldest|youngest|first|second|third|fourth|fifth)\\b", Pattern.CASE_INSENSITIVE);

    // questions (what you asked)
    // only HER birthday ("your cousin's birthday party" used to trigger it: found in the Miku net test, 2026-11-13)
    private static final Pattern Q_BIRTHDAY = Pattern.compile(
            "\\byour birthday\\b|\\bwhen(?:'s| is) (?:your )?birthday\\b|when were you born|\\bborn on\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern Q_SISTERS = Pattern.compile("how many (?:sisters|siblings)", Pattern.CASE_INSENSITIVE);
    private static final Pattern Q_ELDEST = Pattern.compile("\\b(?:eldest|oldest)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern Q_SUBJECT = Pattern.compile("(?:best|favou?rite|strongest|good(?:est)? at|top) subject", Pattern.CASE_INSENSITIVE);
    private static final Pattern Q_DRINK = Pattern.compile("favou?rite drink|what do you (?:like to )?drink", Pattern.CASE_INSENSITIVE);
    // only when you actually ask for your NAME ("Who am I?" can be answered well without it: tested)
    private static final Pattern Q_MY_NAME = Pattern.compile("\\bmy name\\b", Pattern.CASE_INSENSITIVE);

    // leading questions with a wrong fact in them ("You have three sisters, right?")
    private static final Pattern U_SISTERS = Pattern.compile(
            "\\byou(?:'ve| have)?(?:\\s+got)?\\s+(one|two|three|five|six|1|2|3|5|6)\\s+sisters?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern U_ELDEST = Pattern.compile(
            "\\b(nino|miku|yotsuba|itsuki)(?:'s|\\s+is)?\\s+(?:the\\s+)?(?:eldest|oldest)\\b|\\b(?:is|isn't)\\s+(nino|miku|yotsuba|itsuki)\\s+the\\s+(?:eldest|oldest)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern U_YOU_ORDINAL = Pattern.compile(
            "\\byou(?:'re| are)\\s+the\\s+(eldest|oldest|youngest|first|second|third|fourth|fifth)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AGREES = Pattern.compile(
            "^\\W*(yes|yeah|yep|yup|right|that's right|correct|exactly|of course|mm-?hm+|uh-huh|sure|indeed|mhm)\\b", Pattern.CASE_INSENSITIVE);

    // ------------------------------------------------------------ the check

    /**
     * Everything wrong with her reply. Empty list = OK to show.
     * @param prevReply her previous reply in this chat (to catch repeats), may be null
     */
    public static List<Issue> check(Sister s, String stage, boolean friend, String friendName,
                                    String userLine, String reply, String prevReply) {
        List<Issue> out = new ArrayList<>();
        String r = reply == null ? "" : reply.strip();
        String u = userLine == null ? "" : userLine;
        String rl = r.toLowerCase();
        Facts f = FACTS.get(s.key());
        if (r.isEmpty()) return out;

        // ---- staying in character / format
        if (r.contains("*"))
            out.add(new Issue("actions", "used *actions* in her words", "Only say your spoken words, with no *actions*.", true));
        if (r.contains("<|") || r.contains("|>"))
            out.add(new Issue("tokens", "left model tokens in the text", "", true));
        if (AI_TALK.matcher(r).find())
            out.add(new Issue("ai", "talked like an AI", "You are " + s.name() + ", a high school girl, not an AI or an assistant.", false));
        if (CODE.matcher(r).find() || countMatches(LIST_LINE, r) >= 3)
            out.add(new Issue("assistant", "answered like an assistant (code or a list)",
                    "Don't write code or lists. React the way " + s.name() + " would, in one to three short sentences.", false));
        if (SCRIPT.matcher(r).find())
            out.add(new Issue("script", "wrote lines for other speakers", "Only answer as yourself, in one reply.", false));
        if (prevReply != null && norm(r).equals(norm(prevReply)) && norm(r).length() > 3)
            out.add(new Issue("repeat", "repeated her last line", "Say something new; don't repeat your last line.", false));
        if (norm(r).equals(norm(u)) && norm(r).length() > 6)
            out.add(new Issue("echo", "just repeated your words", "Answer instead of repeating what was said.", false));

        // ---- how she calls you
        if (!friend) {
            Set<String> ok = allowedNames(s, stage);
            Set<String> wrong = new LinkedHashSet<>();
            for (String n : names(r)) if (!ok.contains(n)) wrong.add(n);
            if (!wrong.isEmpty())
                out.add(new Issue("name", "called you " + String.join("/", wrong) + " (she says " + mainName(s, stage) + ")",
                        "Call him \"" + mainName(s, stage) + "\".", true));
            if (ABOUT_HIM.matcher(r).find())
                out.add(new Issue("about-him", "talked about you as \"him\" while talking to you",
                        "He is the one you are talking to: say \"you\" to him, not his name with \"is\" or \"he\".", false));
        } else {
            if (CALLS_FUTARO.matcher(r).find())
                out.add(new Issue("friend-name", "called your friend Futaro",
                        "You are talking with your friend" + (friendName == null || friendName.isBlank() ? "" : " " + friendName.strip())
                                + ", not Fuutarou.", false));
            if (friendName != null && !friendName.isBlank() && Q_MY_NAME.matcher(u).find()
                    && !rl.contains(friendName.strip().toLowerCase()))
                out.add(new Issue("friend-unknown", "didn't say your name (" + friendName.strip() + ")",
                        "Your friend's name is " + friendName.strip() + ".", false));
        }

        // ---- facts
        Matcher ws = WRONG_SISTERS.matcher(r);
        if (ws.find())
            out.add(new Issue("sisters", "said she has " + ws.group(1) + " sisters (she has four)", "You have four sisters.", false));
        else if (Q_SISTERS.matcher(u).find() && !rl.matches("(?s).*\\b(four|4)\\b.*"))
            out.add(new Issue("sisters-vague", "didn't say how many sisters (four)", "You have four sisters.", false));

        boolean bdayTalk = Q_BIRTHDAY.matcher(u).find() || rl.contains("birthday") || rl.contains("born on");
        if (bdayTalk) {
            Matcher m = MONTH.matcher(r);
            Matcher d = MAY_DAY.matcher(r);
            boolean wrongDay = false;
            while (d.find()) if (!d.group(1).equals("5")) wrongDay = true;
            if (m.find() || wrongDay)
                out.add(new Issue("birthday", "gave a wrong birthday (it's May 5)", "Your birthday is May 5.", false));
            else if (Q_BIRTHDAY.matcher(u).find() && !MAY_5.matcher(r).find())
                out.add(new Issue("birthday-vague", "didn't give her birthday (May 5)", "Your birthday is May 5.", false));
        }

        Matcher e = IS_ELDEST.matcher(r);
        Matcher y = IS_YOUNGEST.matcher(r);
        if (e.find())
            out.add(new Issue("order", "said " + cap(e.group(1)) + " is the eldest (it's Ichika)", "Ichika is the eldest; Itsuki is the youngest.", false));
        else if (y.find())
            out.add(new Issue("order", "said " + cap(y.group(1)) + " is the youngest (it's Itsuki)", "Ichika is the eldest; Itsuki is the youngest.", false));
        Matcher io = I_AM_ORDINAL.matcher(r);
        while (io.find()) {
            String w = io.group(1).toLowerCase();
            int said = switch (w) { case "eldest", "oldest", "first" -> 1; case "youngest", "fifth" -> 5;
                case "second" -> 2; case "third" -> 3; default -> 4; };
            if (said != f.number()) {
                out.add(new Issue("order", "said she's the " + w + " (she's the " + ORDINAL[f.number()] + ")",
                        "You are the " + ORDINAL[f.number()] + " of the five sisters" + (f.number() == 1 ? ", the eldest." : f.number() == 5 ? ", the youngest." : "."), false));
                break;
            }
        }
        if (Q_ELDEST.matcher(u).find() && out.stream().noneMatch(i -> i.code().equals("order"))) {
            boolean ok = f.number() == 1 ? rl.matches("(?s).*\\b(i am|i'm|me|ichika|big sis)\\b.*") : rl.contains("ichika");
            if (!ok) out.add(new Issue("order-vague", "didn't say who the eldest is (Ichika)", "Ichika is the eldest of the five.", false));
        }

        if (Q_SUBJECT.matcher(u).find()) {
            boolean hers = f.subjectWords().stream().anyMatch(rl::contains);
            if (!hers) {
                boolean other = SUBJECTS.stream().anyMatch(rl::contains);
                out.add(new Issue("subject", (other ? "named the wrong best subject" : "didn't name her best subject") + " (" + f.subject() + ")",
                        "Your best subject is " + f.subject() + ".", false));
            }
        }
        if (s.key().equals("miku") && Q_DRINK.matcher(u).find() && !rl.contains("matcha"))
            out.add(new Issue("drink", "named the wrong favorite drink (matcha soda)", "Your favorite drink is matcha soda.", false));

        // ---- agreeing with a wrong fact in your question
        if (AGREES.matcher(r).find()) {
            if (U_SISTERS.matcher(u).find() && !rl.matches("(?s).*\\b(four|4)\\b.*"))
                out.add(new Issue("agree-sisters", "agreed she has the wrong number of sisters (four)",
                        "You have four sisters; politely correct the mistake.", false));
            boolean bdayQ = u.toLowerCase().contains("birthday") || u.toLowerCase().contains("born");
            if (bdayQ) {
                boolean wrongU = MONTH.matcher(u).find();
                Matcher d = MAY_DAY.matcher(u);
                while (d.find()) if (!d.group(1).equals("5")) wrongU = true;
                if (wrongU && !MAY_5.matcher(r).find())
                    out.add(new Issue("agree-birthday", "agreed with a wrong birthday (May 5)",
                            "Your birthday is May 5; correct the mistake.", false));
            }
            Matcher ue = U_ELDEST.matcher(u);
            if (ue.find() && !rl.contains("ichika") && !(f.number() == 1 && rl.matches("(?s).*\\b(i am|i'm|me)\\b.*")))
                out.add(new Issue("agree-order", "agreed with the wrong eldest (Ichika)",
                        "Ichika is the eldest; correct the mistake.", false));
            Matcher uo = U_YOU_ORDINAL.matcher(u);
            if (uo.find()) {
                String w = uo.group(1).toLowerCase();
                int said = switch (w) { case "eldest", "oldest", "first" -> 1; case "youngest", "fifth" -> 5;
                    case "second" -> 2; case "third" -> 3; default -> 4; };
                if (said != f.number())
                    out.add(new Issue("agree-order", "agreed she's the " + w + " (she's the " + ORDINAL[f.number()] + ")",
                            "You are the " + ORDINAL[f.number()] + " sister; correct the mistake.", false));
            }
        }
        return out;
    }

    /** Problems in the visual cue (line 1). */
    public static List<Issue> checkCue(Sister s, boolean friend, String cue) {
        List<Issue> out = new ArrayList<>();
        if (cue == null || cue.isBlank()) return out;
        String c = cue.toLowerCase();
        for (int k = 1; k <= 5; k++) {
            if (!NAMES[k].equalsIgnoreCase(s.name()) && c.matches("(?s).*\\b" + NAMES[k].toLowerCase() + "\\b.*")) {
                out.add(new Issue("cue-people", "the cue put " + NAMES[k] + " in the scene", "", false));
                break;
            }
        }
        if (c.matches("(?s).*\\b(her sisters?|her twin|twin|glasses|everyone|the others)\\b.*"))
            out.add(new Issue("cue-people", "the cue added people or things that aren't there", "", false));
        if (friend && !names(cue).isEmpty())
            out.add(new Issue("cue-name", "the cue called your friend Futaro", "", false));
        if (CUE_STRANGE.matcher(cue).find())
            out.add(new Issue("cue-strange", "the cue described something creepy or physically strange", "", false));
        return out;
    }

    /** Creepy / gory / impossible body details in a cue (the judge model catches the rest). Everyday knives for bread are fine. */
    private static final Pattern CUE_STRANGE = Pattern.compile(String.join("|",
            "\\bmissing (?:fingers?|toes?|teeth|tooth|eyes?|hands?|arms?|legs?|ears?)\\b",
            "\\b(?:blood\\w*|bleed\\w*|gore|gory|wounds?|corpse|severed|amputat\\w*|decay\\w*|rott\\w*|maggots?|insects? crawl\\w*)\\b",
            "\\bbroken (?:bones?|arms?|legs?|fingers?|neck|wrist)\\b", "\\bbones? (?:stick\\w*|show\\w*|poking)\\b",
            "\\b(?:third|extra|fourth) (?:arm|hand|eye|leg|finger)s?\\b", "\\bsharp teeth\\b", "\\bglow\\w* red\\b",
            "\\bno (?:eyes|face|mouth)\\b", "\\bnot breathing\\b", "\\bpoints? (?:a|the) (?:knife|blade|gun)\\b",
            "\\b(?:knife|blade|gun) (?:at|toward\\w*) (?:him|her|futaro|his|uesugi)\\b",
            "\\b(?:peels?|tears?|rips?) (?:off )?(?:a strip of )?(?:her |his )?skin\\b"), Pattern.CASE_INSENSITIVE);

    private static final String[] PLAIN_CUES = {
            "%1$s glances up at %2$s.", "%1$s tilts her head slightly.", "%1$s looks down for a moment, then back at %2$s.",
            "%1$s pauses, then gives a small nod.", "%1$s meets %2$s's eyes quietly."};
    private static final java.util.Random CUE_RNG = new java.util.Random();

    /** A plain, safe gesture line, used only when every narrated cue was odd. */
    public static String plainCue(String name, String partner) {
        String p = partner == null || partner.isBlank() || partner.equals("Her friend") ? "her friend" : partner;
        return String.format(PLAIN_CUES[CUE_RNG.nextInt(PLAIN_CUES.length)], name, p);
    }

    /** Last-resort fixes that can be done safely without the model (names, *actions*, tokens). */
    public static String autofix(Sister s, String stage, boolean friend, String reply) {
        String r = reply.replaceAll("<\\|[^|]*\\|>", "");
        // drop *actions* (and stray asterisks)
        r = r.replaceAll("\\*[^*]{0,200}\\*", " ").replace("*", "");
        if (!friend) {
            Set<String> ok = allowedNames(s, stage);
            Matcher m = FUTARO_NAME.matcher(r);
            StringBuilder b = new StringBuilder();
            while (m.find()) {
                String found = norm1(m.group(1));
                m.appendReplacement(b, Matcher.quoteReplacement(ok.contains(found) ? m.group(1) : mainName(s, stage)));
            }
            m.appendTail(b);
            r = b.toString();
        }
        return r.replaceAll("[ \\t]{2,}", " ").replaceAll("\\s+([,.!?])", "$1").strip();
    }

    /** Text of the reminder used on the last retry. */
    public static String hint(List<Issue> issues) {
        LinkedHashSet<String> h = new LinkedHashSet<>();
        for (Issue i : issues) if (!i.hint().isBlank()) h.add(i.hint());
        return String.join(" ", h);
    }

    /** Short summary for the badge tooltip. */
    public static String summary(List<Issue> issues) {
        LinkedHashSet<String> h = new LinkedHashSet<>();
        for (Issue i : issues) h.add(i.message());
        return String.join("; ", h);
    }

    // ------------------------------------------------------------ helpers

    static List<String> names(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = FUTARO_NAME.matcher(text);
        while (m.find()) out.add(norm1(m.group(1)));
        return out;
    }

    private static String norm1(String name) { return name.toLowerCase().replaceAll("fuu?tarou?", "futaro"); }

    private static String norm(String s) { return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]+", " ").strip(); }

    private static int countMatches(Pattern p, String s) {
        Matcher m = p.matcher(s);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(); }
}
