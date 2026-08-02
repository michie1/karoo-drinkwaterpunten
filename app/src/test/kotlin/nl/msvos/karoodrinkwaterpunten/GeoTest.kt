package nl.msvos.karoodrinkwaterpunten

import org.junit.Assert.*
import org.junit.Test

class GeoTest {
    @Test
    fun `decodes a standard precision five polyline`() {
        val points = Geo.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, points.size)
        assertEquals(38.5, points[0].latitude, 0.00001)
        assertEquals(-120.2, points[0].longitude, 0.00001)
    }

    @Test
    fun `measures distance from a route segment`() {
        val route = listOf(GeoPoint(52.0, 5.0), GeoPoint(52.1, 5.0))
        val distance = Geo.distanceToPolylineMeters(GeoPoint(52.05, 5.01), route)
        assertTrue(distance in 680.0..690.0)
    }

    @Test
    fun `selects nearby points and route points`() {
        val location = GeoPoint(52.0, 5.0)
        val points = listOf(
            WaterPoint("near", "Near", 52.01, 5.0),
            WaterPoint("route", "Route", 52.5, 5.01),
            WaterPoint("far", "Far", 53.5, 7.0),
        )
        val route = listOf(GeoPoint(52.4, 5.0), GeoPoint(52.6, 5.0))
        assertEquals(listOf("near", "route"), PointSelector.select(points, location, route).map { it.id })
    }

    @Test
    fun `nearby points have priority and result is capped`() {
        val location = GeoPoint(52.0, 5.0)
        val nearby = (0 until 300).map { index ->
            WaterPoint("p$index", "Point $index", 52.0, 5.0 + index * 0.00001)
        }
        val selected = PointSelector.select(nearby, location, emptyList())
        assertEquals(PointSelector.MAX_POINTS, selected.size)
        assertEquals("p0", selected.first().id)
    }
}
