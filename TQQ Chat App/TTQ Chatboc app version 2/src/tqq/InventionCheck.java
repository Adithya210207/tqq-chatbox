package tqq;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The "don't make things up" safety net for all five sisters (2026-11-13; version 2 of the app only,
 * version 1 keeps just the word-rule SafetyCheck). It started for Miku (Miku_ryuu_v1 was trained without
 * the "say you're not sure instead of making one up" rule), but a test with the net off showed all five
 * play along with ~13 of 14 made-up events ("You still owe me 500 yen" -> "I'll pay you back soon").
 * Retest with the net on: 69/70 refused, 40/40 real story events kept, 0/70 false alarms.
 *
 * How it works (tested on 72 of her real replies: caught 60/62, 2/10 false alarms):
 *  1. Word rules spot a message that brings up a past event or something she can't know
 *     ("remember when...", "you still owe me", "how's your pet...", "what's my mom's name").
 *     Real anime events (fireworks festival, Kyoto trip, her bread...) and things said earlier in
 *     this chat are skipped, so she never denies something true.
 *  2. Her reply is checked by word rules (did she say it didn't happen / she doesn't remember?)
 *     AND by a small judge model (JUDGE_MODEL, loaded next to her) that answers ALONG or REJECTS.
 *     Either one flagging it means she played along.
 *  3. ChatView then asks her again with a reminder (tested: she denies ~80-90% of the time with it).
 *     If both judges still say she played along after the retries, a short in-character line is used.
 *
 * The judge is optional: without the judge model (or if it can't stay loaded next to her) only the
 * word rules are used.
 */
public final class InventionCheck {
    private InventionCheck() {}

    // 8-combination live test (2026-11-13): Miku v1 + phi4-mini was best (14/14 good final replies, 0 false alarms, fastest)
    public static final String JUDGE_MODEL = "phi4-mini";

    /**
     * All five sisters. Net-off test (2026-11-13): even the four trained with the rule played along with
     * about 13 of 14 made-up events; with the net on, 13-14 of 14 ended in a proper denial.
     */
    public static boolean appliesTo(Sister s) { return true; }

    // ------------------------------------------------------------ 1) what you said

    /** A past event / shared memory / thing she owns or did, stated or assumed in your message. */
    private static final Pattern PAST = Pattern.compile(String.join("|",
            "\\bremember (?:when|that|the time|how|our|we|you|me)\\b",
            "\\bdo you remember\\b",
            "\\bthat time (?:we|you|i)\\b",
            "\\b(?:last|the other) (?:week|night|month|year|summer|winter|spring|fall|autumn|weekend|sunday|saturday|day)\\b",
            "\\byou (?:still )?owe me\\b",
            "\\byou promised\\b",
            "\\b(?:i lent|i gave|i bought|i got) you\\b",
            "\\byou (?:borrowed|lent me|gave me|bought me)\\b",
            "\\bstill (?:mad|angry|upset|sad|annoyed)\\b",
            "\\b(?:sorry|thanks|thank you)(?: again)? (?:for|about|that) .*\\b(?:last|ago|yesterday|again)\\b",
            "\\bthanks again\\b|\\bsorry again\\b",
            "\\bhow(?:'s| is| are| was) your (?:pet|dog|puppy|cat|kitten|hamster|rabbit|bunny|bird|parrot|fish|turtle|"
                    + "boyfriend|girlfriend|ex|band|club|team|part-?time job|grandma|grandmother|grandpa|grandfather|aunt|uncle|cousin|brother)\\b",
            "\\bwhy did you (?:quit|leave|join|stop|dye|cut|break|lose|lie|cry|run away)\\b",
            "\\b(?:i heard|congrats|congratulations)\\b.*\\b(?:won|win|winning|prize|award|contest|competition|tournament|first place|medal)\\b",
            "\\bdid you finish\\b.*\\b(?:i lent|i gave|you borrowed|i recommended)\\b",
            "\\bwhen (?:we|you) (?:went|were|did|got|had|dyed|broke|lost|won|fell|cried|met)\\b.*\\?",
            "\\b(?:our|that) (?:date|trip|vacation|holiday) (?:to|at|in)\\b"), Pattern.CASE_INSENSITIVE);

    /** Questions about you that she can't know. */
    private static final Pattern CANT_KNOW = Pattern.compile(String.join("|",
            "\\bwhat (?:did|do) i (?:dream|eat|have for)\\b",
            "\\bwhat(?:'s| is| was) my (?:mom|mother|dad|father|grandma|grandpa|brother|dog|cat|pet|best friend)(?:'s)? name\\b",
            "\\bwhat am i thinking\\b",
            "\\bwhere (?:did|do) i (?:go|live|work)\\b"), Pattern.CASE_INSENSITIVE);

    /**
     * Real events and things from the story: never treat these as made up. SHARED = what all five lived
     * through; then each sister's own list, from her fact sheet (facts/<Sister>.txt) and her timeline cards
     * (<Sister>_AI/knowledge/personality.json). Added 2026-11-13 after the all-sisters test, where the
     * Miku-only list made Nino deny cooking dinner, Ichika her movie audition, Itsuki leaving her wallet...
     */
    private static final String SHARED = String.join("|",
            "firework", "school trip", "kyoto", "forest school", "ski", "bonfire", "camp",
            "tutor", "lesson", "study (?:session|group)", "exam", "test", "midterm", "quiz", "homework",
            "first met", "we met", "first time we", "library", "apartment", "raiha", "sevens", "ring", "kiss",
            "graduat\\w*", "wedding", "confess\\w*", "sports festival", "school festival", "hinode", "hot spring", "inn",
            "christmas", "valentine", "new year", "cafeteria", "transfer\\w*", "kurobara", "asahiyama", "maruo", "rena",
            "takoyaki", "pancake", "your (?:mom|mother|dad|father|stepfather|sisters?)", "ichika|nino|miku|yotsuba|itsuki");
    private static final java.util.Map<String, String> OWN_CANON = java.util.Map.of(
            "miku", String.join("|", "warlord", "sengoku", "takeda", "uesugi", "nobunaga", "hideyoshi", "ieyasu", "sanada",
                    "date masamune", "matcha", "headphone", "bread", "bak\\w*", "chocolate", "cake", "history"),
            "ichika", String.join("|", "act\\w*", "audition", "movie", "film", "role", "scene", "shoot\\w*", "boss", "rent",
                    "sleep\\w*", "slept", "wake", "woke", "oversle\\w*", "room", "mess\\w*", "earring", "short hair",
                    "dress\\w* (?:up )?as miku", "disguise\\w*", "card"),
            "nino", String.join("|", "cook\\w*", "dinner", "lunch", "breakfast", "meal", "bento", "food", "bak\\w*", "cake",
                    "sweet\\w*", "fashion", "clothes", "outfit", "nail\\w*", "hair", "haircut", "cut your hair", "tea",
                    "drug\\w*", "sleeping pill\\w*", "lock\\w*", "seven goodbyes", "shopping", "love (?:you|me)"),
            "yotsuba", String.join("|", "track", "relay", "run\\w*", "basketball", "drama club", "club", "help\\w*",
                    "committee", "representative", "ribbon", "make-up exam", "makeup exam", "fail\\w*", "collaps\\w*",
                    "sport\\w*", "marathon"),
            "itsuki", String.join("|", "meat bun\\w*", "food", "eat\\w*", "ate", "cram school", "shimoda", "teach\\w*",
                    "ghost\\w*", "scary", "horror", "glasses", "wallet", "fight with nino", "stayed (?:at|with)",
                    "left home", "ran away", "hairpin\\w*", "boat", "lake", "mudo", "raiha-chan"));

    private static final java.util.Map<String, Pattern> CANON = new java.util.concurrent.ConcurrentHashMap<>();
    private static Pattern canon(Sister s) {
        return CANON.computeIfAbsent(s.key(), k -> Pattern.compile(
                "\\b(?:" + SHARED + (OWN_CANON.containsKey(k) ? "|" + OWN_CANON.get(k) : "") + ")(?:s|es|ed|ing)?\\b",
                Pattern.CASE_INSENSITIVE));
    }

    /** Her mother: only the fact sheet (Rena, a teacher, passed away when she was young). */
    private static final Pattern Q_MOM = Pattern.compile("\\b(?:your|ur) (?:mom|mother|mum|mama)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MOM_DETAILS = Pattern.compile(
            "\\b(?:hair|eyes|braid|pigtails?|smell(?:ed|s)?|perfume|cook(?:ed|ing)?|sang|sing|song|lullab|read (?:us|me)|stories|"
                    + "read with|every night|used to|favou?rite|tall|short|glasses|dress|garden|piano|laugh(?:ed)?|hug(?:ged)?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Set<String> STOP = Set.copyOf(List.of("about", "again", "after", "still", "thanks", "thank", "sorry", "remember",
            "there", "their", "these", "those", "would", "could", "should", "really", "yesterday", "something", "when", "where",
            "which", "while", "with", "your", "you're", "that's", "what's", "doing", "going", "together", "today", "tonight",
            "week", "night", "month", "year", "summer", "winter", "spring", "weekend", "other", "first", "because",
            "you", "the", "and", "how", "how's", "did", "was", "for", "are", "what", "why", "who", "that", "this", "have",
            "has", "had", "not", "but", "can", "get", "got", "our", "out", "all", "one", "too", "yet", "now", "its", "it's",
            "i'm", "her", "his", "she", "him", "they", "them", "then", "than", "just", "like", "yes", "yeah", "hey", "day",
            "way", "see", "saw", "still", "owe", "ago", "time", "last", "were", "went", "said", "told", "say", "tell", "know",
            "think", "back", "again", "sure", "really", "very", "much", "some", "any", "from", "into", "over", "off", "lol",
            "miku", "futaro", "these", "days", "doing", "going", "come", "came", "make", "made", "want", "need", "kidding"));

    public enum Kind { NONE, PAST, CANT_KNOW }

    /** Same, with Miku's list of real events (kept for the earlier tests). */
    public static Kind kind(String userLine, String earlierChat, String friendStory) {
        return kind(Sister.ALL.stream().filter(x -> x.key().equals("miku")).findFirst().orElseThrow(), userLine, earlierChat, friendStory);
    }

    /** Does your message bring up something she must not play along with? */
    public static Kind kind(Sister s, String userLine, String earlierChat, String friendStory) {
        String u = userLine == null ? "" : userLine;
        Kind k = CANT_KNOW.matcher(u).find() ? Kind.CANT_KNOW : PAST.matcher(u).find() ? Kind.PAST : Kind.NONE;
        if (k == Kind.PAST) {
            if (canon(s).matcher(u).find()) return Kind.NONE;                    // a real event from her story
            if (mentionedBefore(u, earlierChat) || mentionedBefore(u, friendStory)) return Kind.NONE; // said earlier / in your story
        }
        return k;
    }

    /** True if a telling word of your message (a noun like "hamster", "tickets") was already in the chat. */
    static boolean mentionedBefore(String userLine, String earlier) {
        if (earlier == null || earlier.isBlank()) return false;
        String e = " " + earlier.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9']+", " ") + " ";
        for (String w : userLine.toLowerCase(Locale.ROOT).split("[^a-z0-9']+")) {
            if (w.length() < 3 || STOP.contains(w)) continue;
            String stem = w.endsWith("s") ? w.substring(0, w.length() - 1) : w;
            if (e.contains(" " + stem)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------ 2) her reply

    /** She says it didn't happen / she doesn't have it / doesn't know or remember. */
    private static final Pattern DENY = Pattern.compile(String.join("|",
            "\\bnever\\b", "\\bdid not\\b",
            "\\bdidn'?t (?:happen|do|go|wear|say|have|win|enter|quit|join|borrow|teach|dye|cry|make|lend|buy|get|break|promise|take)\\b",
            "\\bdon'?t (?:have|remember|know|recall|think so|think (?:i|we|that|it|you) (?:did|ever|have|had|was|were|went))\\b",
            "\\bdon'?t even (?:remember|know|have)\\b", "\\bnot remembering\\b", "\\bno idea\\b", "\\bnot sure\\b",
            "\\bhow would i know\\b", "\\bhow should i know\\b", "\\bthere was no\\b", "\\bthere wasn'?t\\b",
            "\\bwhat are you talking about\\b", "\\bwhat do you mean\\b", "\\byou'?re (?:mixing|thinking of|confusing)\\b", "\\bmixed? (?:me|us) up\\b",
            "\\bdreamed? it\\b", "\\bimagin(?:e|ed|ing) (?:it|that|things)\\b", "\\bi haven'?t\\b", "\\bthat wasn'?t me\\b", "\\bnot me\\b",
            "\\bwasn'?t (?:ever )?(?:in|on|there|me)\\b", "\\bwe (?:never|didn'?t|haven'?t)\\b",
            "\\byou (?:never|didn'?t)(?: ever)? (?:told|tell|lent|gave|give|buy|bought|make|made)\\b", "\\byou ever told\\b",
            "\\bi (?:don'?t|do not) own\\b", "\\bwhat \\w+(?: \\w+)?\\?",
            // added after the live test (2026-11-13): good denials the first version missed
            "\\bdon'?t think (?:we|i|you)(?:'ve|'d| have| ever)? ?(?:ever|been|had|did|gone|went|met|done)\\b",
            "\\b(?:got|have) the wrong (?:girl|person|sister|one|idea)\\b", "\\bwrong (?:girl|person|sister)\\b",
            "\\bno such\\b", "\\bi have an? \\w+\\?", "\\bwhat\\?"), Pattern.CASE_INSENSITIVE);

    /** Her mother passed away: catch her talking about her as if she's alive ("Mom's cooking is better"). */
    private static final Pattern MOM_ALIVE = Pattern.compile(
            "\\b(?:my |our )?(?:mom|mother|mama)(?:'s \\w+ (?:is|are|tastes)| is| says| asks| cooks| makes| will| wants| thinks| knows| always)\\b"
                    + "(?! (?:gone|dead|no longer|not here|in heaven))", Pattern.CASE_INSENSITIVE);

    public static boolean denies(String reply) { return reply != null && DENY.matcher(reply).find(); }

    /**
     * For questions she can't know ("What's my dad's name?"), a denial word is not enough: "I've never heard
     * of him... you don't have a dad" contains "never" but still invents. She must say she doesn't know.
     * (Gap found in the 8-combination test, 2026-11-13.)
     */
    private static final Pattern KNOWS_NOT = Pattern.compile(String.join("|",
            "\\bdon'?t know\\b", "\\bdo not know\\b", "\\bno idea\\b", "\\bnot sure\\b", "\\bhow (?:would|should|could|am) i\\b.*\\bknow\\b",
            "\\bcan'?t (?:know|read your mind|tell|guess)\\b", "\\byou (?:never|didn'?t|haven'?t)(?: ever)? (?:told|tell|mention\\w*|said|talk\\w*)\\b",
            "\\bnever (?:heard you|mentioned)\\b", "\\bonly you (?:could|can|would) (?:answer|know)\\b", "\\bi wasn'?t there\\b"),
            Pattern.CASE_INSENSITIVE);
    /** Inventing an answer anyway (a name, "you don't have a dad", "you had eggs..."). */
    private static final Pattern CLAIMS = Pattern.compile(
            "\\byou (?:don'?t have|have no) (?:a |any )?(?:dad|father|mom|mother|parents?|pet|dog|cat)\\b"
                    + "|\\b(?:his|her|its) name (?:is|was)\\b|\\bnamed\\b|\\bis (?:absent|dead|gone)\\b",
            Pattern.CASE_INSENSITIVE);

    static boolean saysNotKnowing(String reply) {
        return reply != null && KNOWS_NOT.matcher(reply).find() && !CLAIMS.matcher(reply).find();
    }

    /** Result of checking one reply. sure = both judges agree she played along. */
    public record Verdict(List<SafetyCheck.Issue> issues, boolean sure) {
        static final Verdict OK = new Verdict(List.of(), false);
    }

    /**
     * @param judgeOn whether the judge model may be used (it is loaded next to her)
     */
    public static Verdict check(Sister s, String partnerName, String userLine, String reply, String earlierChat,
                                String friendStory, boolean judgeOn) {
        if (!appliesTo(s) || reply == null || reply.isBlank()) return Verdict.OK;
        List<SafetyCheck.Issue> out = new ArrayList<>();
        boolean sure = false;
        String who = partnerName == null || partnerName.isBlank() ? "they" : partnerName;
        Kind k = kind(s, userLine, earlierChat, friendStory);
        if (k != Kind.NONE) {
            boolean ruleSays = k == Kind.CANT_KNOW ? !saysNotKnowing(reply) : !denies(reply);
            Boolean judgeSays = judgeOn ? Ollama.judgeAlong(JUDGE_MODEL, partnerName, s.name(), userLine, reply) : null;
            boolean along = ruleSays || Boolean.TRUE.equals(judgeSays);
            if (along) {
                sure = ruleSays && (judgeSays == null || judgeSays);
                String quote = userLine.strip().replace("\"", "'");
                if (k == Kind.CANT_KNOW)
                    out.add(new SafetyCheck.Issue("invent", "made up something she can't know",
                            who + " asked you something you can't know: \"" + quote + "\". Don't make up an answer. "
                                    + "Say you don't know, in your own words.", false));
                else
                    out.add(new SafetyCheck.Issue("invent", "played along with something that never happened",
                            who + " just talked about something that never happened, as far as you know: \"" + quote + "\". "
                                    + "Don't play along and don't make up details. Tell " + (who.equals("they") ? "them" : who)
                                    + " it didn't happen, or that you don't remember it, in your own words.", false));
            }
        }
        if (Q_MOM.matcher(userLine == null ? "" : userLine).find() && MOM_DETAILS.matcher(reply).find()) {
            out.add(new SafetyCheck.Issue("invent-mom", "made up details about her mother",
                    "About your mother, only say what you know: her name was Rena, she was a teacher, and she passed away "
                            + "when you were young. Don't make up other details about her.", false));
            sure = true;
        }
        if (MOM_ALIVE.matcher(reply).find())   // retried with a reminder, never replaced by a fixed line
            out.add(new SafetyCheck.Issue("invent-mom-alive", "talked about her late mother as if she's alive",
                    "Your mother passed away when you were young. Don't talk about her as if she is alive.", false));
        return new Verdict(out, sure);
    }

    /** True for the issue codes that get the reminder on every retry (not only the last one). */
    public static boolean isInvention(SafetyCheck.Issue i) { return i.code().startsWith("invent"); }

    // ------------------------------------------------------------ 3) last resort

    private static final Random RNG = new Random();

    /** A short in-character line, used only if every try still played along. */
    public static String fallback(Kind k, String partnerName, boolean momIssue) {
        String name = partnerName == null || partnerName.isBlank() || partnerName.equals("Her friend") ? "" : ", " + partnerName;
        String[] lines;
        if (momIssue) lines = new String[]{
                "Mom was a teacher. She passed away when we were little... I don't remember much more than that.",
                "She was a teacher. I was young when she passed away, so... I don't want to make things up about her."};
        else if (k == Kind.CANT_KNOW) lines = new String[]{
                "How would I know that" + name + "?", "I don't know. You never told me.", "...I can't read your mind" + name + "."};
        else lines = new String[]{
                "...I don't remember that" + name + ".", "That didn't happen. I'd remember.",
                "Hm? I don't think so. You're mixing me up with someone.", "I don't remember anything like that" + name + "."};
        return lines[RNG.nextInt(lines.length)];
    }
}
