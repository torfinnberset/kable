package com.juul.kable

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Waits for the result of an L2CAP open. CoreBluetooth cannot cancel an open, and only the end of the connection ends
 * it. A cancel of the caller therefore calls [endConnection], waits until the open has ended, gives a channel that
 * arrived to [abandon], and ends with the cancellation.
 */
internal suspend fun <R> awaitL2CapOpen(
    pending: Deferred<R>,
    endConnection: () -> Unit,
    abandon: (R) -> Unit,
): R = try {
    pending.await()
} catch (e: CancellationException) {
    // An open that ended with a cancellation of its own leaves the caller and its connection as they are.
    if (currentCoroutineContext().isActive) throw e
    withContext(NonCancellable) {
        endConnection()
        runCatching { pending.await() }.getOrNull()?.let(abandon)
    }
    throw e
}
