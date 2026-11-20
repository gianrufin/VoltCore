package com.example.data.ble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

enum class SimulatorMode(val label: String) {
    DISCHARGING("Discharging Load"),
    CHARGING("Fast AC Charging"),
    STAND_BY("Standby"),
    THERMAL_SPIKE("Thermal Alert Test")
}

data class SimulatorState(
    val isActive: Boolean = false,
    val mode: SimulatorMode = SimulatorMode.DISCHARGING,
    val socPercent: Float = 78.4f,
    val chargerWatts: Float = 420f,
    val baseLoadWatts: Float = 165f
)

class BmsSimulator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _state = MutableStateFlow(SimulatorState())
    val state: StateFlow<SimulatorState> = _state.asStateFlow()

    fun start(
        mode: SimulatorMode = SimulatorMode.DISCHARGING,
        onFrame: (ParsedBmsFrame) -> Unit
    ) {
        stop()
        _state.update { it.copy(isActive = true, mode = mode) }

        job = scope.launch {
            var currentTemp = 28.4f
            var cycles = 142
            while (isActive) {
                val sim = _state.value
                val nominalPackVoltage = 51.2f
                val nominalAh = 20f

                val (targetWatts, nextSoc, nextTemp) = when (sim.mode) {
                    SimulatorMode.CHARGING -> {
                        val watts = sim.chargerWatts
                        val soc = (sim.socPercent + 0.35f).coerceAtMost(100f)
                        val temp = (currentTemp + 0.08f).coerceAtMost(36.2f)
                        Triple(watts, soc, temp)
                    }
                    SimulatorMode.DISCHARGING -> {
                        val watts = -sim.baseLoadWatts
                        val soc = (sim.socPercent - 0.18f).coerceAtLeast(8f)
                        val temp = (currentTemp + 0.03f).coerceAtMost(33.5f)
                        Triple(watts, soc, temp)
                    }
                    SimulatorMode.STAND_BY -> {
                        val temp = (currentTemp - 0.05f).coerceAtLeast(24.0f)
                        Triple(0f, sim.socPercent, temp)
                    }
                    SimulatorMode.THERMAL_SPIKE -> {
                        val watts = -480f
                        val soc = (sim.socPercent - 0.25f).coerceAtLeast(10f)
                        val temp = (currentTemp + 1.4f).coerceAtMost(49.5f)
                        Triple(watts, soc, temp)
                    }
                }

                currentTemp = nextTemp
                _state.update { it.copy(socPercent = nextSoc) }

                // Realistic LiFePO4 16S pack voltage curve (49.2V..54.8V)
                val baseCellV = 3.08f + (nextSoc / 100f) * 0.34f
                val sagBias = (targetWatts / 1000f) * 0.04f
                val cells = List(16) { idx ->
                    val jitter = Random.nextFloat() * 0.008f - 0.004f
                    val imbalance = if (idx == 4) 0.006f else 0f
                    (baseCellV + sagBias + jitter + imbalance).coerceIn(2.65f, 3.65f)
                }
                val packVoltage = cells.sum()
                val currentAmps = targetWatts / packVoltage.coerceAtLeast(1f)
                val remAh = (nextSoc / 100f) * nominalAh

                onFrame(
                    ParsedBmsFrame(
                        socPercent = nextSoc.toInt().coerceIn(0, 100),
                        voltageVolts = packVoltage,
                        currentAmps = currentAmps,
                        temperatureCelsius = currentTemp,
                        mosfetTempCelsius = currentTemp + if (abs(targetWatts) > 200f) 4.2f else 1.1f,
                        cycleCount = cycles,
                        stateOfHealthPercent = 98,
                        remainingCapacityAh = remAh,
                        nominalCapacityAh = nominalAh,
                        cellVoltages = cells,
                        chargeMosEnabled = true,
                        dischargeMosEnabled = true
                    )
                )
                delay(2000L)
            }
        }
    }

    fun setMode(mode: SimulatorMode) {
        _state.update { it.copy(mode = mode) }
    }

    fun setSoc(socPercent: Float) {
        _state.update { it.copy(socPercent = socPercent.coerceIn(5f, 100f)) }
    }

    fun setLoadWatts(watts: Float) {
        _state.update { it.copy(baseLoadWatts = watts.coerceIn(10f, 1800f)) }
    }

    fun setChargerWatts(watts: Float) {
        _state.update { it.copy(chargerWatts = watts.coerceIn(50f, 1500f)) }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.update { it.copy(isActive = false) }
    }
}
