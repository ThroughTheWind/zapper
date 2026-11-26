package io.github.throughthewind.zapper

import io.github.throughthewind.zapper.core.remote.TvError

/**
 * What a [TvError] says to the user, in the app's language. [TvError.message] stays English for
 * logs; the UI reads this instead.
 */
fun TvError.describe(): UiText =
    UiText.Res(
        when (this) {
            is TvError.Unreachable -> R.string.error_tv_unreachable
            TvError.PairingRefused -> R.string.error_pairing_refused
            TvError.PairingTimedOut -> R.string.error_pairing_timed_out
            TvError.TokenRejected -> R.string.error_token_rejected
            TvError.CertificateChanged -> R.string.error_certificate_changed
            is TvError.ConnectionLost -> R.string.error_connection_lost
            TvError.NotConnected -> R.string.error_not_connected
            TvError.AppNotInstalled -> R.string.error_app_not_installed
            is TvError.Unsupported -> R.string.error_unsupported
        },
    )
