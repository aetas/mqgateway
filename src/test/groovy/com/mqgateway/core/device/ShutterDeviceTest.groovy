package com.mqgateway.core.device

import static com.mqgateway.core.gatewayconfig.DevicePropertyType.POSITION
import static com.mqgateway.core.gatewayconfig.DevicePropertyType.STATE

import com.mqgateway.utils.UpdateListenerStub
import com.pi4j.io.gpio.PinMode
import com.pi4j.io.gpio.PinState
import com.pi4j.io.gpio.SimulatedGpioProvider
import com.pi4j.io.gpio.impl.GpioControllerImpl
import com.pi4j.io.gpio.impl.GpioPinImpl
import com.pi4j.io.gpio.impl.PinImpl
import java.time.Duration
import spock.lang.Specification
import spock.lang.Subject
import spock.util.time.MutableClock

class ShutterDeviceTest extends Specification {

	def gpioProvider = new SimulatedGpioProvider()

	def stopPinImpl = new PinImpl("com.pi4j.gpio.extension.mcp.MCP23017GpioProvider", 1, "com.pi4j.gpio.extension.mcp.MCP23017GpioProvider", EnumSet<PinMode>.of(PinMode.DIGITAL_OUTPUT))
	def stopPin = new GpioPinImpl(new GpioControllerImpl(gpioProvider), gpioProvider, stopPinImpl)
	RelayDevice stopRelay = new RelayDevice("stopRelay", stopPin)

	def upDownPinImpl = new PinImpl("com.pi4j.gpio.extension.mcp.MCP23017GpioProvider", 2, "com.pi4j.gpio.extension.mcp.MCP23017GpioProvider", EnumSet<PinMode>.of(PinMode.DIGITAL_OUTPUT))
	def upDownPin = new GpioPinImpl(new GpioControllerImpl(gpioProvider), gpioProvider, upDownPinImpl)
	RelayDevice upDownRelay = new RelayDevice("upDownRelay", upDownPin)

	static int FULL_OPEN_MS = 2000
	static int FULL_CLOSE_MS = 1000

	@Subject
	ShutterDevice shutterDevice = new ShutterDevice("testShutter", stopRelay, upDownRelay, FULL_OPEN_MS, FULL_CLOSE_MS)

	UpdateListenerStub listenerStub = new UpdateListenerStub()
	TimerFake stoppingTimer = new TimerFake()
	MutableClock clock = new MutableClock()

	void setup() {
		gpioProvider.setMode(stopPinImpl, PinMode.DIGITAL_OUTPUT)
		gpioProvider.setMode(upDownPinImpl, PinMode.DIGITAL_OUTPUT)
		shutterDevice.setStoppingTimerForTests(stoppingTimer)
	}

	def "should start upDownRelay for 30% of fullCloseTime when position is changed from 100 (OPEN) to 70 (CLOSING/DOWN)"() {
		given:
		shutterDevice.addListener(listenerStub)
		shutterDevice.initProperty(POSITION.toString(), "100")
		shutterDevice.init()

		when:
		shutterDevice.change(POSITION.toString(), "70")

		then:
		stoppingTimer.lastScheduledDelay == (FULL_CLOSE_MS * 0.3).toLong()
		stopPin.state == PinState.LOW
		upDownPin.state == PinState.LOW
		stoppingTimer.runNow()
		stopPin.state == PinState.HIGH
		upDownPin.state == PinState.HIGH
	}

	def "should start upDownRelay for 55% of fullOpenTime when position is changed from 10 (almost closed) to 65 (OPENING/UP)"() {
		given:
		shutterDevice.addListener(listenerStub)
		shutterDevice.initProperty(POSITION.toString(), "10")
		shutterDevice.init()

		when:
		shutterDevice.change(POSITION.toString(), "65")

		then:
		stoppingTimer.lastScheduledDelay == (FULL_OPEN_MS * 0.55).toLong()
		stopPin.state == PinState.LOW
		upDownPin.state == PinState.HIGH
		stoppingTimer.runNow()
		stopPin.state == PinState.HIGH
		upDownPin.state == PinState.HIGH
	}

