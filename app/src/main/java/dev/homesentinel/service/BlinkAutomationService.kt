package dev.homesentinel.service

import dev.homesentinel.domain.model.*
import dev.homesentinel.domain.repository.WifiScanner
import dev.homesentinel.domain.usecase.*
import kotlinx.coroutines.*

/**
 * Coroutine coordinator. Methods run on the service's main dispatcher. Network jobs are cancellable
 * and revision-checked; pending actions are never replayed on restart.
 */
class BlinkAutomationService(
    private val scope: CoroutineScope,
    private val scanner: WifiScanner,
    private val ensure: EnsureBlinkState,
    private val now: () -> Long,
    private val publish: (MonitorStatus) -> Unit,
    private val log: (String, LogType) -> Unit,
    private val settingsStillCurrent: suspend (Settings) -> Boolean = { true },
    private val logError: (String, Throwable) -> Unit = { message, _ -> log(message, LogType.ERROR) },
    private val wallNow: () -> Long = System::currentTimeMillis,
) {
    private val machine = PresenceMachine()
    private var settings = Settings()
    private var deadlineJob: Job? = null
    private var staleJob: Job? = null
    private var commandJob: Job? = null
    private var desired: BlinkStatus? = null
    private var satisfied: BlinkStatus? = null
    private var revision = 0L
    private var lastSignalMono: Long? = null
    private var lastSignalWall: Long? = null

    fun configure(next: Settings) {
        if (next == settings) return
        if (next.signalSourceKey != settings.signalSourceKey) {
            lastSignalMono = null
            lastSignalWall = null
        }
        val needsReset =
            next.enabled != settings.enabled ||
                next.homeSsid != settings.homeSsid ||
                next.mode != settings.mode ||
                next.bluetoothDeviceAddress != settings.bluetoothDeviceAddress ||
                next.systemId != settings.systemId ||
                next.delaySeconds != settings.delaySeconds
        val disarmChanged = next.autoDisarm != settings.autoDisarm
        settings = next
        if (needsReset) {
            invalidate("Waiting for a fresh observation")
            scanner.requestScan()
        } else if (disarmChanged) {
            updateDesired()
            publishState("Disarm-on-return setting updated")
        }
    }

    fun evidence(evidence: WifiEvidence) {
        if (!settings.enabled) return
        val before = machine.state
        val beforeObservation = machine.lastObservation
        val observedNow = now()
        machine.observe(evidence, settings, observedNow)
        if (machine.lastObservation == beforeObservation) return
        evidence.signalAt?.let { signalAt ->
            if (evidence.present && signalAt <= evidence.observedAt &&
                observedNow - signalAt in 0..PresenceMachine.MAX_AGE_MS &&
                (lastSignalMono == null || signalAt > lastSignalMono!!)) {
                lastSignalMono = signalAt
                lastSignalWall = wallNow() - (observedNow - signalAt)
            }
        }
        when {
            machine.state == Presence.HOME && before != Presence.HOME ->
                log("Home ${settings.mode} detected", LogType.SIGNAL_DETECTED)
            machine.state == Presence.AWAY_PENDING && before != Presence.AWAY_PENDING -> {
                log("Home ${settings.mode} lost — Waiting ${settings.delaySeconds} seconds...", LogType.SIGNAL_LOST)
                scheduleDeadline()
            }
            machine.state == Presence.AWAY && before != Presence.AWAY ->
                log("Home ${settings.mode} still unavailable", LogType.ABSENCE_CONFIRMED)
        }
        if (machine.state != Presence.AWAY_PENDING) {
            deadlineJob?.cancel()
            deadlineJob = null
        }
        staleJob?.cancel()
        staleJob =
            scope.launch {
                delay(
                    (machine.lastObservation!! + PresenceMachine.STALE_AFTER_MS - now())
                        .coerceAtLeast(0)
                )
                if (machine.expire(now())) {
                    cancelAction()
                    publishState("Scan results expired: state unknown")
                    log("Presence evidence expired — automation paused", LogType.SIGNAL_UNKNOWN)
                }
            }
        updateDesired()
        publishState(
            when (machine.state) {
                Presence.HOME -> "Home sensor detected"
                Presence.AWAY_PENDING -> "Confirming absence"
                Presence.AWAY -> "Home sensor absence confirmed"
                Presence.UNKNOWN -> "Waiting for a valid scan"
            }
        )
    }

    fun invalid(reason: String) {
        invalidate(reason)
        log("Sensor unavailable — state unknown", LogType.SIGNAL_UNKNOWN)
    }

    private fun invalidate(reason: String) {
        machine.reset()
        deadlineJob?.cancel()
        deadlineJob = null
        staleJob?.cancel()
        staleJob = null
        cancelAction()
        publishState(reason)
    }

    fun scanFailed() {
        // A throttled scan does not erase recent valid evidence and does not confirm absence.
        publishState("Scan denied or failed: waiting for the next Android scan")
    }

    private fun scheduleDeadline() {
        deadlineJob?.cancel()
        deadlineJob =
            scope.launch {
                delay((machine.deadline!! - now()).coerceAtLeast(0))
                if (machine.state == Presence.AWAY_PENDING) {
                    scanner.requestScan()
                    publishState("Delay elapsed: waiting for a fresh confirmation scan")
                    log("Delay elapsed — waiting for a fresh confirmation scan", LogType.WAITING_CONFIRMATION)
                }
            }
    }

    private fun updateDesired() {
        val next =
            when (machine.state) {
                Presence.HOME -> if (settings.autoDisarm) BlinkStatus.DISARMED else null
                Presence.AWAY -> BlinkStatus.ARMED
                else -> null
            }
        if (desired != next) {
            cancelAction()
            desired = next
        }
        if (next != null && satisfied != next && commandJob?.isActive != true) retryNow()
    }

    fun retryNow() {
        val wanted = desired ?: return
        if (satisfied == wanted || commandJob?.isActive == true) return
        if (!fresh()) return
        val generation = revision
        val expectedSettings = settings
        commandJob =
            scope.launch {
                for (attempt in 0..3) {
                    if (attempt > 0) delay(15_000L * (1L shl (attempt - 1)))
                    if (!validAction(generation, wanted, expectedSettings)) return@launch
                    try {
                        val changed =
                            ensure(wanted) { validAction(generation, wanted, expectedSettings) }
                        if (validAction(generation, wanted, expectedSettings)) {
                            satisfied = wanted
                            log(
                                if (changed)
                                    "Blink system ${if (wanted == BlinkStatus.ARMED) "armed" else "disarmed"}"
                                else
                                    "Blink already ${if (wanted == BlinkStatus.ARMED) "armed" else "disarmed"} — no command sent",
                                if (wanted == BlinkStatus.ARMED) LogType.BLINK_ARMED else LogType.BLINK_DISARMED,
                            )
                        }
                        return@launch
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: BlinkException) {
                        logError("Blink command failed: ${e.message}", e)
                        if (
                            e.kind == BlinkException.Kind.AUTH ||
                                e.kind == BlinkException.Kind.PROTOCOL
                        )
                            return@launch
                        if (e.kind == BlinkException.Kind.RATE_LIMIT) return@launch
                    } catch (e: Exception) {
                        logError("Blink command failed: ${e.message}", e)
                        return@launch
                    }
                }
            }
    }

    private fun fresh(): Boolean =
        machine.lastObservation?.let { now() - it <= PresenceMachine.MAX_AGE_MS } == true

    private suspend fun validAction(
        generation: Long,
        wanted: BlinkStatus,
        expected: Settings,
    ): Boolean {
        val unchanged = settingsStillCurrent(expected)
        return unchanged && generation == revision && desired == wanted && fresh()
    }

    private fun cancelAction() {
        revision++
        commandJob?.cancel()
        commandJob = null
        desired = null
        satisfied = null
    }

    private fun publishState(note: String) {
        publish(MonitorStatus(
            true, machine.state, machine.deadline, machine.lastObservation, note,
            lastSignalAtEpochMillis = lastSignalWall,
            lastSignalSource = settings.signalSourceKey,
        ))
    }

    fun close() {
        deadlineJob?.cancel()
        staleJob?.cancel()
        cancelAction()
        machine.reset()
    }
}
