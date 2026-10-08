package app.personal.banglaai.library

/**
 * Turns a Bengali question into English search words for the (mostly English) books WITHOUT the AI model: a hand-made concept list for the themes of the bundled
 * books (power, fear, war, diplomacy, crowd psychology …) plus the dictionary's English glosses as a fallback. It only widens a keyword search — it does not translate and
 * never invents an answer. With the model installed, the model's own keyword expansion is added on top of this.
 */
object BnEnQuery {
    private val concepts: Map<String, List<String>> = buildMap {
        fun c(bn: String, en: String) = bn.split(' ').forEach { put(it, en.split(',').map(String::trim)) }
        c("ক্ষমতা কর্তৃত্ব", "power,authority,strength,command"); c("শক্তি বল", "power,strength,force")
        c("ভয় ভীতি আতঙ্ক", "fear,afraid,terror"); c("ভালোবাসা ভালবাসা প্রেম", "love,loved,affection"); c("ঘৃণা", "hatred,hate,contempt")
        c("রাজা নৃপতি", "king,prince,monarch,sovereign"); c("রাজ্য", "kingdom,state,principality,dominion"); c("রাষ্ট্র", "state,republic,commonwealth,government")
        c("শাসক শাসনকর্তা", "ruler,prince,governor,sovereign"); c("শাসন সরকার", "government,rule,reign,administration")
        c("প্রজা প্রজাগণ", "subjects,people"); c("জনগণ জনতা জনসাধারণ", "people,multitude,crowd,populace"); c("ভিড়", "crowd,mob,multitude")
        c("যুদ্ধ লড়াই সংগ্রাম", "war,battle,combat,campaign,fighting"); c("শান্তি", "peace"); c("শত্রু দুশমন", "enemy,enemies,foe")
        c("মিত্র বন্ধু বন্ধুত্ব", "friend,friends,ally,friendship"); c("জোট মৈত্রী", "alliance,league,confederacy"); c("চুক্তি সন্ধি", "treaty,agreement,peace,league")
        c("কূটনীতি কূটনৈতিক", "diplomacy,negotiation,ambassador,envoy,negotiating"); c("দূত রাষ্ট্রদূত", "ambassador,envoy,minister")
        c("আলোচনা দরকষাকষি", "negotiation,negotiating,negotiate,discussion"); c("সেনা সৈন্য সৈনিক বাহিনী", "army,soldiers,troops,forces")
        c("সেনাপতি অধিনায়ক", "general,commander,captain"); c("ভাড়াটে", "mercenary,mercenaries,auxiliary,auxiliaries"); c("দুর্গ", "fortress,fortification,fortresses,castle")
        c("অবরোধ", "siege,besiege"); c("আক্রমণ", "attack,assault,offensive"); c("প্রতিরক্ষা রক্ষা", "defence,defense,defensive,defend")
        c("কৌশল রণকৌশল", "strategy,stratagem,tactics,art,method"); c("চাতুরী ধূর্ততা ছল কপটতা", "cunning,deceit,fraud,craft,fox")
        c("প্রতারণা ধোঁকা", "deception,deceive,fraud,deceit"); c("গুপ্তচর", "spy,spies,intelligence,espionage"); c("গোপন গোপনীয়তা", "secret,secrecy,concealed")
        c("নিষ্ঠুরতা নির্দয়", "cruelty,cruel,cruelties"); c("দয়া করুণা", "mercy,clemency,compassion,pity"); c("ন্যায়বিচার বিচার ন্যায়", "justice,judgment,just")
        c("আইন", "law,laws,legislation"); c("স্বাধীনতা", "liberty,freedom,free"); c("স্বৈরাচার স্বৈরতন্ত্র অত্যাচারী", "tyrant,tyranny,despotism,despot")
        c("গণতন্ত্র", "democracy,popular,people"); c("প্রজাতন্ত্র", "republic,republics"); c("অভিজাত অভিজাতবর্গ", "nobles,nobility,aristocracy,patricians")
        c("বিদ্রোহ", "rebellion,revolt,sedition,insurrection"); c("ষড়যন্ত্র", "conspiracy,conspiracies,plot"); c("বিশ্বাসঘাতক বিশ্বাসঘাতকতা", "traitor,treachery,betrayal,treason")
        c("আনুগত্য অনুগত বিশ্বস্ত", "loyalty,faith,fidelity,faithful"); c("বিশ্বাস আস্থা", "belief,faith,trust,creed"); c("ভাগ্য নিয়তি", "fortune,fate,destiny,luck,chance")
        c("সাহস বীরত্ব", "courage,valour,bravery,brave"); c("কাপুরুষ কাপুরুষতা", "coward,cowardice,timid"); c("শৃঙ্খলা", "discipline,order")
        c("প্রজ্ঞা বিচক্ষণতা বিচক্ষণ বুদ্ধি জ্ঞান", "wisdom,prudence,prudent,wise,knowledge,reason"); c("সদগুণ গুণ পুণ্য", "virtue,virtues,quality,qualities"); c("দোষ পাপ", "vice,vices,fault,sin,evil")
        c("নৈতিকতা নীতি নীতিবোধ", "morality,moral,principles,policy,ethics"); c("ধর্ম ধর্মীয়", "religion,religious,faith,church"); c("সম্পদ ধন ধনসম্পদ", "wealth,riches,treasure,money")
        c("দারিদ্র্য গরিব", "poverty,poor"); c("কর খাজনা রাজস্ব", "taxes,tribute,revenue,tax"); c("অর্থনীতি অর্থ", "economy,finance,wealth,revenue"); c("ব্যবসা বাণিজ্য ব্যবসায়ী", "trade,commerce,merchants")
        c("কৃষি কৃষক", "agriculture,husbandry,farmers"); c("মনোবিজ্ঞান মন মানসিকতা", "psychology,mind,soul,mental"); c("আবেগ অনুভূতি", "passion,passions,emotion,sentiment,feeling")
        c("লোভ লালসা", "avarice,greed,covetousness"); c("ঈর্ষা হিংসা", "envy,jealousy"); c("উচ্চাকাঙ্ক্ষা", "ambition,ambitious"); c("সম্মান মর্যাদা", "honour,honor,respect,esteem,dignity")
        c("খ্যাতি সুনাম গৌরব", "fame,reputation,glory,renown"); c("লজ্জা অপমান", "shame,disgrace,contempt"); c("প্রভাব প্রভাবিত", "influence,effect,persuade")
        c("অনুকরণ", "imitation,imitate,example"); c("পরামর্শ উপদেশ উপদেষ্টা", "counsel,advice,advisers,counsellors"); c("মন্ত্রী মন্ত্রণালয়", "minister,ministers,secretary")
        c("দরবার সভাসদ", "court,courtiers,palace"); c("উত্তরাধিকার উত্তরাধিকারী", "succession,inheritance,hereditary,heir"); c("সিংহাসন", "throne,crown")
        c("সাম্রাজ্য", "empire,dominion"); c("দখল জয় বিজয়", "conquest,conquer,victory,acquire,acquisition"); c("পরাজয় পরাজিত", "defeat,defeated,vanquished")
        c("ধ্বংস বিনাশ", "ruin,destruction,destroy,overthrow"); c("সংকট বিপদ বিপর্যয়", "crisis,danger,peril,calamity,adversity"); c("ঝুঁকি", "risk,danger,hazard")
        c("সুযোগ", "opportunity,occasion,chance"); c("সময় যুগ", "time,times,age"); c("পরিবর্তন পরিবর্তনশীল", "change,innovation,changes"); c("নতুন নতুনত্ব", "new,innovation,novelty")
        c("সংস্কার", "reform,reforms"); c("শিক্ষা শিক্ষক", "education,instruction,teacher"); c("ইতিহাস", "history,historian,ancient"); c("রোম রোমান", "rome,roman,romans")
        c("গ্রিস গ্রিক", "greece,greek,greeks"); c("ইতালি ইতালীয়", "italy,italian,italians"); c("মানুষ মানব মানবজাতি", "men,man,mankind,human,nature")
        c("স্বভাব প্রকৃতি চরিত্র", "nature,character,disposition,temper"); c("অভ্যাস রীতি প্রথা", "habit,custom,customs,usage"); c("মিথ্যা মিথ্যাবাদী", "lie,falsehood,false,liar")
        c("সত্য সততা সৎ", "truth,honesty,honest,true"); c("প্রতিশ্রুতি অঙ্গীকার শপথ", "promise,oath,pledge,word"); c("দুর্নীতি দুর্নীতিগ্রস্ত", "corruption,corrupt,bribery")
        c("অপরাধ", "crime,offence,wrong"); c("শাস্তি", "punishment,punish,chastise"); c("পুরস্কার", "reward,rewards,prize"); c("দান উদারতা দানশীল", "liberality,generosity,generous,gifts")
        c("কৃপণ কৃপণতা", "miser,parsimony,stinginess,avarice"); c("বিলাস বিলাসিতা", "luxury,luxuries,idleness"); c("আলস্য অলস", "idleness,idle,sloth")
        c("আদেশ নির্দেশ হুকুম", "command,order,commands"); c("আনুগত্য", "obedience,obey,allegiance"); c("নেতা নেতৃত্ব", "leader,leaders,leadership,chief")
        c("দল গোষ্ঠী", "party,parties,faction,factions"); c("বিভেদ বিভক্ত বিভক্তি", "division,discord,faction,divided"); c("ঐক্য একতা", "union,unity,concord,harmony")
        c("উপহার", "gift,gifts,present"); c("অতিথি", "guest,guests,hospitality"); c("শিক্ষক গুরু", "master,teacher,preceptor"); c("সুখ আনন্দ", "happiness,pleasure,joy")
        c("দুঃখ কষ্ট", "sorrow,grief,pain,suffering"); c("রাগ ক্রোধ", "anger,wrath,rage,passion"); c("ধৈর্য", "patience,patient"); c("সতর্কতা সাবধান", "caution,cautious,prudence,vigilance")
        c("পরিকল্পনা", "plan,design,project,scheme"); c("সিদ্ধান্ত", "decision,resolution,determination"); c("পরিণতি ফল", "result,consequence,effect,issue")
        c("অনুগ্রহ", "favour,favor,grace,benefit"); c("উপকার", "benefit,benefits,service"); c("ক্ষতি", "injury,harm,injuries,loss"); c("প্রতিশোধ", "revenge,vengeance")
        c("সন্দেহ", "suspicion,doubt,suspect"); c("নিরাপত্তা", "security,safe,safety"); c("প্রতিযোগিতা", "rivalry,competition,rival"); c("অভিযান", "campaign,expedition,march")
        c("ম্যাকিয়াভেলি", "machiavelli,prince,discourses"); c("সান", "sun,tzu"); c("জু", "tzu"); c("কৌটিল্য চাণক্য", "kautilya,chanakya,arthasastra"); c("লে বঁ লেবন", "le bon,crowd"); c("ক্লাউজেভিৎস", "clausewitz,war")
        c("ক্যালিয়ের কালিয়ের", "callieres,negotiating,princes"); c("গ্রাসিয়ান", "gracian,worldly,wisdom"); c("প্রিন্স", "prince,princes"); c("অর্থশাস্ত্র", "arthasastra,kautilya,treatise")
    }

