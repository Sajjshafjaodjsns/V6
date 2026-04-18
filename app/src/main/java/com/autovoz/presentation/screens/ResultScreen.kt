package com.autovoz.presentation.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.autovoz.domain.model.*
import com.autovoz.presentation.theme.*
import com.autovoz.presentation.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(vm: MainViewModel, onBack: () -> Unit, onSave: () -> Unit) {
    val result   by vm.loadingResult.collectAsState()
    val settings by vm.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Результат", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = OnDark)
                    }
                },
                actions = {
                    IconButton(onClick = onSave) {
                        Icon(Icons.Default.Save, null, tint = OnDark)
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
        if (result == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("Нет данных", color = OnDark.copy(.4f))
            }
            return@Scaffold
        }
        val r = result!!
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StatusCard(r)
            AxleGaugeCard(r, settings)
            PlatformCard(r, settings)
            MassCard(r, settings)
            if (r.warnings.isNotEmpty())         WarningsCard(r.warnings)
            if (r.unplacedVehicles.isNotEmpty())  UnplacedCard(r)
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─── Status Banner ────────────────────────────────────────────────────────────
@Composable
fun StatusCard(r: LoadingResult) {
    val ok = !r.isOverloaded
    val bg  = if (ok) GreenOk.copy(.12f) else RedError.copy(.15f)
    val col = if (ok) GreenOk else RedError
    val icon = if (ok) Icons.Default.CheckCircle else Icons.Default.Warning
    val text = if (ok) "ПОГРУЗКА В НОРМЕ" else "ОБНАРУЖЕН ПЕРЕГРУЗ"

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = col, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, color = col, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                letterSpacing = 0.5.sp)
        }
    }
}

// ─── Axle Gauges ─────────────────────────────────────────────────────────────
@Composable
fun AxleGaugeCard(r: LoadingResult, s: TruckSettings) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionHeader("Нагрузки на оси", Icons.Default.Speed)
            AxleRow("Передняя ось",  r.frontAxleLoad, s.maxFrontAxleLoad.toFloat())
            AxleRow("Задняя тележка", r.rearAxleLoad,  s.maxRearAxleLoad.toFloat())
        }
    }
}

@Composable
fun AxleRow(label: String, load: Float, max: Float) {
    val ratio = (load / max).coerceIn(0f, 1.5f)
    val barColor = when {
        ratio > 1.00f -> RedError
        ratio > 0.85f -> YellowWarn
        else           -> GreenOk
    }
    // Animate bar width
    val animRatio by animateFloatAsState(
        targetValue = ratio.coerceIn(0f, 1f),
        animationSpec = tween(600, easing = EaseOutCubic), label = "bar"
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(label, color = OnDark.copy(.75f), fontSize = 14.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${load.toInt()}",
                    color = barColor, fontWeight = FontWeight.Bold, fontSize = 17.sp
                )
                Text(
                    " / ${max.toInt()} кг",
                    color = OnDark.copy(.5f), fontSize = 13.sp
                )
            }
        }
        // Track
        Box(
            Modifier.fillMaxWidth().height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurfaceVariant)
        ) {
            Box(
                Modifier.fillMaxWidth(animRatio).fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(barColor.copy(.7f), barColor)
                        )
                    )
            )
        }
        Text(
            "${"%.0f".format(ratio * 100)}% от нормы",
            color = barColor.copy(.75f), fontSize = 11.sp
        )
    }
}

// ─── Platform Visualization ───────────────────────────────────────────────────
@Composable
fun PlatformCard(r: LoadingResult, s: TruckSettings) {
    val lower = r.placements.filter { it.deck == Deck.LOWER }
    val upper = r.placements.filter { it.deck == Deck.UPPER }
    val allMasses = r.placements.map { it.vehicle.mass }
    val minM = allMasses.minOrNull() ?: 1
    val maxM = allMasses.maxOrNull() ?: 1

    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Схема размещения", Icons.Default.LocalShipping)

            // Legend
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendDot(GreenOk, "Лёгкие")
                LegendDot(YellowWarn, "Средние")
                LegendDot(RedError, "Тяжёлые")
            }

            // Upper deck
            DeckLabel("Верхний ярус ↑")
            PlatformCanvas(upper, s.platformLength, minM, maxM)

            Spacer(Modifier.height(2.dp))

            // Lower deck
            DeckLabel("Нижний ярус ↓")
            PlatformCanvas(lower, s.platformLength, minM, maxM)

            HorizontalDivider(color = OnDark.copy(.08f))

            // List
            r.placements.forEachIndexed { i, p ->
                PlacementRow(i + 1, p)
            }
        }
    }
}

@Composable
fun DeckLabel(text: String) {
    Text(text, color = OnDark.copy(.45f), fontSize = 11.sp, letterSpacing = 0.3.sp)
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, color = OnDark.copy(.55f), fontSize = 11.sp)
    }
}

