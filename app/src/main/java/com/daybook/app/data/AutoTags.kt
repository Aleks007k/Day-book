package com.daybook.app.data

/**
 * Теги «сами» — без интернета и бесплатно: словарь слов на четырёх языках.
 *
 * Как читать словарь:
 *  - «работ»  — слово начинается так («работа», «работал», «работе»);
 *  - «зал=»   — только ровно это слово (короткие слова, иначе ловится лишнее:
 *               «брат» ловил бы «брать», «лес» — «лестницу»);
 *  - «день рожден» — с пробелом: такое сочетание слов подряд.
 *
 * Это поиск слов, а не понимание смысла: что-то будет пропущено, иногда
 * будет лишнее. Поэтому теги всегда можно поправить руками.
 */
object AutoTags {

    const val MAX_TAGS = 3

    data class TagDef(val name: String, val words: List<String>)

    private fun t(name: String, vararg words: String) = TagDef(name, words.toList().distinct())

    val ALL: List<TagDef> = listOf(
        t(
            "работа",
            "работ", "офис", "коллег", "начальник", "совещан", "созвон", "проект", "клиент", "дедлайн", "зарплат",
            "роботі=", "роботу=", "робота=", "роботою=", "роботи=", "працю", "праці=", "колег", "нарад", "офіс", "проєкт", "клієнт",
            "work=", "worked=", "working=", "office", "meeting", "colleague", "boss=", "project", "client", "deadline",
            "arbeit", "büro", "kolleg", "projekt", "kunde", "besprechung", "chef=",
        ),
        t(
            "семья",
            "мама", "маму=", "мамой=", "маме=", "мамы=", "папа", "папу=", "папой=", "папе=", "отец=", "отца=", "отцу=", "отцом=",
            "родител", "брат=", "брата=", "брату=", "братом=", "братья=", "братьев=", "сестр", "жена=", "жену=", "женой=", "жене=",
            "муж=", "мужа=", "мужу=", "мужем=", "сын=", "сына=", "сыну=", "сыном=", "сынок", "дочь=", "дочк", "дочер",
            "бабушк", "дедушк", "семья=", "семьи=", "семье=", "семьёй=", "семьей=", "родственник",
            "тато=", "тата=", "татом=", "тату=", "батьк", "дружин", "син=", "сина=", "сину=", "сином=", "донь", "бабус", "дідус",
            "mom=", "mum=", "dad=", "mother", "father", "parents", "brother", "sister", "wife=", "husband", "son=", "daughter",
            "family", "grandma", "grandpa",
            "mutter", "vater", "eltern", "bruder", "schwester", "sohn=", "tochter", "familie", "oma=", "opa=",
        ),
        t(
            "дети",
            "дети=", "детей=", "детьми=", "детям=", "ребён", "ребен", "малыш", "садик", "детск",
            "діти=", "дітей=", "дітьми=", "дитин", "малюк", "садоч",
            "kids=", "kid=", "child", "baby=", "kinder=", "kita=",
        ),
        t(
            "друзья",
            "друг=", "друга=", "другу=", "другом=", "друзья", "друзей", "друзьями", "подруг", "приятел",
            "друзі=", "друзів=", "друзями=", "товариш",
            "friend", "freund",
        ),
        t(
            "спорт",
            "спорт", "трениров", "зал=", "зале=", "пробежк", "бегал", "бегаю", "футбол", "плаван", "бассейн", "йог", "велосипед", "качал",
            "тренуван", "пробіжк", "біг=", "бігав", "басейн", "плава",
            "gym=", "workout", "training", "running", "football", "soccer", "swim", "yoga", "bike=", "cycling",
            "sport", "training", "laufen", "joggen", "fitness", "schwimm", "fußball", "fussball", "fahrrad",
        ),
        t(
            "здоровье",
            "врач", "доктор", "болел", "болит", "боль=", "больниц", "температур", "лекарств", "таблетк", "простуд", "здоровье=", "здоровья=", "здоровью=", "здоровьем=", "здоров=", "здорова=", "анализ",
            "зуб", "стоматолог", "давлени", "лечени",
            "лікар", "хвор", "болить", "біль=", "лікарн", "ліки=", "ліків=", "застуд",
            "doctor", "sick=", "ill=", "pain=", "hospital", "fever", "medicine", "pills=", "health", "dentist",
            "arzt", "ärzt", "krank", "schmerz", "fieber", "medikament", "tablette", "gesund", "zahnarzt",
        ),
        t(
            "сон",
            "спал=", "спала=", "выспал", "проснул", "бессонниц", "сон=", "сна=", "уснул", "заснул", "кошмар", "снилось", "приснил",
            "спав=", "спала=", "виспав", "прокинув", "прокинул", "безсонн", "сну=", "заснув", "наснил",
            "slept", "sleep", "woke", "insomnia", "nap=", "dreamt", "nightmare",
            "geschlafen", "schlaf", "aufgewacht", "albtraum",
        ),
        t(
            "еда",
            "еда=", "еду=", "едой=", "поел", "съел", "обед", "ужин", "завтрак", "готовил", "приготов", "рецепт", "вкусн", "пицц",
            "їжа=", "їжу=", "обід", "вечеря", "вечерю", "сніданок", "сніданк", "готував", "смачн",
            "food=", "lunch", "dinner", "breakfast", "cooked", "cooking", "recipe", "tasty", "ate=", "pizza",
            "essen=", "gegessen", "mittagessen", "abendessen", "frühstück", "gekocht", "kochen", "rezept", "lecker",
        ),
        t(
            "кафе",
            "ресторан", "кафе=", "бар=", "баре=", "кофейн", "пиццери",
            "ресторан", "кав'ярн", "кав’ярн",
            "restaurant", "cafe=", "café=", "bar=", "pub=",
            "kneipe", "lokal",
        ),
        t(
            "деньги",
            "деньг", "денег", "заплатил", "потратил", "долг", "кредит", "банк", "бюджет", "налог", "евро=", "доллар", "гривн", "рубл",
            "гроші=", "грошей=", "грошима=", "заплатив", "витратив", "борг", "податк",
            "money", "salary", "paid=", "spent=", "debt", "loan", "bank", "budget", "tax=", "taxes",
            "geld", "gehalt", "bezahlt", "ausgegeben", "schulden", "kredit", "steuer",
        ),
        t(
            "покупки",
            "купил", "покупк", "магазин", "заказал", "доставк", "супермаркет", "рынок", "рынке",
            "купив", "замовив", "замовлен", "ринок", "ринку",
            "bought", "shopping", "store=", "ordered", "delivery", "supermarket",
            "gekauft", "einkauf", "bestellt", "lieferung", "supermarkt",
        ),
        t(
            "дом",
            "квартир", "уборк", "убирал", "ремонт", "дача=", "дачу=", "даче=", "стирк", "переезд", "сосед",
            "прибиран", "прибрав", "переїзд", "сусід",
            "apartment", "cleaning", "cleaned", "renovation", "garden", "moving=", "neighbo",
            "wohnung", "putzen", "geputzt", "aufgeräumt", "renovier", "garten", "umzug", "nachbar",
        ),
        t(
            "машина",
            "машин", "авто=", "автомоб", "бензин", "заправк", "пробк", "парковк", "шиномонтаж", "техосмотр",
            "пальне", "заправ",
            "car=", "fuel", "gas station", "parking", "traffic",
            "auto=", "tanken", "getankt", "benzin", "parken", "stau=", "werkstatt",
        ),
        t(
            "путешествия",
            "поездк", "путешеств", "отпуск", "самолёт", "самолет", "аэропорт", "отел", "гостиниц", "билет", "поезд", "вокзал", "море=",
            "моря=", "морю=", "граница", "границу",
            "подорож", "відпустк", "літак", "аеропорт", "готел", "квиток", "квитк", "потяг", "кордон",
            "trip=", "travel", "vacation", "holiday", "flight", "airport", "hotel", "ticket", "train=", "beach",
            "reise", "urlaub", "flug", "flughafen", "zug=", "bahnhof", "meer=", "strand",
        ),
        t(
            "учёба",
            "учёб", "учеб", "учил", "урок", "курс", "экзамен", "университет", "универ", "школ", "лекци", "домашк", "изуча", "программир",
            "навчан", "вчив", "вчил", "іспит", "екзамен", "університет", "лекці", "вивча",
            "study", "studied", "lesson", "courses", "exam=", "exams", "university", "school", "lecture", "homework", "learn",
            "lernen", "gelernt", "studium", "kurs", "prüfung", "schule", "vorlesung", "hausaufgabe",
        ),
        t(
            "погода",
            "погода=", "погоду=", "погоде=", "погоды=", "погодой=", "дождь", "дожд", "снег", "солнц", "жара=", "жарко", "холодно", "мороз", "ветер", "ветр",
            "дощ", "сніг", "сонц", "спека", "вітер", "вітр",
            "weather", "rain", "snow", "sunny", "windy",
            "wetter", "regen", "schnee", "sonne", "sonnig", "windig",
        ),
        t(
            "прогулка",
            "прогул", "гулял", "погулял", "парк=", "парке=", "парку=", "лес=", "леса=", "лесу=", "лесом=", "природ", "поход=", "походе=", "походы=",
            "прогулян", "гуляв", "погуляв", "ліс=", "лісі=", "лісу=",
            "walk=", "walked", "walking", "park=", "forest", "nature", "hike", "hiking",
            "spazier", "wald=", "natur", "wander",
        ),
        t(
            "радость",
            "рад=", "рада=", "счаст", "весел", "здорово", "кайф", "восторг", "доволен", "довольна",
            "радий", "рада=", "щаслив", "щастя", "чудово",
            "happy", "glad=", "awesome", "excited",
            "glücklich", "froh=", "freue",
        ),
        t(
            "переживания",
            "грустн", "грущ", "печал", "тоск", "плакал", "одинок", "тревог", "волнуюсь", "переживаю", "стресс", "устал", "злюсь", "обидн",
            "сумн", "смуток", "плакав", "самотн", "тривог", "хвилю", "стрес", "втомив", "втомил",
            "sad=", "cried", "lonely", "anxious", "anxiety", "worried", "stress", "tired",
            "traurig", "geweint", "einsam", "angst", "sorge", "müde",
        ),
        t(
            "отношения",
            "свидани", "отношени", "поцел", "влюб", "расстал", "романтич", "любимая", "любимый",
            "побачен", "стосунк", "цілув", "закоха", "розійш", "кохан",
            "dating", "relationship", "kissed", "in love", "broke up", "girlfriend", "boyfriend",
            "verabredung", "beziehung", "kuss", "geküsst", "verliebt", "freundin",
        ),
        t(
            "кино",
            "фильм", "кино=", "кинотеатр", "сериал", "мультфильм", "мультик",
            "фільм", "кіно=", "кінотеатр", "серіал",
            "movie", "film=", "films", "cinema", "series", "netflix",
            "kino=", "serie",
        ),
        t(
            "книги",
            "книг", "книжк", "читал", "прочитал", "дочитал",
            "читав", "прочитав",
            "book=", "books", "reading", "novel",
            "buch=", "bücher", "gelesen", "roman=",
        ),
        t(
            "музыка",
            "музык", "песн", "концерт", "гитар", "пианин",
            "музик", "пісн", "гітар",
            "music", "song", "concert", "guitar", "piano",
            "musik", "lieder", "konzert", "gitarre", "klavier",
        ),
        t(
            "праздник",
            "праздник", "праздновал", "день рожден", "днём рожден", "днем рожден", "подар", "отмечали", "новый год", "свадьб", "юбилей",
            "свято", "свята=", "святкув", "день народжен", "подарун", "весілл", "ювілей",
            "birthday", "party", "gift", "wedding", "celebrat", "christmas", "new year",
            "geburtstag", "feier", "geschenk", "hochzeit", "weihnacht", "silvester",
        ),
        t(
            "питомцы",
            "собак", "кошк", "кот=", "кота=", "коту=", "котом=", "котик", "пёс=", "пес=", "псом=", "щен", "котён", "котен", "ветеринар",
            "кіт=", "кішк", "цуцен", "кошен",
            "dog=", "dogs", "cat=", "cats", "puppy", "kitten", "vet=",
            "hund", "katze", "welpe", "kätzchen", "tierarzt",
        ),
        t(
            "планы",
            "планир", "план=", "планы=", "планов=", "планах=", "цель=", "цели=", "целью=", "мечта", "мечтаю", "задумал",
            "плану", "мрію", "мрія",
            "plan=", "plans=", "planning", "goal=", "goals",
            "plan=", "pläne", "ziel=", "ziele",
        ),
    )

