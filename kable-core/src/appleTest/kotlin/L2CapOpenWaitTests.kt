package com.juul.kable.test

import com.juul.kable.NotConnectedException
import com.juul.kable.awaitL2CapOpen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class L2CapOpenWaitTests {

    @Test
    fun cancelledWait_endsTheConnection_andEndsCancelledWhenTheOpenFails() = runTest {
        val pending = CompletableDeferred<String>()
        var connectionEnded = false
        val abandoned = mutableListOf<String>()
        val wait = launchOutsideTheTest {
            awaitL2CapOpen(
                pending = pending,
                endConnection = {
                    connectionEnded = true
                    pending.completeExceptionally(NotConnectedException("Disconnect detected"))
                },
                abandon = { abandoned += it },
            )
        }

        wait.cancel()
        advanceUntilIdle()

        assertTrue(connectionEnded, "the cancel ends the connection")
        assertTrue(wait.isCompleted, "the wait ends")
        assertTrue(wait.isCancelled, "the wait ends with the cancellation")
        assertEquals(emptyList(), abandoned)
    }

    @Test
    fun cancelledWait_abandonsAChannelThatArrivesAfterTheCancel() = runTest {
        val pending = CompletableDeferred<String>()
        val abandoned = mutableListOf<String>()
        val wait = launchOutsideTheTest {
            awaitL2CapOpen(
                pending = pending,
                endConnection = { pending.complete("late channel") },
                abandon = { abandoned += it },
            )
        }

        wait.cancel()
        advanceUntilIdle()

        assertTrue(wait.isCancelled, "the wait ends with the cancellation")
        assertEquals(listOf("late channel"), abandoned)
    }

    @Test
    fun openThatEndsWithACancellation_doesNotEndTheConnection() = runTest {
        val pending = CompletableDeferred<String>()
        var connectionEnded = false
        pending.completeExceptionally(CancellationException("open cancelled before CoreBluetooth ran it"))

        assertFailsWith<CancellationException> {
            awaitL2CapOpen(
                pending = pending,
                endConnection = { connectionEnded = true },
                abandon = {},
            )
        }

        assertFalse(connectionEnded, "a caller that is not cancelled keeps its connection")
    }

    @Test
    fun openThatDelivers_givesTheChannel() = runTest {
        val pending = CompletableDeferred("channel")

        val channel = awaitL2CapOpen(pending = pending, endConnection = {}, abandon = {})

        assertEquals("channel", channel)
    }
}

// runTest joins its own children: a wait that ignores its cancel must fail the test, not hang it.
private fun TestScope.launchOutsideTheTest(block: suspend () -> Unit): Job =
    CoroutineScope(StandardTestDispatcher(testScheduler) + Job()).launch(start = UNDISPATCHED) { block() }
