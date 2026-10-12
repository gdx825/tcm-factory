package com.tcmfactory.data.seed

/**
 * 内置示例题库。
 *
 * 目的：让用户开箱即可体验「顺序 / 随机 / 章节 / 错题重做」全部模式，
 * 同时充当**题库 JSON 格式的活文档** —— 用户照着这个格式整理自己的题库即可导入。
 *
 * 数据来源：《中国药典》2025 年版一部、GB/T 12346-2021《经穴名称与定位》、
 * 中国中医药出版社规划教材《方剂学》。均为事实性知识点。
 */
object SeedQuestionPacks {

    val packs: List<String> by lazy {
        listOf(materiaMedica, formula, acupuncture)
    }

    // ==================================================================
    //  中药学
    // ==================================================================
    private val materiaMedica = """
    {
      "schemaVersion": 1,
      "packName": "中药学 · 解表药与清热药",
      "packVersion": "1.0.0",
      "subject": "MATERIA_MEDICA",
      "sourceRef": "《中国药典》2025年版一部",
      "questions": [
        {
          "chapter": "解表药",
          "type": "SINGLE",
          "stem": "麻黄的功效不包括下列哪一项？",
          "options": ["发汗解表", "宣肺平喘", "利水消肿", "温通经脉"],
          "answer": "D",
          "explanation": "温通经脉为桂枝的功效。麻黄的功效是发汗解表、宣肺平喘、利水消肿。",
          "difficulty": 1,
          "tags": ["高频", "易混淆"]
        },
        {
          "chapter": "解表药",
          "type": "SINGLE",
          "stem": "具有「发汗解肌，温通经脉，助阳化气，平冲降气」功效的药物是？",
          "options": ["麻黄", "桂枝", "紫苏叶", "荆芥"],
          "answer": "B",
          "explanation": "桂枝为「伤寒论」群方之冠桂枝汤的君药，发汗力缓而长于温通。",
          "difficulty": 1,
          "tags": ["高频"]
        },
        {
          "chapter": "解表药",
          "type": "SINGLE",
          "stem": "细辛的常用内服剂量是？",
          "options": ["1~3g", "3~10g", "10~15g", "15~30g"],
          "answer": "A",
          "explanation": "细辛辛温走窜，用量不宜过大，药典规定 1~3g；反藜芦。",
          "difficulty": 2,
          "tags": ["用量考点"]
        },
        {
          "chapter": "解表药",
          "type": "MULTI",
          "stem": "下列属于桂枝功效的有哪些？",
          "options": ["发汗解肌", "温通经脉", "助阳化气", "利水消肿"],
          "answer": "ABC",
          "explanation": "利水消肿是麻黄的功效。桂枝的四项功效为发汗解肌、温通经脉、助阳化气、平冲降气。",
          "difficulty": 2,
          "tags": ["易混淆"]
        },
        {
          "chapter": "解表药",
          "type": "JUDGE",
          "stem": "薄荷入汤剂宜后下。",
          "answer": "对",
          "explanation": "薄荷含挥发油，久煎有效成分易散失，故宜后下。",
          "difficulty": 1
        },
        {
          "chapter": "清热药",
          "type": "SINGLE",
          "stem": "石膏入汤剂的正确用法是？",
          "options": ["先煎", "后下", "包煎", "烊化"],
          "answer": "A",
          "explanation": "石膏为矿物类药，质地坚硬，需先煎以利于有效成分溶出。",
          "difficulty": 1,
          "tags": ["煎法考点"]
        },
        {
          "chapter": "清热药",
          "type": "SINGLE",
          "stem": "善清中焦脾胃湿热的药物是？",
          "options": ["黄芩", "黄连", "黄柏", "栀子"],
          "answer": "B",
          "explanation": "黄芩善清上焦肺火，黄连善清中焦脾胃与心经之火，黄柏善清下焦湿热。",
          "difficulty": 2,
          "tags": ["高频", "易混淆"]
        },
        {
          "chapter": "清热药",
          "type": "SINGLE",
          "stem": "黄芩、黄连、黄柏三药的共同功效是？",
          "options": ["清热燥湿", "滋阴润燥", "凉血止血", "退虚热"],
          "answer": "A",
          "explanation": "三药同为清热燥湿药，区别在于作用部位：黄芩走上焦、黄连走中焦、黄柏走下焦。",
          "difficulty": 2,
          "tags": ["高频"]
        },
        {
          "chapter": "清热药",
          "type": "SINGLE",
          "stem": "具有「清热解毒，疏散风热」功效，被称为治一切内外痈肿要药的药物是？",
          "options": ["连翘", "金银花", "蒲公英", "板蓝根"],
          "answer": "B",
          "explanation": "金银花与连翘常相须为用；连翘有「疮家圣药」之称，兼能消肿散结、清心利尿。",
          "difficulty": 2,
          "tags": ["易混淆"]
        },
        {
          "chapter": "清热药",
          "type": "MULTI",
          "stem": "下列属于黄连功效的有哪些？",
          "options": ["清热燥湿", "泻火解毒", "滋阴润燥", "安胎"],
          "answer": "AB",
          "explanation": "滋阴润燥是知母的功效，安胎是黄芩的功效。黄连功效为清热燥湿、泻火解毒。",
          "difficulty": 2
        }
      ]
    }
    """.trimIndent()

