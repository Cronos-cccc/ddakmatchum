package com.ddakmatchum.car.car

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

data class CarState(
    val parked: Boolean = true,
    val speedKph: Float = 0f,
    /** 플랫폼이 주행 중 화면 제한을 요구하는 상태 */
    val uxRestricted: Boolean = false,
    val source: String = "연결 전",
    val demo: Boolean = false,
) {
    /** 메뉴판, 결제처럼 오래 보는 화면은 정차(P) 중에만 */
    val canBrowse: Boolean get() = parked && !uxRestricted
}

/**
 * 차량 신호: 기어(Pleos Vehicle SDK) + 속도, 주행 제한(AAOS 표준 Car API).
 * 에뮬레이터에서 Virtual Console로 기어, 속도를 바꾸면 그대로 반영된다.
 * 데모 모드에서는 화면의 데모 패널 버튼으로 바꾼다.
 */
private const val AAOS_GEAR_PARK = 4 // android.car.VehicleGear.GEAR_PARK

class CarSignals(context: Context) {
    private val app = context.applicationContext
    private val _state = MutableStateFlow(CarState())
    val state: StateFlow<CarState> = _state.asStateFlow()

    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var ux: CarUxRestrictionsManager? = null

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = setGear(gear == VehicleGear.GEAR_PARK)
        override fun onFailed(e: Exception) = Unit
    }

    private val propCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (_state.value.demo) return
            when (value.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED ->
                    (value.value as? Float)?.let { mps -> _state.update { it.copy(speedKph = abs(mps) * 3.6f) } }
                VehiclePropertyIds.GEAR_SELECTION ->
                    if (vehicle == null) (value.value as? Int)?.let { g -> setGear(g == AAOS_GEAR_PARK) }
            }
        }

        override fun onErrorEvent(propertyId: Int, zone: Int) = Unit
    }

    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r -> applyUx(r) }

    private fun setGear(parked: Boolean) {
        if (_state.value.demo) return
        _state.update { it.copy(parked = parked) }
    }

    private fun applyUx(r: CarUxRestrictions?) {
        if (_state.value.demo) return
        _state.update { it.copy(uxRestricted = r?.isRequiresDistractionOptimization == true) }
    }

    fun start() {
        if (vehicle == null) {
            vehicle = runCatching { Vehicle(app).also { it.initialize() } }.getOrNull()
            vehicle?.let { v ->
                runCatching {
                    v.getGear().registerGearSelection(gearListener)
                    v.getGear().getCurrentGearSelectionState({ g -> setGear(g == VehicleGear.GEAR_PARK) }, {})
                }
            }
        }
        if (car == null) {
            car = runCatching {
                Car.createCar(app, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                    if (!ready) return@createCar
                    registerProps(connected)
                    runCatching {
                        val m = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                        ux = m
                        m.registerListener(uxListener)
                        applyUx(m.currentCarUxRestrictions)
                    }
                }
            }.getOrNull()
        }
        val src = when {
            vehicle != null && car != null -> "Pleos Vehicle SDK + AAOS"
            car != null -> "AAOS Car API"
            else -> "차량 신호 없음"
        }
        _state.update { it.copy(source = src) }
    }

    /** 속도 권한을 나중에 허용했을 때 다시 등록 */
    fun refresh() {
        car?.let { if (it.isConnected) registerProps(it) }
    }

    private fun registerProps(c: Car) {
        val props = runCatching { c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager }.getOrNull() ?: return
        runCatching { props.unregisterCallback(propCallback) }
        runCatching { props.registerCallback(propCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL) }
        runCatching { props.registerCallback(propCallback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE) }
    }

    // ---- 데모 패널 ----
    fun setDemo(on: Boolean) = _state.update { it.copy(demo = on, uxRestricted = false) }
    fun demoPark() = _state.update { it.copy(demo = true, parked = true, speedKph = 0f, uxRestricted = false) }
    fun demoDrive(kph: Float) = _state.update { it.copy(demo = true, parked = false, speedKph = kph, uxRestricted = kph > 0f) }

    fun stop() {
        vehicle?.let { v -> runCatching { v.getGear().unregisterGearSelection(gearListener) } }
        car?.let { c ->
            runCatching { (c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager).unregisterCallback(propCallback) }
            runCatching { ux?.unregisterListener() }
            runCatching { c.disconnect() }
        }
        car = null
        ux = null
        vehicle = null
    }
}
