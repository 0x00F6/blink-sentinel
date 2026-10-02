package dev.homesentinel.domain.repository

import dev.homesentinel.domain.model.*
import kotlinx.coroutines.flow.StateFlow

interface WifiScanner {
    val networks: StateFlow<List<WifiNetwork>>

    fun requestScan(): Boolean

    /** Refreshes the selection list only; cached results never become presence evidence. */
    fun refreshNetworks()

    fun readSuccessfulScan(homeSsid: String): WifiEvidence?

    fun available(): Boolean
}
