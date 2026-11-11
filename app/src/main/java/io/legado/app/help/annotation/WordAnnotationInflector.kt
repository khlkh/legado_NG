package io.legado.app.help.annotation

import java.util.Locale

/**
 * Debug-only measurement helper, not used by the production analyzer.
 *
 * Used by [WordAnnotationInflectionProbe] to test whether a model item that
 * failed exact matching could be recovered by a conservative forward
 * inflection. Production Word Gloss is exact-surface only; do not call this
 * from the analyzer, the pool resolver, or the renderer.
 *
 * The model is still told to return the exact surface string. This engine
 * expands a known base form (`run`) into candidate forms (`ran`, `running`,
 * `runs`) for measurement. It is our own rule engine: no third-party lemma,
 * frequency, or inflection data is embedded.
 *
 * Deliberately conservative:
 * - English letters only; Chinese and other scripts return an empty list.
 * - Multiword expressions return an empty list; they must match exactly.
 * - Function words and pronouns are not inflected.
 * - Irregular families are explicit; regular words get plural/3rd-person,
 *   past, and -ing forms. Comparatives/superlatives are only generated for the
 *   irregular adjectives (good/bad/far/little/many/much) to avoid false
 *   positives such as `book` -> `booker`.
 * - There is no reverse lemmatization. If the model returns an inflected form,
 *   exact matching is expected to find it; this engine only expands a returned
 *   base form. Every candidate is still validated against the passage and the
 *   word-boundary rules, and exact matching always runs first.
 */
object WordAnnotationInflector {

    private val WORD_ONLY = Regex("^[A-Za-z]+$")

    /** Function words and pronouns must not be turned into other words. */
    private val NON_INFLECTING = setOf(
        "a", "an", "the", "i", "you", "he", "she", "it", "we", "they",
        "me", "him", "her", "us", "them", "my", "your", "his", "its", "our", "their",
        "mine", "yours", "hers", "ours", "theirs", "myself", "yourself", "himself",
        "herself", "itself", "ourselves", "yourselves", "themselves",
        "this", "that", "these", "those", "who", "whom", "whose", "which", "what",
        "and", "or", "but", "if", "because", "as", "than", "then", "so", "not", "no", "yes",
        "of", "to", "in", "on", "at", "by", "for", "with", "from", "about", "into",
        "over", "under", "between", "through", "during", "before", "after", "above", "below",
        "is", "are", "was", "were", "am", "be", "been", "being",
        "do", "does", "did", "done", "doing", "have", "has", "had", "having",
        "will", "would", "can", "could", "shall", "should", "may", "might", "must",
        "there", "here", "when", "where", "why", "how",
        "all", "any", "some", "each", "every", "both", "few", "more", "most",
        "other", "such", "only", "own", "same", "too", "very", "just",
    )

