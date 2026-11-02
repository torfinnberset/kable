package com.juul.kable

import com.juul.kable.CentralManagerDelegate.ConnectionEvent.DidConnect
import com.juul.kable.CentralManagerDelegate.ConnectionEvent.DidDisconnect
import com.juul.kable.CentralManagerDelegate.ConnectionEvent.DidFailToConnect
import com.juul.kable.CentralManagerDelegate.Response.DidDiscoverPeripheral
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerRestoredStatePeripheralsKey
import platform.CoreBluetooth.CBManagerState
import platform.CoreBluetooth.CBManagerStateUnknown
import platform.CoreBluetooth.CBPeripheral
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.NSUUID
import platform.darwin.NSObject

// https://developer.apple.com/documentation/corebluetooth/cbcentralmanagerdelegate
internal class CentralManagerDelegate : NSObject(), CBCentralManagerDelegateProtocol {

    private val _state = MutableStateFlow(CBManagerStateUnknown)
    val state: StateFlow<CBManagerState> = _state.asStateFlow()

    sealed class Response {

        data class DidDiscoverPeripheral(
            val cbPeripheral: CBPeripheral,
            val rssi: NSNumber,
            val advertisementData: Map<String, Any>,
        ) : Response()
    }

    private val _response = MutableSharedFlow<Response>(extraBufferCapacity = 64)
    val response: Flow<Response> = _response.asSharedFlow()

    sealed class ConnectionEvent {

        abstract val identifier: NSUUID

        data class DidConnect(
            override val identifier: NSUUID,
        ) : ConnectionEvent()

        data class DidFailToConnect(
            override val identifier: NSUUID,
            val error: NSError?,
        ) : ConnectionEvent()

        data class DidDisconnect(
            override val identifier: NSUUID,
            val error: NSError?,
        ) : ConnectionEvent()
    }

    // `SharedFlow` (instead of `StateFlow`) for non-conflated behavior, as this flow feeds
    // individual downstream `Peripheral`s.
    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>()
    val connectionEvents: Flow<ConnectionEvent> = _connectionEvents.asSharedFlow()

    /* Monitoring Connections with Peripherals */

    override fun centralManager(
        central: CBCentralManager,
        didConnectPeripheral: CBPeripheral,
    ) {
        _connectionEvents.emitBlocking(DidConnect(didConnectPeripheral.identifier))
    }

    @ObjCSignatureOverride
    override fun centralManager(
        central: CBCentralManager,
        didDisconnectPeripheral: CBPeripheral,
        error: NSError?,
    ) {
        _connectionEvents.emitBlocking(DidDisconnect(didDisconnectPeripheral.identifier, error))
    }

    @ObjCSignatureOverride
    override fun centralManager(
        central: CBCentralManager,
        didFailToConnectPeripheral: CBPeripheral,
        error: NSError?,
    ) {
        _connectionEvents.emitBlocking(DidFailToConnect(didFailToConnectPeripheral.identifier, error))
    }

    // todo: func centralManager(CBCentralManager, connectionEventDidOccur: CBConnectionEvent, for: CBPeripheral)

    /* Discovering and Retrieving Peripherals */

    override fun centralManager(
        central: CBCentralManager,
        didDiscoverPeripheral: CBPeripheral,
        advertisementData: Map<Any?, *>,
        RSSI: NSNumber,
    ) {
        // Per Apple documentation, `advertisementData` is defined as dictionary of `[String : Any]`.
        // https://developer.apple.com/documentation/corebluetooth/cbcentralmanagerdelegate/1518937-centralmanager
        val data = advertisementData as Map<String, Any>

        _response.emitBlocking(DidDiscoverPeripheral(didDiscoverPeripheral, RSSI, data))
    }

    /* Monitoring the Central Manager’s State */

    override fun centralManagerDidUpdateState(
        central: CBCentralManager,
    ) {
        _state.value = central.state
    }

    private val _restoredPeripherals = MutableStateFlow<List<CBPeripheral>>(emptyList())

    /** The peripherals of the last state restoration. CentralManager.connectedPeripherals gives them. */
    val restoredPeripherals: StateFlow<List<CBPeripheral>> = _restoredPeripherals.asStateFlow()

    override fun centralManager(central: CBCentralManager, willRestoreState: Map<Any?, *>) {
        // CoreBluetooth cancels the connection of a restored peripheral that the app does not keep.
        _restoredPeripherals.value = (willRestoreState[CBCentralManagerRestoredStatePeripheralsKey] as? List<*>)
            .orEmpty()
            .filterIsInstance<CBPeripheral>()
    }

    /* Monitoring the Central Manager’s Authorization */

    // todo: func centralManager(CBCentralManager, didUpdateANCSAuthorizationFor: CBPeripheral)
}
