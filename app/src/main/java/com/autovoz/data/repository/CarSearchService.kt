package com.autovoz.data.repository

import android.util.Log
import com.autovoz.domain.model.CargoVehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Car search:
 * 1. First searches local built-in database (instant, offline)
 * 2. If not found locally — queries api-ninjas.com/v1/cars (needs internet)
 *
 * API Ninjas returns: make, model, year, curb_weight_kg (or lbs), length, width, height
 * Free tier: 10,000 requests/month
 *
 * To get your free API key: https://api-ninjas.com/register
 */
class CarSearchService {

    companion object {
        // Replace with your key from https://api-ninjas.com/register (free)
        private const val API_KEY = "YOUR_API_NINJAS_KEY_HERE"
        private const val API_URL = "https://api.api-ninjas.com/v1/cars"
    }

    // ── Local database ────────────────────────────────────────────────────────
    // Format: name, mass(kg), length(m), width(m), height(m)
    private val localCars = listOf(
        CarEntry("Lada Granta седан", 1085, 4.257f, 1.700f, 1.500f),
        CarEntry("Lada Granta лифтбек", 1095, 4.257f, 1.700f, 1.510f),
        CarEntry("Lada Vesta седан", 1195, 4.410f, 1.764f, 1.497f),
        CarEntry("Lada Vesta SW", 1280, 4.418f, 1.764f, 1.530f),
        CarEntry("Lada Vesta SW Cross", 1340, 4.418f, 1.764f, 1.640f),
        CarEntry("Lada Niva Legend", 1210, 3.720f, 1.680f, 1.640f),
        CarEntry("Lada Niva Travel", 1380, 4.240f, 1.790f, 1.630f),
        CarEntry("Lada Largus универсал", 1350, 4.470f, 1.780f, 1.636f),
        CarEntry("Lada XRAY", 1220, 4.162f, 1.777f, 1.558f),
        CarEntry("Kia Rio седан", 1185, 4.370f, 1.729f, 1.450f),
        CarEntry("Kia Rio хэтчбек", 1165, 4.065f, 1.729f, 1.455f),
        CarEntry("Kia Ceed хэтчбек", 1290, 4.310f, 1.800f, 1.450f),
        CarEntry("Kia Sportage", 1565, 4.515f, 1.865f, 1.645f),
        CarEntry("Kia Soul", 1380, 4.195f, 1.800f, 1.605f),
        CarEntry("Kia K5", 1535, 4.905f, 1.860f, 1.445f),
        CarEntry("Hyundai Solaris седан", 1165, 4.400f, 1.729f, 1.450f),
        CarEntry("Hyundai Elantra", 1350, 4.680f, 1.825f, 1.415f),
        CarEntry("Hyundai Creta", 1420, 4.300f, 1.790f, 1.635f),
        CarEntry("Hyundai Tucson", 1570, 4.500f, 1.865f, 1.650f),
        CarEntry("Hyundai Santa Fe", 1850, 4.785f, 1.900f, 1.685f),
        CarEntry("Toyota Camry", 1560, 4.885f, 1.840f, 1.445f),
        CarEntry("Toyota Corolla седан", 1370, 4.630f, 1.780f, 1.435f),
        CarEntry("Toyota RAV4", 1700, 4.600f, 1.855f, 1.685f),
        CarEntry("Toyota Land Cruiser Prado 150", 2310, 4.825f, 1.885f, 1.845f),
        CarEntry("Toyota Land Cruiser 200", 2640, 4.950f, 1.980f, 1.890f),
        CarEntry("VW Polo седан", 1185, 4.416f, 1.751f, 1.469f),
        CarEntry("VW Tiguan", 1685, 4.487f, 1.839f, 1.674f),
        CarEntry("VW Passat B8 седан", 1465, 4.767f, 1.832f, 1.456f),
        CarEntry("Skoda Octavia A8 лифтбек", 1395, 4.689f, 1.829f, 1.470f),
        CarEntry("Skoda Rapid", 1215, 4.416f, 1.732f, 1.469f),
        CarEntry("Skoda Kodiaq", 1760, 4.697f, 1.882f, 1.659f),
        CarEntry("Renault Logan", 1095, 4.346f, 1.734f, 1.517f),
        CarEntry("Renault Duster", 1370, 4.341f, 1.822f, 1.694f),
        CarEntry("Renault Kaptur", 1310, 4.333f, 1.813f, 1.600f),
        CarEntry("Nissan Qashqai", 1490, 4.425f, 1.838f, 1.625f),
        CarEntry("Nissan X-Trail", 1645, 4.680f, 1.840f, 1.710f),
        CarEntry("Nissan Almera", 1115, 4.487f, 1.695f, 1.503f),
        CarEntry("Mazda 3 седан", 1340, 4.662f, 1.797f, 1.440f),
        CarEntry("Mazda CX-5", 1600, 4.550f, 1.840f, 1.680f),
        CarEntry("Mazda CX-30", 1470, 4.395f, 1.795f, 1.540f),
        CarEntry("Mitsubishi Outlander", 1750, 4.710f, 1.860f, 1.745f),
        CarEntry("Mitsubishi ASX", 1440, 4.295f, 1.770f, 1.625f),
        CarEntry("Mitsubishi Eclipse Cross", 1540, 4.545f, 1.805f, 1.685f),
        CarEntry("Chevrolet Niva", 1390, 4.057f, 1.772f, 1.668f),
        CarEntry("Chery Tiggo 4 Pro", 1450, 4.323f, 1.818f, 1.658f),
        CarEntry("Chery Tiggo 7 Pro", 1565, 4.500f, 1.842f, 1.693f),
        CarEntry("Chery Tiggo 8 Pro", 1755, 4.722f, 1.860f, 1.745f),
        CarEntry("Haval Jolion", 1490, 4.472f, 1.841f, 1.628f),
        CarEntry("Haval F7", 1640, 4.610f, 1.876f, 1.730f),
        CarEntry("Geely Coolray", 1390, 4.330f, 1.810f, 1.609f),
        CarEntry("Geely Atlas Pro", 1630, 4.544f, 1.871f, 1.696f),
        CarEntry("BMW 3 Series G20 седан", 1545, 4.709f, 1.827f, 1.435f),
        CarEntry("BMW X5 G05", 2140, 4.922f, 2.004f, 1.745f),
        CarEntry("Mercedes C-Class W206 седан", 1610, 4.751f, 1.820f, 1.438f),
        CarEntry("Mercedes GLC X254", 1955, 4.716f, 1.890f, 1.640f),
        CarEntry("Audi A4 B9 седан", 1490, 4.762f, 1.842f, 1.427f),
        CarEntry("Audi Q5 FY", 1830, 4.663f, 1.893f, 1.659f),
        CarEntry("Ford Focus III хэтчбек", 1270, 4.358f, 1.823f, 1.484f),
        CarEntry("Peugeot 408", 1330, 4.342f, 1.804f, 1.476f)
    )

