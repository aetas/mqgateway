package com.mqgateway.core.device

import com.mqgateway.core.device.RelayDevice.RelayState.CLOSED
import com.mqgateway.core.device.RelayDevice.RelayState.OPEN
import com.mqgateway.core.gatewayconfig.DevicePropertyType.POSITION
import com.mqgateway.core.gatewayconfig.DevicePropertyType.STATE
import com.mqgateway.core.gatewayconfig.DeviceType
import mu.KotlinLogging
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.schedule
import kotlin.math.absoluteValue

private val LOGGER = KotlinLogging.logger {}

class ShutterDevice(
  id: String,
  private val stopRelay: RelayDevice,
  private val upDownRelay: RelayDevice,
  private val fullOpenTimeMs: Long,
  private val fullCloseTimeMs: Long
) : Device(id, DeviceType.SHUTTER) {

  private var currentPosition: Int? = null
    private set(value) {
      field = value
      value?.let { notify(POSITION, it) }
    }
  private var moveStartTime: Instant? = null
  private var state: State = State.STOPPED
    private set(value) {
      field = value
      notify(STATE, value.name)
    }

  private var stoppingTimer = Timer("ShutterMove_$id", false)
  private var scheduledStopTimerTask: TimerTask? = null
  private var clock = Clock.systemDefaultZone()

  override fun initProperty(propertyId: String, value: String) {
    if (propertyId != POSITION.toString()) {
      LOGGER.warn { "Trying to initialize unsupported property '$id.$propertyId'" }
      return
    }
    currentPosition = value.toInt()
  }

  override fun initDevice() {
    super.initDevice()
    stopRelay.init(false)
    upDownRelay.init(false)
    if (currentPosition == null) {
      LOGGER.warn { "Shutter position is unknown. It will be initialized now by closing the shutter." }
      initializeCurrentPositionToClosed()
    }
  }

  private fun initializeCurrentPositionToClosed() {
    goDown()
    stoppingTimer.schedule(fullCloseTimeMs + EXTRA_MS_FOR_RESET_POSITION) {
      stop()
      currentPosition = 0
    }
  }

  override fun change(propertyId: String, newValue: String) {
    if (currentPosition == null) {
      LOGGER.warn {
        "Current position in device $id is unknown. It is currently in state '$state'. " +
            "It is assumed that initialization is running, so this command will be ignored."
      }
      return
    }

    val actualCurrentPosition = calculateActualCurrentPosition()
    if (currentPosition != actualCurrentPosition) {
      LOGGER.debug { "New CurrentPosition found $actualCurrentPosition. Previous was $currentPosition." }
      currentPosition = actualCurrentPosition
    }

    val targetPosition: Int? = calculateTargetPosition(propertyId, newValue)
    if (targetPosition == null) {
      LOGGER.error { "Could not calculate target position for device $id (propertyId=$propertyId, newValue=$newValue)" }
      return
    }

    val requiredMoveTimeMs = calculateRequiredMoveTimeMs(currentPosition!!, targetPosition)

    scheduledStopTimerTask?.cancel()

    val directionToGo = if (targetPosition > currentPosition!!) State.OPENING else State.CLOSING
    if (directionToGo == State.OPENING) {
      LOGGER.info { "Command received to move shutter $id UP to position $targetPosition (${requiredMoveTimeMs}ms)" }
      goUp()
    } else {
      LOGGER.info { "Command received to move shutter $id DOWN to position $targetPosition (${requiredMoveTimeMs}ms)" }
      goDown()
    }

    scheduledStopTimerTask = stoppingTimer.schedule(requiredMoveTimeMs) {
      LOGGER.info { "Stopping shutter $id after move" }
      stop()
      currentPosition = targetPosition
    }
  }

  private fun calculateRequiredMoveTimeMs(currentPosition: Int, targetPosition: Int): Long {
    val positionDifferenceToMove = targetPosition - currentPosition
    val requiredDirection = if (positionDifferenceToMove > 0) State.OPENING else State.CLOSING
    val goUpOrDownTimeMs = if (requiredDirection == State.OPENING) fullOpenTimeMs else fullCloseTimeMs

    return if (targetPosition == POSITION_OPEN || targetPosition == POSITION_CLOSED) {
      goUpOrDownTimeMs + EXTRA_MS_FOR_RESET_POSITION
    } else {
      (goUpOrDownTimeMs * (positionDifferenceToMove.absoluteValue.toFloat() / 100)).toLong()
    }
  }

  private fun calculateTargetPosition(propertyId: String, newValue: String): Int? {
    return when (propertyId) {
      STATE.toString() -> {
        when (newValue) {
          Command.OPEN.name -> POSITION_OPEN
          Command.CLOSE.name -> POSITION_CLOSED
          Command.STOP.name -> currentPosition
          else -> null
        }
      }
      POSITION.toString() -> {
        newValue.toIntOrNull()
      }
      else -> {
        LOGGER.warn { "Trying to change unsupported property '$id.$propertyId'" }
        null
      }
    }
  }

  private fun calculateActualCurrentPosition(): Int {
    return when (state) {
      State.STOPPED -> currentPosition!!
      State.OPENING -> {
        val moveTimeMs = Duration.between(moveStartTime, Instant.now(clock)).toMillis()
        val percentageMoved: Int = ((moveTimeMs.toFloat() / fullOpenTimeMs) * 100).toInt()
        currentPosition!! + percentageMoved
      }
      State.CLOSING -> {
        val moveTimeMs = Duration.between(moveStartTime, Instant.now(clock)).toMillis()
        val percentageMoved: Int = ((moveTimeMs.toFloat() / fullCloseTimeMs) * 100).toInt()
        currentPosition!! - percentageMoved
      }
    }
  }

  private fun goDown() {
    upDownRelay.changeState(CLOSED)
    stopRelay.changeState(CLOSED)
    moveStartTime = Instant.now(clock)
    state = State.CLOSING
  }

  private fun goUp() {
    upDownRelay.changeState(OPEN)
    stopRelay.changeState(CLOSED)
    moveStartTime = Instant.now(clock)
    state = State.OPENING
  }

  private fun stop() {
    stopRelay.changeState(OPEN)
    upDownRelay.changeState(OPEN)
    moveStartTime = null
    state = State.STOPPED
  }

  fun setStoppingTimerForTests(timer: Timer) {
    this.stoppingTimer = timer
  }

  fun setClockForTests(clock: Clock) {
    this.clock = clock
  }

  companion object {
    const val POSITION_CLOSED = 0
    const val POSITION_OPEN = 100
    const val EXTRA_MS_FOR_RESET_POSITION = 1000
  }

  enum class Command {
    OPEN, CLOSE, STOP
  }

  enum class State {
    OPENING, CLOSING, STOPPED
  }
}
