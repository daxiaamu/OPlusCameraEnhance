package com.daxiaamu.opluscameraenhance


import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Restore stock discovery UI for a bonded flash whose ACL connection is absent. */
object FlashDiscoveryResolver {
 private val pendingAddress = ThreadLocal<String>()
 // Runs inside the camera process; its permission is checked below, not the module manifest.
 @android.annotation.SuppressLint("MissingPermission")
 fun install(context: Context, loader: ClassLoader, aroundHook: (Method, (List<Any?>, () -> Any?) -> Any?) -> (() -> Unit)): Boolean {
  val handles = mutableListOf<() -> Unit>()
  return try {
   System.loadLibrary("dexkit")
   DexKitBridge.create(loader, true).use { bridge ->
    val connect = bridge.findMethod { matcher {
     usingStrings("OutFlashServiceListener", "OUT_FLASH", "intent_blue_tooth_mac_address")
     returnType = "void"
    } }.filter { !Modifier.isStatic(it.modifiers) &&
      it.paramTypeNames == listOf("int","android.app.Activity","java.lang.String","boolean") }.single()
    val registry = bridge.findMethod { matcher { usingStrings("key_outflash_connected_device") } }
     .filter { it.name == "<clinit>" }.single().className
    val known = connect.invokes.filter {
     it.className == registry && Modifier.isStatic(it.modifiers) &&
     it.returnTypeName == "boolean" && it.paramTypeNames == listOf("java.lang.String","java.lang.String")
    }.distinctBy { it.descriptor }.single()
    val connectMethod = connect.getMethodInstance(loader)
    val knownMethod = known.getMethodInstance(loader)
    // Fail closed if the platform cannot report an actual ACL connection.
    val isConnected = BluetoothDevice::class.java.getDeclaredMethod("isConnected").apply { isAccessible = true }
    handles += aroundHook(knownMethod) { args, proceed ->
     val pending = pendingAddress.get()
     if(pending != null && args[0] == "OUT_FLASH" && args[1] == pending) false else proceed()
    }
    handles += aroundHook(connectMethod) { args, proceed ->
     val old = pendingAddress.get()
     val address = args[2] as? String
     // Type 2 is ordinary BLE discovery; NFC, ACL events and explicit requests stay stock.
     val disconnected = args[0] == 2 && address != null && runCatching {
      if(context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) return@runCatching false
      val adapter = context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
      val device = adapter?.bondedDevices?.firstOrNull { it.address == address }
      device != null && isConnected.invoke(device) == false
     }.getOrDefault(false)
     try {
      if(disconnected) pendingAddress.set(address) else pendingAddress.remove()
      proceed()
     } finally {
      if(old == null) pendingAddress.remove() else pendingAddress.set(old)
     }
    }
    Log.i("OPlusCameraEnhance", "DexKit flash discovery repair installed")
   }
   true
  } catch(error: Throwable) {
   handles.reversed().forEach { runCatching { it() } }
   Log.i("OPlusCameraEnhance", "Flash discovery repair safely skipped: " + error.message)
   false
  }
 }
}
