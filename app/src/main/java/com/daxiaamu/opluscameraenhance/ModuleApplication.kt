package com.daxiaamu.opluscameraenhance
import android.app.Application
import androidx.compose.runtime.*
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
class ModuleApplication:Application(),XposedServiceHelper.OnServiceListener {
 companion object {
  private var app:ModuleApplication?=null
  fun syncSettings():Boolean = runCatching {
   val context=app ?: return false
   val local=context.getSharedPreferences(SettingsProvider.PREFS,MODE_PRIVATE)
   val remote=service?.getRemotePreferences("features") ?: return false
   remote.edit().putBoolean("enabled",local.getBoolean("enabled",true))
    .putLong("revision",local.getLong("revision",0)).commit()
  }.getOrDefault(false)
  val targets=listOf("com.oplus.camera","com.heytap.mydevices")
  var service by mutableStateOf<XposedService?>(null)
   private set
  var scopeState by mutableIntStateOf(0)
   private set
  private var requested=false
  fun synchronizeScope(retry:Boolean=false) {
   val current=service ?: return
   runCatching {
    if(current.scope.containsAll(targets)) { scopeState=2; return }
    if(requested && !retry) return
    requested=true; scopeState=1
    current.requestScope(targets,object:XposedService.OnScopeEventListener {
     override fun onScopeRequestApproved(approved:List<String>) { scopeState=if(current.scope.containsAll(targets)) 2 else 3 }
     override fun onScopeRequestFailed(message:String) { scopeState=3 }
    })
   }.onFailure { scopeState=3 }
  }
 }
 override fun onCreate() { super.onCreate(); app=this; XposedServiceHelper.registerListener(this) }
 override fun onServiceBind(boundService:XposedService) { service=boundService; syncSettings(); synchronizeScope() }
 override fun onServiceDied(deadService:XposedService) { if(service===deadService) service=null; requested=false; scopeState=0 }
}
