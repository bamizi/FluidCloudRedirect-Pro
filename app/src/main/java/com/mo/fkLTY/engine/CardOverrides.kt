package com.mo.fkLTY.engine

import org.json.JSONArray
import org.json.JSONObject

/**
 * 卡片外观的**分条覆盖**。
 *
 * 1.2.18 之前卡片文字只有一份全局配置（`card_text_*` 四个键），
 * 多条替换规则共用同一套文案——想给"夸克口令卡"和另一条规则写不同的话就做不到。
 *
 * 这里引入两层：
 *  - 全局默认（仍存在 `card_text_*` 四个键里，老用户配置不用迁移）；
 *  - 每条替换规则可单独覆盖（本文件，按 `from` 包名索引）。
 * 取文案时先查本条覆盖，没有就回落全局默认。
 *
 * 存储是一个 JSON 数组，元素结构：
 * ```
 * {"from":"com.quark.browser","title":"打开{app}","subtitle":"…","button":"打开","caps":"…"}
 * ```
 * 字段留空 = 该项不改（与全局语义一致）。只存**有内容**的条目，空对象不落盘。
 */
object CardOverrides {

    /** 解析整张覆盖表；坏数据一律当空表，不让它影响正常路径 */
    fun decodeAll(json: String?): Map<String, CardText> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val array = JSONArray(json)
            buildMap {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val from = o.optString("from").trim()
                    if (from.isEmpty()) continue
                    val text = readText(o)
                    if (text.isBlank()) continue
                    put(from, text)
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun encodeAll(map: Map<String, CardText>): String {
        val array = JSONArray()
        map.forEach { (from, text) ->
            if (from.isBlank() || text.isBlank()) return@forEach
            array.put(writeText(text).apply { put("from", from) })
        }
        return array.toString()
    }

    /**
     * 浏览器卡片那一条（不分规则，单独一份）。
     *
     * 默认返回**全空**而不是默认模板：浏览器卡片数量远多于安装卡，
     * 一旦默认成"打开{app}"，用户开开关的瞬间所有网页卡标题都会被改掉。
     * 空 = 什么都不改，用户写哪一项才改哪一项。
     */
    fun decodeOne(json: String?): CardText {
        if (json.isNullOrBlank()) return EMPTY
        return runCatching { readText(JSONObject(json)) }.getOrDefault(EMPTY)
    }

    /** 四项全空 = 不改任何文字 */
    val EMPTY = CardText(title = "", subtitle = "", button = "", caps = "")

    fun encodeOne(text: CardText): String = writeText(text).toString()

    private fun readText(o: JSONObject): CardText = CardText(
        title = o.optString("title", ""),
        subtitle = o.optString("subtitle", ""),
        button = o.optString("button", ""),
        caps = o.optString("caps", ""),
    )

    private fun writeText(text: CardText): JSONObject = JSONObject().apply {
        put("title", text.title)
        put("subtitle", text.subtitle)
        put("button", text.button)
        put("caps", text.caps)
    }
}

/** 四个字段全空 = 这条什么也不改，不必存 */
private fun CardText.isBlank(): Boolean =
    title.isBlank() && subtitle.isBlank() && button.isBlank() && caps.isBlank()