    private val suffixes = listOf("গুলোর", "গুলির", "গুলো", "গুলি", "দের", "েরা", "ের", "কে", "রা", "টির", "টার", "টি", "টা", "তে", "য়ে", "য়", "ে", "র", "ই", "ও")

    private fun isBengali(w: String) = w.any { it in 'ঀ'..'৿' }
    private fun candidates(w: String): List<String> = listOf(w) + suffixes.filter { w.endsWith(it) && w.length > it.length + 1 }.map { w.dropLast(it.length) }

    /**
     * English search words for [question]. [glossFallback] maps a Bengali word to English glosses (from the dictionary) for words the concept list does not know.
     * Latin words in the question are kept as they are. Result is de-duplicated, concept words first.
     */
    fun englishTerms(question: String, glossFallback: (String) -> List<String> = { emptyList() }): List<String> {
        val out = LinkedHashSet<String>()
        val words = Regex("[\\p{L}\\p{M}]+").findAll(question).map { it.value }.toList()
        for (w in words) {
            if (!isBengali(w)) { out += w.lowercase(); continue }
            val cands = candidates(w)
            val hit = cands.firstNotNullOfOrNull { concepts[it] } ?: concepts.entries.firstOrNull { (k, _) -> k.length >= 4 && cands.any { c -> c.startsWith(k) } }?.value
            if (hit != null) out += hit else cands.firstNotNullOfOrNull { glossFallback(it).takeIf { g -> g.isNotEmpty() } }?.let { out += it }
        }
        return out.toList()
    }

    /** True if at least one Bengali word of the question could be turned into an English search word. */
    fun understood(question: String, glossFallback: (String) -> List<String> = { emptyList() }) = englishTerms(question, glossFallback).any { w -> !w.any { it in 'ঀ'..'৿' } }
}
