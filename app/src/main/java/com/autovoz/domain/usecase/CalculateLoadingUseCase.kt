        package com.autovoz.domain.usecase

import com.autovoz.domain.model.*

/**
 * CORRECT PHYSICS — lever rule for a car transporter:
 *
 * Coordinate system: origin = centre of FRONT axle, positive direction = REARWARD
 *
 *   Front axle ──────────────────────────── Rear axle
 *       0                                   wheelbase (e.g. 4.5m)
 *              |← frontOverhang →|← platform →|
 *              platform_start = frontOverhang (e.g. 1.2m behind front axle)
 *
 * For each car placed at position X from platform front:
 *   carCoG_from_frontAxle = frontOverhang + X + carLength/2
 *
 * Rear axle cargo load  = Σ( mass_i × dist_i ) / wheelbase
 * Front axle cargo load = totalCargoMass − rearCargoLoad
 *
 * Unladen mass is split according to frontAxleLoadRatio (default 40/60).
 *
 * Greedy packing: heavier cars placed FORWARD on lower deck first,
 * so CoG stays near front axle → balanced distribution.
 */
class CalculateLoadingUseCase {

    private val GAP = 0.20f  // min gap between cars, metres

    fun execute(vehicles: List<CargoVehicle>, s: TruckSettings): LoadingResult {
        val warnings = mutableListOf<String>()

        // Sort: must-be-lower (tall) first, then by mass descending (heavy forward)
        val sorted = vehicles.sortedWith(
            compareByDescending<CargoVehicle> { it.height > s.maxHeightUpperDeck }
                .thenByDescending { it.mass }
        )

        val lowerPlacements = mutableListOf<PlacedVehicle>()
        val upperPlacements = mutableListOf<PlacedVehicle>()
        val unplaced = mutableListOf<CargoVehicle>()

        var lowerCursor = 0f  // next free position on lower deck
        var upperCursor = 0f  // next free position on upper deck

        for (car in sorted) {
            val label = car.name.ifEmpty { "${car.mass} кг" }
            val tooTallForLower = car.height > s.maxHeightLowerDeck
            val tooTallForUpper = car.height > s.maxHeightUpperDeck

            when {
                tooTallForLower -> {
                    warnings.add("$label: высота ${car.height}м — не помещается ни на один ярус.")
                    unplaced.add(car); continue
                }
                tooTallForUpper -> {
                    // Must go lower deck
                    if (lowerCursor + car.length <= s.platformLength) {
                        lowerPlacements += PlacedVehicle(car, Deck.LOWER, lowerCursor)
                        lowerCursor += car.length + GAP
                    } else {
                        warnings.add("$label: нижний ярус заполнен — не размещён.")
                        unplaced.add(car)
                    }
                }
                else -> {
                    // Try lower first (heavier cars forward = better balance)
                    if (lowerCursor + car.length <= s.platformLength) {
                        lowerPlacements += PlacedVehicle(car, Deck.LOWER, lowerCursor)
                        lowerCursor += car.length + GAP
                    } else if (upperCursor + car.length <= s.platformLength) {
                        upperPlacements += PlacedVehicle(car, Deck.UPPER, upperCursor)
                        upperCursor += car.length + GAP
                    } else {
                        warnings.add("$label: оба яруса заполнены — не размещён.")
                        unplaced.add(car)
                    }
                }
            }
        }

        val allPlacements = lowerPlacements + upperPlacements
        val cargoMass = allPlacements.sumOf { it.vehicle.mass }
        val totalMass = s.unladenMass + cargoMass

        // ── Axle loads via lever rule ─────────────────────────────────────────
        // Each car's CoG distance from front axle (rearward positive):
        //   = frontOverhang + positionOnPlatform + carLength/2
        val rearCargoLoad: Float = if (cargoMass == 0) 0f else {
            allPlacements.sumOf { p ->
                val distFromFrontAxle = s.frontOverhang + p.positionFromFront + p.vehicle.length / 2.0
                distFromFrontAxle * p.vehicle.mass
            }.toFloat() / s.wheelbase
        }
        val frontCargoLoad = cargoMass - rearCargoLoad

        // Unladen loads
        val unladenFront = s.unladenMass * s.frontAxleLoadRatio
        val unladenRear  = s.unladenMass * (1f - s.frontAxleLoadRatio)

        val frontAxleLoad = unladenFront + frontCargoLoad
        val rearAxleLoad  = unladenRear  + rearCargoLoad

        // Warnings
        if (totalMass > s.maxTotalMass)
            warnings += "⚠ Полная масса ${totalMass} кг > допустимых ${s.maxTotalMass} кг!"
        if (frontAxleLoad > s.maxFrontAxleLoad)
            warnings += "⚠ Передняя ось: ${frontAxleLoad.toInt()} кг > ${s.maxFrontAxleLoad} кг!"
        if (rearAxleLoad > s.maxRearAxleLoad)
            warnings += "⚠ Задняя тележка: ${rearAxleLoad.toInt()} кг > ${s.maxRearAxleLoad} кг!"

        return LoadingResult(
            placements        = allPlacements,
            totalMass         = totalMass,
            frontAxleLoad     = frontAxleLoad,
            rearAxleLoad      = rearAxleLoad,
            isOverloaded      = totalMass > s.maxTotalMass
                    || frontAxleLoad > s.maxFrontAxleLoad
                    || rearAxleLoad > s.maxRearAxleLoad,
            warnings          = warnings,
            unplacedVehicles  = unplaced
        )
    }
}
