// @name Next Edge TTS
// @schema 1
// @version 1.0.11
// @uuid next_edge_proxy
// @author Legado
// @url http://5.45.99.149:8075/tts
// @enabled true
// @cookieJar false
// @audioType audio/mpeg
// @defaultSpeed 50
// @defaultVolume 50
// @defaultPitch 50
// @sampleText 前不见古人，后不见来者。念天地之悠悠，独怆然而涕下。
// @capabilities style_tags,emotion,synthesis_speed,synthesis_pitch
// @description 第三方 Edge TTS 中转调试模板，发音人画像和 styles 参考 Edge VoiceTag 与 Azure Speech 文档。

var STYLE_NAMES;

function styleNames() {
    if (STYLE_NAMES) {
        return STYLE_NAMES;
    }
    STYLE_NAMES = {
    "advertisement-upbeat": "广告活力",
    "affectionate": "深情",
    "angry": "愤怒",
    "anxious": "焦虑",
    "assassin": "刺客",
    "assistant": "助手",
    "calm": "平静",
    "captain": "队长",
    "cavalier": "骑士",
    "chat": "聊天",
    "chat-casual": "随意聊天",
    "cheerful": "欢快",
    "comforting": "安慰",
    "complaining": "抱怨",
    "curious": "好奇",
    "customer-service": "客户服务",
    "customerservice": "客户服务",
    "cute": "可爱",
    "debating": "辩论",
    "depressed": "沮丧",
    "disappointed": "失望",
    "disgruntled": "不满",
    "documentary-narration": "纪录片旁白",
    "empathetic": "共情",
    "embarrassed": "尴尬",
    "encouraging": "鼓励",
    "envious": "羡慕",
    "excited": "兴奋",
    "fearful": "恐惧",
    "friendly": "友好",
    "game-narrator": "游戏旁白",
    "gentle": "温柔",
    "geomancer": "方士",
    "guilty": "愧疚",
    "live-commercial": "直播带货",
    "livecommercial": "直播带货",
    "lonely": "孤独",
    "lyrical": "抒情",
    "narration-professional": "专业旁白",
    "narration-relaxed": "舒缓旁白",
    "newscast": "新闻播报",
    "newscast-casual": "轻松新闻",
    "news": "新闻",
    "poet": "诗人",
    "poetry-reading": "诗歌朗诵",
    "prince": "王子",
    "sad": "悲伤",
    "sentimental": "感伤",
    "serious": "严肃",
    "shy": "害羞",
    "sorry": "歉意",
    "sports-commentary": "体育解说",
    "sports-commentary-excited": "兴奋体育解说",
    "story": "故事",
    "story-telling": "讲故事",
    "strict": "严厉",
    "surprised": "惊讶",
    "tired": "疲惫",
    "voice-assistant": "语音助手",
    "whispering": "耳语"
    };
    return STYLE_NAMES;
}

var EXPRESSIVE_STYLE_ALIASES;

function expressiveStyleAliases() {
    if (EXPRESSIVE_STYLE_ALIASES) {
        return EXPRESSIVE_STYLE_ALIASES;
    }
    EXPRESSIVE_STYLE_ALIASES = [
    { style: "angry", terms: ["angry", "愤怒", "生气", "恼怒", "暴怒"] },
    { style: "anxious", terms: ["anxious", "焦虑", "紧张", "不安"] },
    { style: "calm", terms: ["calm", "平静", "克制", "冷静", "沉着"] },
    { style: "cheerful", terms: ["cheerful", "欢快", "开心", "喜悦", "活泼"] },
    { style: "comforting", terms: ["comforting", "安慰", "宽慰"] },
    { style: "curious", terms: ["curious", "好奇", "疑惑"] },
    { style: "depressed", terms: ["depressed", "沮丧", "低落", "压抑"] },
    { style: "disappointed", terms: ["disappointed", "失望"] },
    { style: "embarrassed", terms: ["embarrassed", "尴尬", "窘迫"] },
    { style: "empathetic", terms: ["empathetic", "共情", "同情"] },
    { style: "excited", terms: ["excited", "兴奋", "激动", "高昂"] },
    { style: "fearful", terms: ["fearful", "恐惧", "害怕", "惊恐"] },
    { style: "gentle", terms: ["gentle", "温柔", "柔和", "轻柔"] },
    { style: "sad", terms: ["sad", "悲伤", "难过", "哀伤"] },
    { style: "serious", terms: ["serious", "严肃", "郑重", "庄重"] },
    { style: "shy", terms: ["shy", "害羞", "羞涩"] },
    { style: "sorry", terms: ["sorry", "歉意", "抱歉", "愧疚"] },
    { style: "surprised", terms: ["surprised", "惊讶", "震惊", "意外"] },
    { style: "whispering", terms: ["whispering", "耳语", "低语", "悄声"] },
    { style: "newscast", terms: ["newscast", "新闻播报", "播报"] },
    { style: "poetry-reading", terms: ["poetry-reading", "诗歌朗诵", "朗诵"] },
    { style: "story", terms: ["story", "故事", "讲故事", "叙事"] },
    { style: "story-telling", terms: ["story-telling", "故事", "讲故事", "叙事"] }
    ];
    return EXPRESSIVE_STYLE_ALIASES;
}

var API_OPTIONS = [
    { label: "5.45.99.149", value: "http://5.45.99.149:8075/tts" },
    { label: "146.56.188.115", value: "http://146.56.188.115:8080/tts" }
];

