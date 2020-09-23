package com.mqgateway.core.gatewayconfig.homeassistant

import com.fasterxml.jackson.databind.ObjectMapper
import com.mqgateway.homie.mqtt.MqttMessage
import com.mqgateway.utils.MqttClientStub
import groovy.json.JsonSlurper
import spock.lang.Specification
import spock.lang.Subject

class HomeAssistantPublisherTest extends Specification {

	@Subject
	HomeAssistantPublisher publisher = new HomeAssistantPublisher(new ObjectMapper())

	MqttClientStub mqttClientStub = new MqttClientStub()

	void setup() {
		mqttClientStub.connect(new MqttMessage("", "", 1, true), true)
	}

	def "should publish all components configurations to the proper MQTT topic"() {
		given:
		def component1 = new HomeAssistantLight(
			new HomeAssistantComponentBasicProperties("test1Name", "someNodeId1", "someObjectId1"),
			"someStateTopic1", "someCommandTopic1", true, "ONtest1", "OFFtest1")
		def component2 = new HomeAssistantLight(
			new HomeAssistantComponentBasicProperties("test2Name", "someNodeId2", "someObjectId2"),
			"someStateTopic2", "someCommandTopic2", false, "ONtest2", "OFFtest2")

		when:
		publisher.publish(mqttClientStub, "testRoot", [component1, component2])

		then:
		def publishedMessages = mqttClientStub.getPublishedMessages()
		publishedMessages[0].topic == "testRoot/light/someNodeId1/someObjectId1/config"
		publishedMessages[1].topic == "testRoot/light/someNodeId2/someObjectId2/config"
		assertJsonEqual(component1, new JsonSlurper().parseText(publishedMessages[0].payload) as Map)
		assertJsonEqual(component2, new JsonSlurper().parseText(publishedMessages[1].payload) as Map)

		publishedMessages.every { it.retain }
	}

	static void assertJsonEqual(HomeAssistantLight light, Map jsonLight) {
		assert light.basicProperties.name == jsonLight.name
		assert light.stateTopic == jsonLight.state_topic
		assert light.commandTopic == jsonLight.command_topic
		assert light.retain == jsonLight.retain
		assert light.payloadOn == jsonLight.payload_on
		assert light.payloadOff == jsonLight.payload_off
	}
}