    data class CarEntry(
        val name: String,
        val mass: Int,
        val length: Float,
        val width: Float,
        val height: Float
    )

    data class SearchResult(
        val vehicle: CargoVehicle?,
        val suggestions: List<CargoVehicle>,
        val source: String,
        val error: String? = null
    )

    // ── Search ────────────────────────────────────────────────────────────────

    /**
     * Search by query string.
     * Returns top matches from local DB first, then API if no good local match.
     */
    suspend fun search(query: String): SearchResult = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()

        // 1. Local search
        val localMatches = localCars.filter { car ->
            car.name.lowercase().contains(q)
        }.map { it.toCargoVehicle() }

        if (localMatches.isNotEmpty()) {
            return@withContext SearchResult(
                vehicle = localMatches.first(),
                suggestions = localMatches.take(5),
                source = "local"
            )
        }

        // 2. API search
        if (API_KEY == "YOUR_API_NINJAS_KEY_HERE") {
            return@withContext SearchResult(
                vehicle = null,
                suggestions = emptyList(),
                source = "none",
                error = "API ключ не настроен. Получите бесплатный ключ на api-ninjas.com"
            )
        }

        return@withContext searchApi(query)
    }

    /** Search only local database — no internet needed */
    fun searchLocal(query: String): List<CargoVehicle> {
        val q = query.trim().lowercase()
        return localCars.filter { it.name.lowercase().contains(q) }
            .map { it.toCargoVehicle() }
    }

    private fun CarEntry.toCargoVehicle() = CargoVehicle(
        name = name, mass = mass, length = length, width = width, height = height
    )

    // ── API Ninjas ────────────────────────────────────────────────────────────

    private fun searchApi(query: String): SearchResult {
        return try {
            // Parse "Make Model" — API Ninjas wants them separately
            val parts = query.trim().split(" ", limit = 2)
            val make = URLEncoder.encode(parts[0], "UTF-8")
            val model = URLEncoder.encode(parts.getOrElse(1) { "" }, "UTF-8")

            val url = URL("$API_URL?make=$make&model=$model&limit=5")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("X-Api-Key", API_KEY)
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val code = conn.responseCode
            if (code != 200) {
                return SearchResult(null, emptyList(), "api",
                    error = "Ошибка API: HTTP $code")
            }

            val json = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(json)

            if (arr.length() == 0) {
                return SearchResult(null, emptyList(), "api",
                    error = "Модель не найдена: «$query»")
            }

            val vehicles = mutableListOf<CargoVehicle>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val make2 = obj.optString("make", "")
                val model2 = obj.optString("model", "")
                val year = obj.optInt("year", 0)

                // API Ninjas returns dimensions in inches/lbs — convert
                val lengthIn = obj.optDouble("length", 0.0)
                val widthIn = obj.optDouble("width", 0.0)
                val heightIn = obj.optDouble("height", 0.0)
                val weightLbs = obj.optDouble("curb_weight", 0.0)

                if (lengthIn <= 0 || weightLbs <= 0) continue

                val lengthM = (lengthIn * 0.0254).toFloat()
                val widthM = (widthIn * 0.0254).toFloat()
                val heightM = (heightIn * 0.0254).toFloat()
                val massKg = (weightLbs * 0.453592).toInt()

                vehicles.add(CargoVehicle(
                    name = "$make2 $model2${if (year > 0) " ($year)" else ""}",
                    mass = massKg,
                    length = lengthM,
                    width = widthM,
                    height = heightM
                ))
            }

            if (vehicles.isEmpty()) {
                SearchResult(null, emptyList(), "api",
                    error = "API не вернул габариты для «$query»")
            } else {
                SearchResult(vehicles.first(), vehicles, "api")
            }

        } catch (e: Exception) {
            Log.e("CarSearch", "API error", e)
            SearchResult(null, emptyList(), "api",
                error = "Нет соединения с интернетом")
        }
    }
}