var VOICES;

function englishVoiceCatalog() {
    return [
        { name: "Ava Dragon HD Latest", id: "en-US-Ava:DragonHDLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Andrew Dragon HD Latest", id: "en-US-Andrew:DragonHDLatestNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Adam Dragon HD Latest", id: "en-US-Adam:DragonHDLatestNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Alloy Dragon HD Latest", id: "en-US-Alloy:DragonHDLatestNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Aria Dragon HD Latest", id: "en-US-Aria:DragonHDLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Bree Dragon HD Latest", id: "en-US-Bree:DragonHDLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Brian Dragon HD Latest", id: "en-US-Brian:DragonHDLatestNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Davis Dragon HD Latest", id: "en-US-Davis:DragonHDLatestNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Emma Dragon HD Latest", id: "en-US-Emma:DragonHDLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Jane Dragon HD Latest", id: "en-US-Jane:DragonHDLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Jenny Dragon HD Latest", id: "en-US-Jenny:DragonHDLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Nova Dragon HD Latest", id: "en-US-Nova:DragonHDLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Phoebe Dragon HD Latest", id: "en-US-Phoebe:DragonHDLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Serena Dragon HD Latest", id: "en-US-Serena:DragonHDLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Steffan Dragon HD Latest", id: "en-US-Steffan:DragonHDLatestNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Andrew Dragon HD Omni Latest", id: "en-US-Andrew:DragonHDOmniLatestNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Brian Dragon HD Omni Latest", id: "en-US-Brian:DragonHDOmniLatestNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Caleb Dragon HD Omni Latest", id: "en-US-Caleb:DragonHDOmniLatestNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Dana Dragon HD Omni Latest", id: "en-US-Dana:DragonHDOmniLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Lewis Dragon HD Omni Latest", id: "en-US-Lewis:DragonHDOmniLatestNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Phoebe Dragon HD Omni Latest", id: "en-US-Phoebe:DragonHDOmniLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Ava Multilingual", id: "en-US-AvaMultilingualNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Andrew Multilingual", id: "en-US-AndrewMultilingualNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: ["empathetic", "relieved"], roles: [], roleHints: [] },
        { name: "Amanda Multilingual", id: "en-US-AmandaMultilingualNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Adam Multilingual", id: "en-US-AdamMultilingualNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Emma Multilingual", id: "en-US-EmmaMultilingualNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Phoebe Multilingual", id: "en-US-PhoebeMultilingualNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: ["empathetic", "sad", "serious"], roles: [], roleHints: [] },
        { name: "Alloy Turbo Multilingual", id: "en-US-AlloyTurboMultilingualNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Echo Turbo Multilingual", id: "en-US-EchoTurboMultilingualNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Fable Turbo Multilingual", id: "en-US-FableTurboMultilingualNeural", gender: "neutral", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Onyx Turbo Multilingual", id: "en-US-OnyxTurboMultilingualNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Nova Turbo Multilingual", id: "en-US-NovaTurboMultilingualNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Shimmer Turbo Multilingual", id: "en-US-ShimmerTurboMultilingualNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Brian Multilingual", id: "en-US-BrianMultilingualNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Ava", id: "en-US-AvaNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: ["angry", "fearful", "sad"], roles: [], roleHints: [] },
        { name: "Andrew", id: "en-US-AndrewNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Emma", id: "en-US-EmmaNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Brian", id: "en-US-BrianNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Jenny", id: "en-US-JennyNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: ["assistant", "chat", "customerservice", "newscast", "angry", "cheerful", "sad", "excited", "friendly", "terrified", "shouting", "unfriendly", "whispering", "hopeful"], roles: [], roleHints: [] },
        { name: "Guy", id: "en-US-GuyNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: ["newscast", "angry", "cheerful", "sad", "excited", "friendly", "terrified", "shouting", "unfriendly", "whispering", "hopeful"], roles: [], roleHints: [] },
        { name: "Aria", id: "en-US-AriaNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: ["chat", "customerservice", "narration-professional", "newscast-casual", "newscast-formal", "cheerful", "empathetic", "angry", "sad", "excited", "friendly", "terrified", "shouting", "unfriendly", "whispering", "hopeful"], roles: [], roleHints: [] },
        { name: "Davis", id: "en-US-DavisNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: ["chat", "angry", "cheerful", "excited", "friendly", "hopeful", "sad", "shouting", "terrified", "unfriendly", "whispering"], roles: [], roleHints: [] },
        { name: "Jane", id: "en-US-JaneNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: ["angry", "cheerful", "excited", "friendly", "hopeful", "sad", "shouting", "terrified", "unfriendly", "whispering"], roles: [], roleHints: [] },
        { name: "Jason", id: "en-US-JasonNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: ["angry", "cheerful", "excited", "friendly", "hopeful", "sad", "shouting", "terrified", "unfriendly", "whispering"], roles: [], roleHints: [] },
        { name: "Kai", id: "en-US-KaiNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: ["conversation"], roles: [], roleHints: [] },
        { name: "Luna", id: "en-US-LunaNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: ["conversation"], roles: [], roleHints: [] },
        { name: "Sara", id: "en-US-SaraNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: ["angry", "cheerful", "excited", "friendly", "hopeful", "sad", "shouting", "terrified", "unfriendly", "whispering"], roles: [], roleHints: [] },
        { name: "Tony", id: "en-US-TonyNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: ["angry", "cheerful", "excited", "friendly", "hopeful", "sad", "shouting", "terrified", "unfriendly", "whispering"], roles: [], roleHints: [] },
        { name: "Nancy", id: "en-US-NancyNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: ["angry", "cheerful", "excited", "friendly", "hopeful", "sad", "shouting", "terrified", "unfriendly", "whispering"], roles: [], roleHints: [] },
        { name: "Cora Multilingual", id: "en-US-CoraMultilingualNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Christopher Multilingual", id: "en-US-ChristopherMultilingualNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Brandon Multilingual", id: "en-US-BrandonMultilingualNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "AIGenerate1", id: "en-US-AIGenerate1Neural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "AIGenerate2", id: "en-US-AIGenerate2Neural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Amber", id: "en-US-AmberNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Ana", id: "en-US-AnaNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Ashley", id: "en-US-AshleyNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Brandon", id: "en-US-BrandonNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Christopher", id: "en-US-ChristopherNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Cora", id: "en-US-CoraNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Davis Multilingual", id: "en-US-DavisMultilingualNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: ["empathetic", "funny", "relieved"], roles: [], roleHints: [] },
        { name: "Derek Multilingual", id: "en-US-DerekMultilingualNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: ["empathetic", "excited", "relieved", "shy"], roles: [], roleHints: [] },
        { name: "Dustin Multilingual", id: "en-US-DustinMultilingualNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Elizabeth", id: "en-US-ElizabethNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Eric", id: "en-US-EricNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Evelyn Multilingual", id: "en-US-EvelynMultilingualNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Jacob", id: "en-US-JacobNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Jenny Multilingual", id: "en-US-JennyMultilingualNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Jimmie Dragon HD Flash Latest", id: "en-US-Jimmie:DragonHDFlashLatestNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Lewis Multilingual", id: "en-US-LewisMultilingualNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Lola Multilingual", id: "en-US-LolaMultilingualNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Michelle", id: "en-US-MichelleNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Monica", id: "en-US-MonicaNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Nancy Multilingual", id: "en-US-NancyMultilingualNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: ["excited", "friendly", "funny", "relieved", "shy"], roles: [], roleHints: [] },
        { name: "Roger", id: "en-US-RogerNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Ryan Multilingual", id: "en-US-RyanMultilingualNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Samuel Multilingual", id: "en-US-SamuelMultilingualNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Serena Multilingual", id: "en-US-SerenaMultilingualNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: ["empathetic", "excited", "friendly", "shy", "serious", "relieved", "sad"], roles: [], roleHints: [] },
        { name: "Steffan Multilingual", id: "en-US-SteffanMultilingualNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Steffan", id: "en-US-SteffanNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Tiana Dragon HD Flash Latest", id: "en-US-Tiana:DragonHDFlashLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Tyler Dragon HD Flash Latest", id: "en-US-Tyler:DragonHDFlashLatestNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Andrew Dragon Latest", id: "en-US-Andrew:DragonLatestNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Ash Turbo Multilingual", id: "en-US-AshTurboMultilingualNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Ava Dragon Latest", id: "en-US-Ava:DragonLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Blue", id: "en-US-BlueNeural", gender: "neutral", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Ethan MAI-Voice-2-Flash", id: "en-US-Ethan:MAI-Voice-2-Flash", gender: "male", profile: "EN US", categories: [], personalities: [], styles: ["angry", "confused", "determined", "disgusted", "embarrassed", "excited", "fearful", "happy", "hopeful", "jealous", "joyful", "regretful", "relieved", "sad", "shouting", "softvoice", "surprised", "whispering"], roles: [], roleHints: [] },
        { name: "Ethan MAI-Voice-2.1-Flash", id: "en-US-Ethan:MAI-Voice-2.1-Flash", gender: "male", profile: "EN US", categories: [], personalities: [], styles: ["angry", "confused", "determined", "disgusted", "embarrassed", "excited", "fearful", "happy", "hopeful", "jealous", "joyful", "regretful", "relieved", "sad", "shouting", "softvoice", "surprised", "whispering"], roles: [], roleHints: [] },
        { name: "Grant MAI-Voice-2-Flash", id: "en-US-Grant:MAI-Voice-2-Flash", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Grant MAI-Voice-2.1-Flash", id: "en-US-Grant:MAI-Voice-2.1-Flash", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Harper MAI-Voice-2-Flash", id: "en-US-Harper:MAI-Voice-2-Flash", gender: "female", profile: "EN US", categories: [], personalities: [], styles: ["angry", "confused", "determined", "embarrassed", "excited", "happy", "hopeful", "joyful", "regretful", "relieved", "sad", "shouting", "softvoice", "whispering"], roles: [], roleHints: [] },
        { name: "Harper MAI-Voice-2.1-Flash", id: "en-US-Harper:MAI-Voice-2.1-Flash", gender: "female", profile: "EN US", categories: [], personalities: [], styles: ["angry", "confused", "determined", "embarrassed", "excited", "happy", "hopeful", "joyful", "regretful", "relieved", "sad", "shouting", "softvoice", "whispering"], roles: [], roleHints: [] },
        { name: "Iris MAI-Voice-2-Flash", id: "en-US-Iris:MAI-Voice-2-Flash", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Iris MAI-Voice-2.1-Flash", id: "en-US-Iris:MAI-Voice-2.1-Flash", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Jasper MAI-Voice-2-Flash", id: "en-US-Jasper:MAI-Voice-2-Flash", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Jasper MAI-Voice-2.1-Flash", id: "en-US-Jasper:MAI-Voice-2.1-Flash", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Olivia MAI-Voice-2-Flash", id: "en-US-Olivia:MAI-Voice-2-Flash", gender: "female", profile: "EN US", categories: [], personalities: [], styles: ["angry", "confused", "determined", "disgusted", "embarrassed", "excited", "fearful", "happy", "hopeful", "jealous", "joyful", "regretful", "relieved", "sad", "shouting", "softvoice", "surprised", "whispering"], roles: [], roleHints: [] },
        { name: "Olivia MAI-Voice-2.1-Flash", id: "en-US-Olivia:MAI-Voice-2.1-Flash", gender: "female", profile: "EN US", categories: [], personalities: [], styles: ["angry", "confused", "determined", "disgusted", "embarrassed", "excited", "fearful", "happy", "hopeful", "jealous", "joyful", "regretful", "relieved", "sad", "shouting", "softvoice", "surprised", "whispering"], roles: [], roleHints: [] },
        { name: "Evelyn Dragon HD Latest", id: "en-US-Evelyn:DragonHDLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Jimmie Dragon HD Latest", id: "en-US-Jimmie:DragonHDLatestNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Juno Dragon HD Latest", id: "en-US-Juno:DragonHDLatestNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Mila Dragon HD Latest", id: "en-US-Mila:DragonHDLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Tessa Dragon HD Latest", id: "en-US-Tessa:DragonHDLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Tiana Dragon HD Latest", id: "en-US-Tiana:DragonHDLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Tyler Dragon HD Latest", id: "en-US-Tyler:DragonHDLatestNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Vance Dragon HD Latest", id: "en-US-Vance:DragonHDLatestNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Andrew Preview Dragon HD Latest", id: "en-US-Andrew-Preview:DragonHDLatestNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Ava Preview Dragon HD Latest", id: "en-US-Ava-Preview:DragonHDLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Ava Dragon HD Omni Latest", id: "en-us-ava:DragonHDOmniLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Blushzephyr Dragon HD Omni Latest", id: "en-us-blushzephyr:DragonHDOmniLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Colin Dragon HD Latest", id: "en-US-Colin:DragonHDLatestNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Emma Dragon HD Omni Latest", id: "en-us-emma:DragonHDOmniLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Ethan MAI-Voice-2", id: "en-US-Ethan:MAI-Voice-2", gender: "male", profile: "EN US", categories: [], personalities: [], styles: ["angry", "confused", "determined", "disgusted", "embarrassed", "excited", "fearful", "happy", "hopeful", "jealous", "joyful", "regretful", "relieved", "sad", "shouting", "softvoice", "surprised", "whispering"], roles: [], roleHints: [] },
        { name: "Ethan MAI-Voice-2.1", id: "en-US-Ethan:MAI-Voice-2.1", gender: "male", profile: "EN US", categories: [], personalities: [], styles: ["angry", "confused", "determined", "disgusted", "embarrassed", "excited", "fearful", "happy", "hopeful", "jealous", "joyful", "regretful", "relieved", "sad", "shouting", "softvoice", "surprised", "whispering"], roles: [], roleHints: [] },
        { name: "Goldenspark Dragon HD Omni Latest", id: "en-us-goldenspark:DragonHDOmniLatestNeural", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Grant MAI-Voice-2", id: "en-US-Grant:MAI-Voice-2", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Grant MAI-Voice-2.1", id: "en-US-Grant:MAI-Voice-2.1", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Harper MAI-Voice-2", id: "en-US-Harper:MAI-Voice-2", gender: "female", profile: "EN US", categories: [], personalities: [], styles: ["angry", "confused", "determined", "embarrassed", "excited", "happy", "hopeful", "joyful", "regretful", "relieved", "sad", "shouting", "softvoice", "whispering"], roles: [], roleHints: [] },
        { name: "Harper MAI-Voice-2.1", id: "en-US-Harper:MAI-Voice-2.1", gender: "female", profile: "EN US", categories: [], personalities: [], styles: ["angry", "confused", "determined", "embarrassed", "excited", "happy", "hopeful", "joyful", "regretful", "relieved", "sad", "shouting", "softvoice", "whispering"], roles: [], roleHints: [] },
        { name: "Iris MAI-Voice-2", id: "en-US-Iris:MAI-Voice-2", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Iris MAI-Voice-2.1", id: "en-US-Iris:MAI-Voice-2.1", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Jasper MAI-Voice-2", id: "en-US-Jasper:MAI-Voice-2", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Jasper MAI-Voice-2.1", id: "en-US-Jasper:MAI-Voice-2.1", gender: "male", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Jelly Dragon HD Omni Latest", id: "en-us-jelly:DragonHDOmniLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Olivia MAI-Voice-2", id: "en-US-Olivia:MAI-Voice-2", gender: "female", profile: "EN US", categories: [], personalities: [], styles: ["angry", "confused", "determined", "disgusted", "embarrassed", "excited", "fearful", "happy", "hopeful", "jealous", "joyful", "regretful", "relieved", "sad", "shouting", "softvoice", "surprised", "whispering"], roles: [], roleHints: [] },
        { name: "Olivia MAI-Voice-2.1", id: "en-US-Olivia:MAI-Voice-2.1", gender: "female", profile: "EN US", categories: [], personalities: [], styles: ["angry", "confused", "determined", "disgusted", "embarrassed", "excited", "fearful", "happy", "hopeful", "jealous", "joyful", "regretful", "relieved", "sad", "shouting", "softvoice", "surprised", "whispering"], roles: [], roleHints: [] },
        { name: "Paige Dragon HD Latest", id: "en-US-Paige:DragonHDLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Serena Preview Dragon HD Latest", id: "en-US-Serena-Preview:DragonHDLatestNeural", gender: "female", profile: "EN US", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Ada Dragon HD Latest", id: "en-GB-Ada:DragonHDLatestNeural", gender: "female", profile: "EN UK", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Ollie Dragon HD Latest", id: "en-GB-Ollie:DragonHDLatestNeural", gender: "male", profile: "EN UK", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Ada Multilingual", id: "en-GB-AdaMultilingualNeural", gender: "female", profile: "EN UK", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Ollie Multilingual", id: "en-GB-OllieMultilingualNeural", gender: "male", profile: "EN UK", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Sonia", id: "en-GB-SoniaNeural", gender: "female", profile: "EN UK", categories: [], personalities: [], styles: ["cheerful", "sad"], roles: [], roleHints: [] },
        { name: "Ryan", id: "en-GB-RyanNeural", gender: "male", profile: "EN UK", categories: [], personalities: [], styles: ["cheerful", "chat", "whispering", "sad"], roles: [], roleHints: [] },
        { name: "Libby", id: "en-GB-LibbyNeural", gender: "female", profile: "EN UK", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Abbi", id: "en-GB-AbbiNeural", gender: "female", profile: "EN UK", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Alfie", id: "en-GB-AlfieNeural", gender: "male", profile: "EN UK", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Bella", id: "en-GB-BellaNeural", gender: "female", profile: "EN UK", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Elliot", id: "en-GB-ElliotNeural", gender: "male", profile: "EN UK", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Ethan", id: "en-GB-EthanNeural", gender: "male", profile: "EN UK", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Hollie", id: "en-GB-HollieNeural", gender: "female", profile: "EN UK", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Maisie", id: "en-GB-MaisieNeural", gender: "female", profile: "EN UK", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Noah", id: "en-GB-NoahNeural", gender: "male", profile: "EN UK", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Oliver", id: "en-GB-OliverNeural", gender: "male", profile: "EN UK", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Olivia", id: "en-GB-OliviaNeural", gender: "female", profile: "EN UK", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Thomas", id: "en-GB-ThomasNeural", gender: "male", profile: "EN UK", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Mia", id: "en-GB-MiaNeural", gender: "female", profile: "EN UK", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Ryan Dragon HD Latest", id: "en-GB-Ryan:DragonHDLatestNeural", gender: "male", profile: "EN UK", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
        { name: "Sonia Dragon HD Latest", id: "en-GB-Sonia:DragonHDLatestNeural", gender: "female", profile: "EN UK", categories: [], personalities: [], styles: [], roles: [], roleHints: [] },
    ];
}

function voiceCatalog() {
    if (VOICES) {
        return VOICES;
    }
    VOICES = [
    {
        name: "晓晓",
        id: "zh-CN-XiaoxiaoNeural",
        gender: "female",
        profile: "少女感-温柔旁白",
        categories: ["News", "Novel"],
        personalities: ["Warm"],
        styles: ["affectionate", "angry", "assistant", "calm", "chat", "chat-casual", "cheerful", "customerservice", "disgruntled", "excited", "fearful", "friendly", "gentle", "lyrical", "newscast", "poetry-reading", "sad", "serious", "sorry", "whispering"],
        roles: [],
        roleHints: ["温柔旁白", "年轻女主", "新闻播报"]
    },
    {
        name: "晓亦",
        id: "zh-CN-XiaoyiNeural",
        gender: "female",
        profile: "少女-活泼甜亮",
        categories: ["Cartoon", "Novel"],
        personalities: ["Lively"],
        styles: ["affectionate", "angry", "cheerful", "disgruntled", "embarrassed", "fearful", "gentle", "sad", "serious"],
        roles: [],
        roleHints: ["少女", "校园女主", "活泼配角"]
    },
    {
        name: "晓辰",
        id: "zh-CN-XiaochenNeural",
        gender: "female",
        profile: "女青年-自然柔和",
        categories: [],
        personalities: [],
        styles: ["livecommercial"],
        roles: [],
        roleHints: ["少女", "温柔女主", "情绪对白"]
    },
    {
        name: "晓涵",
        id: "zh-CN-XiaohanNeural",
        gender: "female",
        profile: "少女-高亮温柔",
        categories: [],
        personalities: [],
        styles: ["affectionate", "angry", "calm", "cheerful", "disgruntled", "embarrassed", "fearful", "gentle", "sad", "serious"],
        roles: [],
        roleHints: ["女主", "女青年", "温柔配角"]
    },
    {
        name: "晓梦",
        id: "zh-CN-XiaomengNeural",
        gender: "female",
        profile: "少女-轻柔聊天",
        categories: [],
        personalities: [],
        styles: ["chat"],
        roles: [],
        roleHints: ["少女", "轻柔聊天", "自然对话"]
    },
    {
        name: "晓墨",
        id: "zh-CN-XiaomoNeural",
        gender: "female",
        profile: "少女-多角色温柔",
        categories: [],
        personalities: [],
        styles: ["affectionate", "angry", "calm", "cheerful", "depressed", "disgruntled", "embarrassed", "envious", "fearful", "gentle", "sad", "serious"],
        roles: ["Boy", "Girl", "OlderAdultFemale", "OlderAdultMale", "SeniorFemale", "SeniorMale", "YoungAdultFemale", "YoungAdultMale"],
        roleHints: ["多角色", "少女", "年轻女主"]
    },
    { name: "晓秋", id: "zh-CN-XiaoqiuNeural", gender: "female", profile: "成熟女声-高亮硬朗", categories: [], personalities: [], styles: [], roles: [], roleHints: ["母亲", "老师", "成熟女性", "旁白"] },
    { name: "晓柔", id: "zh-CN-XiaorouNeural", gender: "female", profile: "少女-清甜", categories: [], personalities: [], styles: [], roles: [], roleHints: ["少女", "校园女主", "活泼配角"] },
    { name: "晓睿", id: "zh-CN-XiaoruiNeural", gender: "female", profile: "少女-冷亮情绪", categories: [], personalities: [], styles: ["angry", "calm", "fearful", "sad"], roles: [], roleHints: ["少女", "冷静角色", "情绪对白"] },
    { name: "晓双", id: "zh-CN-XiaoshuangNeural", gender: "female", profile: "女童/少女-稚嫩清亮", categories: [], personalities: [], styles: ["chat"], roles: [], roleHints: ["女童", "少女", "轻松聊天"] },
    { name: "晓颜", id: "zh-CN-XiaoyanNeural", gender: "female", profile: "女青年-自然柔和", categories: [], personalities: [], styles: [], roles: [], roleHints: ["女主", "女青年", "温柔配角"] },
    { name: "晓悠", id: "zh-CN-XiaoyouNeural", gender: "female", profile: "少女-高亮稚嫩", categories: [], personalities: [], styles: [], roles: [], roleHints: ["少女", "活泼配角", "清亮旁白"] },
    { name: "晓甄", id: "zh-CN-XiaozhenNeural", gender: "female", profile: "女青年-清亮情绪", categories: [], personalities: [], styles: ["angry", "cheerful", "disgruntled", "fearful", "sad", "serious"], roles: [], roleHints: ["女青年", "情绪对白", "剧情配角"] },
    {
        name: "云希",
        id: "zh-CN-YunxiNeural",
        gender: "male",
        profile: "青年男声-阳光叙事",
        categories: ["Novel"],
        personalities: ["Lively", "Sunshine"],
        styles: ["angry", "assistant", "chat", "cheerful", "depressed", "disgruntled", "embarrassed", "fearful", "narration-relaxed", "newscast", "sad", "serious"],
        roles: ["Boy", "Narrator", "YoungAdultMale"],
        roleHints: ["年轻男主", "少年", "旁白"]
    },
    {
        name: "云间",
        id: "zh-CN-YunjianNeural",
        gender: "male",
        profile: "低沉男声-热血解说",
        categories: ["Sports", "Novel"],
        personalities: ["Passion"],
        styles: ["angry", "cheerful", "depressed", "disgruntled", "documentary-narration", "narration-relaxed", "sad", "serious", "sports-commentary", "sports-commentary-excited"],
        roles: [],
        roleHints: ["热血男配", "战斗旁白", "赛事解说"]
    },
    { name: "云扬", id: "zh-CN-YunyangNeural", gender: "male", profile: "青年男声-专业播报", categories: ["News"], personalities: ["Professional", "Reliable"], styles: ["customerservice", "narration-professional", "newscast-casual"], roles: [], roleHints: ["新闻播报", "专业旁白", "官方说明"] },
    { name: "云枫", id: "zh-CN-YunfengNeural", gender: "male", profile: "青年男声-清朗情绪", categories: [], personalities: [], styles: ["angry", "cheerful", "depressed", "disgruntled", "fearful", "sad", "serious"], roles: [], roleHints: ["男主", "书生", "清朗旁白"] },
    { name: "云皓", id: "zh-CN-YunhaoNeural", gender: "male", profile: "低沉男声-活力广告", categories: [], personalities: [], styles: ["advertisement-upbeat"], roles: [], roleHints: ["成熟男声", "活力说明", "广告旁白"] },
    { name: "云杰", id: "zh-CN-YunjieNeural", gender: "male", profile: "少年男声-温和", categories: [], personalities: [], styles: [], roles: [], roleHints: ["少年", "年轻男主", "同学"] },
    { name: "云夏", id: "zh-CN-YunxiaNeural", gender: "male", profile: "男童/少年-高亮稚嫩", categories: ["Cartoon", "Novel"], personalities: ["Cute"], styles: ["angry", "calm", "cheerful", "fearful", "sad"], roles: [], roleHints: ["男童", "少年", "活泼男配"] },
    { name: "云野", id: "zh-CN-YunyeNeural", gender: "male", profile: "低沉大叔-多角色", categories: [], personalities: [], styles: ["angry", "calm", "cheerful", "disgruntled", "embarrassed", "fearful", "sad", "serious"], roles: ["Boy", "Girl", "OlderAdultFemale", "OlderAdultMale", "SeniorFemale", "SeniorMale", "YoungAdultFemale", "YoungAdultMale"], roleHints: ["大叔", "父亲", "反派", "厚重旁白"] },
    { name: "云泽", id: "zh-CN-YunzeNeural", gender: "male", profile: "低沉长辈-纪录片", categories: [], personalities: [], styles: ["angry", "calm", "cheerful", "depressed", "disgruntled", "documentary-narration", "fearful", "sad", "serious"], roles: ["OlderAdultMale", "SeniorMale"], roleHints: ["成熟男性", "长辈", "纪录片旁白"] },
    { name: "晓晓DIA", id: "zh-CN-XiaoxiaoDialectsNeural", gender: "female", profile: "女青年-方言清亮", categories: [], personalities: [], styles: [], roles: [], roleHints: ["方言", "女青年", "旁白"] },
    { name: "晓晓MT", id: "zh-CN-XiaoxiaoMultilingualNeural", gender: "female", profile: "女青年-温柔多语", categories: [], personalities: [], styles: ["affectionate", "cheerful", "empathetic", "excited", "poetry-reading", "sorry", "story"], roles: [], roleHints: ["多语言", "温柔女主", "故事旁白"] },
    { name: "晓宇MT", id: "zh-CN-XiaoyuMultilingualNeural", gender: "female", profile: "少女-柔亮多语", categories: [], personalities: [], styles: [], roles: [], roleHints: ["多语言", "少女", "配角"] },
    { name: "云晓MT", id: "zh-CN-YunxiaoMultilingualNeural", gender: "male", profile: "青年男声-温和多语", categories: [], personalities: [], styles: [], roles: [], roleHints: ["多语言", "男青年", "旁白"] },
    { name: "云逸MT", id: "zh-CN-YunyiMultilingualNeural", gender: "male", profile: "青年男声-清朗多语", categories: [], personalities: [], styles: [], roles: [], roleHints: ["多语言", "男青年", "配角"] },
    { name: "云帆MT", id: "zh-CN-YunfanMultilingualNeural", gender: "male", profile: "青年男声-明亮多语", categories: [], personalities: [], styles: [], roles: [], roleHints: ["多语言", "男青年", "旁白"] },
    { name: "晓辰HD", id: "zh-CN-Xiaochen:DragonHDLatestNeural", gender: "female", profile: "高清女声-自然柔和", categories: [], personalities: [], styles: [], roles: [], hd: true, roleHints: ["女青年", "HD", "高质量旁白"] },
    { name: "云帆HD", id: "zh-CN-Yunfan:DragonHDLatestNeural", gender: "male", profile: "高清男声-清朗", categories: [], personalities: [], styles: [], roles: [], hd: true, roleHints: ["男青年", "HD", "高质量旁白"] },
    { name: "晓辰FHD", id: "zh-CN-Xiaochen:DragonHDFlashLatestNeural", gender: "female", profile: "高清少女-明亮情绪", categories: [], personalities: [], styles: ["cheerful", "debating", "empathetic", "live-commercial", "poetry-reading", "sad", "sorry"], roles: [], hd: true, roleHints: ["女青年", "直播", "诗歌朗诵"] },
    { name: "晓晓FHD", id: "zh-CN-Xiaoxiao:DragonHDFlashLatestNeural", gender: "female", profile: "高清少女-助手情绪", categories: [], personalities: [], styles: ["angry", "chat", "cheerful", "customer-service", "excited", "fearful", "sad", "voice-assistant"], roles: [], hd: true, roleHints: ["少女", "助手", "情绪对白"] },
    { name: "潇潇FHD", id: "zh-CN-Xiaoxiao2:DragonHDFlashLatestNeural", gender: "female", profile: "高清女青年-细腻情绪", categories: [], personalities: [], styles: ["affectionate", "angry", "anxious", "cheerful", "curious", "disappointed", "empathetic", "encouraging", "excited", "fearful", "guilty", "lonely", "poetry-reading", "sad", "sentimental", "sorry", "story", "surprised", "tired", "whispering"], roles: [], hd: true, roleHints: ["女青年", "情绪对白", "故事旁白"] },
    { name: "云萧FHD", id: "zh-CN-Yunxiao:DragonHDFlashLatestNeural", gender: "male", profile: "高清男声-清朗", categories: [], personalities: [], styles: [], roles: [], hd: true, roleHints: ["男青年", "高质量旁白", "配角"] },
    { name: "云野FHD", id: "zh-CN-Yunye:DragonHDFlashLatestNeural", gender: "male", profile: "高清大叔-低沉", categories: [], personalities: [], styles: [], roles: [], hd: true, roleHints: ["男中年", "沉稳旁白", "反派"] },
    { name: "云逸FHD", id: "zh-CN-Yunyi:DragonHDFlashLatestNeural", gender: "male", profile: "高清男声-角色演绎", categories: [], personalities: [], styles: ["assassin", "captain", "cavalier", "game-narrator", "geomancer", "poet", "prince"], roles: [], hd: true, roleHints: ["游戏角色", "王子", "诗人"] },
    { name: "晓宸_繁", id: "zh-TW-HsiaoChenNeural", gender: "female", profile: "台配女青年-自然亲和", categories: ["General"], personalities: ["Friendly", "Positive"], styles: [], roles: [], roleHints: ["繁中女声", "女青年", "旁白"] },
    { name: "晓语_繁", id: "zh-TW-HsiaoYuNeural", gender: "female", profile: "台配成熟女声-明亮", categories: ["General"], personalities: ["Friendly", "Positive"], styles: [], roles: [], roleHints: ["繁中女声", "成熟女性", "旁白"] },
    { name: "云喆_繁", id: "zh-TW-YunJheNeural", gender: "male", profile: "台配男声-温和低沉", categories: ["General"], personalities: ["Friendly", "Positive"], styles: [], roles: [], roleHints: ["繁中男声", "成熟男性", "旁白"] }
    ].concat(englishVoiceCatalog());
    return VOICES;
}

function options() {
    return [
        {
            key: "api",
            label: "接口",
            type: "select",
            defaultValue: "http://5.45.99.149:8075/tts",
            values: API_OPTIONS
        },
        { key: "timeout", label: "超时秒数", type: "number", defaultValue: "30" }
    ];
}

function voices(options, ctx) {
    var result = [];
    var catalog = voiceCatalog();
    for (var i = 0; i < catalog.length; i++) {
        var item = catalog[i];
        var tags = ["Edge"];
        if (item.profile) tags.push(item.profile);
        appendAll(tags, item.categories);
        appendAll(tags, item.personalities);
        if (item.hd) {
            tags.push("HD");
        }
        result.push({
            id: item.id,
            name: item.name,
            language: languageOf(item.id),
            gender: item.gender,
            style: item.styles && item.styles.length ? item.styles.length + " 种风格" : "",
            tags: tags,
            extra: {
                provider: "next-edge-proxy",
                profile: item.profile,
                categories: item.categories || [],
                personalities: item.personalities || [],
                azure_styles: item.styles || [],
                roles: item.roles || [],
                role_hints: item.roleHints || [],
                styles: styleOptions(item.styles || [])
            }
        });
    }
    return result;
}

function styleOptions(styles) {
    var result = [];
    var names = styleNames();
    for (var i = 0; i < styles.length; i++) {
        var id = String(styles[i]);
        result.push({
            id: id,
            name: names[id] || id,
            value: id
        });
    }
    return result;
}

function appendAll(target, values) {
    values = values || [];
    for (var i = 0; i < values.length; i++) {
        target.push(String(values[i]));
    }
}

function synthesize(text, voice, params, options, ctx) {
    var api = String(options.api || "http://5.45.99.149:8075/tts").replace(/\/+$/, "");
    var voiceId = String((voice && voice.id) || "zh-CN-YunxiNeural");
    var style = selectedStyleValue(voice) || automaticStyleValue(voice, ctx);
    var rate = clamp((normalizedParam(params, "speed") - 50) * 2, -100, 100);
    var pitch = clamp((normalizedParam(params, "pitch") - 50) * 2, -100, 100);
    var query = [
        "t=" + encodeURIComponent(text),
        "v=" + encodeURIComponent(voiceId),
        "r=" + encodeURIComponent(rate),
        "p=" + encodeURIComponent(pitch),
        "s=" + encodeURIComponent(style),
        "api_key="
    ].join("&");
    return {
        url: api + "?" + query,
        method: "GET",
        audioContentType: "audio/mpeg",
        timeout: Number(options.timeout || 30),
        retry: 1
    };
}

function selectedStyleValue(voice) {
    if (voice && voice.selected_style && voice.selected_style.value != null) {
        return String(voice.selected_style.value);
    }
    if (voice && voice.style_value != null) {
        return String(voice.style_value);
    }
    return "";
}

function automaticStyleValue(voice, ctx) {
    var supported = voice && voice.extra && voice.extra.azure_styles
        ? voice.extra.azure_styles
        : [];
    if (!supported.length) {
        return "";
    }
    var expressive = ctx && ctx.synthesis && ctx.synthesis.expressive
        ? ctx.synthesis.expressive
        : null;
    if (!expressive) {
        return "";
    }
    var concepts = [];
    if (expressive.emotion) {
        concepts.push(String(expressive.emotion));
    }
    var styleConcepts = expressive.style_concepts || [];
    for (var i = 0; i < styleConcepts.length; i++) {
        concepts.push(String(styleConcepts[i]));
    }
    for (var j = 0; j < concepts.length; j++) {
        var matched = styleForConcept(concepts[j], supported);
        if (matched) {
            return matched;
        }
    }
    return "";
}

function styleForConcept(concept, supported) {
    var normalized = String(concept || "").toLowerCase().replace(/^\s+|\s+$/g, "");
    if (!normalized) {
        return "";
    }
    if (containsStyle(supported, normalized)) {
        return normalized;
    }
    var aliases = expressiveStyleAliases();
    for (var i = 0; i < aliases.length; i++) {
        var item = aliases[i];
        if (!containsStyle(supported, item.style)) {
            continue;
        }
        for (var j = 0; j < item.terms.length; j++) {
            if (normalized.indexOf(item.terms[j]) >= 0) {
                return item.style;
            }
        }
    }
    return "";
}

function containsStyle(styles, expected) {
    for (var i = 0; i < styles.length; i++) {
        if (String(styles[i]).toLowerCase() === expected) {
            return true;
        }
    }
    return false;
}

function languageOf(voiceId) {
    var id = String(voiceId).toLowerCase();
    if (id.indexOf("zh-tw-") === 0 || id.indexOf("zh-tw:") === 0) {
        return "zh-TW";
    }
    if (id.indexOf("en-gb-") === 0 || id.indexOf("en-gb:") === 0) {
        return "en-GB";
    }
    if (id.indexOf("en-us-") === 0 || id.indexOf("en-us:") === 0) {
        return "en-US";
    }
    return "zh-CN";
}

function clamp(value, min, max) {
    value = Number(value);
    if (isNaN(value)) {
        return 0;
    }
    return Math.max(min, Math.min(max, Math.round(value)));
}

function normalizedParam(params, key) {
    var value = params && params[key] != null ? Number(params[key]) : 50;
    if (isNaN(value)) value = 50;
    return Math.max(0, Math.min(100, Math.round(value)));
}
