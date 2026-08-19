package nl.msvos.karoodrinkwaterpunten

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

class WaterPointParser {
    fun parse(input: InputStream): PointCache {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(input, "UTF-8")
        }
        val points = mutableListOf<WaterPoint>()
        var sourceTime: String? = null
        var inMetadata = false
        var elementType: String? = null
        var elementId: String? = null
        var latitude: Double? = null
        var longitude: Double? = null
        var name: String? = null
        var sourceId: String? = null

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "osm" -> sourceTime = parser.getAttributeValue(null, "timestamp_osm_base")
                    "meta" -> sourceTime = parser.getAttributeValue(null, "osm_base") ?: sourceTime
                    "metadata" -> inMetadata = true
                    "wpt" -> {
                        elementType = "node"
                        elementId = null
                        latitude = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                        longitude = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                        name = null
                        sourceId = null
                    }
                    "node", "way", "relation" -> {
                        elementType = parser.name
                        elementId = parser.getAttributeValue(null, "id")
                        latitude = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                        longitude = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                        name = null
                        sourceId = null
                    }
                    "center" -> if (elementType != null) {
                        latitude = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                        longitude = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                    }
                    "tag" -> if (parser.getAttributeValue(null, "k") == "name") {
                        name = parser.getAttributeValue(null, "v")
                    }
                    "name" -> if (latitude != null) name = parser.nextText().trim()
                    "time" -> if (inMetadata) sourceTime = parser.nextText().trim()
                    "link" -> if (latitude != null && sourceId == null) {
                        val href = parser.getAttributeValue(null, "href").orEmpty()
                        sourceId = GPX_ID_PATTERN.find(href)?.groupValues?.get(1)
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "metadata" -> inMetadata = false
                    "wpt", "node", "way", "relation" -> {
                        val lat = latitude
                        val lon = longitude
                        if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                            val id = elementId?.let { "${elementType}_$it" }
                                ?: sourceId
                                ?: "${lat.toString().replace('.', '_')}_${lon.toString().replace('.', '_')}"
                            points += WaterPoint(
                                id,
                                name?.ifBlank { DEFAULT_NAME } ?: DEFAULT_NAME,
                                lat,
                                lon,
                            )
                        }
                        elementType = null
                        elementId = null
                        latitude = null
                        longitude = null
                    }
                }
            }
            parser.next()
        }
        return PointCache(points.distinctBy { it.id }, sourceTime)
    }

    private companion object {
        const val DEFAULT_NAME = "Drinkwaterpunt"
        val GPX_ID_PATTERN = Regex("[?&]id=(\\d+)")
    }
}