@Composable
fun PlatformCanvas(
    placements: List<PlacedVehicle>,
    platformLen: Float,
    minMass: Int, maxMass: Int
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        Modifier.fillMaxWidth().height(58.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141414))
            .border(1.dp, OnDark.copy(.1f), RoundedCornerShape(8.dp))
    ) {
        val w = size.width
        val h = size.height
        val scaleX = w / platformLen
        val padV = 6.dp.toPx()

        // Platform outline gradient ticks
        for (m in 0..platformLen.toInt()) {
            val x = m * scaleX
            drawLine(OnDark.copy(.06f), Offset(x, h - 10.dp.toPx()), Offset(x, h), strokeWidth = 1f)
        }

        for (p in placements) {
            val t = if (maxMass > minMass)
                (p.vehicle.mass - minMass).toFloat() / (maxMass - minMass)
            else 0.5f
            val carCol = lerpColor(GreenOk, RedError, t)

            val x0 = p.positionFromFront * scaleX + 2f
            val carW = (p.vehicle.length * scaleX - 4f).coerceAtLeast(10f)
            val y0 = padV
            val carH = h - padV * 2

            // Car body
            drawRoundRect(
                color = carCol.copy(.88f),
                topLeft = Offset(x0, y0),
                size = Size(carW, carH),
                cornerRadius = CornerRadius(4f),
            )
            // Shine
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(.18f), Color.Transparent),
                    startY = y0, endY = y0 + carH * 0.4f
                ),
                topLeft = Offset(x0, y0),
                size = Size(carW, carH * 0.4f),
                cornerRadius = CornerRadius(4f)
            )
            // Wheels
            val wheelR = 4.dp.toPx()
            val wheelY = h - padV - wheelR * 0.5f
            drawCircle(Color.Black.copy(.7f), wheelR, Offset(x0 + wheelR * 1.5f, wheelY))
            drawCircle(Color.Black.copy(.7f), wheelR, Offset(x0 + carW - wheelR * 1.5f, wheelY))

            // Number label inside car if wide enough
            if (carW > 24.dp.toPx()) {
                val num = (placements.indexOf(p) + 1).toString()
                val style = TextStyle(
                    color = Color.Black.copy(.85f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                val tl = textMeasurer.measure(num, style)
                val tx = x0 + (carW - tl.size.width) / 2f
                val ty = y0 + (carH - tl.size.height) / 2f - 2.dp.toPx()
                drawText(tl, topLeft = Offset(tx, ty))
            }
        }

        // Front arrow
        drawLine(TruckOrange.copy(.5f), Offset(4f, h / 2), Offset(20f, h / 2), strokeWidth = 2f)
        drawLine(TruckOrange.copy(.5f), Offset(4f, h / 2), Offset(10f, h / 2 - 5), strokeWidth = 2f)
        drawLine(TruckOrange.copy(.5f), Offset(4f, h / 2), Offset(10f, h / 2 + 5), strokeWidth = 2f)
    }
}

@Composable
fun PlacementRow(num: Int, p: PlacedVehicle) {
    val deckLabel = if (p.deck == Deck.LOWER) "Нижний" else "Верхний"
    val deckColor = if (p.deck == Deck.LOWER) TruckOrange else TruckOrangeLight

    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Number badge
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .background(deckColor.copy(.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text("$num", color = deckColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                p.vehicle.name.ifEmpty { "Автомобиль" },
                color = OnDark.copy(.9f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                "$deckLabel ярус  •  ${"%.1f".format(p.positionFromFront)} м от начала",
                color = OnDark.copy(.4f), fontSize = 11.sp
            )
        }
        Text("${p.vehicle.mass} кг", color = OnDark.copy(.6f), fontSize = 12.sp)
    }
}

// ─── Mass Summary ────────────────────────────────────────────────────────────
@Composable
fun MassCard(r: LoadingResult, s: TruckSettings) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Массы", Icons.Default.FitnessCenter)
            MassRow("Снаряжённая масса", "${s.unladenMass} кг")
            MassRow("Груз (${r.placements.size} авт.)", "+${r.placements.sumOf { it.vehicle.mass }} кг",
                accent = true)
            HorizontalDivider(color = TruckOrange.copy(.25f), thickness = 1.dp)
            MassRow(
                "Полная масса", "${r.totalMass} кг",
                warn = r.totalMass > s.maxTotalMass, bold = true
            )
            MassRow("Допустимо", "${s.maxTotalMass} кг", muted = true)
        }
    }
}

@Composable
fun MassRow(label: String, value: String,
            accent: Boolean = false, warn: Boolean = false,
            bold: Boolean = false, muted: Boolean = false) {
    val labelColor = when {
        muted -> OnDark.copy(.4f)
        else   -> OnDark.copy(.75f)
    }
    val valueColor = when {
        warn   -> RedError
        accent -> TruckOrangeLight
        muted  -> OnDark.copy(.4f)
        bold   -> OnDark
        else   -> OnDark.copy(.85f)
    }
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(label, color = labelColor, fontSize = 14.sp)
        Text(value, color = valueColor,
            fontWeight = if (bold || warn) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp)
    }
}

// ─── Warnings ────────────────────────────────────────────────────────────────
@Composable
fun WarningsCard(warnings: List<String>) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1A0A))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = YellowWarn, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Предупреждения", color = YellowWarn,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            warnings.forEach { w ->
                Text("• $w", color = OnDark.copy(.8f), fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

// ─── Unplaced ────────────────────────────────────────────────────────────────
@Composable
fun UnplacedCard(r: LoadingResult) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E0F0F))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Block, null, tint = RedError, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Не размещено (${r.unplacedVehicles.size})",
                    color = RedError, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            r.unplacedVehicles.forEach { car ->
                Text(
                    "• ${car.name.ifEmpty { "Автомобиль" }} — ${car.mass} кг, " +
                    "${car.length}×${car.height} м",
                    color = OnDark.copy(.7f), fontSize = 13.sp
                )
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────
@Composable
fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = TruckOrange, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = TruckOrange, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

fun lerpColor(a: Color, b: Color, t: Float): Color {
    val c = t.coerceIn(0f, 1f)
    return Color(
        red   = a.red   + (b.red   - a.red)   * c,
        green = a.green + (b.green - a.green)  * c,
        blue  = a.blue  + (b.blue  - a.blue)   * c,
    )
}
