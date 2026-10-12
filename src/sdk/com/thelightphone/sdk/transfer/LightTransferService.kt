package com.thelightphone.sdk.transfer

import com.thelightphone.sdk.SealedLightContext

/**
 * light-amp's SDK patch: a foreground service that keeps long downloads from being throttled.
 * A browser tab has no such thing; start and stop do nothing.
 */
class LightTransferService {
    companion object {
        fun start(context: SealedLightContext, text: String) {}
        fun stop(context: SealedLightContext) {}
    }
}
