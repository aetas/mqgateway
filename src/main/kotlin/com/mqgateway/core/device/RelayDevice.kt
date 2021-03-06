package com.mqgateway.core.device

import com.mqgateway.core.device.RelayDevice.RelayState.CLOSED
import com.mqgateway.core.device.RelayDevice.RelayState.OPEN
import com.mqgateway.core.gatewayconfig.DevicePropertyType.STATE
import com.mqgateway.core.gatewayconfig.DeviceType
import com.mqgateway.core.hardware.MqGpioPinDigitalOutput
import com.pi4j.io.gpio.PinState
import mu.KotlinLogging

private val LOGGER = KotlinLogging.logger {}

class RelayDevice(id: String, pin: MqGpioPinDigitalOutput) : DigitalOutputDevice(id, DeviceType.RELAY, pin) {

  fun changeState(newState: RelayState) {
    if (newState == CLOSED) {
      pin.setState(RELAY_CLOSED_STATE)
      notify(STATE, STATE_ON)
    } else {
      pin.setState(RELAY_OPEN_STATE)
      notify(STATE, STATE_OFF)
    }
  }

  override fun change(propertyId: String, newValue: String) {
    LOGGER.debug { "Changing state on relay $id to $newValue" }
    if (newValue == STATE_ON) {
      changeState(CLOSED)
    } else {
      changeState(OPEN)
    }
  }

  enum class RelayState {
    OPEN, CLOSED
  }

  companion object {
    val RELAY_CLOSED_STATE = PinState.LOW
    val RELAY_OPEN_STATE = PinState.getInverseState(RELAY_CLOSED_STATE)!!
    const val STATE_ON = "ON"
    const val STATE_OFF = "OFF"
  }
}