	def "should notify about new position when move has finished"() {
		given:
		shutterDevice.addListener(listenerStub)
		shutterDevice.initProperty(POSITION.toString(), "80")
		shutterDevice.init()

		when:
		shutterDevice.change(POSITION.toString(), "20")

		then:
		stoppingTimer.runNow()
		listenerStub.updatesByPropertyId(POSITION.toString())[1].newValue == "20"
	}

	def "should notify about OPENING/CLOSING and STOPPED when moving from #startPosition to #targetPosition"() {
		given:
		shutterDevice.addListener(listenerStub)
		shutterDevice.initProperty(POSITION.toString(), startPosition.toString())
		shutterDevice.init()

		when:
		shutterDevice.change(POSITION.toString(), targetPosition.toString())

		then:
		listenerStub.updatesByPropertyId("state").size() == 1
		listenerStub.updatesByPropertyId("state")[0].newValue == expectedUpdates[0]
		stoppingTimer.runNow()
		listenerStub.updatesByPropertyId("state")[1].newValue == expectedUpdates[1]

		where:
		startPosition | targetPosition | expectedUpdates
		10            | 50             | ["OPENING", "STOPPED"]
		60            | 20             | ["CLOSING", "STOPPED"]
		0             | 100            | ["OPENING", "STOPPED"]
		100           | 0              | ["CLOSING", "STOPPED"]

	}

	def "should go DOWN and set position to 0 when position has not been initialized earlier"() {
		given:
		shutterDevice.addListener(listenerStub)

		when:
		shutterDevice.init()

		then:
		stoppingTimer.lastScheduledDelay > FULL_CLOSE_MS
		listenerStub.updatesByPropertyId("state").size() == 1
		listenerStub.updatesByPropertyId("state")[0].newValue == "CLOSING"
		stoppingTimer.runNow()
		listenerStub.updatesByPropertyId("state")[1].newValue == "STOPPED"
		listenerStub.updatesByPropertyId("position")[0].newValue == "0"
	}

	def "should ignore any new property changes when current position has not been initialized yet (it is in progress)"() {
		given:
		shutterDevice.addListener(listenerStub)

		when:
		shutterDevice.init()

		then:
		shutterDevice.change(POSITION.toString(), "50")
		shutterDevice.change(POSITION.toString(), "0")
		shutterDevice.change(POSITION.toString(), "100")
		shutterDevice.change(STATE.toString(), "OPEN")
		shutterDevice.change(STATE.toString(), "CLOSE")
		listenerStub.updatesByPropertyId("state").size() == 1
	}

	def "should update position and start moving to new one when new position has been received during move #directionWhenChanged"() {
		given:
		shutterDevice.clockForTests = clock
		shutterDevice.addListener(listenerStub)
		shutterDevice.initProperty(POSITION.toString(), initialPosition)
		shutterDevice.init()
		shutterDevice.change(POSITION.toString(), targetPositon)

		when:
		clock.plus(Duration.ofMillis((changedAfterMs).toLong()))
		shutterDevice.change(POSITION.toString(), "70")

		then:
		listenerStub.updatesByPropertyId("position")[0].newValue == initialPosition
		listenerStub.updatesByPropertyId("position")[1].newValue == expectedPositionOnChange
		stoppingTimer.runNow()
		listenerStub.updatesByPropertyId("position")[2].newValue == "70"
		listenerStub.updatesByPropertyId("state")[2].newValue == "STOPPED"

		where:
		initialPosition | targetPositon | changedAfterMs      | expectedPositionOnChange | directionWhenChanged
		"100"           | "10"          | FULL_CLOSE_MS * 0.5 | "50"                     | "down"
		"10"            | "90"          | FULL_OPEN_MS * 0.25 | "35"                     | "up"
	}

	def "should not change anything and not throw when trying to change unsupported/unknown property"() {
		given:
		shutterDevice.addListener(listenerStub)
		shutterDevice.initProperty(POSITION.toString(), "80")
		shutterDevice.init()

		when:
		shutterDevice.change("something", "OPEN")

		then:
		listenerStub.getReceivedUpdates() == [new UpdateListenerStub.Update(shutterDevice.id, POSITION.toString(), "80")]
		notThrown()
	}

	def "should not change anything and not throw when trying to set STATE to unknown value"() {
		given:
		shutterDevice.addListener(listenerStub)
		shutterDevice.initProperty(POSITION.toString(), "80")
		shutterDevice.init()

		when:
		shutterDevice.change(STATE.toString(), "90")

		then:
		listenerStub.getReceivedUpdates() == [new UpdateListenerStub.Update(shutterDevice.id, POSITION.toString(), "80")]
		notThrown()
	}

