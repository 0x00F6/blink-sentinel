package dev.homesentinel.domain.usecase

import dev.homesentinel.domain.model.MonitorStatus
import dev.homesentinel.domain.model.Presence

/** One alert per confirmed departure, only after a live HOME observation in this service lifetime. */
class DepartureAlertPolicy {
    private var homeObserved = false

    fun onStatus(status: MonitorStatus): Boolean {
        if (!status.running || status.presence == Presence.UNKNOWN) {
            homeObserved = false
            return false
        }
        return when (status.presence) {
            Presence.HOME -> {
                homeObserved = true
                false
            }
            Presence.AWAY -> {
                val alert = homeObserved
                homeObserved = false
                alert
            }
            else -> false
        }
    }
}