    /**
     * Explicit irregular families. Values contain every form the engine may
     * match, including the base. Adding regular forms for these bases is
     * intentionally suppressed: `run` must not produce the real word `runed`.
     */
    private val IRREGULAR_VERBS: Map<String, List<String>> = mapOf(
        "be" to listOf("be", "am", "is", "are", "was", "were", "been", "being"),
        "bear" to listOf("bear", "bears", "bore", "born", "borne", "bearing"),
        "beat" to listOf("beat", "beats", "beaten", "beating"),
        "become" to listOf("become", "becomes", "became", "becoming"),
        "begin" to listOf("begin", "begins", "began", "begun", "beginning"),
        "bend" to listOf("bend", "bends", "bent", "bending"),
        "bet" to listOf("bet", "bets", "betting"),
        "bid" to listOf("bid", "bids", "bade", "bidden", "bidding"),
        "bind" to listOf("bind", "binds", "bound", "binding"),
        "bite" to listOf("bite", "bites", "bit", "bitten", "biting"),
        "bleed" to listOf("bleed", "bleeds", "bled", "bleeding"),
        "blow" to listOf("blow", "blows", "blew", "blown", "blowing"),
        "break" to listOf("break", "breaks", "broke", "broken", "breaking"),
        "breed" to listOf("breed", "breeds", "bred", "breeding"),
        "bring" to listOf("bring", "brings", "brought", "bringing"),
        "build" to listOf("build", "builds", "built", "building"),
        "burn" to listOf("burn", "burns", "burned", "burnt", "burning"),
        "burst" to listOf("burst", "bursts", "bursting"),
        "buy" to listOf("buy", "buys", "bought", "buying"),
        "cast" to listOf("cast", "casts", "casting"),
        "catch" to listOf("catch", "catches", "caught", "catching"),
        "choose" to listOf("choose", "chooses", "chose", "chosen", "choosing"),
        "cling" to listOf("cling", "clings", "clung", "clinging"),
        "come" to listOf("come", "comes", "came", "coming"),
        "cost" to listOf("cost", "costs", "costing"),
        "creep" to listOf("creep", "creeps", "crept", "creeping"),
        "cut" to listOf("cut", "cuts", "cutting"),
        "deal" to listOf("deal", "deals", "dealt", "dealing"),
        "dig" to listOf("dig", "digs", "dug", "digging"),
        "dive" to listOf("dive", "dives", "dived", "dove", "diving"),
        "do" to listOf("do", "does", "did", "done", "doing"),
        "draw" to listOf("draw", "draws", "drew", "drawn", "drawing"),
        "dream" to listOf("dream", "dreams", "dreamed", "dreamt", "dreaming"),
        "drink" to listOf("drink", "drinks", "drank", "drunk", "drinking"),
        "drive" to listOf("drive", "drives", "drove", "driven", "driving"),
        "eat" to listOf("eat", "eats", "ate", "eaten", "eating"),
        "fall" to listOf("fall", "falls", "fell", "fallen", "falling"),
        "feed" to listOf("feed", "feeds", "fed", "feeding"),
        "feel" to listOf("feel", "feels", "felt", "feeling"),
        "fight" to listOf("fight", "fights", "fought", "fighting"),
        "find" to listOf("find", "finds", "found", "finding"),
        "fit" to listOf("fit", "fits", "fitted", "fitting"),
        "flee" to listOf("flee", "flees", "fled", "fleeing"),
        "fling" to listOf("fling", "flings", "flung", "flinging"),
        "fly" to listOf("fly", "flies", "flew", "flown", "flying"),
        "forbid" to listOf("forbid", "forbids", "forbade", "forbidden", "forbidding"),
        "forecast" to listOf("forecast", "forecasts", "forecasted", "forecasting"),
        "forget" to listOf("forget", "forgets", "forgot", "forgotten", "forgetting"),
        "forgive" to listOf("forgive", "forgives", "forgave", "forgiven", "forgiving"),
        "freeze" to listOf("freeze", "freezes", "froze", "frozen", "freezing"),
        "get" to listOf("get", "gets", "got", "gotten", "getting"),
        "give" to listOf("give", "gives", "gave", "given", "giving"),
        "go" to listOf("go", "goes", "went", "gone", "going"),
        "grind" to listOf("grind", "grinds", "ground", "grinding"),
        "grow" to listOf("grow", "grows", "grew", "grown", "growing"),
        "hang" to listOf("hang", "hangs", "hung", "hanged", "hanging"),
        "have" to listOf("have", "has", "had", "having"),
        "hear" to listOf("hear", "hears", "heard", "hearing"),
        "hide" to listOf("hide", "hides", "hid", "hidden", "hiding"),
        "hit" to listOf("hit", "hits", "hitting"),
        "hold" to listOf("hold", "holds", "held", "holding"),
        "hurt" to listOf("hurt", "hurts", "hurting"),
        "keep" to listOf("keep", "keeps", "kept", "keeping"),
        "kneel" to listOf("kneel", "kneels", "knelt", "kneeled", "kneeling"),
        "knit" to listOf("knit", "knits", "knitted", "knitting"),
        "know" to listOf("know", "knows", "knew", "known", "knowing"),
        "lay" to listOf("lay", "lays", "laid", "laying"),
        "lead" to listOf("lead", "leads", "led", "leading"),
        "leap" to listOf("leap", "leaps", "leaped", "leapt", "leaping"),
        "learn" to listOf("learn", "learns", "learned", "learnt", "learning"),
        "leave" to listOf("leave", "leaves", "left", "leaving"),
        "lend" to listOf("lend", "lends", "lent", "lending"),
        "let" to listOf("let", "lets", "letting"),
        "lie" to listOf("lie", "lies", "lay", "lain", "lied", "lying"),
        "light" to listOf("light", "lights", "lit", "lighted", "lighting"),
        "lose" to listOf("lose", "loses", "lost", "losing"),
        "make" to listOf("make", "makes", "made", "making"),
        "mean" to listOf("mean", "means", "meant", "meaning"),
        "meet" to listOf("meet", "meets", "met", "meeting"),
        "mistake" to listOf("mistake", "mistakes", "mistook", "mistaken", "mistaking"),
        "mow" to listOf("mow", "mows", "mowed", "mown", "mowing"),
        "overcome" to listOf("overcome", "overcomes", "overcame", "overcoming"),
        "pay" to listOf("pay", "pays", "paid", "paying"),
        "plead" to listOf("plead", "pleads", "pleaded", "pled", "pleading"),
        "prove" to listOf("prove", "proves", "proved", "proven", "proving"),
        "put" to listOf("put", "puts", "putting"),
        "quit" to listOf("quit", "quits", "quitting"),
        "read" to listOf("read", "reads", "reading"),
        "rid" to listOf("rid", "rids", "ridding"),
        "ride" to listOf("ride", "rides", "rode", "ridden", "riding"),
        "ring" to listOf("ring", "rings", "rang", "rung", "ringing"),
        "rise" to listOf("rise", "rises", "rose", "risen", "rising"),
        "run" to listOf("run", "runs", "ran", "running"),
        "say" to listOf("say", "says", "said", "saying"),
        "see" to listOf("see", "sees", "saw", "seen", "seeing"),
        "seek" to listOf("seek", "seeks", "sought", "seeking"),
        "sell" to listOf("sell", "sells", "sold", "selling"),
        "send" to listOf("send", "sends", "sent", "sending"),
        "set" to listOf("set", "sets", "setting"),
        "sew" to listOf("sew", "sews", "sewed", "sewn", "sewing"),
        "shake" to listOf("shake", "shakes", "shook", "shaken", "shaking"),
        "shear" to listOf("shear", "shears", "sheared", "shorn", "shearing"),
        "shed" to listOf("shed", "sheds", "shedding"),
        "shine" to listOf("shine", "shines", "shone", "shined", "shining"),
        "shoot" to listOf("shoot", "shoots", "shot", "shooting"),
        "show" to listOf("show", "shows", "showed", "shown", "showing"),
        "shut" to listOf("shut", "shuts", "shutting"),
        "sing" to listOf("sing", "sings", "sang", "sung", "singing"),
        "sink" to listOf("sink", "sinks", "sank", "sunk", "sinking"),
        "sit" to listOf("sit", "sits", "sat", "sitting"),
        "sleep" to listOf("sleep", "sleeps", "slept", "sleeping"),
        "slide" to listOf("slide", "slides", "slid", "sliding"),
        "sling" to listOf("sling", "slings", "slung", "slinging"),
        "smell" to listOf("smell", "smells", "smelled", "smelt", "smelling"),
        "sow" to listOf("sow", "sows", "sowed", "sown", "sowing"),
        "speak" to listOf("speak", "speaks", "spoke", "spoken", "speaking"),
        "speed" to listOf("speed", "speeds", "sped", "speeded", "speeding"),
        "spell" to listOf("spell", "spells", "spelled", "spelt", "spelling"),
        "spend" to listOf("spend", "spends", "spent", "spending"),
        "spill" to listOf("spill", "spills", "spilled", "spilt", "spilling"),
        "spin" to listOf("spin", "spins", "spun", "spinning"),
        "spit" to listOf("spit", "spits", "spat", "spitting"),
        "split" to listOf("split", "splits", "splitting"),
        "spoil" to listOf("spoil", "spoils", "spoiled", "spoilt", "spoiling"),
        "spread" to listOf("spread", "spreads", "spreading"),
        "spring" to listOf("spring", "springs", "sprang", "sprung", "springing"),
        "stand" to listOf("stand", "stands", "stood", "standing"),
        "steal" to listOf("steal", "steals", "stole", "stolen", "stealing"),
        "stick" to listOf("stick", "sticks", "stuck", "sticking"),
        "sting" to listOf("sting", "stings", "stung", "stinging"),
        "stink" to listOf("stink", "stinks", "stank", "stunk", "stinking"),
        "stride" to listOf("stride", "strides", "strode", "stridden", "striding"),
        "strike" to listOf("strike", "strikes", "struck", "stricken", "striking"),
        "string" to listOf("string", "strings", "strung", "stringing"),
        "strive" to listOf("strive", "strives", "strove", "striven", "striving"),
        "swear" to listOf("swear", "swears", "swore", "sworn", "swearing"),
        "sweep" to listOf("sweep", "sweeps", "swept", "sweeping"),
        "swell" to listOf("swell", "swells", "swelled", "swollen", "swelling"),
        "swim" to listOf("swim", "swims", "swam", "swum", "swimming"),
        "swing" to listOf("swing", "swings", "swung", "swinging"),
        "take" to listOf("take", "takes", "took", "taken", "taking"),
        "teach" to listOf("teach", "teaches", "taught", "teaching"),
        "tear" to listOf("tear", "tears", "tore", "torn", "tearing"),
        "tell" to listOf("tell", "tells", "told", "telling"),
        "think" to listOf("think", "thinks", "thought", "thinking"),
        "throw" to listOf("throw", "throws", "threw", "thrown", "throwing"),
        "thrust" to listOf("thrust", "thrusts", "thrusting"),
        "tread" to listOf("tread", "treads", "trod", "trodden", "treading"),
        "undergo" to listOf("undergo", "undergoes", "underwent", "undergone", "undergoing"),
        "understand" to listOf("understand", "understands", "understood", "understanding"),
        "undertake" to listOf("undertake", "undertakes", "undertook", "undertaken", "undertaking"),
        "upset" to listOf("upset", "upsets", "upsetting"),
        "wake" to listOf("wake", "wakes", "woke", "woken", "waked", "waking"),
        "wear" to listOf("wear", "wears", "wore", "worn", "wearing"),
        "weave" to listOf("weave", "weaves", "wove", "woven", "weaving"),
        "weep" to listOf("weep", "weeps", "wept", "weeping"),
        "win" to listOf("win", "wins", "won", "winning"),
        "wind" to listOf("wind", "winds", "wound", "winding"),
        "withdraw" to listOf("withdraw", "withdraws", "withdrew", "withdrawn", "withdrawing"),
        "wring" to listOf("wring", "wrings", "wrung", "wringing"),
        "write" to listOf("write", "writes", "wrote", "written", "writing"),
    )

