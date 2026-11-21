package com.juul.kable

import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Connects this socket, and calls [close] when the connect fails or is cancelled, so a failed or cancelled open never
 * leaks the socket. [BluetoothSocket.close] aborts an in-progress connect, which is a blocking call that a cancelled
 * coroutine cannot interrupt. The connect runs in a child, so a cancel reaches [close] while the connect still blocks.
 */
internal suspend fun BluetoothSocket.connectOrClose(close: BluetoothSocket.() -> Unit) {
    try {
        coroutineScope {
            val connecting = async(Dispatchers.IO) { connect() }
            try {
                connecting.await()
            } catch (e: CancellationException) {
                close()
                throw e
            }
        }
    } catch (e: IOException) {
        close()
        throw e.toL2CapException()
    }
}
