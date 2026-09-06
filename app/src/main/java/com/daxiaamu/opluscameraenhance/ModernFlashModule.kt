package com.daxiaamu.opluscameraenhance
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

class ModernFlashModule : XposedModule() {
 override fun onPackageLoaded(param: PackageLoadedParam) {
  if (!param.isFirstPackage || param.packageName !in setOf("com.oplus.camera","com.heytap.mydevices")) return
  val loader=param.defaultClassLoader
  runCatching {
   val properties=Class.forName("android.os.SystemProperties")
   val original=properties.getMethod("get",String::class.java,String::class.java).invoke(null,NativeSupportPolicy.PROPERTY,"") as String
   val decision=NativeSupportPolicy.decide(original,true)
   reportLog("Guard: "+decision+" / "+param.packageName)
   if(decision!=NativeSupportPolicy.Decision.ELIGIBLE) return
    val config=runCatching { getRemotePreferences("features") }.getOrNull()
    val enabled=config?.getBoolean("enabled",false)==true
    val revision=config?.getLong("revision",-1) ?: -1L
    val observed=java.util.concurrent.atomic.AtomicBoolean()
    if(enabled) {
     for(name in listOf("android.os.SystemProperties","com.oplus.wrapper.os.SystemProperties")) {
      val owner=runCatching { Class.forName(name,false,loader) }.getOrNull() ?: continue
      owner.declaredMethods.filter { it.name in setOf("get","getInt","getLong","getBoolean") &&
        it.parameterTypes.firstOrNull()==String::class.java }.forEach { method ->
       hook(method).intercept { call ->
        if(call.getArg(0)!=NativeSupportPolicy.PROPERTY) call.proceed()
        else {
         if(observed.compareAndSet(false,true)) reportLog("External flash support consumed / "+param.packageName)
         when(method.returnType) {
         Int::class.javaPrimitiveType -> 1
         Long::class.javaPrimitiveType -> 1L
         Boolean::class.javaPrimitiveType -> true
         String::class.java -> "1"
         else -> call.proceed()
         }
        }
       }
      }
     }
    }
   hook(Application::class.java.getDeclaredMethod("attach",Context::class.java)).intercept { chain ->
    val context=chain.getArg(0) as Context
    val result=chain.proceed()
    if(revision>=0) {
     context.registerReceiver(object:android.content.BroadcastReceiver() {
      override fun onReceive(c:Context,intent:android.content.Intent) { Receipt.resend(context) }
     },android.content.IntentFilter("com.daxiaamu.opluscameraenhance.REFRESH"),
      "com.daxiaamu.opluscameraenhance.STATUS",null,Context.RECEIVER_EXPORTED)
     Receipt.send(context,revision,"injected")
     reportLog("Feature setting enabled="+enabled+" revision="+revision+" / "+param.packageName)
     if(param.packageName=="com.oplus.camera") {
      Thread({
       DexUiResolver.install(context,loader,enabled,revision) { method, callback ->
        val handle=hook(method).intercept { call ->
         val value=call.proceed()
         callback(call.thisObject!!)
         value
        }
        val unhook: () -> Unit = { handle.unhook() }
        unhook
       }
             if(enabled && !FlashDiscoveryResolver.install(context,loader) { method, callback ->
        val handle=hook(method).intercept { call ->
         callback(method.parameterTypes.indices.map { call.getArg(it) }) { call.proceed() }
        }
        val unhook: () -> Unit = { handle.unhook() }
        unhook
       }) Receipt.send(context,revision,"unsupported")
      },"FlashDexKit").apply { isDaemon=true; start() }
     }
    }
    result
   }
  }.onFailure { reportLog("Safely skipped: "+it) }
 }
 private fun reportLog(message:String) { log(Log.INFO,"OPlusCameraEnhance",message) }
}
object Receipt {
 @Volatile private var latestRevision=-1L
 @Volatile private var latestStatus=""
 fun resend(context:Context) { if(latestRevision>=0) send(context,latestRevision,latestStatus) }
 fun send(context:Context,revision:Long,status:String) {
  latestRevision=revision; latestStatus=status
  runCatching {
   val pkg=context.packageManager.getPackageInfo(context.packageName,0)
   val data=Bundle().apply {
    putLong("revision",revision); putInt("version",BuildConfig.VERSION_CODE)
    putString("status",status); putLong("targetCode",pkg.longVersionCode)
    putLong("targetUpdate",pkg.lastUpdateTime)
    putInt("boot",android.provider.Settings.Global.getInt(context.contentResolver,"boot_count",-1))
   }
   context.contentResolver.call(SettingsProvider.URI,"applied",null,data)
  }
 }
}