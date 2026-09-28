package com.vinalayan.bancada

enum class ClusterPage(val label: String) {
    ANALOG("Analógico"),
    DIGITAL("Digital"),
    TRIP_A("Trip A"),
    TRIP_B("Trip B"),
    RANGE("Autonomia"),
    CONSUME("Consumo"),
    BATTERY("Bateria"),
    SERVICE("Revisão"),
    TEMP("Temperatura"),
}

enum class RideMode(val label: String, val eco: Boolean, val rearAbsOff: Boolean) {
    PERF_ABS("PERF", false, false),
    PERF_REAR_OFF("PERF", false, true),
    ECO_ABS("ECO", true, false),
    ECO_REAR_OFF("ECO", true, true),
}

data class BikeSim(
    val speed: Int = 0,
    val rpm: Int = 900,
    val gear: Int = 0,
    val fuelBars: Int = 6,
    val tempC: Int = 82,
    val odoKm: Int = 4820,
    val tripA: Double = 36.4,
    val tripB: Double = 128.0,
    val consumption: Double = 22.4,
    val volts: Float = 13.8f,
    val serviceKm: Int = 760,
    val serviceDays: Int = 44,
    val outsideC: Int = 27,
    val mode: RideMode = RideMode.PERF_ABS,
    val highBeam: Boolean = false,
    val leftTurn: Boolean = false,
    val rightTurn: Boolean = false,
    val sideStand: Boolean = false,
    val engineWarn: Boolean = false,
    val absWarn: Boolean = false,
    val day: Boolean = false,
)
