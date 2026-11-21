package com.juul.kable

import android.bluetooth.BluetoothSocket
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BluetoothSocketConnectTests {

    @Test
    fun connectOrClose_cancelledWhileConnectBlocks_closesTheSocketAndEndsCancelled(): Unit = runBlocking {
        // As on Android: connect() blocks until the peer answers, and a close makes it throw.
        val closed = CountDownLatch(1)
        val socket = mockk<BluetoothSocket> {
            every { connect() } answers {
                closed.await(60, SECONDS)
                throw IOException("read failed, socket might closed or timeout, read ret: -1")
            }
            every { close() } answers { closed.countDown() }
        }
        // Its own scope: a connect that does not end must fail the check, not hold the test.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val open = scope.launch { socket.connectOrClose { close() } }
        val cause = CompletableDeferred<Throwable?>()
        open.invokeOnCompletion { cause.complete(it) }
        delay(500.milliseconds)

        open.cancel()
        val ended = withTimeoutOrNull(5.seconds) { cause.await() }
        closed.countDown()
        scope.cancel()

        assertNotNull(ended, "the cancelled open did not end within 5 s")
        // The caller cancelled the open: the abort of the connect that the close makes is not a connection failure.
        assertIs<CancellationException>(ended, "the cancelled open ended with $ended")
    }

    @Test
    fun connectOrClose_connectFails_closesTheSocketAndThrowsL2CapException() = runBlocking {
        var closes = 0
        val socket = mockk<BluetoothSocket> {
            every { connect() } throws IOException("read failed, socket might closed or timeout, read ret: -1")
            every { close() } answers { closes++ }
        }

        assertFailsWith<L2CapException> { socket.connectOrClose { close() } }
        assertEquals(1, closes)
    }
}
