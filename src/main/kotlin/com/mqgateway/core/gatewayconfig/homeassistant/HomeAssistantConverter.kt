package com.mqgateway.core.gatewayconfig.homeassistant

import com.mqgateway.core.device.EmulatedSwitchButtonDevice
import com.mqgateway.core.device.MotionSensorDevice
import com.mqgateway.core.device.ReedSwitchDevice
import com.mqgateway.core.device.RelayDevice
import com.mqgateway.core.device.SwitchButtonDevice
import com.mqgateway.core.device.serial.PeriodicSerialInputDevice
import com.mqgateway.core.gatewayconfig.DeviceConfig
import com.mqgateway.core.gatewayconfig.DevicePropertyType
import com.mqgateway.core.gatewayconfig.DeviceType
import com.mqgateway.core.gatewayconfig.Gateway
import com.mqgateway.core.gatewayconfig.homeassistant.HomeAssistantComponentType.BINARY_SENSOR
import com.mqgateway.core.gatewayconfig.homeassistant.HomeAssistantComponentType.LIGHT
import com.mqgateway.core.gatewayconfig.homeassistant.HomeAssistantComponentType.SENSOR
import com.mqgateway.core.gatewayconfig.homeassistant.HomeAssistantComponentType.SWITCH
import com.mqgateway.core.gatewayconfig.homeassistant.HomeAssistantComponentType.TRIGGER
import com.mqgateway.core.gatewayconfig.homeassistant.HomeAssistantTrigger.TriggerType.BUTTON_LONG_PRESS
import com.mqgateway.core.gatewayconfig.homeassistant.HomeAssistantTrigger.TriggerType.BUTTON_LONG_RELEASE
import com.mqgateway.core.gatewayconfig.homeassistant.HomeAssistantTrigger.TriggerType.BUTTON_SHORT_PRESS
import com.mqgateway.core.gatewayconfig.homeassistant.HomeAssistantTrigger.TriggerType.BUTTON_SHORT_RELEASE
import com.mqgateway.homie.HOMIE_PREFIX
import mu.KotlinLogging

private val LOGGER = KotlinLogging.logger {}

// TODO maj add logs
// TODO maj write tests
class HomeAssistantConverter {

