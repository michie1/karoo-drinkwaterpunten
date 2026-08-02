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
        val parsed = GpxParser().parse(xml.byteInputStream())
        assertEquals("2026-08-01T09:35:42+02:00", parsed.sourceTime)
        assertEquals(1, parsed.points.size)
        assertEquals("42", parsed.points.single().id)
        assertEquals("Kraan", parsed.points.single().name)
    }
}
