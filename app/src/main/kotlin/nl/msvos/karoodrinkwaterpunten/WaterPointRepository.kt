package nl.msvos.karoodrinkwaterpunten

import android.content.Context
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WaterPointRepository(private val context: Context) {
    private val parser = WaterPointParser()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val activeFile = File(context.filesDir, ACTIVE_FILE)

    suspend fun load(): PointCache = withContext(Dispatchers.IO) {
        ensureSeeded()
        activeFile.inputStream().buffered().use(parser::parse)
    }

    suspend fun status(): CacheStatus = withContext(Dispatchers.IO) {
        val cache = load()
        CacheStatus(cache.points.size, cache.sourceTime, prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0L })
    }

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        val download = File(context.filesDir, "$ACTIVE_FILE.new")
        val connection = URL(SOURCE_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 90_000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/xml")
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.setRequestProperty("User-Agent", "Drinkwaterpunten-for-Karoo/1.0")
            val request = "data=${URLEncoder.encode(OVERPASS_QUERY, "UTF-8")}"
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(request) }
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> Unit
                else -> error("Serverfout ${connection.responseCode}")
            }
            connection.inputStream.buffered().use { input ->
                download.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            val parsed = download.inputStream().buffered().use(parser::parse)
            require(parsed.points.size >= MIN_VALID_POINT_COUNT) { "Het bestand bevat te weinig geldige punten" }
            Os.rename(download.absolutePath, activeFile.absolutePath)
            prefs.edit()
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .apply()
            SyncResult.Updated(CacheStatus(parsed.points.size, parsed.sourceTime, System.currentTimeMillis()))
        } finally {
            connection.disconnect()
            if (download.exists()) download.delete()
        }
    }

    private fun ensureSeeded() {
        if (activeFile.exists() && activeFile.length() > 0L) return
        context.resources.openRawResource(nl.msvos.karoodrinkwaterpunten.R.raw.publieke_drinkwaterpunten_nl)
            .use { input -> activeFile.outputStream().buffered().use { output -> input.copyTo(output) } }
    }

    sealed class SyncResult {
        abstract val status: CacheStatus
        data class Updated(override val status: CacheStatus) : SyncResult()
    }

    companion object {
        const val CACHE_UPDATED_ACTION = "nl.msvos.karoodrinkwaterpunten.CACHE_UPDATED"
        const val SOURCE_URL = "https://overpass-api.de/api/interpreter"
        const val OVERPASS_QUERY = "[out:xml][timeout:60];(nwr[\"amenity\"=\"drinking_water\"][\"access\"!~\"^(private|no|customers)$\"](area:3600047796);nwr[\"drinking_water\"=\"yes\"][\"access\"!~\"^(private|no|customers)$\"](area:3600047796););out center tags;"
        private const val ACTIVE_FILE = "water_points.osm.xml"
        private const val PREFS_NAME = "water_point_cache"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val MIN_VALID_POINT_COUNT = 1_000
    }
}