    val NAMES: List<String> = ALL.map { it.name }

    private val WORD = Regex("[\\p{L}'’]+")

    /** Подобрать до [MAX_TAGS] тегов для текста — самые «частые» первыми. */
    fun suggest(text: String): List<String> {
        val lower = text.lowercase()
        val words = WORD.findAll(lower).map { it.value }.toList()
        if (words.isEmpty()) return emptyList()
        val joined = " " + words.joinToString(" ") + " "
        val scores = mutableListOf<Pair<String, Int>>()
        for (def in ALL) {
            var score = 0
            for (w in def.words) {
                score += when {
                    ' ' in w -> if (joined.contains(" $w")) 1 else 0
                    w.endsWith("=") -> words.count { it == w.dropLast(1) }
                    else -> words.count { it.startsWith(w) }
                }
            }
            if (score > 0) scores += def.name to score
        }
        return scores.sortedByDescending { it.second }.take(MAX_TAGS).map { it.first }
    }

    /** «работа, семья» → список; пустые и повторы убираются. */
    fun parse(stored: String): List<String> =
        stored.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun store(tags: List<String>): String = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")

    /** Свой тег: без «#», пробелов по краям и запятых, строчными. */
    fun clean(raw: String): String =
        raw.trim().trimStart('#').replace(",", " ").replace(Regex("\\s+"), " ").trim().lowercase()
}
