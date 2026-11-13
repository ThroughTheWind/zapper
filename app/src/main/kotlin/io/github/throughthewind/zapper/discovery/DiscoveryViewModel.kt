package io.github.throughthewind.zapper.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.throughthewind.zapper.connection.MulticastLock
import io.github.throughthewind.zapper.core.remote.AdapterRegistry
import io.github.throughthewind.zapper.core.remote.DiscoveredTv
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class DiscoveryUiState(
    val scanning: Boolean = false,
    val found: List<DiscoveredTv> = emptyList(),
    /** The scan ended with nothing found: show the tips and the manual path (SPEC FR-1.3). */
    val nothingFound: Boolean = false,
    val manualAddress: String = "",
    val manualChecking: Boolean = false,
    val manualError: String? = null,
)

/** Scans for TVs for [scanTimeoutMs], and probes an address the user types (SPEC F1). */
class DiscoveryViewModel(
    private val adapters: AdapterRegistry,
    private val multicastLock: MulticastLock,
    private val scanTimeoutMs: Long = SCAN_TIMEOUT_MS,
) : ViewModel() {
    private val _state = MutableStateFlow(DiscoveryUiState())
    val state: StateFlow<DiscoveryUiState> = _state.asStateFlow()

    private var scan: Job? = null

    fun scan() {
        scan?.cancel()
        _state.update { it.copy(scanning = true, nothingFound = false, found = emptyList()) }
        scan =
            viewModelScope.launch {
                withTimeoutOrNull(scanTimeoutMs) {
                    multicastLock.hold {
                        adapters.discover().collect { tv -> _state.update { it.copy(found = it.found + tv) } }
                    }
                }
                _state.update { it.copy(scanning = false, nothingFound = it.found.isEmpty()) }
            }
    }

    fun onAddressChange(text: String) {
        val cleaned = text.filter { it.isDigit() || it == '.' }.take(15)
        _state.update { it.copy(manualAddress = cleaned, manualError = null) }
    }

    /** Probes the typed address with every adapter; [onFound] gets the TV that answered. */
    fun addByAddress(onFound: (DiscoveredTv) -> Unit) {
        val address = _state.value.manualAddress
        if (!isIpv4(address)) {
            _state.update { it.copy(manualError = "Enter an address like 192.168.1.20") }
            return
        }
        _state.update { it.copy(manualChecking = true, manualError = null) }
        viewModelScope.launch {
            val tv = adapters.probe(address)
            _state.update {
                it.copy(manualChecking = false, manualError = if (tv == null) "No supported TV answered at this address" else null)
            }
            tv?.let(onFound)
        }
    }

    companion object {
        const val SCAN_TIMEOUT_MS = 10_000L

        fun isIpv4(text: String): Boolean {
            val parts = text.split('.')
            return parts.size == 4 &&
                parts.all { part -> part.isNotEmpty() && part.length <= 3 && part.toIntOrNull()?.let { it in 0..255 } == true }
        }
    }
}
