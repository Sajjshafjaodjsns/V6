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
        CarEntry("Lada Granta седан", 1085, 4.257f, 1.7f, 1.5f),
        CarEntry("Lada Granta лифтбек", 1095, 4.257f, 1.7f, 1.51f),
        CarEntry("Lada Granta хэтчбек", 1075, 4.032f, 1.7f, 1.5f),
        CarEntry("Lada Granta универсал", 1110, 4.267f, 1.7f, 1.53f),
        CarEntry("Lada Granta Cross лифтбек", 1120, 4.257f, 1.7f, 1.56f),
        CarEntry("Lada Vesta седан", 1195, 4.41f, 1.764f, 1.497f),
        CarEntry("Lada Vesta SW универсал", 1280, 4.418f, 1.764f, 1.53f),
        CarEntry("Lada Vesta SW Cross", 1340, 4.418f, 1.764f, 1.64f),
        CarEntry("Lada Vesta Sport", 1220, 4.41f, 1.764f, 1.467f),
        CarEntry("Lada Niva Legend 3d", 1210, 3.72f, 1.68f, 1.64f),
        CarEntry("Lada Niva Legend 5d", 1260, 4.015f, 1.68f, 1.64f),
        CarEntry("Lada Niva Travel", 1380, 4.24f, 1.79f, 1.63f),
        CarEntry("Lada Largus универсал", 1350, 4.47f, 1.78f, 1.636f),
        CarEntry("Lada Largus Cross", 1380, 4.47f, 1.78f, 1.686f),
        CarEntry("Lada XRAY", 1220, 4.162f, 1.777f, 1.558f),
        CarEntry("Lada XRAY Cross", 1260, 4.162f, 1.777f, 1.608f),
        CarEntry("Lada Priora седан", 1100, 4.35f, 1.68f, 1.42f),
        CarEntry("Lada Priora хэтчбек", 1085, 4.11f, 1.68f, 1.445f),
        CarEntry("Lada Kalina седан", 1030, 4.132f, 1.68f, 1.438f),
        CarEntry("Lada Kalina хэтчбек", 1015, 3.9f, 1.68f, 1.45f),
        CarEntry("Lada Samara 2114", 990, 4.006f, 1.65f, 1.402f),
        CarEntry("Lada Samara 2115", 1010, 4.27f, 1.65f, 1.402f),
        CarEntry("Lada 2107", 1010, 4.073f, 1.62f, 1.44f),
        CarEntry("Lada 2106", 955, 4.166f, 1.611f, 1.38f),
        CarEntry("Lada 2105", 950, 4.073f, 1.62f, 1.44f),
        CarEntry("Kia Rio III седан", 1115, 4.37f, 1.72f, 1.455f),
        CarEntry("Kia Rio IV седан", 1185, 4.37f, 1.729f, 1.45f),
        CarEntry("Kia Rio IV хэтчбек", 1165, 4.065f, 1.729f, 1.455f),
        CarEntry("Kia Rio X", 1285, 4.215f, 1.76f, 1.53f),
        CarEntry("Kia Rio X-Line", 1300, 4.245f, 1.76f, 1.54f),
        CarEntry("Kia Ceed II хэтчбек", 1255, 4.305f, 1.78f, 1.47f),
        CarEntry("Kia Ceed III хэтчбек", 1290, 4.31f, 1.8f, 1.45f),
        CarEntry("Kia Ceed III универсал", 1345, 4.605f, 1.8f, 1.475f),
        CarEntry("Kia ProCeed", 1325, 4.605f, 1.8f, 1.415f),
        CarEntry("Kia Cerato III седан", 1285, 4.56f, 1.78f, 1.455f),
        CarEntry("Kia Cerato IV седан", 1340, 4.64f, 1.8f, 1.43f),
        CarEntry("Kia Optima IV седан", 1465, 4.855f, 1.86f, 1.47f),
        CarEntry("Kia K5 III седан", 1535, 4.905f, 1.86f, 1.445f),
        CarEntry("Kia Sportage III", 1495, 4.44f, 1.855f, 1.635f),
        CarEntry("Kia Sportage IV", 1565, 4.48f, 1.855f, 1.645f),
        CarEntry("Kia Sportage V", 1570, 4.515f, 1.865f, 1.645f),
        CarEntry("Kia Sorento III", 1814, 4.78f, 1.89f, 1.685f),
        CarEntry("Kia Sorento IV", 1920, 4.81f, 1.9f, 1.7f),
        CarEntry("Kia Soul II", 1380, 4.14f, 1.8f, 1.6f),
        CarEntry("Kia Soul III", 1395, 4.195f, 1.8f, 1.605f),
        CarEntry("Kia Stinger", 1700, 4.83f, 1.87f, 1.4f),
        CarEntry("Kia Telluride", 2060, 4.995f, 1.985f, 1.75f),
        CarEntry("Kia Seltos", 1420, 4.37f, 1.8f, 1.62f),
        CarEntry("Kia Carnival IV", 2065, 5.155f, 1.995f, 1.775f),
        CarEntry("Kia Niro I", 1385, 4.355f, 1.805f, 1.545f),
        CarEntry("Kia EV6", 1960, 4.695f, 1.89f, 1.55f),
        CarEntry("Hyundai Solaris I седан", 1115, 4.37f, 1.7f, 1.47f),
        CarEntry("Hyundai Solaris II седан", 1165, 4.4f, 1.729f, 1.45f),
        CarEntry("Hyundai Solaris II хэтчбек", 1140, 4.065f, 1.729f, 1.455f),
        CarEntry("Hyundai Elantra VI седан", 1280, 4.57f, 1.8f, 1.45f),
        CarEntry("Hyundai Elantra VII седан", 1350, 4.68f, 1.825f, 1.415f),
        CarEntry("Hyundai i30 III хэтчбек", 1255, 4.34f, 1.795f, 1.455f),
        CarEntry("Hyundai i30 III универсал", 1295, 4.59f, 1.795f, 1.47f),
        CarEntry("Hyundai Creta I", 1370, 4.27f, 1.78f, 1.63f),
        CarEntry("Hyundai Creta II", 1420, 4.3f, 1.79f, 1.635f),
        CarEntry("Hyundai Tucson III", 1495, 4.48f, 1.85f, 1.655f),
        CarEntry("Hyundai Tucson IV", 1570, 4.5f, 1.865f, 1.65f),
        CarEntry("Hyundai Santa Fe IV", 1800, 4.77f, 1.9f, 1.685f),
        CarEntry("Hyundai Santa Fe V", 1850, 4.785f, 1.9f, 1.685f),
        CarEntry("Hyundai Sonata VIII седан", 1530, 4.9f, 1.86f, 1.445f),
        CarEntry("Hyundai Grandeur VI", 1680, 4.99f, 1.87f, 1.47f),
        CarEntry("Hyundai Palisade", 2060, 4.98f, 1.975f, 1.75f),
        CarEntry("Hyundai Kona I", 1360, 4.165f, 1.8f, 1.565f),
        CarEntry("Hyundai Venue", 1195, 4.04f, 1.77f, 1.59f),
        CarEntry("Hyundai Ioniq 5", 1985, 4.635f, 1.89f, 1.605f),
        CarEntry("Hyundai Ioniq 6", 1985, 4.855f, 1.88f, 1.495f),
        CarEntry("Hyundai Staria", 2150, 5.253f, 1.997f, 1.99f),
        CarEntry("Hyundai H-1 Starex", 1980, 5.125f, 1.92f, 1.92f),
        CarEntry("Toyota Camry VII седан", 1480, 4.85f, 1.82f, 1.47f),
        CarEntry("Toyota Camry VIII седан", 1560, 4.885f, 1.84f, 1.445f),
        CarEntry("Toyota Camry IX седан", 1595, 4.92f, 1.84f, 1.455f),
        CarEntry("Toyota Corolla XI седан", 1290, 4.62f, 1.775f, 1.455f),
        CarEntry("Toyota Corolla XII седан", 1370, 4.63f, 1.78f, 1.435f),
        CarEntry("Toyota Corolla XII хэтчбек", 1330, 4.37f, 1.79f, 1.46f),
        CarEntry("Toyota Corolla XII универсал", 1395, 4.65f, 1.79f, 1.46f),
        CarEntry("Toyota RAV4 IV", 1620, 4.57f, 1.845f, 1.68f),
        CarEntry("Toyota RAV4 V", 1700, 4.6f, 1.855f, 1.685f),
        CarEntry("Toyota Land Cruiser Prado 150", 2310, 4.825f, 1.885f, 1.845f),
        CarEntry("Toyota Land Cruiser 200", 2640, 4.95f, 1.98f, 1.89f),
        CarEntry("Toyota Land Cruiser 300", 2540, 4.985f, 1.98f, 1.925f),
        CarEntry("Toyota Highlander III", 1930, 4.855f, 1.925f, 1.72f),
        CarEntry("Toyota Highlander IV", 2050, 4.965f, 1.93f, 1.755f),
        CarEntry("Toyota C-HR", 1365, 4.36f, 1.795f, 1.565f),
        CarEntry("Toyota Yaris III", 1050, 3.885f, 1.695f, 1.51f),
        CarEntry("Toyota Yaris IV", 1110, 3.94f, 1.745f, 1.49f),
        CarEntry("Toyota Auris II хэтчбек", 1270, 4.24f, 1.76f, 1.47f),
        CarEntry("Toyota Venza II", 1835, 4.88f, 1.87f, 1.68f),
        CarEntry("Toyota Fortuner II", 2025, 4.795f, 1.855f, 1.835f),
        CarEntry("Toyota Hilux VIII", 1920, 5.33f, 1.855f, 1.815f),
        CarEntry("Toyota bZ4X", 2010, 4.69f, 1.86f, 1.65f),
        CarEntry("Toyota Avensis III седан", 1395, 4.77f, 1.81f, 1.47f),
        CarEntry("Toyota Prius IV", 1390, 4.54f, 1.76f, 1.47f),
        CarEntry("VW Polo V седан", 1105, 4.388f, 1.699f, 1.453f),
        CarEntry("VW Polo VI седан", 1185, 4.416f, 1.751f, 1.469f),
        CarEntry("VW Polo VI хэтчбек", 1145, 4.053f, 1.751f, 1.461f),
        CarEntry("VW Golf VII хэтчбек", 1270, 4.255f, 1.8f, 1.452f),
        CarEntry("VW Golf VIII хэтчбек", 1315, 4.284f, 1.789f, 1.456f),
        CarEntry("VW Golf VIII универсал", 1370, 4.633f, 1.789f, 1.482f),
        CarEntry("VW Passat B7 седан", 1385, 4.769f, 1.82f, 1.467f),
        CarEntry("VW Passat B8 седан", 1465, 4.767f, 1.832f, 1.456f),
        CarEntry("VW Passat B8 универсал", 1495, 4.767f, 1.832f, 1.477f),
        CarEntry("VW Tiguan I", 1540, 4.427f, 1.809f, 1.686f),
        CarEntry("VW Tiguan II", 1580, 4.487f, 1.839f, 1.674f),
        CarEntry("VW Tiguan II Allspace", 1670, 4.703f, 1.839f, 1.674f),
        CarEntry("VW Touareg III", 1995, 4.878f, 1.984f, 1.702f),
        CarEntry("VW Arteon", 1590, 4.862f, 1.871f, 1.427f),
        CarEntry("VW T-Cross", 1240, 4.108f, 1.76f, 1.58f),
        CarEntry("VW T-Roc", 1340, 4.234f, 1.819f, 1.573f),
        CarEntry("VW ID.4", 2060, 4.584f, 1.852f, 1.64f),
        CarEntry("VW ID.6", 2215, 4.876f, 1.848f, 1.68f),
        CarEntry("VW Jetta VII седан", 1340, 4.702f, 1.799f, 1.454f),
        CarEntry("VW Touran II", 1520, 4.527f, 1.829f, 1.674f),
        CarEntry("Skoda Octavia A7 лифтбек", 1290, 4.67f, 1.814f, 1.461f),
        CarEntry("Skoda Octavia A8 лифтбек", 1395, 4.689f, 1.829f, 1.47f),
        CarEntry("Skoda Octavia A8 универсал", 1440, 4.689f, 1.829f, 1.491f),
        CarEntry("Skoda Rapid I лифтбек", 1185, 4.483f, 1.706f, 1.459f),
        CarEntry("Skoda Rapid II лифтбек", 1215, 4.416f, 1.732f, 1.469f),
        CarEntry("Skoda Fabia III хэтчбек", 1030, 4.0f, 1.732f, 1.467f),
        CarEntry("Skoda Fabia IV хэтчбек", 1115, 4.108f, 1.78f, 1.485f),
        CarEntry("Skoda Superb III лифтбек", 1510, 4.861f, 1.864f, 1.468f),
        CarEntry("Skoda Superb III универсал", 1555, 4.861f, 1.864f, 1.479f),
        CarEntry("Skoda Kodiaq I", 1650, 4.697f, 1.882f, 1.659f),
        CarEntry("Skoda Kodiaq II", 1720, 4.758f, 1.864f, 1.659f),
        CarEntry("Skoda Karoq", 1380, 4.382f, 1.841f, 1.605f),
        CarEntry("Skoda Kamiq", 1270, 4.241f, 1.793f, 1.576f),
        CarEntry("Skoda Enyaq iV", 2016, 4.649f, 1.879f, 1.616f),
        CarEntry("Skoda Scala хэтчбек", 1265, 4.362f, 1.793f, 1.471f),
        CarEntry("Renault Logan II седан", 1095, 4.346f, 1.734f, 1.517f),
        CarEntry("Renault Logan III седан", 1115, 4.362f, 1.763f, 1.5f),
        CarEntry("Renault Logan MCV универсал", 1190, 4.246f, 1.734f, 1.549f),
        CarEntry("Renault Sandero II хэтчбек", 1055, 4.007f, 1.734f, 1.526f),
        CarEntry("Renault Sandero III хэтчбек", 1075, 4.086f, 1.763f, 1.5f),
        CarEntry("Renault Sandero Stepway II", 1115, 4.032f, 1.734f, 1.576f),
        CarEntry("Renault Duster I", 1296, 4.315f, 1.822f, 1.695f),
        CarEntry("Renault Duster II", 1370, 4.341f, 1.822f, 1.694f),
        CarEntry("Renault Kaptur I", 1310, 4.333f, 1.813f, 1.6f),
        CarEntry("Renault Kaptur II", 1345, 4.369f, 1.813f, 1.6f),
        CarEntry("Renault Arkana", 1395, 4.545f, 1.82f, 1.571f),
        CarEntry("Renault Megane IV седан", 1350, 4.62f, 1.814f, 1.445f),
        CarEntry("Renault Megane IV хэтчбек", 1295, 4.359f, 1.814f, 1.447f),
        CarEntry("Renault Clio V хэтчбек", 1095, 4.05f, 1.798f, 1.44f),
        CarEntry("Renault Fluence седан", 1305, 4.62f, 1.808f, 1.477f),
        CarEntry("Renault Koleos II", 1665, 4.672f, 1.843f, 1.68f),
        CarEntry("Renault Talisman седан", 1510, 4.852f, 1.871f, 1.455f),
        CarEntry("Renault Zoe II", 1530, 4.087f, 1.787f, 1.562f),
        CarEntry("Nissan Almera G15 седан", 1115, 4.487f, 1.695f, 1.503f),
        CarEntry("Nissan Note II хэтчбек", 1150, 4.1f, 1.695f, 1.55f),
        CarEntry("Nissan Tiida II седан", 1195, 4.435f, 1.695f, 1.5f),
        CarEntry("Nissan Qashqai II", 1395, 4.377f, 1.806f, 1.59f),
        CarEntry("Nissan Qashqai III", 1490, 4.425f, 1.838f, 1.625f),
        CarEntry("Nissan X-Trail III", 1645, 4.68f, 1.84f, 1.71f),
        CarEntry("Nissan X-Trail IV", 1700, 4.68f, 1.84f, 1.72f),
        CarEntry("Nissan Murano III", 1930, 4.89f, 1.93f, 1.72f),
        CarEntry("Nissan Pathfinder IV", 2065, 4.899f, 1.94f, 1.76f),
        CarEntry("Nissan Juke I", 1255, 4.135f, 1.765f, 1.57f),
        CarEntry("Nissan Juke II", 1295, 4.21f, 1.8f, 1.595f),
        CarEntry("Nissan Sentra B17 седан", 1220, 4.566f, 1.762f, 1.502f),
        CarEntry("Nissan Patrol Y62", 2650, 5.165f, 1.995f, 1.96f),
        CarEntry("Nissan Navara D40", 1880, 5.05f, 1.85f, 1.8f),
        CarEntry("Nissan Navara D23", 1900, 5.255f, 1.85f, 1.83f),
        CarEntry("Mazda 2 DJ хэтчбек", 1000, 4.06f, 1.695f, 1.5f),
        CarEntry("Mazda 3 BM хэтчбек", 1290, 4.46f, 1.795f, 1.45f),
        CarEntry("Mazda 3 BM седан", 1295, 4.58f, 1.795f, 1.45f),
        CarEntry("Mazda 3 BP хэтчбек", 1320, 4.46f, 1.797f, 1.435f),
        CarEntry("Mazda 3 BP седан", 1340, 4.662f, 1.797f, 1.44f),
        CarEntry("Mazda 6 GJ седан", 1395, 4.87f, 1.84f, 1.45f),
        CarEntry("Mazda 6 GJ универсал", 1440, 4.8f, 1.84f, 1.48f),
        CarEntry("Mazda CX-3", 1195, 4.275f, 1.765f, 1.55f),
        CarEntry("Mazda CX-30", 1470, 4.395f, 1.795f, 1.54f),
        CarEntry("Mazda CX-5 II", 1600, 4.55f, 1.84f, 1.68f),
        CarEntry("Mazda CX-60", 1920, 4.745f, 1.89f, 1.685f),
        CarEntry("Mazda CX-8", 1870, 4.9f, 1.84f, 1.73f),
        CarEntry("Mazda CX-9", 2045, 5.075f, 1.97f, 1.747f),
        CarEntry("Mazda MX-5 IV", 1010, 3.915f, 1.735f, 1.235f),
        CarEntry("Mitsubishi Lancer X седан", 1215, 4.57f, 1.76f, 1.49f),
        CarEntry("Mitsubishi ASX I", 1440, 4.295f, 1.77f, 1.625f),
        CarEntry("Mitsubishi ASX II", 1460, 4.41f, 1.8f, 1.62f),
        CarEntry("Mitsubishi Eclipse Cross I", 1540, 4.545f, 1.805f, 1.685f),
        CarEntry("Mitsubishi Eclipse Cross II", 1580, 4.545f, 1.805f, 1.685f),
        CarEntry("Mitsubishi Outlander III", 1750, 4.695f, 1.8f, 1.74f),
        CarEntry("Mitsubishi Outlander IV", 1780, 4.71f, 1.86f, 1.745f),
        CarEntry("Mitsubishi Pajero Sport III", 2065, 4.785f, 1.815f, 1.825f),
        CarEntry("Mitsubishi Pajero IV", 2245, 4.9f, 1.875f, 1.935f),
        CarEntry("Mitsubishi L200 V", 1880, 5.275f, 1.815f, 1.775f),
        CarEntry("Mitsubishi Galant IX седан", 1440, 4.73f, 1.78f, 1.48f),
        CarEntry("BMW 1 F20 хэтчбек", 1295, 4.324f, 1.765f, 1.421f),
        CarEntry("BMW 1 F40 хэтчбек", 1345, 4.361f, 1.799f, 1.434f),
        CarEntry("BMW 2 F44 Gran Coupe", 1445, 4.526f, 1.8f, 1.42f),
        CarEntry("BMW 3 F30 седан", 1480, 4.624f, 1.811f, 1.429f),
        CarEntry("BMW 3 G20 седан", 1545, 4.709f, 1.827f, 1.435f),
        CarEntry("BMW 3 G21 универсал", 1595, 4.709f, 1.827f, 1.44f),
        CarEntry("BMW 5 G30 седан", 1670, 4.936f, 1.868f, 1.466f),
        CarEntry("BMW 5 G31 универсал", 1720, 4.936f, 1.868f, 1.498f),
        CarEntry("BMW 7 G11 седан", 1895, 5.098f, 1.902f, 1.467f),
        CarEntry("BMW 7 G70 седан", 2060, 5.392f, 1.95f, 1.544f),
        CarEntry("BMW X1 F48", 1560, 4.439f, 1.821f, 1.598f),
        CarEntry("BMW X1 U11", 1670, 4.5f, 1.845f, 1.642f),
        CarEntry("BMW X3 G01", 1740, 4.708f, 1.891f, 1.676f),
        CarEntry("BMW X3 G45", 1840, 4.755f, 1.92f, 1.66f),
        CarEntry("BMW X5 G05", 2140, 4.922f, 2.004f, 1.745f),
        CarEntry("BMW X6 G06", 2120, 4.935f, 2.004f, 1.696f),
        CarEntry("BMW X7 G07", 2335, 5.151f, 2.0f, 1.805f),
        CarEntry("BMW iX3", 2080, 4.734f, 1.891f, 1.668f),
        CarEntry("BMW iX", 2440, 4.953f, 1.967f, 1.696f),
        CarEntry("Mercedes A W177 хэтчбек", 1395, 4.419f, 1.796f, 1.433f),
        CarEntry("Mercedes B W247 минивэн", 1480, 4.419f, 1.796f, 1.54f),
        CarEntry("Mercedes C W205 седан", 1545, 4.686f, 1.81f, 1.442f),
        CarEntry("Mercedes C W206 седан", 1610, 4.751f, 1.82f, 1.438f),
        CarEntry("Mercedes C W206 универсал", 1640, 4.751f, 1.82f, 1.44f),
        CarEntry("Mercedes E W213 седан", 1710, 4.923f, 1.852f, 1.468f),
        CarEntry("Mercedes E W214 седан", 1770, 4.949f, 1.88f, 1.468f),
        CarEntry("Mercedes S W222 седан", 2025, 5.116f, 1.899f, 1.469f),
        CarEntry("Mercedes S W223 седан", 2080, 5.289f, 1.954f, 1.503f),
        CarEntry("Mercedes GLA H247", 1555, 4.41f, 1.834f, 1.611f),
        CarEntry("Mercedes GLB X247", 1635, 4.634f, 1.834f, 1.663f),
        CarEntry("Mercedes GLC X253", 1815, 4.656f, 1.89f, 1.644f),
        CarEntry("Mercedes GLC X254", 1955, 4.716f, 1.89f, 1.64f),
        CarEntry("Mercedes GLE W167", 2145, 4.924f, 2.004f, 1.796f),
        CarEntry("Mercedes GLS X166", 2385, 5.13f, 1.92f, 1.85f),
        CarEntry("Mercedes GLS X167", 2490, 5.207f, 2.03f, 1.823f),
        CarEntry("Mercedes EQA H243", 2040, 4.463f, 1.834f, 1.62f),
        CarEntry("Mercedes EQB X243", 2110, 4.684f, 1.834f, 1.667f),
        CarEntry("Mercedes EQC N293", 2425, 4.762f, 1.884f, 1.624f),
        CarEntry("Audi A1 GB хэтчбек", 1115, 4.03f, 1.74f, 1.415f),
        CarEntry("Audi A3 8V седан", 1295, 4.456f, 1.796f, 1.416f),
        CarEntry("Audi A3 8Y седан", 1395, 4.494f, 1.816f, 1.431f),
        CarEntry("Audi A4 B8 седан", 1395, 4.701f, 1.826f, 1.427f),
        CarEntry("Audi A4 B9 седан", 1490, 4.762f, 1.842f, 1.427f),
        CarEntry("Audi A4 B9 универсал", 1540, 4.762f, 1.842f, 1.452f),
        CarEntry("Audi A6 C7 седан", 1650, 4.933f, 1.874f, 1.457f),
        CarEntry("Audi A6 C8 седан", 1760, 4.939f, 1.886f, 1.457f),
        CarEntry("Audi A7 C8 хэтчбек", 1830, 4.969f, 1.908f, 1.422f),
        CarEntry("Audi A8 D5 седан", 1945, 5.172f, 1.945f, 1.46f),
        CarEntry("Audi Q2 GA", 1205, 4.191f, 1.794f, 1.508f),
        CarEntry("Audi Q3 F3", 1450, 4.484f, 1.856f, 1.585f),
        CarEntry("Audi Q5 FY", 1830, 4.663f, 1.893f, 1.659f),
        CarEntry("Audi Q7 4M", 2050, 5.052f, 1.968f, 1.741f),
        CarEntry("Audi Q8 4M", 2115, 4.986f, 1.995f, 1.705f),
        CarEntry("Audi e-tron GE", 2490, 4.901f, 1.935f, 1.629f),
        CarEntry("Audi e-tron GT", 2350, 4.989f, 1.964f, 1.396f),
        CarEntry("Audi Q4 e-tron", 2135, 4.588f, 1.865f, 1.614f),
        CarEntry("Ford Focus III хэтчбек", 1270, 4.358f, 1.823f, 1.484f),
        CarEntry("Ford Focus III седан", 1290, 4.531f, 1.823f, 1.484f),
        CarEntry("Ford Focus III универсал", 1320, 4.56f, 1.823f, 1.503f),
        CarEntry("Ford Mondeo V седан", 1530, 4.871f, 1.852f, 1.481f),
        CarEntry("Ford Mondeo V универсал", 1570, 4.871f, 1.852f, 1.505f),
        CarEntry("Ford EcoSport II", 1220, 4.143f, 1.765f, 1.647f),
        CarEntry("Ford Kuga II", 1560, 4.524f, 1.838f, 1.703f),
        CarEntry("Ford Kuga III", 1600, 4.614f, 1.883f, 1.686f),
        CarEntry("Ford Explorer V", 2060, 5.001f, 2.004f, 1.778f),
        CarEntry("Ford Explorer VI", 2170, 5.052f, 2.004f, 1.778f),
        CarEntry("Ford Mustang VI купе", 1670, 4.784f, 1.916f, 1.381f),
        CarEntry("Ford Ranger III пикап", 1875, 5.362f, 1.86f, 1.815f),
        CarEntry("Ford Transit Custom", 1850, 4.973f, 2.149f, 1.998f),
        CarEntry("Opel Astra J хэтчбек", 1235, 4.27f, 1.814f, 1.51f),
        CarEntry("Opel Astra K хэтчбек", 1265, 4.37f, 1.809f, 1.475f),
        CarEntry("Opel Insignia B седан", 1500, 4.899f, 1.862f, 1.459f),
        CarEntry("Opel Insignia B универсал", 1545, 4.899f, 1.862f, 1.49f),
        CarEntry("Opel Mokka A", 1335, 4.278f, 1.776f, 1.656f),
        CarEntry("Opel Mokka B", 1340, 4.151f, 1.791f, 1.534f),
        CarEntry("Opel Crossland X", 1280, 4.212f, 1.765f, 1.604f),
        CarEntry("Opel Zafira C минивэн", 1535, 4.673f, 1.82f, 1.62f),
        CarEntry("Opel Grandland X", 1450, 4.478f, 1.862f, 1.609f),
        CarEntry("Opel Corsa E хэтчбек", 1080, 4.019f, 1.737f, 1.477f),
        CarEntry("Opel Corsa F хэтчбек", 1140, 4.06f, 1.765f, 1.431f),
        CarEntry("Peugeot 208 I хэтчбек", 1040, 3.963f, 1.739f, 1.462f),
        CarEntry("Peugeot 208 II хэтчбек", 1080, 4.055f, 1.745f, 1.43f),
        CarEntry("Peugeot 301 седан", 1140, 4.44f, 1.748f, 1.471f),
        CarEntry("Peugeot 308 II хэтчбек", 1175, 4.253f, 1.804f, 1.458f),
        CarEntry("Peugeot 308 III хэтчбек", 1285, 4.365f, 1.852f, 1.444f),
        CarEntry("Peugeot 408 I седан", 1330, 4.342f, 1.804f, 1.476f),
        CarEntry("Peugeot 408 II лифтбек", 1465, 4.69f, 1.862f, 1.474f),
        CarEntry("Peugeot 508 II лифтбек", 1495, 4.75f, 1.859f, 1.403f),
        CarEntry("Peugeot 2008 II", 1355, 4.3f, 1.77f, 1.55f),
        CarEntry("Peugeot 3008 II", 1475, 4.447f, 1.841f, 1.624f),
        CarEntry("Peugeot 5008 II", 1620, 4.641f, 1.841f, 1.646f),
        CarEntry("Citroen C3 III хэтчбек", 1050, 3.995f, 1.748f, 1.494f),
        CarEntry("Citroen C4 III хэтчбек", 1220, 4.36f, 1.8f, 1.51f),
        CarEntry("Citroen C5 Aircross", 1475, 4.5f, 1.84f, 1.67f),
        CarEntry("Citroen C3 Aircross", 1210, 4.154f, 1.748f, 1.606f),
        CarEntry("Citroen Berlingo III", 1340, 4.403f, 1.848f, 1.848f),
        CarEntry("Honda Civic X седан", 1260, 4.648f, 1.799f, 1.416f),
        CarEntry("Honda Civic XI седан", 1345, 4.674f, 1.802f, 1.415f),
        CarEntry("Honda Civic XI хэтчбек", 1325, 4.551f, 1.802f, 1.449f),
        CarEntry("Honda CR-V IV", 1555, 4.537f, 1.82f, 1.685f),
        CarEntry("Honda CR-V V", 1620, 4.602f, 1.855f, 1.689f),
        CarEntry("Honda CR-V VI", 1750, 4.698f, 1.866f, 1.687f),
        CarEntry("Honda HR-V II", 1300, 4.295f, 1.772f, 1.605f),
        CarEntry("Honda HR-V III", 1380, 4.348f, 1.79f, 1.59f),
        CarEntry("Honda Accord X седан", 1560, 4.893f, 1.862f, 1.449f),
        CarEntry("Honda Pilot III", 2030, 4.993f, 1.994f, 1.8f),
        CarEntry("Honda Jazz IV хэтчбек", 1210, 4.045f, 1.694f, 1.525f),
        CarEntry("Honda ZR-V", 1590, 4.568f, 1.84f, 1.62f),
        CarEntry("Subaru Impreza V хэтчбек", 1290, 4.46f, 1.775f, 1.48f),
        CarEntry("Subaru Impreza V седан", 1315, 4.625f, 1.775f, 1.48f),
        CarEntry("Subaru Legacy VI седан", 1495, 4.8f, 1.84f, 1.5f),
        CarEntry("Subaru Outback VI универсал", 1670, 4.87f, 1.875f, 1.67f),
        CarEntry("Subaru Forester V", 1600, 4.625f, 1.815f, 1.73f),
        CarEntry("Subaru XV II", 1480, 4.465f, 1.8f, 1.615f),
        CarEntry("Subaru Crosstrek", 1500, 4.48f, 1.8f, 1.615f),
        CarEntry("Subaru Levorg II универсал", 1500, 4.755f, 1.795f, 1.5f),
        CarEntry("Subaru BRZ II купе", 1270, 4.265f, 1.775f, 1.31f),
        CarEntry("Volvo S60 III седан", 1660, 4.761f, 1.85f, 1.432f),
        CarEntry("Volvo S90 II седан", 1770, 4.963f, 1.879f, 1.443f),
        CarEntry("Volvo V60 II универсал", 1690, 4.761f, 1.85f, 1.484f),
        CarEntry("Volvo V90 II универсал", 1790, 4.963f, 1.879f, 1.475f),
        CarEntry("Volvo XC40", 1660, 4.425f, 1.863f, 1.652f),
        CarEntry("Volvo XC60 II", 1855, 4.688f, 1.902f, 1.66f),
        CarEntry("Volvo XC90 II", 2150, 4.953f, 2.008f, 1.776f),
        CarEntry("Volvo C40 Recharge", 1970, 4.44f, 1.873f, 1.595f),
        CarEntry("Volvo EX30", 1750, 4.233f, 1.837f, 1.55f),
        CarEntry("Lexus IS III седан", 1645, 4.665f, 1.81f, 1.43f),
        CarEntry("Lexus ES VII седан", 1715, 4.975f, 1.865f, 1.45f),
        CarEntry("Lexus GS IV седан", 1720, 4.85f, 1.84f, 1.455f),
        CarEntry("Lexus LS V седан", 2130, 5.235f, 1.9f, 1.455f),
        CarEntry("Lexus UX", 1620, 4.495f, 1.84f, 1.54f),
        CarEntry("Lexus NX II", 1870, 4.63f, 1.865f, 1.66f),
        CarEntry("Lexus RX V", 2010, 4.89f, 1.92f, 1.7f),
        CarEntry("Lexus GX II", 2445, 4.79f, 1.885f, 1.84f),
        CarEntry("Lexus LX III", 2560, 5.08f, 1.98f, 1.925f),
        CarEntry("Lexus LC 500 купе", 1920, 4.76f, 1.92f, 1.345f),
        CarEntry("Chery Tiggo 4 Pro", 1450, 4.323f, 1.818f, 1.658f),
        CarEntry("Chery Tiggo 7 Pro", 1565, 4.5f, 1.842f, 1.693f),
        CarEntry("Chery Tiggo 7 Pro Max", 1620, 4.5f, 1.842f, 1.693f),
        CarEntry("Chery Tiggo 8 Pro", 1755, 4.722f, 1.86f, 1.745f),
        CarEntry("Chery Tiggo 8 Pro Max", 1800, 4.722f, 1.86f, 1.745f),
        CarEntry("Chery Arrizo 8 седан", 1455, 4.848f, 1.86f, 1.493f),
        CarEntry("Haval Jolion", 1490, 4.472f, 1.841f, 1.628f),
        CarEntry("Haval F7", 1640, 4.61f, 1.876f, 1.73f),
        CarEntry("Haval F7x", 1645, 4.6f, 1.876f, 1.69f),
        CarEntry("Haval H6 III", 1620, 4.653f, 1.886f, 1.73f),
        CarEntry("Haval H9", 2250, 4.85f, 1.932f, 1.9f),
        CarEntry("Haval Dargo", 1680, 4.725f, 1.895f, 1.76f),
        CarEntry("Omoda C5", 1480, 4.428f, 1.83f, 1.643f),
        CarEntry("Omoda S5 седан", 1430, 4.696f, 1.838f, 1.46f),
        CarEntry("Geely Coolray", 1390, 4.33f, 1.81f, 1.609f),
        CarEntry("Geely Atlas Pro", 1630, 4.544f, 1.871f, 1.696f),
        CarEntry("Geely Monjaro", 1810, 4.77f, 1.895f, 1.695f),
        CarEntry("Geely Okavango", 1930, 4.77f, 1.89f, 1.78f),
        CarEntry("Tank 300", 2130, 4.76f, 1.91f, 1.85f),
        CarEntry("Tank 500", 2680, 5.1f, 1.994f, 1.905f),
        CarEntry("Jetour X70 Plus", 1680, 4.68f, 1.862f, 1.72f),
        CarEntry("Jetour Dashing", 1530, 4.544f, 1.862f, 1.668f),
        CarEntry("BAIC X55 II", 1560, 4.518f, 1.855f, 1.695f),
        CarEntry("FAW Bestune T77 Pro", 1530, 4.548f, 1.843f, 1.696f),
        CarEntry("Exeed TXL", 1680, 4.685f, 1.87f, 1.73f),
        CarEntry("Exeed LX", 1540, 4.365f, 1.855f, 1.675f),
        CarEntry("Avatr 11", 2220, 4.98f, 1.97f, 1.601f),
        CarEntry("BYD Seal седан", 2150, 4.8f, 1.875f, 1.46f),
        CarEntry("BYD Atto 3", 1750, 4.455f, 1.875f, 1.615f),
        CarEntry("BYD Han", 2090, 4.995f, 1.91f, 1.495f),
        CarEntry("BYD Tang", 2500, 4.87f, 1.95f, 1.725f),
        CarEntry("Li Auto L7", 2330, 5.05f, 1.995f, 1.5f),
        CarEntry("Li Auto L9", 2600, 5.218f, 1.998f, 1.8f),
        CarEntry("Voyah Free", 2360, 4.905f, 1.95f, 1.645f),
        CarEntry("SEAT Leon III хэтчбек", 1215, 4.259f, 1.816f, 1.444f),
        CarEntry("SEAT Leon IV хэтчбек", 1290, 4.368f, 1.799f, 1.462f),
        CarEntry("SEAT Ibiza V хэтчбек", 1090, 4.059f, 1.78f, 1.434f),
        CarEntry("SEAT Ateca", 1355, 4.382f, 1.841f, 1.6f),
        CarEntry("SEAT Tarraco", 1630, 4.735f, 1.839f, 1.668f),
        CarEntry("Cupra Formentor", 1500, 4.45f, 1.839f, 1.511f),
        CarEntry("Cupra Born", 1665, 4.322f, 1.809f, 1.54f),
        CarEntry("Suzuki Vitara IV", 1120, 4.175f, 1.775f, 1.61f),
        CarEntry("Suzuki SX4 S-Cross II", 1190, 4.3f, 1.785f, 1.585f),
        CarEntry("Suzuki Swift V хэтчбек", 885, 3.84f, 1.735f, 1.495f),
        CarEntry("Suzuki Jimny IV", 1135, 3.645f, 1.645f, 1.72f),
        CarEntry("Suzuki Grand Vitara II", 1565, 4.38f, 1.81f, 1.695f),
        CarEntry("Suzuki Ignis II хэтчбек", 890, 3.7f, 1.69f, 1.595f),
        CarEntry("Jeep Renegade I", 1370, 4.233f, 1.805f, 1.694f),
        CarEntry("Jeep Compass II", 1570, 4.404f, 1.818f, 1.649f),
        CarEntry("Jeep Cherokee KL", 1815, 4.624f, 1.859f, 1.679f),
        CarEntry("Jeep Grand Cherokee WK2", 2029, 4.828f, 1.943f, 1.766f),
        CarEntry("Jeep Grand Cherokee WL", 2175, 4.895f, 1.979f, 1.783f),
        CarEntry("Jeep Wrangler JL", 1955, 4.33f, 1.894f, 1.844f),
        CarEntry("Land Rover Defender 90", 2060, 4.323f, 1.996f, 1.967f),
        CarEntry("Land Rover Defender 110", 2185, 5.018f, 1.996f, 1.967f),
        CarEntry("Land Rover Discovery Sport", 1760, 4.599f, 1.936f, 1.727f),
        CarEntry("Land Rover Discovery V", 2110, 4.957f, 2.073f, 1.888f),
        CarEntry("Land Rover Freelander 2", 1810, 4.5f, 1.91f, 1.74f),
        CarEntry("Land Rover Range Rover IV", 2230, 4.999f, 2.047f, 1.835f),
        CarEntry("Land Rover Range Rover V", 2560, 5.052f, 2.047f, 1.87f),
        CarEntry("Land Rover Range Rover Sport II", 2070, 4.879f, 2.073f, 1.78f),
        CarEntry("Land Rover Range Rover Evoque II", 1750, 4.371f, 1.904f, 1.649f),
        CarEntry("Land Rover Range Rover Velar", 1820, 4.803f, 2.032f, 1.665f),
        CarEntry("Porsche Cayenne III", 2045, 4.918f, 1.983f, 1.696f),
        CarEntry("Porsche Macan I", 1770, 4.681f, 1.923f, 1.624f),
        CarEntry("Porsche Macan II", 2100, 4.784f, 1.938f, 1.622f),
        CarEntry("Porsche Panamera II", 1995, 5.049f, 1.937f, 1.423f),
        CarEntry("Porsche Taycan", 2295, 4.963f, 1.966f, 1.378f),
        CarEntry("Porsche 911 992 купе", 1505, 4.519f, 1.852f, 1.3f),
        CarEntry("Chevrolet Niva I", 1390, 4.057f, 1.772f, 1.668f),
        CarEntry("Chevrolet Cruze II седан", 1310, 4.64f, 1.785f, 1.475f),
        CarEntry("Chevrolet Captiva I", 1755, 4.638f, 1.85f, 1.72f),
        CarEntry("Chevrolet Trailblazer III", 1845, 4.995f, 1.9f, 1.795f),
        CarEntry("Chevrolet Equinox III", 1640, 4.652f, 1.843f, 1.66f),
        CarEntry("Chevrolet Tahoe V", 2520, 5.18f, 2.06f, 1.93f),
        CarEntry("Chevrolet Traverse II", 2115, 5.189f, 2.03f, 1.795f),
        CarEntry("Chevrolet Camaro VI купе", 1676, 4.783f, 1.897f, 1.348f),
        CarEntry("Genesis G70 II седан", 1700, 4.685f, 1.85f, 1.4f),
        CarEntry("Genesis G80 III седан", 1895, 4.995f, 1.925f, 1.465f),
        CarEntry("Genesis GV70", 1860, 4.715f, 1.91f, 1.63f),
        CarEntry("Genesis GV80", 2060, 4.945f, 1.975f, 1.715f),
        CarEntry("Alfa Romeo Giulia седан", 1660, 4.643f, 1.869f, 1.436f),
        CarEntry("Alfa Romeo Stelvio", 1830, 4.687f, 1.903f, 1.671f),
        CarEntry("Alfa Romeo Tonale", 1700, 4.528f, 1.839f, 1.597f),
        CarEntry("MINI Cooper F56 хэтчбек", 1155, 3.821f, 1.727f, 1.414f),
        CarEntry("MINI Countryman F60", 1445, 4.309f, 1.822f, 1.557f),
        CarEntry("MINI Clubman F54 универсал", 1330, 4.253f, 1.8f, 1.441f),
        CarEntry("Infiniti Q50 седан", 1700, 4.8f, 1.82f, 1.45f),
        CarEntry("Infiniti Q60 купе", 1750, 4.692f, 1.85f, 1.389f),
        CarEntry("Infiniti QX50 II", 1855, 4.687f, 1.921f, 1.685f),
        CarEntry("Infiniti QX60 III", 2030, 5.0f, 1.975f, 1.75f),
        CarEntry("Infiniti QX70", 1930, 4.81f, 1.925f, 1.658f),
        CarEntry("Infiniti QX80", 2730, 5.34f, 2.03f, 1.945f),
        CarEntry("Acura MDX III", 1985, 4.956f, 1.96f, 1.726f),
        CarEntry("Acura RDX III", 1720, 4.607f, 1.902f, 1.659f),
        CarEntry("Cadillac Escalade V", 2695, 5.179f, 2.031f, 1.936f),
        CarEntry("Cadillac XT5 I", 1870, 4.812f, 1.964f, 1.676f),
        CarEntry("Cadillac XT6", 2045, 5.002f, 1.964f, 1.745f),
        CarEntry("Lincoln Navigator IV", 2860, 5.341f, 2.06f, 1.948f),
        CarEntry("Lincoln Aviator II", 2165, 5.052f, 1.998f, 1.764f),
        CarEntry("Tesla Model 3 седан", 1763, 4.694f, 1.849f, 1.443f),
        CarEntry("Tesla Model Y", 1979, 4.751f, 1.921f, 1.624f),
        CarEntry("Tesla Model S седан", 2162, 4.979f, 1.964f, 1.445f),
        CarEntry("Tesla Model X", 2487, 5.037f, 2.07f, 1.684f),
        CarEntry("УАЗ Патриот", 2040, 4.785f, 1.9f, 1.905f),
        CarEntry("УАЗ Хантер", 1660, 3.985f, 1.8f, 1.91f),
        CarEntry("УАЗ Буханка", 2210, 4.36f, 2.0f, 2.145f),
        CarEntry("УАЗ Пикап", 2070, 4.88f, 1.9f, 1.905f),
        CarEntry("ГАЗ Газель Next бортовой", 2490, 6.068f, 2.07f, 2.358f),
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
        // API Ninjas /v1/cars (deprecated) does not return dimensions/weight
        // The endpoints that DO return dimensions (/v1/cardetails) require paid plan
        // So we only use the local database
        return@withContext SearchResult(
            vehicle = null,
            suggestions = emptyList(),
            source = "none",
            error = "«$query» не найден в базе из 435 моделей"
        )
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
            // API Ninjas /v1/cars accepts:
            //   ?make=bmw&model=x1  — when user types "bmw x1"
            //   ?make=bmw           — when user types just "bmw"
            //   ?model=vesta        — when user types just "vesta" (no brand)
            //
            // Strategy: send the whole query as "model", and if first word
            // looks like a known brand, split into make+model.
            val parts = query.trim().split("\s+".toRegex(), limit = 2)
            val knownBrands = setOf(
                "bmw", "audi", "mercedes", "volkswagen", "vw", "toyota",
                "hyundai", "kia", "honda", "ford", "chevrolet", "nissan",
                "mazda", "mitsubishi", "skoda", "renault", "peugeot",
                "lada", "vaz", "haval", "chery", "geely", "lexus",
                "volvo", "subaru", "suzuki", "seat", "opel", "fiat"
            )
            val firstWord = parts[0].lowercase()
            val (makeParam, modelParam) = if (firstWord in knownBrands && parts.size > 1) {
                Pair(parts[0], parts[1])
            } else if (firstWord in knownBrands) {
                Pair(parts[0], "")
            } else {
                Pair("", query.trim())
            }

            val queryStr = buildString {
                if (makeParam.isNotEmpty())
                    append("make=${URLEncoder.encode(makeParam, "UTF-8")}&")
                if (modelParam.isNotEmpty())
                    append("model=${URLEncoder.encode(modelParam, "UTF-8")}&")
                append("limit=5")
            }
            val url = URL("$API_URL?$queryStr")
            Log.d("CarSearch", "Requesting: $url")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("X-Api-Key", API_KEY)
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val code = conn.responseCode
            if (code != 200) {
                val errBody = try {
                    conn.errorStream?.bufferedReader()?.readText()?.take(200) ?: ""
                } catch (_: Exception) { "" }
                Log.e("CarSearch", "API error $code: $errBody | URL: $url")
                return SearchResult(null, emptyList(), "api",
                    error = "Ошибка API: HTTP $code\n$errBody")
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