    private val IRREGULAR_NOUNS: Map<String, List<String>> = mapOf(
        "child" to listOf("child", "children"),
        "man" to listOf("man", "men"),
        "woman" to listOf("woman", "women"),
        "person" to listOf("person", "people", "persons"),
        "mouse" to listOf("mouse", "mice"),
        "goose" to listOf("goose", "geese"),
        "foot" to listOf("foot", "feet"),
        "tooth" to listOf("tooth", "teeth"),
        "ox" to listOf("ox", "oxen"),
        "die" to listOf("die", "dice", "dies", "died", "dying"),
        "penny" to listOf("penny", "pence", "pennies"),
        "leaf" to listOf("leaf", "leaves"),
        "life" to listOf("life", "lives"),
        "wife" to listOf("wife", "wives"),
        "knife" to listOf("knife", "knives"),
        "wolf" to listOf("wolf", "wolves"),
        "shelf" to listOf("shelf", "shelves"),
        "half" to listOf("half", "halves"),
        "self" to listOf("self", "selves"),
        "thief" to listOf("thief", "thieves"),
        "loaf" to listOf("loaf", "loaves"),
        "calf" to listOf("calf", "calves"),
        "elf" to listOf("elf", "elves"),
        "scarf" to listOf("scarf", "scarves", "scarfs"),
        "hoof" to listOf("hoof", "hooves", "hoofs"),
        "crisis" to listOf("crisis", "crises"),
        "analysis" to listOf("analysis", "analyses"),
        "basis" to listOf("basis", "bases"),
        "thesis" to listOf("thesis", "theses"),
        "diagnosis" to listOf("diagnosis", "diagnoses"),
        "hypothesis" to listOf("hypothesis", "hypotheses"),
        "oasis" to listOf("oasis", "oases"),
        "parenthesis" to listOf("parenthesis", "parentheses"),
        "synopsis" to listOf("synopsis", "synopses"),
        "axis" to listOf("axis", "axes"),
        "criterion" to listOf("criterion", "criteria"),
        "phenomenon" to listOf("phenomenon", "phenomena"),
        "datum" to listOf("datum", "data"),
        "medium" to listOf("medium", "media"),
        "curriculum" to listOf("curriculum", "curricula", "curriculums"),
        "memorandum" to listOf("memorandum", "memoranda", "memorandums"),
        "bacterium" to listOf("bacterium", "bacteria"),
        "stratum" to listOf("stratum", "strata"),
        "symposium" to listOf("symposium", "symposia", "symposiums"),
        "millennium" to listOf("millennium", "millennia", "millenniums"),
        "spectrum" to listOf("spectrum", "spectra", "spectrums"),
        "referendum" to listOf("referendum", "referenda", "referendums"),
        "index" to listOf("index", "indices", "indexes", "indexed", "indexing"),
        "matrix" to listOf("matrix", "matrices"),
        "vertex" to listOf("vertex", "vertices"),
        "appendix" to listOf("appendix", "appendices", "appendixes"),
        "cactus" to listOf("cactus", "cacti", "cactuses"),
        "fungus" to listOf("fungus", "fungi", "funguses"),
        "nucleus" to listOf("nucleus", "nuclei", "nucleuses"),
        "radius" to listOf("radius", "radii", "radiuses"),
        "stimulus" to listOf("stimulus", "stimuli"),
        "syllabus" to listOf("syllabus", "syllabi", "syllabuses"),
        "alumnus" to listOf("alumnus", "alumni"),
        "focus" to listOf("focus", "foci", "focuses", "focused", "focusing", "focussed", "focussing"),
        "formula" to listOf("formula", "formulae", "formulas"),
        "antenna" to listOf("antenna", "antennae", "antennas"),
        "sheep" to listOf("sheep"),
        "deer" to listOf("deer"),
        "fish" to listOf("fish", "fishes", "fished", "fishing"),
        "series" to listOf("series"),
        "species" to listOf("species"),
        "aircraft" to listOf("aircraft"),
        "offspring" to listOf("offspring"),
        "salmon" to listOf("salmon"),
        "trout" to listOf("trout"),
        "swine" to listOf("swine"),
        "means" to listOf("means"),
        "headquarters" to listOf("headquarters"),
        "crossroads" to listOf("crossroads"),
        "barracks" to listOf("barracks"),
    )

