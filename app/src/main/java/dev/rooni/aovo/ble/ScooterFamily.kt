package dev.rooni.aovo.ble

import java.util.UUID

/** The protocol a connected scooter actually speaks. */
enum class ScooterFamily {
    /** ZYD controller: F1F0 data service plus the F2F0 AT command channel. */
    ZYD,

    /** ViCont / SAMIK / Benben dashboard: one FEE0 (or FFF0) characteristic, no password. */
    VICONT,

    /** Connected, but nothing we know how to talk to. */
    UNKNOWN,
}

data class ScooterEndpoint(
    val family: ScooterFamily,
    val service: UUID,
    val write: UUID,
    val notify: UUID,
)

object FamilyDetection {

    /** Checked ahead of ZYD: ViCont dashboards expose both, and only FEE0 is fully backed. */
    private val VICONT_ENDPOINTS = listOf(
        Triple(ViContProtocol.SERVICE, ViContProtocol.CHARACTERISTIC, ViContProtocol.CHARACTERISTIC),
        Triple(
            ViContProtocol.ALT_SERVICE,
            ViContProtocol.ALT_CHARACTERISTIC,
            ViContProtocol.ALT_CHARACTERISTIC,
        ),
    )

    fun endpointFor(services: Collection<UUID>): ScooterEndpoint? {
        val available = services.toHashSet()
        for ((service, write, notify) in VICONT_ENDPOINTS) {
            if (service in available) {
                return ScooterEndpoint(ScooterFamily.VICONT, service, write, notify)
            }
        }
        if (Protocol.DATA_SERVICE in available) {
            return ScooterEndpoint(
                ScooterFamily.ZYD,
                Protocol.DATA_SERVICE,
                Protocol.DATA_TX,
                Protocol.DATA_RX,
            )
        }
        return null
    }

    fun familyFor(services: Collection<UUID>): ScooterFamily =
        endpointFor(services)?.family ?: ScooterFamily.UNKNOWN
}
