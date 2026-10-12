package com.tcmfactory.core.database

/**
 * 内置种子知识库。
 *
 * 数据来源原则（详见方案 6.3）：
 *  · 中药性味功效 —— 《中国药典》2025 年版一部（2025-10-01 施行）
 *  · 腧穴定位 —— GB/T 12346-2021《经穴名称与定位》（2021-11-26 实施）
 *  · 方剂组成 —— 中国中医药出版社规划教材《方剂学》
 *  · 经典条文 —— 公有领域古籍原文（《伤寒论》《黄帝内经·素问》《灵枢》）
 *
 * 每条均带 sourceRef 便于溯源校对。这是演示用种子数据，
 * 完整知识库将走「预编译 SQLite + JSON 增量包」通道。
 */
object SeedData {

    // ---------- 科目枚举（对齐 307 中医综合） ----------
    const val SUBJECT_MATERIA_MEDICA = "MATERIA_MEDICA"
    const val SUBJECT_FORMULA = "FORMULA"
    const val SUBJECT_ACUPUNCTURE = "ACUPUNCTURE"
    const val SUBJECT_CLASSICS = "CLASSICS"

    data class SeedDeck(
        val name: String,
        val subject: String,
        val sortOrder: Int
    )

    data class SeedCard(
        val deck: String,
        val subject: String,
        val front: String,
        val back: String,
        val extra: String? = null,
        val sourceRef: String? = null,
        val tags: List<String> = emptyList(),
        val type: String = "BASIC"
    )

    private const val DECK_MM_JIEBIAO = "中药学·解表药"
    private const val DECK_MM_QINGRE = "中药学·清热药"
    private const val DECK_FM_JIEBIAO = "方剂学·解表剂"
    private const val DECK_FM_BUYI = "方剂学·补益剂"
    private const val DECK_FM_QITA = "方剂学·和解·温里·祛湿"
    private const val DECK_ACU_POINT = "针灸学·常用腧穴定位"
    private const val DECK_MERIDIAN = "针灸学·经络循行"
    private const val DECK_CLS_SHANGHAN = "经典条文·伤寒论"
    private const val DECK_CLS_NEIJING = "经典条文·黄帝内经"

    val decks: List<SeedDeck> = listOf(
        SeedDeck(DECK_MM_JIEBIAO, SUBJECT_MATERIA_MEDICA, 10),
        SeedDeck(DECK_MM_QINGRE, SUBJECT_MATERIA_MEDICA, 20),
        SeedDeck(DECK_FM_JIEBIAO, SUBJECT_FORMULA, 30),
        SeedDeck(DECK_FM_BUYI, SUBJECT_FORMULA, 40),
        SeedDeck(DECK_FM_QITA, SUBJECT_FORMULA, 50),
        SeedDeck(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE, 60),
        SeedDeck(DECK_MERIDIAN, SUBJECT_ACUPUNCTURE, 70),
        SeedDeck(DECK_CLS_SHANGHAN, SUBJECT_CLASSICS, 80),
        SeedDeck(DECK_CLS_NEIJING, SUBJECT_CLASSICS, 90)
    )

    private const val SRC_YAODIAN = "《中国药典》2025年版一部"
    private const val SRC_JIAOCAI_FORMULA = "规划教材《方剂学》"
    private const val SRC_GB12346 = "GB/T 12346-2021《经穴名称与定位》"

