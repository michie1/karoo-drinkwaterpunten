package nl.msvos.karoodrinkwaterpunten.extension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HideSymbols
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.ShowSymbols
import io.hammerhead.karooext.models.Symbol
import kotlinx.coroutines.*
import nl.msvos.karoodrinkwaterpunten.*
import nl.msvos.karoodrinkwaterpunten.BuildConfig

class DrinkwaterpuntenExtension : KarooExtension("drinkwaterpunten", BuildConfig.VERSION_NAME) {
    private lateinit var karooSystem: KarooSystemService
    private lateinit var repository: WaterPointRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var points: List<WaterPoint> = emptyList()
    private var location: GeoPoint? = null
    private var route: List<GeoPoint> = emptyList()
    private var lastSelectionLocation: GeoPoint? = null
    private var mapEmitter: Emitter<MapEffect>? = null
    private var locationConsumerId: String? = null
    private var navigationConsumerId: String? = null
    private var shownIds: Set<String> = emptySet()
    private var renderJob: Job? = null

    private val cacheReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WaterPointRepository.CACHE_UPDATED_ACTION) reloadAndRender()
        }
    }

    override fun onCreate() {
        super.onCreate()
        karooSystem = KarooSystemService(this)
        repository = WaterPointRepository(this)
        ContextCompat.registerReceiver(
            this,
            cacheReceiver,
            IntentFilter(WaterPointRepository.CACHE_UPDATED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        karooSystem.connect { connected -> Log.i(TAG, "Karoo System connected: $connected") }
        reloadAndRender()
    }

    override fun startMap(emitter: Emitter<MapEffect>) {
        mapEmitter = emitter
        shownIds = emptySet()
        subscribeToMapInputs()
        render()
        emitter.setCancellable {
            locationConsumerId?.let(karooSystem::removeConsumer)
            navigationConsumerId?.let(karooSystem::removeConsumer)
            locationConsumerId = null
            navigationConsumerId = null
            mapEmitter = null
            shownIds = emptySet()
        }
    }

    private fun subscribeToMapInputs() {
        locationConsumerId?.let(karooSystem::removeConsumer)
        navigationConsumerId?.let(karooSystem::removeConsumer)
        locationConsumerId = karooSystem.addConsumer<OnLocationChanged>(
            onError = { Log.w(TAG, "Location stream: $it") },
        ) { event ->
            val next = GeoPoint(event.lat, event.lng)
            location = next
            val previous = lastSelectionLocation
            if (previous == null || Geo.haversineMeters(previous, next) >= REFRESH_DISTANCE_METERS) {
                lastSelectionLocation = next
                render()
            }
        }
        navigationConsumerId = karooSystem.addConsumer<OnNavigationState>(
            onError = { Log.w(TAG, "Navigation stream: $it") },
        ) { event ->
            route = try {
                when (val state = event.state) {
                    is OnNavigationState.NavigationState.NavigatingRoute -> Geo.decodePolyline(state.routePolyline)
                    is OnNavigationState.NavigationState.NavigatingToDestination -> Geo.decodePolyline(state.polyline)
                    is OnNavigationState.NavigationState.Idle -> emptyList()
                }
            } catch (error: IllegalArgumentException) {
                Log.w(TAG, "Invalid route polyline", error)
                emptyList()
            }
            render()
        }
    }

    private fun reloadAndRender() {
        scope.launch {
            runCatching { repository.load() }
                .onSuccess {
                    points = it.points
                    render()
                }
                .onFailure { Log.e(TAG, "Could not load water points", it) }
        }
    }

    @Synchronized
    private fun render() {
        val emitter = mapEmitter ?: return
        val pointSnapshot = points
        val locationSnapshot = location
        val routeSnapshot = route
        renderJob?.cancel()
        renderJob = scope.launch {
            val selected = PointSelector.select(pointSnapshot, locationSnapshot, routeSnapshot)
            val nextIds = selected.asSequence().map { it.id }.toSet()
            val removed = shownIds - nextIds
            if (removed.isNotEmpty()) emitter.onNext(HideSymbols(removed.toList()))
            if (selected.isNotEmpty()) {
                emitter.onNext(ShowSymbols(selected.map { point ->
                    Symbol.POI(
                        id = point.id,
                        lat = point.latitude,
                        lng = point.longitude,
                        type = Symbol.POI.Types.WATER,
                        name = point.name,
                    )
                }))
            }
            shownIds = nextIds
            Log.i(TAG, "Showing ${selected.size} water points")
        }
    }

    override fun onDestroy() {
        locationConsumerId?.let(karooSystem::removeConsumer)
        navigationConsumerId?.let(karooSystem::removeConsumer)
        unregisterReceiver(cacheReceiver)
        karooSystem.disconnect()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "Drinkwaterpunten"
        const val REFRESH_DISTANCE_METERS = 2_000.0
    }
}
