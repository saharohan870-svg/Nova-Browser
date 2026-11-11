package com.zorvi.engine

internal data class ZorviDiscoveredResource(
    val url: ZorviUrl,
    val type: ZorviResourceType,
    val priority: Int,
    val documentOrder: Int
)

/** Extracts common URL-bearing HTML elements; CSS @import/srcset parsing is not included yet. */
internal object ZorviResourceDiscovery {
    fun discover(document: HtmlDocument, documentUrl: ZorviUrl): List<ZorviDiscoveredResource> {
        val baseHref = document.findAll("base").firstOrNull()?.attributes?.get("href")
        val baseUrl = baseHref?.let { runCatching { ZorviUrl(it, documentUrl) }.getOrNull() } ?: documentUrl
        val found = mutableListOf<ZorviDiscoveredResource>()
        var order = 0

        fun add(raw: String?, type: ZorviResourceType, priority: Int) {
            val value = raw?.trim().orEmpty()
            if (value.isEmpty()) return
            val resolved = runCatching { ZorviUrl(value, baseUrl) }.getOrNull() ?: return
            found += ZorviDiscoveredResource(resolved, type, priority, order++)
        }

        document.findAll("link").forEach { node ->
            val rel = node.attributes["rel"].orEmpty().lowercase().split(Regex("\\s+"))
            when {
                "stylesheet" in rel -> add(node.attributes["href"], ZorviResourceType.STYLESHEET, 0)
                "preload" in rel -> {
                    val type = when (node.attributes["as"]?.lowercase()) {
                        "style" -> ZorviResourceType.STYLESHEET
                        "script" -> ZorviResourceType.SCRIPT
                        "image" -> ZorviResourceType.IMAGE
                        "font" -> ZorviResourceType.FONT
                        "video", "audio" -> ZorviResourceType.MEDIA
                        else -> ZorviResourceType.OTHER
                    }
                    add(node.attributes["href"], type, 1)
                }
                "icon" in rel -> add(node.attributes["href"], ZorviResourceType.IMAGE, 4)
            }
        }
        document.findAll("script").forEach { add(it.attributes["src"], ZorviResourceType.SCRIPT, 2) }
        document.findAll("img").forEach { add(it.attributes["src"], ZorviResourceType.IMAGE, 4) }
        document.findAll("source").forEach { add(it.attributes["src"], ZorviResourceType.MEDIA, 5) }
        document.findAll("video").forEach { add(it.attributes["poster"], ZorviResourceType.IMAGE, 4) }
        document.findAll("audio").forEach { add(it.attributes["src"], ZorviResourceType.MEDIA, 5) }
        return found.sortedWith(compareBy<ZorviDiscoveredResource> { it.priority }.thenBy { it.documentOrder })
    }
}
