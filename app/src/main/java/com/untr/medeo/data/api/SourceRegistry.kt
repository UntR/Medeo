package com.untr.medeo.data.api

data class VodSource(
    val id: String,
    val name: String,
    val baseUrl: String,
    val defaultEnabled: Boolean,
    val needsHttpsBypass: Boolean = false
)

val BUILTIN_SOURCES = listOf(
    VodSource("dbzy", "豆瓣资源", "https://dbzy.tv/api.php/provide/vod", defaultEnabled = true),
    VodSource("dytt", "电影天堂", "http://caiji.dyttzyapi.com/api.php/provide/vod", defaultEnabled = true),
    VodSource("jszy", "极速资源", "https://jszyapi.com/api.php/provide/vod", defaultEnabled = true),
    VodSource("hnzy", "红牛资源", "https://www.hongniuzy2.com/api.php/provide/vod", defaultEnabled = true),
    VodSource("wjzy", "无尽资源", "https://api.wujinapi.me/api.php/provide/vod", defaultEnabled = true),
    VodSource("rycj", "如意资源", "https://cj.rycjapi.com/api.php/provide/vod", defaultEnabled = true),
    VodSource("360zy", "360资源", "https://360zy.com/api.php/provide/vod", defaultEnabled = false),
    VodSource("mdzy", "魔都资源", "https://www.mdzyapi.com/api.php/provide/vod", defaultEnabled = false),
    VodSource("zdzy", "最大资源", "https://api.zuidapi.com/api.php/provide/vod", defaultEnabled = false),
    VodSource("ikun", "iKun资源", "https://ikunzyapi.com/api.php/provide/vod", defaultEnabled = false),
    VodSource("xlzy", "新浪资源", "https://api.xinlangapi.com/xinlangapi.php/provide/vod", defaultEnabled = false),
    VodSource("hhzy", "豪华资源", "https://hhzyapi.com/api.php/provide/vod", defaultEnabled = false),
    VodSource("sbzy", "速博资源", "https://subocaiji.com/api.php/provide/vod", defaultEnabled = false),
    VodSource("plzy", "飘零资源", "https://p2100.net/api.php/provide/vod", defaultEnabled = false),
    VodSource("iqyzy", "爱奇艺资源", "https://iqiyizyapi.com/api.php/provide/vod", defaultEnabled = false)
)

val DEFAULT_ENABLED_SOURCE_IDS: Set<String> =
    BUILTIN_SOURCES.filter { it.defaultEnabled }.mapTo(linkedSetOf()) { it.id }

val PLAYBACK_SOURCE_PRIORITY: List<String> = listOf(
    "dbzy",
    "dytt",
    "jszy",
    "hnzy",
    "wjzy",
    "rycj",
    "360zy",
    "mdzy",
    "zdzy",
    "ikun",
    "xlzy",
    "hhzy",
    "sbzy",
    "plzy",
    "iqyzy"
)
