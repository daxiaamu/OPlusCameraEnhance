package com.daxiaamu.opluscameraenhance

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.view.*
import android.util.AttributeSet
import dalvik.system.PathClassLoader

/** Loads only the installed, system-owned Settings package. Never copies OEM code/resources. */
internal class ColorOsRuntime(activity: Activity) {
 private val positions=java.util.WeakHashMap<View,Int>()
 companion object {
  private var cachedPath=""
  private var cachedLoader:ClassLoader?=null
  @Synchronized private fun loaderFor(paths:String):ClassLoader {
   if(cachedPath!=paths || cachedLoader==null) { cachedPath=paths; cachedLoader=PathClassLoader(paths,ClassLoader.getSystemClassLoader()) }
   return requireNotNull(cachedLoader)
  }
 }
 private val info=activity.packageManager.getApplicationInfo("com.android.settings",0).also {
  check(it.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0)
 }
 private val paths=(listOf(info.sourceDir)+info.splitSourceDirs.orEmpty()).joinToString(java.io.File.pathSeparator)
 private val loader=loaderFor(paths)
 private val foreign=activity.createPackageContext("com.android.settings",0).createConfigurationContext(activity.resources.configuration)
 val context:Context=object:ContextThemeWrapper(activity,0) {
  override fun getResources():Resources=foreign.resources
  override fun getAssets()=foreign.assets
  override fun getClassLoader()=loader
  private val ownTheme by lazy { resources.newTheme().apply { applyStyle(info.theme.takeIf { it!=0 } ?: id("style","Theme.COUI"),true) } }
  override fun getTheme()=ownTheme
  override fun setTheme(resid:Int) { ownTheme.applyStyle(resid,true) }
  override fun getPackageName()=foreign.packageName
  private val inflater by lazy {
   LayoutInflater.from(activity).cloneInContext(this).apply {
    factory2=object:LayoutInflater.Factory2 {
     override fun onCreateView(parent:View?,name:String,c:Context,attrs:AttributeSet):View?=onCreateView(name,c,attrs)
     override fun onCreateView(name:String,c:Context,attrs:AttributeSet):View? {
      if(!name.contains('.')) return null
      return Class.forName(name,true,loader).getConstructor(Context::class.java,AttributeSet::class.java).newInstance(c,attrs) as View
     }
    }
   }
  }
  override fun getSystemService(name:String):Any?=if(name==Context.LAYOUT_INFLATER_SERVICE) inflater else super.getSystemService(name)
 }
 fun id(type:String,name:String):Int=foreign.resources.getIdentifier(name,type,"com.android.settings").also { check(it!=0) { "Missing ColorOS resource: $type/$name" } }
 fun widget(name:String):View=Class.forName(name,true,loader).getConstructor(Context::class.java).newInstance(context) as View
 fun layout(name:String,parent:ViewGroup?=null):View=LayoutInflater.from(context).inflate(id("layout",name),parent,false)
 private var themeOverlayName:String?=null
 private fun applyThemeOverlay() {
  val cls=Class.forName("com.coui.appcompat.theme.COUIThemeOverlay",true,loader)
  val instance=cls.getMethod("getInstance").invoke(null)
  cls.getMethod("applyThemeOverlays",Context::class.java).invoke(instance,context)
  val overlay=cls.getMethod("getThemeOverlay",Int::class.javaPrimitiveType).invoke(instance,id("id","coui_global_theme")) as Int
  themeOverlayName=if(overlay!=0) context.resources.getResourceEntryName(overlay) else null
 }
 init {
  applyThemeOverlay()
  android.util.Log.i("ColorOsRuntime","Settings UI source="+info.sourceDir)
 }