	def "should not change anything and not throw when trying to set POSITION to non-numeric value"() {
		given:
		shutterDevice.addListener(listenerStub)
		shutterDevice.initProperty(POSITION.toString(), "80")
		shutterDevice.init()

		when:
		shutterDevice.change(POSITION.toString(), "OPEN")

		then:
		listenerStub.getReceivedUpdates() == [new UpdateListenerStub.Update(shutterDevice.id, POSITION.toString(), "80")]
		notThrown()
	}

	def "should update current position when stopped during movement"() {
		given:
		shutterDevice.clockForTests = clock
		shutterDevice.addListener(listenerStub)
		shutterDevice.initProperty(POSITION.toString(), "90")
		shutterDevice.init()
		shutterDevice.change(POSITION.toString(), "10")
		clock.plus(Duration.ofMillis((FULL_CLOSE_MS * 0.25).toLong()))

		when:
		shutterDevice.change(STATE.toString(), "STOP")

		then:
		listenerStub.updatesByPropertyId(POSITION.toString()).last().newValue == (90-25).toString()
	}

	def "should cancel previously scheduled stopping when new command has been received"() {
		given:
		shutterDevice.clockForTests = clock
		shutterDevice.addListener(listenerStub)
		shutterDevice.initProperty(POSITION.toString(), "100")
		shutterDevice.init()
		shutterDevice.change(POSITION.toString(), "90")

		when:
		shutterDevice.change(POSITION.toString(), "10")
		clock.plus(Duration.ofMillis((FULL_CLOSE_MS * 0.10).toLong()))

		then:
		listenerStub.updatesByPropertyId(STATE.toString()).last().newValue == "CLOSING"
		stoppingTimer.scheduledTasks.findAll{ it.hasBeenCancelled() }.size() == 1
		stoppingTimer.scheduledTasks.findAll{ !it.hasBeenCancelled() }.size() == 1

	}

	def "should move to position #expectedPosition when state property is changed to #newStatus"() {
		given:
		shutterDevice.addListener(listenerStub)
		shutterDevice.initProperty(POSITION.toString(), "50")
		shutterDevice.init()

		when:
		shutterDevice.change(STATE.toString(), newStatus)

		then:
		stoppingTimer.runNow()
		listenerStub.updatesByPropertyId(POSITION.toString())[1].newValue == expectedPosition

		where:
		newStatus | expectedPosition
		"OPEN"    | "100"
		"CLOSE"   | "0"
	}

	def "should move shutter for more than full time when target position is fully #targetPositionDesc (#targetPosition) regardless of initial position"() {
		given:
		shutterDevice.addListener(listenerStub)
		shutterDevice.initProperty(POSITION.toString(), "10")
		shutterDevice.init()

		when:
		shutterDevice.change(POSITION.toString(), targetPosition)

		then:
		stoppingTimer.lastScheduledDelay == expectedScheduledTimeBeforeStop

		where:
		targetPositionDesc | targetPosition | expectedScheduledTimeBeforeStop
		"closed"           | "0"            | FULL_CLOSE_MS + ShutterDevice.EXTRA_MS_FOR_RESET_POSITION
		"open"             | "100"          | FULL_OPEN_MS + ShutterDevice.EXTRA_MS_FOR_RESET_POSITION

	}
}


class TimerTaskWrapper {

	TimerTask originalTimerTask

	TimerTaskWrapper(TimerTask originalTimerTask) {
		this.originalTimerTask = originalTimerTask
	}

	boolean hasBeenCancelled() {
		def stateField = TimerTask.class.getDeclaredField("state")
		stateField.setAccessible(true)
		def state = stateField.get(originalTimerTask)
		return state == 3 // CANCELLED
	}
}

class TimerFake extends Timer {

	Long lastScheduledDelay
	TimerTask lastTaskToRun

	List<TimerTaskWrapper> scheduledTasks = new ArrayList<>()

	@Override
	void schedule(TimerTask task, long delay) {
		lastScheduledDelay = delay
		scheduledTasks.add(new TimerTaskWrapper(task))
		lastTaskToRun = task
	}

	void runNow() {
		lastTaskToRun.run()
	}
}