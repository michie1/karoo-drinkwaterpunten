package nl.msvos.karoodrinkwaterpunten

import org.junit.Assert.assertEquals
import org.junit.Test

class GpxParserTest {
    @Test
    fun `parses metadata and deduplicates source ids`() {
        val xml = """
            <gpx version="1.1">
              <metadata><time>2026-08-01T09:35:42+02:00</time></metadata>
              <wpt lat="52.1" lon="5.1"><name>Kraan</name><link href="https://drinkwaterpunten.nl?id=42" /></wpt>
              <wpt lat="52.2" lon="5.2"><name>Dubbel</name><link href="https://drinkwaterpunten.nl?id=42" /></wpt>
              <wpt lat="999" lon="5.3"><name>Fout</name></wpt>
            </gpx>
        """.trimIndent()
        val parsed = WaterPointParser().parse(xml.byteInputStream())
        assertEquals("2026-08-01T09:35:42+02:00", parsed.sourceTime)
        assertEquals(1, parsed.points.size)
        assertEquals("42", parsed.points.single().id)
        assertEquals("Kraan", parsed.points.single().name)
    }

    @Test
    fun `parses osm nodes and way centres`() {
        val xml = """
            <osm>
              <meta osm_base="2026-08-19T12:00:00Z"/>
              <node id="42" lat="52.1" lon="5.1"><tag k="amenity" v="drinking_water"/><tag k="name" v="Kraan"/></node>
              <way id="84"><center lat="52.2" lon="5.2"/><tag k="amenity" v="drinking_water"/></way>
              <relation id="126"><center lat="52.3" lon="5.3"/><tag k="amenity" v="drinking_water"/></relation>
            </osm>
        """.trimIndent()
        val parsed = WaterPointParser().parse(xml.byteInputStream())
        assertEquals("2026-08-19T12:00:00Z", parsed.sourceTime)
        assertEquals(3, parsed.points.size)
        assertEquals("node_42", parsed.points[0].id)
        assertEquals("Kraan", parsed.points[0].name)
        assertEquals("way_84", parsed.points[1].id)
        assertEquals("Drinkwaterpunt", parsed.points[1].name)
        assertEquals("relation_126", parsed.points[2].id)
    }
}
