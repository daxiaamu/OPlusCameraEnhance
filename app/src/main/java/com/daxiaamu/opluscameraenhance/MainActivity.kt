package com.daxiaamu.opluscameraenhance
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import com.daxiaamu.opluscameraenhance.update.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
class MainActivity:ComponentActivity() {
 private var nativeScreen:NativeScreen?=null
 private val uiPrefs by lazy { getSharedPreferences("appearance",MODE_PRIVATE) }
 private val prefs by lazy { getSharedPreferences(SettingsProvider.PREFS,MODE_PRIVATE) }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState); enableEdgeToEdge(); UpdateManager.initialize(this)
  if(uiPrefs.getBoolean("system_ui",false)) {
   val attempt=runCatching { nativeScreen=NativeScreen(this); nativeScreen!!.show() }
   if(attempt.isSuccess) return
   android.util.Log.e("ColorOsRuntime","Official UI load failed",attempt.exceptionOrNull())
   toast(getString(R.string.coloros_unavailable))
   nativeScreen?.close(); nativeScreen=null; uiPrefs.edit().putBoolean("system_ui",false).apply()
  }
  setContent {
   MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF176B61),background=Color(0xFFF5F6F2),
    surface=Color.White,onSurface=Color(0xFF172B28),secondaryContainer=Color(0xFFE4EEE9))) {
    val decision by produceState<NativeSupportPolicy.Decision?>(null) { value=withContext(Dispatchers.IO) { inspectDevice() } }
    LaunchedEffect(Unit) { delay(2000); UpdateManager.checkAutomatically() }
    LaunchedEffect(UpdateManager.manualMessage) { UpdateManager.manualMessage?.let { toast(it) } }
    Screen(decision)
    UpdateManager.dialogUpdate?.let { UpdateDialog(this,it) }
   }
  }
 }
 override fun onResume() { super.onResume(); if(nativeScreen?.themeChanged()==true) { recreate(); return }; ModuleApplication.syncSettings(); ModuleApplication.synchronizeScope(); UpdateManager.resumeInstallation(this); ModuleApplication.targets.forEach { sendBroadcast(Intent("com.daxiaamu.opluscameraenhance.REFRESH").setPackage(it)) } }
 internal fun inspectDevice():NativeSupportPolicy.Decision {
  var process:Process?=null
  val raw=runCatching {
   val running=ProcessBuilder("/system/bin/getprop",NativeSupportPolicy.PROPERTY).redirectErrorStream(true).start()
   process=running
   check(running.waitFor(2,TimeUnit.SECONDS) && running.exitValue()==0)
   running.inputStream.bufferedReader().use { it.readText().trim() }
  }
  process?.destroy()
  return NativeSupportPolicy.decide(Build.BRAND,Build.MANUFACTURER,raw.getOrNull(),raw.isSuccess)
 }
 internal data class State(val applied:Boolean=false,val camera:Boolean=false,val devices:Boolean=false,val compatible:String="",val version:String="")
 internal fun receiptState():State {
  val running=runCatching { ModuleApplication.service?.runningTargets }.getOrNull().orEmpty()
  val revision=prefs.getLong("revision",0)
  val boot=android.provider.Settings.Global.getInt(contentResolver,"boot_count",-1)
  fun valid(pkg:String):Boolean=runCatching {
   val info=packageManager.getPackageInfo(pkg,0)
   running.any { it.processName==pkg && it.state==io.github.libxposed.service.HookedTarget.State.UP_TO_DATE } &&
   prefs.getInt(pkg+".version",-1)==BuildConfig.VERSION_CODE &&
    prefs.getLong(pkg+".targetCode",-1)==info.longVersionCode &&
    prefs.getLong(pkg+".targetUpdate",-1)==info.lastUpdateTime &&
    boot>=0 && prefs.getInt(pkg+".boot",-2)==boot
  }.getOrDefault(false)
  val camera=valid("com.oplus.camera"); val devices=valid("com.heytap.mydevices")
  val applied=camera && devices && ModuleApplication.targets.all { prefs.getLong(it+".revision",-1)==revision }
  return State(applied,camera,devices,if(camera) prefs.getString("com.oplus.camera.status","") ?: "" else "",
   runCatching { packageManager.getPackageInfo("com.oplus.camera",0).versionName ?: getString(R.string.unknown) }.getOrDefault(getString(R.string.unknown)))
 }
 @Composable private fun Screen(decision:NativeSupportPolicy.Decision?) {
  var enabled by remember { mutableStateOf(prefs.getBoolean("enabled",true)) }
  var state by remember { mutableStateOf(State()) }
  LaunchedEffect(enabled) { while(true) { state=withContext(Dispatchers.IO) { receiptState() }; delay(1000) } }
  val eligible=decision==NativeSupportPolicy.Decision.ELIGIBLE
  val status=when {
   decision==null -> R.string.checking
   decision==NativeSupportPolicy.Decision.NATIVE_SUPPORTED -> R.string.native_supported
   !eligible -> R.string.unavailable
   !state.applied -> if(enabled) R.string.pending_enable else R.string.pending_disable
   enabled -> R.string.enhanced
   else -> R.string.disabled
  }
  Scaffold(containerColor=MaterialTheme.colorScheme.background) { insets ->
   Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
    Text("OPlus Camera\nEnhance",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.SemiBold)
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
     SectionTitle(R.string.section_features)
    Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface) {
     Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
      Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
       Text(stringResource(R.string.feature_title),style=MaterialTheme.typography.titleSmall)
       Text(stringResource(status),color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodySmall)
      }
      val switchName=stringResource(R.string.feature_title)
      Switch(modifier=Modifier.semantics { contentDescription=switchName },checked=eligible && enabled,enabled=eligible,onCheckedChange={ next ->
       if(setFeatureEnabled(next)) { enabled=next; state=state.copy(applied=false) }
      })
     }
    }
    Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface) {
     Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
      Text(stringResource(R.string.explanation),style=MaterialTheme.typography.titleSmall)
      Text(explanationText(decision,state,enabled),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
      if(eligible && !state.applied && ModuleApplication.scopeState==3) TextButton(onClick={ ModuleApplication.synchronizeScope(true) }) { Text(stringResource(R.string.retry)) }
     }
    }
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
     SectionTitle(R.string.section_about)
    Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface) {
     Row(Modifier.fillMaxWidth().padding(start=20.dp,end=8.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
       Text(stringResource(R.string.version),style=MaterialTheme.typography.titleSmall)
       Text(BuildConfig.VERSION_NAME,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if(UpdateManager.hasUpdateBadge) Text("•",color=MaterialTheme.colorScheme.error)
      TextButton(onClick={ UpdateManager.checkManually() },modifier=Modifier.widthIn(min=80.dp).heightIn(min=48.dp)) {
       Box(contentAlignment=Alignment.Center) {
        val checking=UpdateManager.checkState==CheckState.Checking
        Text(stringResource(R.string.check),modifier=Modifier.alpha(if(checking) 0f else 1f),maxLines=1,softWrap=false)
        if(checking) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)
       }
      }
     }
    }
    Surface(onClick={ openSource() },shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface) {
     Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
      Text(stringResource(R.string.source),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall); Text("›")
     }
    }
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
     SectionTitle(R.string.section_appearance)
    Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface) {
     Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
      val label=stringResource(R.string.system_ui)
      Text(label,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
      Switch(checked=false,onCheckedChange={ setSystemUi(it) },modifier=Modifier.semantics { contentDescription=label })
     }
    }
    }
   }
  }
 }
 @Composable private fun SectionTitle(id:Int) {
  Text(stringResource(id),Modifier.semantics { heading() },style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
 }
 override fun onDestroy() { nativeScreen?.close(); super.onDestroy() }
 internal fun explanationText(decision:NativeSupportPolicy.Decision?,state:State,enabled:Boolean):String {
  val eligible=decision==NativeSupportPolicy.Decision.ELIGIBLE
  val devicesVersion=runCatching { packageManager.getPackageInfo("com.heytap.mydevices",0).versionName }.getOrNull() ?: getString(R.string.unknown)
  fun target(name:Int,injected:Boolean,version:String):String {
   val status=if(eligible) getString(if(injected) R.string.injected else R.string.not_injected) else getString(statusText(decision,state,enabled))
   return getString(name)+": "+status+" ("+getString(R.string.version)+" "+version+")"
  }
  return buildList {
   add(target(R.string.camera_name,state.camera,state.version))
   if(eligible) add(compatibilityText(state))
   add(target(R.string.devices_name,state.devices,devicesVersion))
   if(eligible && !state.applied) {
    add(getString(R.string.restart_needed))
    if(ModuleApplication.scopeState!=2) add(getString(when(ModuleApplication.scopeState) { 1->R.string.scope_sync; 3->R.string.scope_failed; else->R.string.scope_unavailable }))
   }
  }.joinToString("\n")
 }
 internal fun featureEnabled() = prefs.getBoolean("enabled",true)
 internal fun setFeatureEnabled(next:Boolean):Boolean {
  if(!prefs.edit().putBoolean("enabled",next).putLong("revision",prefs.getLong("revision",0)+1).commit()) { toast(getString(R.string.save_failed)); return false }
  ModuleApplication.syncSettings(); toast(getString(R.string.restart_needed)); return true
 }
 internal fun setSystemUi(value:Boolean) {
  if(uiPrefs.edit().putBoolean("system_ui",value).commit()) recreate() else toast(getString(R.string.save_failed))
 }
 internal fun openSource() {
  runCatching { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/daxiaamu/OPlusCameraEnhance"))) }.onFailure { toast(getString(R.string.browser_failed)) }
 }
 internal fun statusText(decision:NativeSupportPolicy.Decision?,state:State,enabled:Boolean):Int=when {
  decision==null -> R.string.checking
  decision==NativeSupportPolicy.Decision.NATIVE_SUPPORTED -> R.string.native_supported
  decision!=NativeSupportPolicy.Decision.ELIGIBLE -> R.string.unavailable
  !state.applied -> if(enabled) R.string.pending_enable else R.string.pending_disable
  enabled -> R.string.enhanced
  else -> R.string.disabled
 }
 internal fun compatibilityText(state:State):String=when(state.compatible) {
  "compatible"->getString(R.string.compat_supported)
  "injected"->getString(R.string.checking_compat)
  "unsupported"->getString(R.string.compat_unsupported,state.version)
  else->getString(R.string.compat_unknown_camera,state.version)
 }
 internal fun toast(text:String) { Toast.makeText(this,text,Toast.LENGTH_LONG).show() }
}
