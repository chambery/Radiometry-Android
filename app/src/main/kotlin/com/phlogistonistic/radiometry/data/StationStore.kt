package com.phlogistonistic.radiometry.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.io.File

class StationStore(context: Context) {
    private val file = File(context.filesDir, "stations.json")
    private val prefs = context.getSharedPreferences("stations", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _stations = MutableStateFlow(load())
    val stations: StateFlow<List<Station>> = _stations.asStateFlow()

    init {
        prefs.edit { putStringSet(SEEDED_KEY, Station.defaults.map { it.url }.toSet()) }
        save()
    }

    fun add(station: Station) = mutate { it + station }

    fun update(station: Station) = mutate { list -> list.map { if (it.id == station.id) station else it } }

    fun delete(id: String) = mutate { list -> list.filterNot { it.id == id } }

    fun toggleFavorite(id: String) =
        mutate { list -> list.map { if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it } }

    fun resetToDefaults() = mutate { Station.defaults.map { it.copy() } }

    fun find(id: String?): Station? = id?.let { wanted -> _stations.value.firstOrNull { it.id == wanted } }

    private fun mutate(transform: (List<Station>) -> List<Station>) {
        _stations.update(transform)
        save()
    }

    private fun load(): List<Station> {
        val saved = runCatching { json.decodeFromString<List<Station>>(file.readText()) }.getOrNull()
            ?: return Station.defaults
        return addingNewDefaults(saved)
    }

    /**
     * Inserts defaults added in later versions into an existing list, once, at their default position.
     * A default the user deleted after it was seeded stays deleted.
     */
    private fun addingNewDefaults(stations: List<Station>): List<Station> {
        val seeded = prefs.getStringSet(SEEDED_KEY, null) ?: Station.defaults.map { it.url }.toSet()
        val existing = stations.map { it.url }.toSet()
        val result = stations.toMutableList()
        var insertAt = 0
        for (station in Station.defaults) {
            val index = result.indexOfFirst { it.url == station.url }
            if (index >= 0) {
                insertAt = index + 1
            } else if (station.url !in seeded && station.url !in existing) {
                result.add(insertAt.coerceAtMost(result.size), station)
                insertAt++
            }
        }
        return result
    }

    private fun save() {
        runCatching { file.writeText(json.encodeToString(_stations.value)) }
    }

    private companion object {
        const val SEEDED_KEY = "seededDefaultStationURLs"
    }
}