  fun convert(gateway: Gateway): List<HomeAssistantComponent> {

    val devices = gateway.rooms
      .flatMap { it.points }
      .flatMap { it.devices }

    return devices.flatMap { device ->
      val basicProperties = HomeAssistantComponentBasicProperties(device.name, gateway.name, device.id)

      when (device.type) {
        DeviceType.RELAY -> {
          val stateTopic = homieStateTopic(gateway, device, DevicePropertyType.STATE)
          val commandTopic = homieCommandTopic(gateway, device, DevicePropertyType.STATE)
          if (device.config?.get(DEVICE_CONFIG_HA_COMPONENT) == LIGHT.toString()) {
            listOf(HomeAssistantLight(basicProperties, stateTopic, commandTopic, true, RelayDevice.STATE_ON, RelayDevice.STATE_OFF))
          } else {
            listOf(HomeAssistantSwitch(basicProperties, stateTopic, commandTopic, true, RelayDevice.STATE_ON, RelayDevice.STATE_OFF))
          }
        }
        DeviceType.SWITCH_BUTTON -> {
          val homieStateTopic = homieStateTopic(gateway, device, DevicePropertyType.STATE)
          listOf(
            HomeAssistantTrigger(
              basicProperties,
              homieStateTopic,
              SwitchButtonDevice.PRESSED_STATE_VALUE,
              BUTTON_SHORT_PRESS,
              "button"
            ),
            HomeAssistantTrigger(
              basicProperties,
              homieStateTopic,
              SwitchButtonDevice.PRESSED_STATE_VALUE,
              BUTTON_SHORT_RELEASE,
              "button"
            ),
            HomeAssistantTrigger(
              basicProperties,
              homieStateTopic,
              SwitchButtonDevice.LONG_PRESSED_STATE_VALUE,
              BUTTON_LONG_PRESS,
              "button"
            ),
            HomeAssistantTrigger(
              basicProperties,
              homieStateTopic,
              SwitchButtonDevice.LONG_RELEASED_STATE_VALUE,
              BUTTON_LONG_RELEASE,
              "button"
            )
          )
        }
        DeviceType.REED_SWITCH -> {
          listOf(
            HomeAssistantBinarySensor(
              basicProperties,
              homieStateTopic(gateway, device, DevicePropertyType.STATE),
              ReedSwitchDevice.OPEN_STATE_VALUE,
              ReedSwitchDevice.CLOSED_STATE_VALUE,
              HomeAssistantBinarySensor.DeviceClass.OPENING
            )
          )
        }
        DeviceType.MOTION_DETECTOR -> {
          listOf(
            HomeAssistantBinarySensor(
              basicProperties,
              homieStateTopic(gateway, device, DevicePropertyType.STATE),
              MotionSensorDevice.MOVE_START_STATE_VALUE,
              MotionSensorDevice.MOVE_STOP_STATE_VALUE,
              HomeAssistantBinarySensor.DeviceClass.MOTION
            )
          )
        }
        DeviceType.EMULATED_SWITCH -> {
          val stateTopic = homieStateTopic(gateway, device, DevicePropertyType.STATE)
          val commandTopic = homieCommandTopic(gateway, device, DevicePropertyType.STATE)
          listOf(
            HomeAssistantSwitch(
              basicProperties,
              stateTopic,
              commandTopic,
              false,
              EmulatedSwitchButtonDevice.PRESSED_STATE_VALUE,
              EmulatedSwitchButtonDevice.RELEASED_STATE_VALUE
            )
          )
        }
        DeviceType.BME280 -> {
          val availabilityTopic = homieStateTopic(gateway, device, DevicePropertyType.STATE)

          listOf(
            HomeAssistantSensor(
              basicProperties,
              availabilityTopic,
              PeriodicSerialInputDevice.AVAILABILITY_ONLINE_STATE,
              PeriodicSerialInputDevice.AVAILABILITY_OFFLINE_STATE,
              HomeAssistantSensor.DeviceClass.TEMPERATURE,
              homieStateTopic(gateway, device, DevicePropertyType.TEMPERATURE),
              device.type.property(DevicePropertyType.TEMPERATURE).unit.value
            ),
            HomeAssistantSensor(
              basicProperties,
              availabilityTopic,
              PeriodicSerialInputDevice.AVAILABILITY_ONLINE_STATE,
              PeriodicSerialInputDevice.AVAILABILITY_OFFLINE_STATE,
              HomeAssistantSensor.DeviceClass.HUMIDITY,
              homieStateTopic(gateway, device, DevicePropertyType.HUMIDITY),
              device.type.property(DevicePropertyType.HUMIDITY).unit.value
            ),
            HomeAssistantSensor(
              basicProperties,
              availabilityTopic,
              PeriodicSerialInputDevice.AVAILABILITY_ONLINE_STATE,
              PeriodicSerialInputDevice.AVAILABILITY_OFFLINE_STATE,
              HomeAssistantSensor.DeviceClass.PRESSURE,
              homieStateTopic(gateway, device, DevicePropertyType.PRESSURE),
              device.type.property(DevicePropertyType.PRESSURE).unit.value
            ),
            HomeAssistantSensor(
              basicProperties,
              availabilityTopic,
              PeriodicSerialInputDevice.AVAILABILITY_ONLINE_STATE,
              PeriodicSerialInputDevice.AVAILABILITY_OFFLINE_STATE,
              HomeAssistantSensor.DeviceClass.TIMESTAMP,
              homieStateTopic(gateway, device, DevicePropertyType.LAST_PING),
              device.type.property(DevicePropertyType.LAST_PING).unit.value
            )
          )
        }
        DeviceType.DHT22 -> {
          val availabilityTopic = homieStateTopic(gateway, device, DevicePropertyType.STATE)
          listOf(
            HomeAssistantSensor(
              basicProperties,
              availabilityTopic,
              PeriodicSerialInputDevice.AVAILABILITY_ONLINE_STATE,
              PeriodicSerialInputDevice.AVAILABILITY_OFFLINE_STATE,
              HomeAssistantSensor.DeviceClass.TEMPERATURE,
              homieStateTopic(gateway, device, DevicePropertyType.TEMPERATURE),
              device.type.property(DevicePropertyType.TEMPERATURE).unit.value
            ),
            HomeAssistantSensor(
              basicProperties,
              availabilityTopic,
              PeriodicSerialInputDevice.AVAILABILITY_ONLINE_STATE,
              PeriodicSerialInputDevice.AVAILABILITY_OFFLINE_STATE,
              HomeAssistantSensor.DeviceClass.HUMIDITY,
              homieStateTopic(gateway, device, DevicePropertyType.HUMIDITY),
              device.type.property(DevicePropertyType.HUMIDITY).unit.value
            ),
            HomeAssistantSensor(
              basicProperties,
              availabilityTopic,
              PeriodicSerialInputDevice.AVAILABILITY_ONLINE_STATE,
              PeriodicSerialInputDevice.AVAILABILITY_OFFLINE_STATE,
              HomeAssistantSensor.DeviceClass.TIMESTAMP,
              homieStateTopic(gateway, device, DevicePropertyType.LAST_PING),
              device.type.property(DevicePropertyType.LAST_PING).unit.value
            )
          )
        }
        DeviceType.SCT013 -> {
          val availabilityTopic = homieStateTopic(gateway, device, DevicePropertyType.STATE)
          listOf(
            HomeAssistantSensor(
              basicProperties,
              availabilityTopic,
              PeriodicSerialInputDevice.AVAILABILITY_ONLINE_STATE,
              PeriodicSerialInputDevice.AVAILABILITY_OFFLINE_STATE,
              HomeAssistantSensor.DeviceClass.POWER,
              homieStateTopic(gateway, device, DevicePropertyType.POWER),
              device.type.property(DevicePropertyType.POWER).unit.value
            ),
            HomeAssistantSensor(
              basicProperties,
              availabilityTopic,
              PeriodicSerialInputDevice.AVAILABILITY_ONLINE_STATE,
              PeriodicSerialInputDevice.AVAILABILITY_OFFLINE_STATE,
              HomeAssistantSensor.DeviceClass.TIMESTAMP,
              homieStateTopic(gateway, device, DevicePropertyType.LAST_PING),
              device.type.property(DevicePropertyType.LAST_PING).unit.value
            )
          )
        }
        DeviceType.TIMER_SWITCH -> listOf()
      }
    }
  }

  private fun homieStateTopic(gateway: Gateway, device: DeviceConfig, propertyType: DevicePropertyType): String {
    return "$HOMIE_PREFIX/${gateway.name}/${device.id}/$propertyType"
  }

  private fun homieCommandTopic(gateway: Gateway, device: DeviceConfig, propertyType: DevicePropertyType): String {
    return homieStateTopic(gateway, device, propertyType) + "/set"
  }

  private val defaultMqGatewayTypeToHaComponent = mapOf(
    DeviceType.RELAY to LIGHT,
    DeviceType.SWITCH_BUTTON to TRIGGER,
    DeviceType.EMULATED_SWITCH to SWITCH,
    DeviceType.MOTION_DETECTOR to BINARY_SENSOR,
    DeviceType.REED_SWITCH to BINARY_SENSOR,
    DeviceType.BME280 to SENSOR,
    DeviceType.DHT22 to SENSOR,
  )

  companion object {
    const val DEVICE_CONFIG_HA_COMPONENT: String = "haComponent"
  }
}