 fun category(title:CharSequence):View=layout("coui_preference_category_layout").apply {
  ((this as ViewGroup).getChildAt(0) as android.widget.TextView).apply { text=title; isAccessibilityHeading=true }
 }
 fun preference(title:CharSequence, summary:CharSequence?=null, widget:View?=null):View {
  val row=layout("coui_preference")
  row.findViewById<android.widget.TextView>(android.R.id.title).apply { text=title; visibility=if(title.isEmpty()) View.GONE else View.VISIBLE }
  row.findViewById<android.widget.TextView>(android.R.id.summary).apply { text=summary; visibility=if(summary.isNullOrEmpty()) View.GONE else View.VISIBLE }
  row.findViewById<ViewGroup>(android.R.id.widget_frame).apply { visibility=if(widget==null) View.GONE else View.VISIBLE; if(widget!=null) addView(widget) }
  return row
 }
 fun dialog(title:CharSequence,content:View,positive:CharSequence,negative:CharSequence?,neutral:CharSequence?):OfficialDialog {
  val cls=Class.forName("com.coui.appcompat.dialog.COUIAlertDialogBuilder",true,loader)
  val builder=cls.getConstructor(Context::class.java).newInstance(context)
  cls.getMethod("setTitle",CharSequence::class.java).invoke(builder,title)
  cls.getMethod("setView",View::class.java).invoke(builder,content)
  val listener=android.content.DialogInterface.OnClickListener::class.java
  cls.getMethod("setPositiveButton",CharSequence::class.java,listener).invoke(builder,positive,null)
  if(negative!=null) cls.getMethod("setNegativeButton",CharSequence::class.java,listener).invoke(builder,negative,null)
  if(neutral!=null) cls.getMethod("setNeutralButton",CharSequence::class.java,listener).invoke(builder,neutral,null)
  return OfficialDialog(cls.getMethod("create").invoke(builder) as android.app.Dialog)
 }
 class OfficialDialog(private val delegate:android.app.Dialog) {
  fun show()=delegate.show()
  fun dismiss()=delegate.dismiss()
  fun getButton(which:Int)=delegate.javaClass.getMethod("getButton",Int::class.javaPrimitiveType).invoke(delegate,which) as? android.widget.Button
  fun setCancelable(value:Boolean)=delegate.setCancelable(value)
  fun setCanceledOnTouchOutside(value:Boolean)=delegate.setCanceledOnTouchOutside(value)
  fun setOnCancelListener(listener:android.content.DialogInterface.OnCancelListener)=delegate.setOnCancelListener(listener)
 }

 fun group(rows:List<View>) {
  val visible=rows.filter { it.visibility!=View.GONE }
  visible.forEachIndexed { index,row ->
   val position=when { visible.size==1->4; index==0->1; index==visible.lastIndex->3; else->2 }
   if(positions[row]!=position) {
    row.javaClass.getMethod("setPositionInGroup",Int::class.javaPrimitiveType).invoke(row,position)
    positions[row]=position
    divider(row,index<visible.lastIndex)
   }
  }
 }
 fun spacing():View=View(context).apply {
  layoutParams=android.widget.LinearLayout.LayoutParams(-1,foreign.resources.getDimensionPixelSize(id("dimen","common_category_top_padding")))
 }

 fun updateTextColors():android.content.res.ColorStateList {
  val pkg="com.heytap.speechassist"
  val component=android.content.ComponentName(pkg,"com.heytap.speechassist.home.settings.ui.SettingsActivity")
  val activityInfo=context.packageManager.getActivityInfo(component,0)
  check(activityInfo.applicationInfo.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0)
  val source=context.createPackageContext(pkg,0).createConfigurationContext(context.resources.configuration)
  val theme=ContextThemeWrapper(source,activityInfo.themeResource)
  themeOverlayName?.let { name ->
   val overlay=source.resources.getIdentifier(name,"style",pkg)
   check(overlay!=0) { "Missing matching official theme overlay: $name" }
   theme.theme.applyStyle(overlay,true)
  }
  val resource=source.resources.getIdentifier("selector_nx_summary_color","color",pkg)
  check(resource!=0) { "Missing official update text colors" }
  return source.resources.getColorStateList(resource,theme.theme)
 }

 private val dividers=java.util.WeakHashMap<View,android.graphics.drawable.Drawable>()
 private fun divider(row:View,show:Boolean) {
  var drawable=dividers[row]
  if(drawable==null && show) {
   drawable=requireNotNull(context.getDrawable(id("drawable","coui_divider_horizontal_without_padding"))).mutate()
   dividers[row]=drawable
   val line=drawable
   val inset=context.resources.getDimensionPixelSize(id("dimen","coui_preference_divider_default_horizontal_padding"))
   fun resize() {
    val height=maxOf(1,line.intrinsicHeight)
    line.setBounds(inset,maxOf(0,row.height-height),maxOf(inset,row.width-inset),row.height)
   }
   row.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> resize() }
   resize()
  }
  drawable?.let { if(show) row.overlay.add(it) else row.overlay.remove(it) }
 }
}
