package nl.msvos.karoodrinkwaterpunten

import kotlin.math.*

data class GeoPoint(val latitude: Double, val longitude: Double)

object Geo {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun distanceToPolylineMeters(point: GeoPoint, route: List<GeoPoint>): Double {
        if (route.isEmpty()) return Double.POSITIVE_INFINITY
        if (route.size == 1) return haversineMeters(point, route.first())
        var minimum = Double.POSITIVE_INFINITY
        for (index in 0 until route.lastIndex) {
            minimum = min(minimum, distanceToSegmentMeters(point, route[index], route[index + 1]))
            if (minimum < 1.0) return minimum
        }
        return minimum
    }

    private fun distanceToSegmentMeters(point: GeoPoint, start: GeoPoint, end: GeoPoint): Double {
        val referenceLat = Math.toRadians(point.latitude)
        fun x(longitude: Double) = Math.toRadians(longitude - point.longitude) * cos(referenceLat) * EARTH_RADIUS_METERS
        fun y(latitude: Double) = Math.toRadians(latitude - point.latitude) * EARTH_RADIUS_METERS
        val ax = x(start.longitude)
        val ay = y(start.latitude)
        val bx = x(end.longitude)
        val by = y(end.latitude)
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0.0) 0.0 else (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
        return hypot(ax + t * dx, ay + t * dy)
    }

    fun decodePolyline(encoded: String): List<GeoPoint> {
        val result = mutableListOf<GeoPoint>()
        var index = 0
        var latitude = 0
        var longitude = 0
        while (index < encoded.length) {
            val latResult = decodeValue(encoded, index)
            index = latResult.nextIndex
            latitude += latResult.value
            val lonResult = decodeValue(encoded, index)
            index = lonResult.nextIndex
            longitude += lonResult.value
            result += GeoPoint(latitude / 1e5, longitude / 1e5)
        }
        return result
    }

    private fun decodeValue(encoded: String, start: Int): DecodedValue {
        var index = start
        var result = 0
        var shift = 0
        var value: Int
        do {
            require(index < encoded.length) { "Invalid encoded polyline" }
            value = encoded[index++].code - 63
            result = result or ((value and 0x1f) shl shift)
            shift += 5
        } while (value >= 0x20)
        val decoded = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        return DecodedValue(decoded, index)
    }

    private data class DecodedValue(val value: Int, val nextIndex: Int)
}

object PointSelector {
    const val NEARBY_RADIUS_METERS = 30_000.0
    const val ROUTE_RADIUS_METERS = 5_000.0
    const val MAX_POINTS = 250

    fun select(points: List<WaterPoint>, location: GeoPoint?, route: List<GeoPoint>): List<WaterPoint> {
        val distances = if (location == null) emptyMap() else points.associateWith {
            Geo.haversineMeters(location, GeoPoint(it.latitude, it.longitude))
        }
        val nearby = distances.asSequence()
            .filter { it.value <= NEARBY_RADIUS_METERS }
            .sortedBy { it.value }
            .map { it.key }
            .take(MAX_POINTS)
            .toMutableList()
        if (nearby.size == MAX_POINTS || route.isEmpty()) return nearby

        val nearbyIds = nearby.asSequence().map { it.id }.toHashSet()
        val routePoints = points.asSequence()
            .filterNot { it.id in nearbyIds }
            .filter { Geo.distanceToPolylineMeters(GeoPoint(it.latitude, it.longitude), route) <= ROUTE_RADIUS_METERS }
            .sortedBy { distances[it] ?: Double.MAX_VALUE }
            .take(MAX_POINTS - nearby.size)
        nearby += routePoints
        return nearby
    }
}