    val cards: List<SeedCard> = buildList {

        // ==================== 中药学 · 解表药 ====================

        add(SeedCard(DECK_MM_JIEBIAO, SUBJECT_MATERIA_MEDICA,
            "麻黄的功效是什么？", "发汗解表，宣肺平喘，利水消肿。",
            "与桂枝相鉴别：麻黄发汗力强，偏于宣肺平喘；桂枝发汗力缓，长于温通经脉、助阳化气。",
            "$SRC_YAODIAN·麻黄", listOf("高频", "易混淆")))

        add(SeedCard(DECK_MM_JIEBIAO, SUBJECT_MATERIA_MEDICA,
            "桂枝的功效是什么？", "发汗解肌，温通经脉，助阳化气，平冲降气。",
            "《伤寒论》群方之冠桂枝汤的君药。",
            "$SRC_YAODIAN·桂枝", listOf("高频")))

        add(SeedCard(DECK_MM_JIEBIAO, SUBJECT_MATERIA_MEDICA,
            "紫苏叶的功效是什么？", "解表散寒，行气和胃。",
            "兼能解鱼蟹毒；与紫苏梗、紫苏子同出一物而功用各异。",
            "$SRC_YAODIAN·紫苏叶", listOf("易混淆")))

        add(SeedCard(DECK_MM_JIEBIAO, SUBJECT_MATERIA_MEDICA,
            "荆芥的功效是什么？", "解表散风，透疹，消疮。",
            "荆芥炭可止血；其为发散风寒药中药性最平和者。",
            "$SRC_YAODIAN·荆芥", emptyList()))

        add(SeedCard(DECK_MM_JIEBIAO, SUBJECT_MATERIA_MEDICA,
            "防风的功效是什么？", "祛风解表，胜湿止痛，止痉。",
            "风药中之润剂，为治风通用之品。",
            "$SRC_YAODIAN·防风", listOf("高频")))

        add(SeedCard(DECK_MM_JIEBIAO, SUBJECT_MATERIA_MEDICA,
            "羌活的功效是什么？", "解表散寒，祛风除湿，止痛。",
            "善治上半身风寒湿痹，与独活（主下半身）相对。",
            "$SRC_YAODIAN·羌活", listOf("易混淆")))

        add(SeedCard(DECK_MM_JIEBIAO, SUBJECT_MATERIA_MEDICA,
            "白芷的功效是什么？", "解表散寒，祛风止痛，宣通鼻窍，燥湿止带，消肿排脓。",
            "阳明经引经药，善治前额头痛、鼻渊。",
            "$SRC_YAODIAN·白芷", emptyList()))

        add(SeedCard(DECK_MM_JIEBIAO, SUBJECT_MATERIA_MEDICA,
            "细辛的功效是什么？", "解表散寒，祛风止痛，通窍，温肺化饮。",
            "用量不宜过大（药典规定 1～3g）；反藜芦。",
            "$SRC_YAODIAN·细辛", listOf("高频", "用量考点")))

        add(SeedCard(DECK_MM_JIEBIAO, SUBJECT_MATERIA_MEDICA,
            "薄荷的功效是什么？", "疏散风热，清利头目，利咽透疹，疏肝行气。",
            "入汤剂宜后下。",
            "$SRC_YAODIAN·薄荷", emptyList()))

        add(SeedCard(DECK_MM_JIEBIAO, SUBJECT_MATERIA_MEDICA,
            "牛蒡子的功效是什么？", "疏散风热，宣肺透疹，解毒利咽。",
            "性寒滑肠，脾虚便溏者慎用。",
            "$SRC_YAODIAN·牛蒡子", emptyList()))

        add(SeedCard(DECK_MM_JIEBIAO, SUBJECT_MATERIA_MEDICA,
            "蝉蜕的功效是什么？", "疏散风热，利咽开音，透疹，明目退翳，息风止痉。",
            "为治小儿夜啼、破伤风之常用药。",
            "$SRC_YAODIAN·蝉蜕", listOf("高频")))

        add(SeedCard(DECK_MM_JIEBIAO, SUBJECT_MATERIA_MEDICA,
            "葛根的功效是什么？", "解肌退热，生津止渴，透疹，升阳止泻，通经活络，解酒毒。",
            "生用解肌退热、生津；煨用升阳止泻。",
            "$SRC_YAODIAN·葛根", listOf("高频")))

        // ==================== 中药学 · 清热药 ====================

        add(SeedCard(DECK_MM_QINGRE, SUBJECT_MATERIA_MEDICA,
            "石膏的功效是什么？", "生用：清热泻火，除烦止渴；煅用：敛疮生肌，收湿止血。",
            "为清泻肺胃实火之要药；先煎。",
            "$SRC_YAODIAN·石膏", listOf("高频", "炮制考点")))

        add(SeedCard(DECK_MM_QINGRE, SUBJECT_MATERIA_MEDICA,
            "知母的功效是什么？", "清热泻火，滋阴润燥。",
            "上能清肺，中能清胃，下能滋肾，为清热与滋阴并行之品。",
            "$SRC_YAODIAN·知母", listOf("高频")))

        add(SeedCard(DECK_MM_QINGRE, SUBJECT_MATERIA_MEDICA,
            "栀子的功效是什么？", "泻火除烦，清热利湿，凉血解毒；外用消肿止痛。",
            "善清心、肺、三焦之火而除烦，为治热病心烦之要药。",
            "$SRC_YAODIAN·栀子", emptyList()))

        add(SeedCard(DECK_MM_QINGRE, SUBJECT_MATERIA_MEDICA,
            "黄芩的功效是什么？", "清热燥湿，泻火解毒，止血，安胎。",
            "善清上焦肺火；与黄连（清中焦）、黄柏（清下焦）三药相较。",
            "$SRC_YAODIAN·黄芩", listOf("高频", "易混淆")))

        add(SeedCard(DECK_MM_QINGRE, SUBJECT_MATERIA_MEDICA,
            "黄连的功效是什么？", "清热燥湿，泻火解毒。",
            "善清中焦脾胃与心经之火，为治湿热泻痢之要药。",
            "$SRC_YAODIAN·黄连", listOf("高频")))

        add(SeedCard(DECK_MM_QINGRE, SUBJECT_MATERIA_MEDICA,
            "黄柏的功效是什么？", "清热燥湿，泻火除蒸，解毒疗疮。",
            "善清下焦湿热，退虚热（阴虚发热、骨蒸劳热）。",
            "$SRC_YAODIAN·黄柏", listOf("易混淆")))

        add(SeedCard(DECK_MM_QINGRE, SUBJECT_MATERIA_MEDICA,
            "金银花的功效是什么？", "清热解毒，疏散风热。",
            "为治一切内外痈肿之要药；与连翘常相须为用。",
            "$SRC_YAODIAN·金银花", listOf("高频")))

        add(SeedCard(DECK_MM_QINGRE, SUBJECT_MATERIA_MEDICA,
            "连翘的功效是什么？", "清热解毒，消肿散结，疏散风热。",
            "有「疮家圣药」之称，兼能清心利尿。",
            "$SRC_YAODIAN·连翘", emptyList()))

        // ==================== 方剂学 · 解表剂 ====================

        add(SeedCard(DECK_FM_JIEBIAO, SUBJECT_FORMULA,
            "麻黄汤的组成是什么？", "麻黄、桂枝、杏仁、甘草。",
            "功用：发汗解表，宣肺平喘。主治外感风寒表实证（无汗而喘）。",
            SRC_JIAOCAI_FORMULA, listOf("高频", "组成必背")))

        add(SeedCard(DECK_FM_JIEBIAO, SUBJECT_FORMULA,
            "桂枝汤的组成是什么？", "桂枝、芍药、生姜、大枣、甘草。",
            "功用：解肌发表，调和营卫。主治外感风寒表虚证（有汗）。桂枝:芍药 = 1:1。",
            SRC_JIAOCAI_FORMULA, listOf("高频", "组成必背")))

        add(SeedCard(DECK_FM_JIEBIAO, SUBJECT_FORMULA,
            "小青龙汤的组成是什么？", "麻黄、桂枝、芍药、细辛、干姜、甘草、半夏、五味子。",
            "功用：解表散寒，温肺化饮。主治外寒里饮证。",
            SRC_JIAOCAI_FORMULA, listOf("高频", "组成必背")))

        add(SeedCard(DECK_FM_JIEBIAO, SUBJECT_FORMULA,
            "银翘散的组成是什么？", "连翘、金银花、苦桔梗、薄荷、竹叶、生甘草、荆芥穗、淡豆豉、牛蒡子、芦根。",
            "功用：辛凉透表，清热解毒。主治温病初起。为「辛凉平剂」。",
            SRC_JIAOCAI_FORMULA, listOf("高频", "组成必背")))

        add(SeedCard(DECK_FM_JIEBIAO, SUBJECT_FORMULA,
            "桑菊饮的组成是什么？", "桑叶、菊花、杏仁、连翘、薄荷、苦桔梗、生甘草、苇根。",
            "功用：疏风清热，宣肺止咳。为「辛凉轻剂」。",
            SRC_JIAOCAI_FORMULA, listOf("易混淆", "对比银翘散")))

        add(SeedCard(DECK_FM_JIEBIAO, SUBJECT_FORMULA,
            "麻杏石甘汤的组成是什么？", "麻黄、杏仁、石膏、甘草。",
            "功用：辛凉疏表，清肺平喘。主治外感风邪、邪热壅肺证。",
            SRC_JIAOCAI_FORMULA, listOf("高频")))

        add(SeedCard(DECK_FM_JIEBIAO, SUBJECT_FORMULA,
            "九味羌活汤的组成是什么？", "羌活、防风、苍术、细辛、川芎、白芷、生地黄、黄芩、甘草。",
            "功用：发汗祛湿，兼清里热。为「分经论治」的代表方。",
            SRC_JIAOCAI_FORMULA, listOf("组成必背")))

        add(SeedCard(DECK_FM_JIEBIAO, SUBJECT_FORMULA,
            "大青龙汤主治什么证候？", "外感风寒，内有郁热证（表寒里热，不汗出而烦躁）。",
            "组成：麻黄、桂枝、杏仁、甘草、石膏、生姜、大枣。发汗力峻猛，需中病即止。",
            SRC_JIAOCAI_FORMULA, listOf("高频")))

        // ==================== 方剂学 · 补益剂 ====================

        add(SeedCard(DECK_FM_BUYI, SUBJECT_FORMULA,
            "四君子汤的组成是什么？", "人参、白术、茯苓、甘草。",
            "功用：益气健脾。为治脾胃气虚证的基础方，补气方之祖。",
            SRC_JIAOCAI_FORMULA, listOf("高频", "组成必背")))

        add(SeedCard(DECK_FM_BUYI, SUBJECT_FORMULA,
            "四物汤的组成是什么？", "熟地黄、当归、白芍、川芎。",
            "功用：补血调血。为补血调经的基础方，妇科第一方。",
            SRC_JIAOCAI_FORMULA, listOf("高频", "组成必背")))

        add(SeedCard(DECK_FM_BUYI, SUBJECT_FORMULA,
            "六味地黄丸的组成是什么？", "熟地黄、山茱萸、山药、泽泻、牡丹皮、茯苓。",
            "功用：滋补肝肾。三补三泻：熟地/泽泻、山茱萸/丹皮、山药/茯苓。",
            SRC_JIAOCAI_FORMULA, listOf("高频", "组成必背")))

        add(SeedCard(DECK_FM_BUYI, SUBJECT_FORMULA,
            "补中益气汤的组成是什么？", "黄芪、甘草、人参、当归、橘皮、升麻、柴胡、白术。",
            "功用：补中益气，升阳举陷。主治脾虚气陷证、气虚发热证。",
            SRC_JIAOCAI_FORMULA, listOf("高频", "组成必背")))

        add(SeedCard(DECK_FM_BUYI, SUBJECT_FORMULA,
            "逍遥散的组成是什么？", "柴胡、当归、白芍、白术、茯苓、甘草、烧生姜、薄荷。",
            "功用：疏肝解郁，养血健脾。主治肝郁血虚脾弱证。",
            SRC_JIAOCAI_FORMULA, listOf("高频", "组成必背")))

        add(SeedCard(DECK_FM_BUYI, SUBJECT_FORMULA,
            "归脾汤的组成是什么？", "白术、当归、茯苓、黄芪、龙眼肉、远志、酸枣仁、木香、甘草、人参。",
            "功用：益气补血，健脾养心。主治心脾气血两虚证、脾不统血证。",
            SRC_JIAOCAI_FORMULA, listOf("组成必背")))

        // ==================== 方剂学 · 和解·温里·祛湿·泻下 ====================

        add(SeedCard(DECK_FM_QITA, SUBJECT_FORMULA,
            "小柴胡汤的组成是什么？", "柴胡、黄芩、人参、半夏、甘草、生姜、大枣。",
            "功用：和解少阳。主治伤寒少阳证。为「和剂之祖」。",
            SRC_JIAOCAI_FORMULA, listOf("高频", "组成必背")))

        add(SeedCard(DECK_FM_QITA, SUBJECT_FORMULA,
            "白虎汤的组成是什么？", "石膏、知母、甘草、粳米。",
            "功用：清热生津。主治气分热盛证（四大症：大热、大汗、大渴、脉洪大）。",
            SRC_JIAOCAI_FORMULA, listOf("高频", "组成必背")))

        add(SeedCard(DECK_FM_QITA, SUBJECT_FORMULA,
            "四逆汤的组成是什么？", "附子（生用）、干姜、甘草。",
            "功用：回阳救逆。主治心肾阳衰寒厥证。为「回阳救逆第一方」。",
            SRC_JIAOCAI_FORMULA, listOf("高频", "组成必背")))

        add(SeedCard(DECK_FM_QITA, SUBJECT_FORMULA,
            "理中丸的组成是什么？", "人参、干姜、甘草、白术。",
            "功用：温中祛寒，补气健脾。主治脾胃虚寒证。",
            SRC_JIAOCAI_FORMULA, listOf("组成必背")))

        add(SeedCard(DECK_FM_QITA, SUBJECT_FORMULA,
            "真武汤的组成是什么？", "茯苓、芍药、生姜、白术、附子。",
            "功用：温阳利水。主治阳虚水泛证。",
            SRC_JIAOCAI_FORMULA, listOf("组成必背")))

        add(SeedCard(DECK_FM_QITA, SUBJECT_FORMULA,
            "五苓散的组成是什么？", "猪苓、泽泻、白术、茯苓、桂枝。",
            "功用：利水渗湿，温阳化气。主治膀胱气化不利之蓄水证。",
            SRC_JIAOCAI_FORMULA, listOf("高频", "组成必背")))

        add(SeedCard(DECK_FM_QITA, SUBJECT_FORMULA,
            "二陈汤的组成是什么？", "半夏、橘红、白茯苓、甘草（加生姜、乌梅）。",
            "功用：燥湿化痰，理气和中。为治痰之基础方。",
            SRC_JIAOCAI_FORMULA, listOf("组成必背")))

        add(SeedCard(DECK_FM_QITA, SUBJECT_FORMULA,
            "大承气汤的组成是什么？", "大黄、厚朴、枳实、芒硝。",
            "功用：峻下热结。主治阳明腑实证。用法：先煎枳实厚朴，后下大黄，芒硝溶服。",
            SRC_JIAOCAI_FORMULA, listOf("高频", "组成必背")))

        add(SeedCard(DECK_FM_QITA, SUBJECT_FORMULA,
            "血府逐瘀汤的组成是什么？", "桃仁、红花、当归、生地黄、川芎、赤芍、牛膝、桔梗、柴胡、枳壳、甘草。",
            "功用：活血化瘀，行气止痛。主治胸中血瘀证。",
            SRC_JIAOCAI_FORMULA, listOf("组成必背")))

        add(SeedCard(DECK_FM_QITA, SUBJECT_FORMULA,
            "乌梅丸的组成是什么？", "乌梅、细辛、干姜、黄连、当归、附子、蜀椒、桂枝、人参、黄柏。",
            "功用：温脏安蛔。主治蛔厥证，亦治久泻久痢。",
            SRC_JIAOCAI_FORMULA, listOf("易混淆")))

        // ==================== 针灸学 · 常用腧穴定位 ====================

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "合谷穴（LI4）的定位是？", "在手背，第 2 掌骨桡侧的中点处。",
            "手阳明大肠经原穴。孕妇慎用。四总穴「面口合谷收」。",
            "$SRC_GB12346 §5.2 手阳明大肠经穴", listOf("高频"), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "曲池穴（LI11）的定位是？", "在肘区，尺泽（LU5）与肱骨外上髁连线的中点处。",
            "手阳明大肠经合穴。新版国标以「肱骨外上髁」替代旧版「肘横纹外侧端」表述。",
            "$SRC_GB12346 §5.2 手阳明大肠经穴", listOf("高频", "国标修订点"), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "肩髃穴（LI15）的定位是？", "在三角肌区，肩峰外侧缘前端与肱骨大结节两骨间凹陷中。",
            "手阳明大肠经穴，治肩臂疼痛要穴。",
            "$SRC_GB12346 §5.2 手阳明大肠经穴", emptyList(), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "迎香穴（LI20）的定位是？", "在面部，鼻翼外缘中点旁，当鼻唇沟中。",
            "手阳明大肠经穴，治鼻塞不闻香臭。",
            "$SRC_GB12346 §5.2 手阳明大肠经穴", emptyList(), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "地仓穴（ST4）的定位是？", "在面部，当口角旁开 0.4 寸（指寸）。",
            "足阳明胃经穴，治口眼㖞斜。",
            "$SRC_GB12346 §5.3 足阳明胃经穴", emptyList(), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "足三里穴（ST36）的定位是？", "在小腿外侧，犊鼻（ST35）下 3 寸，犊鼻与解溪（ST41）连线上。",
            "足阳明胃经合穴、胃下合穴。四总穴「肚腹三里留」；为强壮保健要穴。",
            "$SRC_GB12346 §5.3 足阳明胃经穴", listOf("高频"), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "丰隆穴（ST40）的定位是？", "在小腿外侧，外踝尖上 8 寸，胫骨前肌的外缘。",
            "足阳明胃经络穴，为「化痰要穴」。",
            "$SRC_GB12346 §5.3 足阳明胃经穴", listOf("高频"), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "内庭穴（ST44）的定位是？", "在足背，第 2、3 趾间，趾蹼缘后方赤白肉际处。",
            "足阳明胃经荥穴，善清胃火。",
            "$SRC_GB12346 §5.3 足阳明胃经穴", emptyList(), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "三阴交穴（SP6）的定位是？", "在小腿内侧，内踝尖上 3 寸，胫骨内侧缘后际。",
            "足太阴脾经穴，为足三阴经（脾、肝、肾）交会穴。孕妇慎用。",
            "$SRC_GB12346 §5.4 足太阴脾经穴", listOf("高频", "孕妇慎用"), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "阴陵泉穴（SP9）的定位是？", "在小腿内侧，胫骨内侧髁下缘与胫骨内侧缘之间的凹陷中。",
            "足太阴脾经合穴，为「祛湿要穴」。",
            "$SRC_GB12346 §5.4 足太阴脾经穴", listOf("高频"), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "血海穴（SP10）的定位是？", "在股前区，髌底内侧端上 2 寸，股内侧肌隆起处。",
            "足太阴脾经穴，为「理血要穴」。",
            "$SRC_GB12346 §5.4 足太阴脾经穴", emptyList(), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "通里穴（HT5）的定位是？", "在前臂前区，腕掌侧远端横纹上 1 寸，尺侧腕屈肌腱的桡侧缘。",
            "手少阴心经络穴，治暴喑、舌强不语。",
            "$SRC_GB12346 §5.5 手少阴心经穴", emptyList(), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "神门穴（HT7）的定位是？", "在腕前区，腕掌侧远端横纹尺侧端，尺侧腕屈肌腱的桡侧缘。",
            "手少阴心经原穴、输穴，为「安神要穴」。",
            "$SRC_GB12346 §5.5 手少阴心经穴", listOf("高频"), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "少商穴（LU11）的定位是？", "在手指，拇指末节桡侧，指甲根角侧上方 0.1 寸（指寸）。",
            "手太阴肺经井穴，点刺放血可治咽喉肿痛。",
            "$SRC_GB12346 §5.1 手太阴肺经穴", emptyList(), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "后溪穴（SI3）的定位是？", "在手内侧，第 5 掌指关节尺侧近端赤白肉际凹陷中。",
            "手太阳小肠经输穴、八脉交会穴（通督脉）。四总穴「颈项后溪求」。",
            "$SRC_GB12346 §5.6 手太阳小肠经穴", listOf("高频"), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "天宗穴（SI11）的定位是？", "在肩胛区，肩胛冈中点与肩胛骨下角连线上 1/3 与下 2/3 交点凹陷中。",
            "手太阳小肠经穴，治肩胛疼痛。",
            "$SRC_GB12346 §5.6 手太阳小肠经穴", emptyList(), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "听宫穴（SI19）的定位是？", "在面部，耳屏正中与下颌骨髁突之间的凹陷中。",
            "手太阳小肠经穴，张口取穴，治耳鸣耳聋。",
            "$SRC_GB12346 §5.6 手太阳小肠经穴", emptyList(), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "睛明穴（BL1）的定位是？", "在面部，目内眦内上方眶内侧壁凹陷中。",
            "足太阳膀胱经穴，治目疾；针刺需注意手法，避免出血。",
            "$SRC_GB12346 §5.7 足太阳膀胱经穴", listOf("危险穴位"), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "委中穴（BL40）的定位是？", "在膝后区，腘横纹中点。",
            "足太阳膀胱经合穴、膀胱下合穴。四总穴「腰背委中求」。",
            "$SRC_GB12346 §5.7 足太阳膀胱经穴", listOf("高频"), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "承山穴（BL57）的定位是？", "在小腿后区，腓肠肌两肌腹与肌腱交角处。",
            "足太阳膀胱经穴，治痔疾、腿肚转筋。",
            "$SRC_GB12346 §5.7 足太阳膀胱经穴", emptyList(), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "太溪穴（KI3）的定位是？", "在踝区，内踝尖与跟腱之间的凹陷中。",
            "足少阴肾经原穴、输穴，为「滋阴要穴」。",
            "$SRC_GB12346 §5.8 足少阴肾经穴", listOf("高频"), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "内关穴（PC6）的定位是？", "在前臂前区，腕掌侧远端横纹上 2 寸，掌长肌腱与桡侧腕屈肌腱之间。",
            "手厥阴心包经络穴、八脉交会穴（通阴维脉）。治心胸胃病症。",
            "$SRC_GB12346 §5.9 手厥阴心包经穴", listOf("高频"), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "外关穴（TE5）的定位是？", "在前臂后区，腕背侧远端横纹上 2 寸，尺骨与桡骨间隙中点。",
            "手少阳三焦经络穴、八脉交会穴（通阳维脉）。与内关相对。",
            "$SRC_GB12346 §5.10 手少阳三焦经穴", listOf("易混淆"), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "风池穴（GB20）的定位是？", "在颈后区，枕骨之下，胸锁乳突肌上端与斜方肌上端之间的凹陷中。",
            "足少阳胆经穴，治外感、头痛、眩晕、颈项强痛。深部为延髓，须严格掌握针刺方向与深度。",
            "$SRC_GB12346 §5.11 足少阳胆经穴", listOf("高频", "危险穴位"), "ACUPOINT"))

        add(SeedCard(DECK_ACU_POINT, SUBJECT_ACUPUNCTURE,
            "环跳穴（GB30）的定位是？", "在臀区，股骨大转子最凸点与骶管裂孔连线的外 1/3 与内 2/3 交点处。",
            "足少阳胆经穴，为治腰腿痛要穴。",
            "$SRC_GB12346 §5.11 足少阳胆经穴", emptyList(), "ACUPOINT"))

        // ==================== 针灸学 · 经络循行 ====================

        add(SeedCard(DECK_MERIDIAN, SUBJECT_ACUPUNCTURE,
            "手太阴肺经的循行原文是什么？",
            "肺手太阴之脉，起于中焦，下络大肠，还循胃口，上膈属肺，从肺系横出腋下，下循臑内，行少阴心主之前，下肘中，循臂内上骨下廉，入寸口，上鱼，循鱼际，出大指之端；其支者，从腕后直出次指内廉，出其端。",
            "出自《灵枢·经脉》。记忆要点：起中焦 → 络大肠 → 属肺 → 出腋下 → 出大指端。",
            "《灵枢·经脉》", listOf("高频", "原文必背")))

        add(SeedCard(DECK_MERIDIAN, SUBJECT_ACUPUNCTURE,
            "手阳明大肠经的循行原文是什么？",
            "大肠手阳明之脉，起于大指次指之端，循指上廉，出合谷两骨之间，上入两筋之中，循臂上廉，入肘外廉，上臑外前廉，上肩，出髃骨之前廉，上出于柱骨之会上，下入缺盆，络肺，下膈，属大肠；其支者，从缺盆上颈，贯颊，入下齿中，还出挟口，交人中，左之右，右之左，上挟鼻孔。",
            "出自《灵枢·经脉》。",
            "《灵枢·经脉》", listOf("高频", "原文必背")))

        add(SeedCard(DECK_MERIDIAN, SUBJECT_ACUPUNCTURE,
            "足阳明胃经的循行原文是什么？",
            "胃足阳明之脉，起于鼻，交頞中，旁约太阳之脉，下循鼻外，入上齿中，还出挟口环唇，下交承浆，却循颐后下廉，出大迎，循颊车，上耳前，过客主人，循发际，至额颅；其支者，从大迎前下人迎，循喉咙，入缺盆，下膈，属胃络脾。",
            "出自《灵枢·经脉》。",
            "《灵枢·经脉》", listOf("原文必背")))

        add(SeedCard(DECK_MERIDIAN, SUBJECT_ACUPUNCTURE,
            "足太阴脾经的循行原文是什么？",
            "脾足太阴之脉，起于大指之端，循指内侧白肉际，过核骨后，上内踝前廉，上踹内，循胫骨后，交出厥阴之前，上膝股内前廉，入腹属脾络胃，上膈，挟咽，连舌本，散舌下；其支者，复从胃，别上膈，注心中。",
            "出自《灵枢·经脉》。",
            "《灵枢·经脉》", listOf("原文必背")))

        add(SeedCard(DECK_MERIDIAN, SUBJECT_ACUPUNCTURE,
            "督脉与任脉的循行出处与要点是什么？",
            "督脉：起于下极之俞，并于脊里，上至风府，入属于脑（《难经·二十八难》）。\n任脉：起于中极之下，以上毛际，循腹里，上关元，至咽喉，上颐循面入目（《素问·骨空论》）。",
            "任督二脉同起于胞中，与十二正经合称「十四经」。",
            "《难经·二十八难》《素问·骨空论》", listOf("高频")))

        // ==================== 经典条文 · 伤寒论 ====================

        add(SeedCard(DECK_CLS_SHANGHAN, SUBJECT_CLASSICS,
            "《伤寒论》第 1 条原文是什么？",
            "太阳之为病，脉浮，头项强痛而恶寒。",
            "此为太阳病提纲证。",
            "《伤寒论》辨太阳病脉证并治上", listOf("高频", "提纲证")))

        add(SeedCard(DECK_CLS_SHANGHAN, SUBJECT_CLASSICS,
            "《伤寒论》第 2 条原文是什么？",
            "太阳病，发热，汗出，恶风，脉缓者，名为中风。",
            "太阳中风（表虚）证。",
            "《伤寒论》辨太阳病脉证并治上", listOf("高频")))

        add(SeedCard(DECK_CLS_SHANGHAN, SUBJECT_CLASSICS,
            "《伤寒论》第 3 条原文是什么？",
            "太阳病，或已发热，或未发热，必恶寒，体痛，呕逆，脉阴阳俱紧者，名为伤寒。",
            "太阳伤寒（表实）证。注意与第 2 条中风证鉴别：有汗无汗、脉缓脉紧。",
            "《伤寒论》辨太阳病脉证并治上", listOf("高频", "易混淆")))

        add(SeedCard(DECK_CLS_SHANGHAN, SUBJECT_CLASSICS,
            "《伤寒论》第 13 条原文是什么？",
            "太阳病，头痛发热，汗出恶风，桂枝汤主之。",
            "桂枝汤主治条文。可与第 35 条麻黄汤证对照记忆。",
            "《伤寒论》辨太阳病脉证并治上", listOf("高频")))

        add(SeedCard(DECK_CLS_SHANGHAN, SUBJECT_CLASSICS,
            "《伤寒论》第 35 条原文是什么？",
            "太阳病，头痛发热，身疼腰痛，骨节疼痛，恶风，无汗而喘者，麻黄汤主之。",
            "麻黄汤主治条文。与第 13 条桂枝汤证鉴别要点：无汗而喘。",
            "《伤寒论》辨太阳病脉证并治中", listOf("高频", "易混淆")))

        add(SeedCard(DECK_CLS_SHANGHAN, SUBJECT_CLASSICS,
            "《伤寒论》第 96 条原文是什么？",
            "伤寒五六日中风，往来寒热，胸胁苦满，嘿嘿不欲饮食，心烦喜呕，或胸中烦而不呕，或渴，或腹中痛，或胁下痞硬，或心下悸、小便不利，或不渴、身有微热，或咳者，小柴胡汤主之。",
            "小柴胡汤七大或然证治法由此条展开。",
            "《伤寒论》辨太阳病脉证并治中", listOf("高频", "方证")))

        add(SeedCard(DECK_CLS_SHANGHAN, SUBJECT_CLASSICS,
            "《伤寒论》第 71 条原文是什么？",
            "太阳病，发汗后，大汗出，胃中干，烦躁不得眠，欲得饮水者，少少与饮之，令胃气和则愈。若脉浮，小便不利，微热消渴者，五苓散主之。",
            "五苓散主治条文。",
            "《伤寒论》辨太阳病脉证并治中", emptyList()))

        add(SeedCard(DECK_CLS_SHANGHAN, SUBJECT_CLASSICS,
            "《伤寒论》第 82 条原文是什么？",
            "太阳病发汗，汗出不解，其人仍发热，心下悸，头眩，身瞤动，振振欲擗地者，真武汤主之。",
            "真武汤主治条文，属阳虚水泛。",
            "《伤寒论》辨太阳病脉证并治中", listOf("方证")))

        add(SeedCard(DECK_CLS_SHANGHAN, SUBJECT_CLASSICS,
            "《伤寒论》第 263 条原文是什么？",
            "少阳之为病，口苦，咽干，目眩也。",
            "少阳病提纲证。",
            "《伤寒论》辨少阳病脉证并治", listOf("高频", "提纲证")))

        add(SeedCard(DECK_CLS_SHANGHAN, SUBJECT_CLASSICS,
            "《伤寒论》第 273 条原文是什么？",
            "太阴之为病，腹满而吐，食不下，自利益甚，时腹自痛。若下之，必胸下结硬。",
            "太阴病提纲证。",
            "《伤寒论》辨太阴病脉证并治", listOf("高频", "提纲证")))

        add(SeedCard(DECK_CLS_SHANGHAN, SUBJECT_CLASSICS,
            "《伤寒论》第 281 条原文是什么？",
            "少阴之为病，脉微细，但欲寐也。",
            "少阴病提纲证。",
            "《伤寒论》辨少阴病脉证并治", listOf("高频", "提纲证")))

        add(SeedCard(DECK_CLS_SHANGHAN, SUBJECT_CLASSICS,
            "《伤寒论》第 326 条原文是什么？",
            "厥阴之为病，消渴，气上撞心，心中疼热，饥而不欲食，食则吐蛔，下之利不止。",
            "厥阴病提纲证。",
            "《伤寒论》辨厥阴病脉证并治", listOf("高频", "提纲证")))

        add(SeedCard(DECK_CLS_SHANGHAN, SUBJECT_CLASSICS,
            "《伤寒论》六经病提纲证分别是哪六条？",
            "太阳：脉浮，头项强痛而恶寒（第1条）。\n阳明：胃家实是也（第180条）。\n少阳：口苦，咽干，目眩也（第263条）。\n太阴：腹满而吐，食不下，自利益甚，时腹自痛（第273条）。\n少阴：脉微细，但欲寐也（第281条）。\n厥阴：消渴，气上撞心，心中疼热，饥而不欲食，食则吐蛔（第326条）。",
            "六经提纲证是《伤寒论》的总纲，务必整体成诵。",
            "《伤寒论》", listOf("高频", "整体记忆"), "CLOZE"))

        add(SeedCard(DECK_CLS_SHANGHAN, SUBJECT_CLASSICS,
            "《伤寒论》第 180 条原文是什么？",
            "阳明之为病，胃家实是也。",
            "阳明病提纲证。",
            "《伤寒论》辨阳明病脉证并治", listOf("提纲证")))

        // ==================== 经典条文 · 黄帝内经 ====================

        add(SeedCard(DECK_CLS_NEIJING, SUBJECT_CLASSICS,
            "「阴阳者，天地之道也」出自哪一篇？全句是什么？",
            "出自《素问·阴阳应象大论》。\n阴阳者，天地之道也，万物之纲纪，变化之父母，生杀之本始，神明之府也。治病必求于本。",
            "「治病必求于本」为中医治疗学的总原则。",
            "《素问·阴阳应象大论》", listOf("高频", "原文必背")))

        add(SeedCard(DECK_CLS_NEIJING, SUBJECT_CLASSICS,
            "「上古之人，其知道者」一段的完整表述是什么？",
            "上古之人，其知道者，法于阴阳，和于术数，食饮有节，起居有常，不妄作劳，故能形与神俱，而尽终其天年，度百岁乃去。",
            "出自《素问·上古天真论》，为养生总纲。",
            "《素问·上古天真论》", listOf("高频", "原文必背")))

        add(SeedCard(DECK_CLS_NEIJING, SUBJECT_CLASSICS,
            "《素问·生气通天论》中关于阳气的著名论述是什么？",
            "阳气者，若天与日，失其所则折寿而不彰。故天运当以日光明。是故阳因而上，卫外者也。",
            "强调阳气在人体中的主宰地位。",
            "《素问·生气通天论》", listOf("高频")))

        add(SeedCard(DECK_CLS_NEIJING, SUBJECT_CLASSICS,
            "「诸风掉眩」等病机十九条出自哪一篇？前三句是什么？",
            "出自《素问·至真要大论》。\n诸风掉眩，皆属于肝；诸寒收引，皆属于肾；诸气膹郁，皆属于肺。",
            "病机十九条是《内经》病机学说核心，需整体背诵。",
            "《素问·至真要大论》", listOf("高频", "原文必背")))

        add(SeedCard(DECK_CLS_NEIJING, SUBJECT_CLASSICS,
            "病机十九条中「皆属于火」的共有几条？分别是什么？",
            "共 5 条：诸热瞀瘛，皆属于火；诸禁鼓栗，如丧神守，皆属于火；诸逆冲上，皆属于火；诸躁狂越，皆属于火；诸病胕肿，疼酸惊骇，皆属于火。",
            "另有「皆属于热」4 条、「皆属于下」1 条、「皆属于上」1 条。",
            "《素问·至真要大论》", listOf("高频", "易混淆")))

        add(SeedCard(DECK_CLS_NEIJING, SUBJECT_CLASSICS,
            "「诸湿肿满」与「诸痉项强」分别归属于何脏何邪？",
            "诸湿肿满，皆属于脾；诸痉项强，皆属于湿。",
            "前者言病位在脾，后者言病因属湿。",
            "《素问·至真要大论》", listOf("易混淆")))

        add(SeedCard(DECK_CLS_NEIJING, SUBJECT_CLASSICS,
            "「正气存内，邪不可干」的原文出处与完整表述是什么？",
            "出自《素问·刺法论》。\n原文作：「正气存内，邪不可干，避其毒气。」",
            "常与《素问·评热病论》「邪之所凑，其气必虚」并记。",
            "《素问·刺法论》", listOf("高频")))

        add(SeedCard(DECK_CLS_NEIJING, SUBJECT_CLASSICS,
            "「邪之所凑，其气必虚」出自哪一篇？",
            "出自《素问·评热病论》。",
            "阐述发病的正邪关系，是「正虚邪犯」理论的经典依据。",
            "《素问·评热病论》", emptyList()))

        add(SeedCard(DECK_CLS_NEIJING, SUBJECT_CLASSICS,
            "《素问·灵兰秘典论》中「心者，君主之官」一段的完整表述是什么？",
            "心者，君主之官也，神明出焉。肺者，相傅之官，治节出焉。肝者，将军之官，谋虑出焉。胆者，中正之官，决断出焉。膻中者，臣使之官，喜乐出焉。",
            "以官制喻脏腑功能，为中医藏象学说的经典表述。",
            "《素问·灵兰秘典论》", listOf("高频", "原文必背")))

        add(SeedCard(DECK_CLS_NEIJING, SUBJECT_CLASSICS,
            "「不治已病治未病」的原文与出处是什么？",
            "出自《素问·四气调神大论》。\n原文：「是故圣人不治已病治未病，不治已乱治未乱，此之谓也。夫病已成而后药之，乱已成而后治之，譬犹渴而穿井，斗而铸锥，不亦晚乎！」",
            "中医「治未病」思想的源头。",
            "《素问·四气调神大论》", listOf("高频")))

        add(SeedCard(DECK_CLS_NEIJING, SUBJECT_CLASSICS,
            "「阴阳者，天地之道也」属于哪种题型的挖空练习？",
            "阴阳者，{{c1::天地之道}}也，{{c2::万物之纲纪}}，{{c3::变化之父母}}，{{c4::生杀之本始}}，{{c5::神明之府}}也。治病必求于本。",
            "这是一张挖空（CLOZE）示例卡：背到一定程度后，用挖空自测比看整句更有效。",
            "《素问·阴阳应象大论》", listOf("挖空示例"), "CLOZE"))
    }
}
