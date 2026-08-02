package nl.msvos.karoodrinkwaterpunten

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

class GpxParser {
    fun parse(input: InputStream): PointCache {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(input, "UTF-8")
        }
        val points = mutableListOf<WaterPoint>()
        var sourceTime: String? = null
        var inMetadata = false
        var latitude: Double? = null
        var longitude: Double? = null
        var name: String? = null
        var sourceId: String? = null

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "metadata" -> inMetadata = true
                    "wpt" -> {
                        latitude = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                        longitude = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                        name = null
                        sourceId = null
                    }
                    "name" -> if (latitude != null) name = parser.nextText().trim()
                    "time" -> if (inMetadata) sourceTime = parser.nextText().trim()
                    "link" -> if (latitude != null && sourceId == null) {
                        val href = parser.getAttributeValue(null, "href").orEmpty()
                        sourceId = ID_PATTERN.find(href)?.groupValues?.get(1)
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "metadata" -> inMetadata = false
                    "wpt" -> {
                        val lat = latitude
                        val lon = longitude
                        if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                            val id = sourceId ?: "${lat.toString().replace('.', '_')}_${lon.toString().replace('.', '_')}"
                            points += WaterPoint(id, name?.ifBlank { "Drinkwaterpunt" } ?: "Drinkwaterpunt", lat, lon)
                        }
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
        val ID_PATTERN = Regex("[?&]id=(\\d+)")
    }
}