    private val IRREGULAR_ADJECTIVES: Map<String, List<String>> = mapOf(
        "good" to listOf("good", "better", "best"),
        "bad" to listOf("bad", "worse", "worst"),
        "far" to listOf("far", "farther", "further", "farthest", "furthest"),
        "little" to listOf("little", "less", "least"),
        "many" to listOf("many", "more", "most"),
        "much" to listOf("much", "more", "most"),
        "well" to listOf("well", "better", "best"),
    )

    private val IRREGULAR: Map<String, List<String>> =
        IRREGULAR_VERBS + IRREGULAR_NOUNS + IRREGULAR_ADJECTIVES

    private val VOWELS = setOf('a', 'e', 'i', 'o', 'u')

    /**
     * Candidate surface forms for [base], most useful first. Returns an empty
     * list when this engine should not try to inflect [base].
     */
    fun candidateForms(base: String): List<String> {
        val trimmed = base.trim()
        if (trimmed.length < 2 || !WORD_ONLY.matches(trimmed)) return emptyList()
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower in NON_INFLECTING) return emptyList()

        val family = IRREGULAR[lower] ?: regularForms(lower)
        val out = LinkedHashSet<String>()
        for (form in family) {
            out += caseVariants(form)
        }
        out += caseVariants(trimmed)
        return out.sortedWith(compareByDescending<String> { it.length }.thenBy { it })
    }

    private fun regularForms(base: String): Set<String> {
        val forms = LinkedHashSet<String>()
        forms += base

        // plural noun / third-person singular verb
        when {
            base.endsWith("s") || base.endsWith("x") || base.endsWith("z") ||
                base.endsWith("ch") || base.endsWith("sh") -> forms += base + "es"
            endsConsonantY(base) -> forms += base.dropLast(1) + "ies"
            base.endsWith("o") -> {
                forms += base + "es"
                forms += base + "s"
            }
            else -> forms += base + "s"
        }

        // past tense
        when {
            base.endsWith("e") -> forms += base + "d"
            endsConsonantY(base) -> forms += base.dropLast(1) + "ied"
            base.endsWith("c") -> forms += base + "ked"
            else -> {
                forms += base + "ed"
                forms += doubled(base) + "ed"
            }
        }

        // present participle
        when {
            base.endsWith("ie") -> forms += base.dropLast(2) + "ying"
            base.endsWith("e") && !base.endsWith("ee") && !base.endsWith("ye") && !base.endsWith("oe") -> {
                forms += base.dropLast(1) + "ing"
                forms += base + "ing"
            }
            base.endsWith("c") -> forms += base + "king"
            else -> {
                forms += base + "ing"
                forms += doubled(base) + "ing"
            }
        }

        return forms
    }

    private fun endsConsonantY(word: String): Boolean =
        word.length > 1 && word.endsWith("y") && word[word.length - 2] !in VOWELS

    /** CVC ending such as `stop` -> `stopp`; used for stopped/stopping. */
    private fun doubled(word: String): String {
        if (word.length < 3) return word
        val c1 = word[word.length - 3]
        val v = word[word.length - 2]
        val c2 = word[word.length - 1]
        if (c2 == 'w' || c2 == 'x' || c2 == 'y') return word
        if (c1 in VOWELS || v !in VOWELS || c2 in VOWELS) return word
        return word + c2
    }

    private fun caseVariants(form: String): List<String> {
        val lower = form.lowercase(Locale.ROOT)
        if (lower.length == 1) return listOf(lower)
        val capitalized = lower.replaceFirstChar { it.uppercase(Locale.ROOT) }
        return if (capitalized == lower) listOf(lower) else listOf(lower, capitalized)
    }
}
