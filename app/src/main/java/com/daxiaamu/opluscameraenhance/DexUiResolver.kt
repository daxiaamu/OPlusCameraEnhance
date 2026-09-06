package com.daxiaamu.opluscameraenhance

import android.content.Context
import android.view.View
import android.view.ViewGroup


import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.security.MessageDigest

/** Semantic DEX lookup; no camera class, obfuscated method, field or version mappings. */
object DexUiResolver {
 private const val SCHEMA = "flash-ui-v2"
 private data class Mapping(val expand: Method, val collapse: Method, val siblings: Method,
   val icon: Method, val spacing: Method, val list: Field)

 @JvmStatic fun install(context: Context, loader: ClassLoader, enabled: Boolean, revision: Long, afterHook: (Method, (Any) -> Unit) -> (() -> Unit)) {
  try {
   val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
   val paths = listOf(context.applicationInfo.sourceDir) + context.applicationInfo.splitSourceDirs.orEmpty()
   val identity = SCHEMA + android.os.Build.FINGERPRINT + pkg.longVersionCode + pkg.lastUpdateTime +
     paths.joinToString { val f = File(it); "$it:${f.length()}:${f.lastModified()}" }
   val key = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
   val cache = File(context.codeCacheDir, "opluscameraenhance-dexui.cache")
   var mapping: Mapping? = null
   runCatching {
    val lines = cache.readLines()
    if (lines.size == 7 && lines[0] == key) {
     mapping = decode(lines.drop(1), loader)
     android.util.Log.i("OPlusCameraEnhance", "DexKit mapping cache hit")
    }
   }
   if (mapping == null) {
    System.loadLibrary("dexkit")
    DexKitBridge.create(loader, true).use { bridge ->
     fun find(text: String, params: List<String>, owner: String? = null): MethodData {
      val found = bridge.findMethod { matcher { usingStrings(text); returnType = "void" } }
       .filter { it.paramTypeNames == params && !Modifier.isStatic(it.modifiers) &&
         (owner == null || it.className == owner) }
      require(found.size == 1) { "Non-unique DEX anchor: $text (${found.size})" }
      return found.single()
     }
     val expand = find("expandAnim, debug, mbUseSharedIcon:", emptyList())
     // Newer stock code may already implement the external branch. Never double-patch it.
     if ("pref_app_outflash_key" in expand.usingStrings) { Receipt.send(context,revision,"compatible"); return }
     require("pref_camera_flashmode_key" in expand.usingStrings && "pref_app_outflash_key" !in expand.usingStrings) {
      "Stock animation changed or already handles external flash; UI repair skipped"
     }
     val collapse = find("collapseAnimFromCurrentPosition, fromCurrentPosition:", listOf("boolean", "boolean"), expand.className)
     val spacing = find("playCheckListBarTransition, expanded:", listOf("boolean", "boolean"), expand.className)
     val shared = expand.invokes.filter { candidate ->
      candidate.className == expand.className && candidate.paramTypeNames == listOf("boolean") &&
       candidate.returnTypeName == "void" && collapse.invokes.any { it.descriptor == candidate.descriptor }
     }.distinctBy { it.descriptor }
     val siblings = shared.filter { method -> method.invokes.any {
      it.name == "setClickable" && it.paramTypeNames == listOf("boolean")
     } }.single()
     val icon = shared.filter { method -> method.invokes.any {
      it.className == "android.animation.ObjectAnimator" && it.name == "ofFloat"
     } && method.invokes.any { it.className == "android.animation.AnimatorSet" && it.name == "start" } }.single()
     val list = spacing.usingFields.map { it.field }.distinctBy { it.descriptor }.filter { field ->
      field.declaredClassName == expand.className && runCatching {
       View::class.java.isAssignableFrom(field.getFieldInstance(loader).type)
      }.getOrDefault(false)
     }.single()
     val descriptors = listOf(expand.descriptor, collapse.descriptor, siblings.descriptor,
       icon.descriptor, spacing.descriptor, list.descriptor)
     mapping = decode(descriptors, loader)
     runCatching { cache.writeText((listOf(key) + descriptors).joinToString("\n")) }
     android.util.Log.i("OPlusCameraEnhance", "DexKit resolved UI anchors: ${expand.descriptor}; ${collapse.descriptor}")
    }
   }
   val resolved = requireNotNull(mapping)
   if (!enabled) { Receipt.send(context,revision,"compatible"); return }
   val installed = mutableListOf<() -> Unit>()
   try {
    installed += afterHook(resolved.expand, callback(resolved, true))
    installed += afterHook(resolved.collapse, callback(resolved, false))
   } catch (error: Throwable) { installed.forEach { it() }; throw error }
   Receipt.send(context,revision,"compatible")
   android.util.Log.i("OPlusCameraEnhance", "DexKit external flash UI repair installed")
  } catch (error: Throwable) {
   Receipt.send(context,revision,"unsupported")
   android.util.Log.i("OPlusCameraEnhance", "DexKit UI repair safely skipped: ${error.message}")
  }
 }

 private fun decode(values: List<String>, loader: ClassLoader): Mapping {
  require(values.size == 6)
  val methods = values.take(5).map { DexMethod(it).getMethodInstance(loader).apply { isAccessible = true } }
  val field = DexField(values[5]).getFieldInstance(loader).apply { isAccessible = true }
  val owner = methods[0].declaringClass
  require(ViewGroup::class.java.isAssignableFrom(owner))
  require(methods.all { it.declaringClass == owner && it.returnType == Void.TYPE && !Modifier.isStatic(it.modifiers) })
  require(methods[0].parameterCount == 0)
  require(methods[1].parameterTypes.toList() == listOf(Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType))
  require(methods[2].parameterTypes.toList() == listOf(Boolean::class.javaPrimitiveType))
  require(methods[3].parameterTypes.toList() == listOf(Boolean::class.javaPrimitiveType))
  require(methods[4].parameterTypes.toList() == methods[1].parameterTypes.toList())
  require(field.declaringClass == owner && View::class.java.isAssignableFrom(field.type) && !Modifier.isStatic(field.modifiers))
  return Mapping(methods[0], methods[1], methods[2], methods[3], methods[4], field)
 }

 private fun callback(mapping: Mapping, opening: Boolean): (Any) -> Unit = callback@{ instance ->


   try {
    val root = instance as ViewGroup
    fun id(name: String) = root.resources.getIdentifier(name, "id", "com.oplus.camera")
    val iconId = id("out_flash_mode")
    val contentId = id("out_flash_view_id")
    if (iconId == 0 || contentId == 0 || root.findViewById<View>(iconId) == null) return@callback
    val content = root.findViewById<ViewGroup>(contentId) ?: return@callback
    // A stock hidden/off panel is left alone. All controls retain original listeners and values.
    if (content.visibility != View.VISIBLE) return@callback
    val list = mapping.list.get(root) as? View ?: return@callback
    if (list.parent == null) return@callback
    mapping.siblings.invoke(root, opening)
    mapping.icon.invoke(root, opening)
    mapping.spacing.invoke(root, opening, false)
    for (view in listOf(list, content)) {
     view.animate().cancel()
     view.animate().alpha(if (opening) 1f else 0f).setStartDelay(if (opening) 200 else 0)
      .setDuration(if (opening) 300 else 200).start()
    }
    if (opening) android.util.Log.i("OPlusCameraEnhance", "DexKit restored external flash panel")
   } catch (error: Throwable) { android.util.Log.i("OPlusCameraEnhance", "UI repair skipped for this view: ${error.message}") }

 }
}