    // ==================================================================
    //  方剂学
    // ==================================================================
    private val formula = """
    {
      "schemaVersion": 1,
      "packName": "方剂学 · 常用方剂组成与主治",
      "packVersion": "1.0.0",
      "subject": "FORMULA",
      "sourceRef": "规划教材《方剂学》",
      "questions": [
        {
          "chapter": "解表剂",
          "type": "MULTI",
          "stem": "麻黄汤的组成包括下列哪些药物？",
          "options": ["麻黄", "桂枝", "杏仁", "甘草", "生姜"],
          "answer": "ABCD",
          "explanation": "麻黄汤组成：麻黄、桂枝、杏仁、甘草。功用发汗解表、宣肺平喘，主治外感风寒表实证。",
          "difficulty": 1,
          "tags": ["组成必背"]
        },
        {
          "chapter": "解表剂",
          "type": "SINGLE",
          "stem": "桂枝汤中桂枝与芍药的用量比例是？",
          "options": ["1:1", "1:2", "2:1", "3:2"],
          "answer": "A",
          "explanation": "桂枝与芍药等量配伍，一散一收，调和营卫，是桂枝汤配伍的精妙之处。",
          "difficulty": 2,
          "tags": ["高频"]
        },
        {
          "chapter": "解表剂",
          "type": "SINGLE",
          "stem": "银翘散的功用是？",
          "options": ["辛凉透表，清热解毒", "疏风清热，宣肺止咳", "发汗祛湿，兼清里热", "解肌发表，调和营卫"],
          "answer": "A",
          "explanation": "银翘散为「辛凉平剂」；桑菊饮为「辛凉轻剂」，功用疏风清热、宣肺止咳。",
          "difficulty": 2,
          "tags": ["高频", "易混淆"]
        },
        {
          "chapter": "和解剂",
          "type": "SINGLE",
          "stem": "小柴胡汤的功用是？",
          "options": ["解表散寒", "和解少阳", "清热生津", "温阳利水"],
          "answer": "B",
          "explanation": "小柴胡汤为「和剂之祖」，功用和解少阳，主治伤寒少阳证。",
          "difficulty": 1,
          "tags": ["高频"]
        },
        {
          "chapter": "清热剂",
          "type": "SINGLE",
          "stem": "白虎汤主治的「四大症」不包括下列哪一项？",
          "options": ["大热", "大汗", "大渴", "大便秘结"],
          "answer": "D",
          "explanation": "白虎汤主治气分热盛证，四大症为大热、大汗、大渴、脉洪大。",
          "difficulty": 2,
          "tags": ["高频"]
        },
        {
          "chapter": "补益剂",
          "type": "MULTI",
          "stem": "四君子汤的组成包括下列哪些药物？",
          "options": ["人参", "白术", "茯苓", "甘草", "当归"],
          "answer": "ABCD",
          "explanation": "四君子汤组成：人参、白术、茯苓、甘草，功用益气健脾，为补气方之祖。",
          "difficulty": 1,
          "tags": ["组成必背"]
        },
        {
          "chapter": "补益剂",
          "type": "SINGLE",
          "stem": "六味地黄丸中「三补」指的是哪三味药？",
          "options": ["熟地黄、山茱萸、山药", "熟地黄、泽泻、牡丹皮", "山药、茯苓、泽泻", "山茱萸、牡丹皮、茯苓"],
          "answer": "A",
          "explanation": "三补为熟地黄、山茱萸、山药；三泻为泽泻、牡丹皮、茯苓，补泻兼施。",
          "difficulty": 2,
          "tags": ["高频", "易混淆"]
        },
        {
          "chapter": "补益剂",
          "type": "SINGLE",
          "stem": "补中益气汤的君药是？",
          "options": ["人参", "黄芪", "白术", "升麻"],
          "answer": "B",
          "explanation": "黄芪补中益气、升阳固表为君；升麻、柴胡为佐使，升举下陷之阳气。",
          "difficulty": 2,
          "tags": ["高频"]
        },
        {
          "chapter": "泻下剂",
          "type": "SINGLE",
          "stem": "大承气汤的正确煎服方法是？",
          "options": [
            "先煎枳实、厚朴，后下大黄，芒硝溶服",
            "四药同煎",
            "先煎大黄",
            "芒硝与他药同煎"
          ],
          "answer": "A",
          "explanation": "大黄后下取其泻下之力，芒硝溶服以免煎煮失效，枳实厚朴先煎以行气除满。",
          "difficulty": 3,
          "tags": ["煎法考点"]
        }
      ]
    }
    """.trimIndent()

