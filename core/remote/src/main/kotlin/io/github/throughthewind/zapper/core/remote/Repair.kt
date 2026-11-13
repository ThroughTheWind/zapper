package io.github.throughthewind.zapper.core.remote

import io.github.throughthewind.zapper.core.model.TvDevice

/** Why pairing runs again for a TV that was already paired. */
enum class RepairReason {
    /** The TV no longer accepts the saved token; pairing re-runs automatically. */
    TokenRejected,

    /** The TV presented a different certificate; pairing re-runs only when the user asks. */
    IdentityChanged,
}

/**
 * Connects to [device] with a fresh session from [adapter]. [onSession] sees each session before it
 * connects, so the caller can show its state. When the TV rejects a saved token, the credentials are
 * token is forgotten and pairing runs again once (SPEC FR-2.4), after [onRepair].
 *
 * A changed certificate ([TvError.CertificateChanged]) is never re-trusted here: a different
 * certificate can mean a different machine answering at the TV's address, so the user decides,
 * through [forgetAndConnect].
 *
 * @throws TvException when the last attempt fails.
 */
suspend fun connectOrRepair(
    device: TvDevice,
    adapter: TvAdapter,
    credentials: CredentialStore,
    onSession: suspend (TvSession) -> Unit,
    onRepair: (RepairReason) -> Unit = {},
): TvSession {
    val first = adapter.createSession(device, credentials)
    onSession(first)
    try {
        first.connect()
        return first
    } catch (e: TvException) {
        if (e.error != TvError.TokenRejected) throw e
    }
    // Only the token goes: the certificate pin is kept, so the new pairing still talks to the same TV.
    onRepair(RepairReason.TokenRejected)
    credentials.remove(device.id, TOKEN)
    val second = adapter.createSession(device, credentials)
    onSession(second)
    second.connect()
    return second
}

/** Forgets [device]'s credentials, then connects, so the TV asks to pair again. */
suspend fun forgetAndConnect(
    device: TvDevice,
    adapter: TvAdapter,
    credentials: CredentialStore,
    onSession: suspend (TvSession) -> Unit,
): TvSession {
    credentials.clear(device.id)
    val session = adapter.createSession(device, credentials)
    onSession(session)
    session.connect()
    return session
}
