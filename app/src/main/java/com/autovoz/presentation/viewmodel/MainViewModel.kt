package com.autovoz.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.autovoz.data.db.TripEntity
import com.autovoz.data.repository.AppRepository
import com.autovoz.data.repository.CarSearchService
import com.autovoz.domain.model.*
import com.autovoz.domain.usecase.CalculateLoadingUseCase
import com.autovoz.presentation.screens.CarSearchState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repo = AppRepository(application)
    private val calculator = CalculateLoadingUseCase()
    private val carSearch = CarSearchService()

    // ── Settings ──────────────────────────────────────────────────────────────
    val settings: StateFlow<TruckSettings> = repo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, TruckSettings())

    fun saveSettings(s: TruckSettings) = viewModelScope.launch { repo.saveSettings(s) }

    // ── Car Library ───────────────────────────────────────────────────────────
    val library = repo.getCarsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun saveCarToLibrary(car: CargoVehicle) = viewModelScope.launch { repo.saveCar(car) }
    fun deleteCarFromLibrary(id: Long) = viewModelScope.launch { repo.deleteCar(id) }

    // ── Trips ─────────────────────────────────────────────────────────────────
    val trips = repo.getTripsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun deleteTrip(id: Long) = viewModelScope.launch { repo.deleteTrip(id) }

    // ── Current session ───────────────────────────────────────────────────────
    private val _currentVehicles = MutableStateFlow<List<CargoVehicle>>(emptyList())
    val currentVehicles: StateFlow<List<CargoVehicle>> = _currentVehicles

    private val _loadingResult = MutableStateFlow<LoadingResult?>(null)
    val loadingResult: StateFlow<LoadingResult?> = _loadingResult

    fun addVehicle(v: CargoVehicle) {
        _currentVehicles.value = _currentVehicles.value +
                v.copy(id = System.currentTimeMillis() + _currentVehicles.value.size)
        recalculate()
    }

    fun removeVehicle(id: Long) {
        _currentVehicles.value = _currentVehicles.value.filter { it.id != id }
        recalculate()
    }

    fun clearVehicles() {
        _currentVehicles.value = emptyList()
        _loadingResult.value = null
    }

    fun recalculate() {
        val vehicles = _currentVehicles.value
        if (vehicles.isEmpty()) { _loadingResult.value = null; return }
        _loadingResult.value = calculator.execute(vehicles, settings.value)
    }

    fun saveCurrentTrip(name: String) = viewModelScope.launch {
        val result = _loadingResult.value ?: return@launch
        val summary = buildString {
            append("Масса: ${result.totalMass} кг | ")
            append("Пер.ось: ${result.frontAxleLoad.toInt()} кг | ")
            append("Задн.: ${result.rearAxleLoad.toInt()} кг")
            if (result.isOverloaded) append(" | ⚠ ПЕРЕГРУЗ")
        }
        repo.saveTrip(name, _currentVehicles.value, summary)
    }

    fun loadTrip(trip: TripEntity) {
        val vehicles = repo.parseVehicles(trip.vehiclesJson)
        _currentVehicles.value = vehicles
        recalculate()
    }

    // ── Car Search ────────────────────────────────────────────────────────────
    private val _carSearchState = MutableStateFlow<CarSearchState>(CarSearchState.Idle)
    val carSearchState: StateFlow<CarSearchState> = _carSearchState

    fun searchCarsLocal(query: String) {
        val results = carSearch.searchLocal(query)
        _carSearchState.value = if (results.isEmpty()) CarSearchState.Idle
        else CarSearchState.Results(results, "local")
    }

    fun searchCarsApi(query: String) {
        viewModelScope.launch {
            _carSearchState.value = CarSearchState.Loading
            val result = carSearch.search(query)
            _carSearchState.value = when {
                result.error != null -> CarSearchState.Error(result.error)
                result.suggestions.isNotEmpty() ->
                    CarSearchState.Results(result.suggestions, result.source)
                else -> CarSearchState.Error("Ничего не найдено для «$query»")
            }
        }
    }

    fun clearCarSearch() {
        _carSearchState.value = CarSearchState.Idle
    }
}