    // ==================================================================
    //  针灸学
    // ==================================================================
    private val acupuncture = """
    {
      "schemaVersion": 1,
      "packName": "针灸学 · 常用腧穴定位",
      "packVersion": "1.0.0",
      "subject": "ACUPUNCTURE",
      "sourceRef": "GB/T 12346-2021《经穴名称与定位》",
      "questions": [
        {
          "chapter": "常用腧穴",
          "type": "SINGLE",
          "stem": "合谷穴（LI4）的定位是？",
          "options": [
            "在手背，第 2 掌骨桡侧的中点处",
            "在腕背侧远端横纹上 2 寸",
            "在肘区，尺泽与肱骨外上髁连线的中点处",
            "在手指，拇指末节桡侧，指甲根角侧上方 0.1 寸"
          ],
          "answer": "A",
          "explanation": "合谷为手阳明大肠经原穴。孕妇慎用。四总穴「面口合谷收」。",
          "difficulty": 1,
          "tags": ["高频"]
        },
        {
          "chapter": "常用腧穴",
          "type": "SINGLE",
          "stem": "足三里穴（ST36）位于犊鼻下几寸？",
          "options": ["2 寸", "3 寸", "4 寸", "5 寸"],
          "answer": "B",
          "explanation": "足三里在小腿外侧，犊鼻下 3 寸，犊鼻与解溪连线上。为胃下合穴、强壮保健要穴。",
          "difficulty": 1,
          "tags": ["高频"]
        },
        {
          "chapter": "常用腧穴",
          "type": "MULTI",
          "stem": "下列属于足阳明胃经的腧穴有哪些？",
          "options": ["足三里", "丰隆", "内庭", "三阴交"],
          "answer": "ABC",
          "explanation": "三阴交属足太阴脾经，是足三阴经（脾、肝、肾）的交会穴。",
          "difficulty": 2,
          "tags": ["易混淆"]
        },
        {
          "chapter": "常用腧穴",
          "type": "SINGLE",
          "stem": "内关穴（PC6）的定位是？",
          "options": [
            "在前臂前区，腕掌侧远端横纹上 2 寸，掌长肌腱与桡侧腕屈肌腱之间",
            "在前臂后区，腕背侧远端横纹上 2 寸，尺骨与桡骨间隙中点",
            "在前臂前区，腕掌侧远端横纹上 1 寸，尺侧腕屈肌腱的桡侧缘",
            "在腕前区，腕掌侧远端横纹尺侧端，尺侧腕屈肌腱的桡侧缘"
          ],
          "answer": "A",
          "explanation": "B 为外关（TE5），C 为通里（HT5），D 为神门（HT7）。内关为心包经络穴、八脉交会穴（通阴维脉）。",
          "difficulty": 3,
          "tags": ["高频", "易混淆"]
        },
        {
          "chapter": "常用腧穴",
          "type": "SINGLE",
          "stem": "三阴交（SP6）是哪三条经脉的交会穴？",
          "options": [
            "足太阴、足厥阴、足少阴",
            "足太阴、足阳明、足少阳",
            "足少阴、足太阳、足厥阴",
            "足太阴、足少阳、足厥阴"
          ],
          "answer": "A",
          "explanation": "三阴交为足太阴脾经穴，是足三阴经（脾、肝、肾）交会穴。孕妇慎用。",
          "difficulty": 2,
          "tags": ["高频"]
        },
        {
          "chapter": "常用腧穴",
          "type": "SINGLE",
          "stem": "风池穴（GB20）的定位是？",
          "options": [
            "在颈后区，枕骨之下，胸锁乳突肌上端与斜方肌上端之间的凹陷中",
            "在项部，当枕骨之下，与风府相平",
            "在头部，当前发际正中直上 1 寸",
            "在肩胛区，肩胛冈中点与肩胛骨下角连线上"
          ],
          "answer": "A",
          "explanation": "风池属足少阳胆经。深部为延髓，针刺须严格掌握方向与深度，属危险穴位。",
          "difficulty": 3,
          "tags": ["危险穴位"]
        }
      ]
    }
    """.trimIndent()

    /** 供文档展示：题库 JSON 格式的最小示例。 */
    val minimalExample: String = """
    {
      "schemaVersion": 1,
      "packName": "我的题库",
      "subject": "CUSTOM",
      "questions": [
        {
          "chapter": "第一章",
          "type": "SINGLE",
          "stem": "题干写在这里？",
          "options": ["选项一", "选项二", "选项三", "选项四"],
          "answer": "A",
          "explanation": "解析写在这里（可省略）"
        },
        {
          "type": "JUDGE",
          "stem": "判断题可以不写选项，系统会自动补「正确 / 错误」。",
          "answer": "对"
        },
        {
          "type": "MULTI",
          "stem": "多选题答案写成连续字母或逗号分隔均可。",
          "options": ["甲", "乙", "丙", "丁"],
          "answer": "A,C"
        }
      ]
    }
    """.trimIndent()
}
