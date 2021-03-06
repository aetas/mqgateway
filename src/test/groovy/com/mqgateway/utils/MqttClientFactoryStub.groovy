package com.mqgateway.utils


import kotlin.Unit
import kotlin.jvm.functions.Function0
import org.jetbrains.annotations.NotNull
import com.mqgateway.homie.mqtt.MqttClient
import com.mqgateway.homie.mqtt.MqttClientFactory

class MqttClientFactoryStub implements MqttClientFactory {

	MqttClientStub mqttClient

	@Override
	MqttClient create(@NotNull String clientId, @NotNull Function0<Unit> connectedListener, @NotNull Function0<Unit> disconnectedListener) {
		def listener = new TestMqttConnectionListener() {

			@Override
			def onConnected() {
				connectedListener.invoke()
			}

			@Override
			def onDisconnected() {
				disconnectedListener.invoke()
			}
		}
		this.mqttClient = new MqttClientStub([listener])
		return mqttClient
	}
}