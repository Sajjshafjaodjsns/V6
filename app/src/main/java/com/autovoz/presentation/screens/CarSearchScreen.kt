package com.autovoz.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autovoz.domain.model.CargoVehicle
import com.autovoz.presentation.theme.*
import com.autovoz.presentation.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarSearchScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onCarSelected: (CargoVehicle) -> Unit
) {
    val searchState by vm.carSearchState.collectAsState()
    var query by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Поиск автомобиля", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = OnDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                    titleContentColor = TruckOrange
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search field
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.length >= 2) vm.searchCarsLocal(it)
                    else vm.clearCarSearch()
                },
                label = { Text("Например: Kia Rio, Toyota RAV4, Vesta") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TruckOrange)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; vm.clearCarSearch() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Очистить", tint = OnDark.copy(alpha = 0.5f))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    if (query.isNotBlank()) vm.searchCarsApi(query)
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TruckOrange,
                    unfocusedBorderColor = OnDark.copy(alpha = 0.3f),
                    focusedLabelColor = TruckOrange,
                    cursorColor = TruckOrange,
                    focusedTextColor = OnDark,
                    unfocusedTextColor = OnDark
                ),
                singleLine = true
            )

            // Search button (API)
            Button(
                onClick = {
                    focusManager.clearFocus()
                    if (query.isNotBlank()) vm.searchCarsApi(query)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TruckOrange),
                enabled = query.isNotBlank() && searchState !is CarSearchState.Loading
            ) {
                if (searchState is CarSearchState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = androidx.compose.ui.graphics.Color.Black,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Поиск...", color = androidx.compose.ui.graphics.Color.Black, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Найти в интернете", color = androidx.compose.ui.graphics.Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            // Info chip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = TruckOrange.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Поиск по тексту — из встроенной базы (58 моделей). Кнопка «В интернете» — запрос к api-ninjas.com",
                    color = OnDark.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }

            // Results
            when (val state = searchState) {
                is CarSearchState.Idle -> {
                    if (query.length >= 2) {
                        Text("Ничего не найдено в базе. Нажмите «Найти в интернете»",
                            color = OnDark.copy(alpha = 0.4f), fontSize = 14.sp)
                    } else {
                        Text("Введите название модели для поиска",
                            color = OnDark.copy(alpha = 0.3f), fontSize = 14.sp)
                    }
                }
                is CarSearchState.Loading -> {}
                is CarSearchState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RedError.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = RedError, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(state.message, color = OnDark.copy(alpha = 0.8f), fontSize = 14.sp)
                        }
                    }
                }
                is CarSearchState.Results -> {
                    Text(
                        "Найдено: ${state.vehicles.size} — источник: ${if (state.source == "local") "встроенная база" else "интернет"}",
                        color = GreenOk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.vehicles) { car ->
                            CarSearchResultCard(car = car, onClick = { onCarSelected(car) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CarSearchResultCard(car: CargoVehicle, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DirectionsCar,
                contentDescription = null,
                tint = TruckOrange,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(car.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = OnDark)
                Text(
                    "${car.mass} кг  •  ${car.length}×${car.width}×${car.height} м",
                    fontSize = 13.sp,
                    color = OnDark.copy(alpha = 0.6f)
                )
            }
            Icon(Icons.Default.AddCircleOutline, contentDescription = "Добавить", tint = GreenOk, modifier = Modifier.size(28.dp))
        }
    }
}

// ── State ─────────────────────────────────────────────────────────────────────

sealed class CarSearchState {
    object Idle : CarSearchState()
    object Loading : CarSearchState()
    data class Results(val vehicles: List<CargoVehicle>, val source: String) : CarSearchState()
    data class Error(val message: String) : CarSearchState()
}
