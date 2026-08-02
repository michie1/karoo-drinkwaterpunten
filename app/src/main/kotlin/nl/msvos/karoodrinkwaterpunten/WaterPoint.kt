package nl.msvos.karoodrinkwaterpunten

data class WaterPoint(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

data class PointCache(
    val points: List<WaterPoint>,
    val sourceTime: String?,
)

data class CacheStatus(
    val pointCount: Int,
    val sourceTime: String?,
    val lastSyncMillis: Long?,
)
