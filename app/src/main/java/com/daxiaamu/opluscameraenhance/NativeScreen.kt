package com.daxiaamu.opluscameraenhance

import android.app.AlertDialog
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import com.daxiaamu.opluscameraenhance.update.*
import kotlinx.coroutines.*

/** Framework widgets only; feature and update state remain owned by the shared controllers. */
internal class NativeScreen(private val activity: MainActivity) {
 private val initialConfiguration=activity.resources.configuration.toString()
 fun themeChanged()=initialConfiguration!=activity.resources.configuration.toString()
 private val oem=ColorOsRuntime(activity)
 private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
 private val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
 private val feature = oem.widget("com.coui.appcompat.couiswitch.COUISwitch") as CompoundButton
 private lateinit var status:TextView
 private lateinit var details:TextView
 private lateinit var check:TextView
 private val progress = ProgressBar(activity)
 private var dialog: ColorOsRuntime.OfficialDialog? = null
 private var dialogKey: String? = null
 private var errorLabel: TextView? = null
 private var downloadProgress: ProgressBar? = null
 private var message: String? = null
 private var refreshing = false
 private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
 private fun label() = TextView(activity).apply { setTextAppearance(android.R.style.TextAppearance_Material_Body1); setPadding(0,dp(6),0,dp(6)) }
 private fun text(id: Int) = activity.getString(id)
 private fun add(view: View) { column.addView(view,LinearLayout.LayoutParams(-1,-2)) }
 private fun divider() {
  val value=android.util.TypedValue()
  activity.theme.resolveAttribute(android.R.attr.listDivider,value,true)
  add(ImageView(activity).apply { setImageResource(value.resourceId) })
 }
 private fun button(id: Int, action: () -> Unit) = Button(activity).apply { setText(id); setOnClickListener { action() }; minHeight=dp(48) }

 fun show() {
  val scroll = ScrollView(activity).apply { isFillViewport=true; addView(column) }
  val background=android.util.TypedValue()
  oem.context.theme.resolveAttribute(android.R.attr.windowBackground,background,true)
  if(background.resourceId!=0) scroll.background=oem.context.getDrawable(background.resourceId) else scroll.setBackgroundColor(background.data)
  scroll.setOnApplyWindowInsetsListener { view, insets ->
   val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
   view.setPadding(bars.left,bars.top,bars.right,bars.bottom); insets
  }
  activity.setContentView(scroll)
  val toolbar=oem.widget("com.coui.appcompat.toolbar.COUIToolbar")
  toolbar.javaClass.getMethod("setTitle",CharSequence::class.java).invoke(toolbar,"OPlus Camera Enhance")
  add(toolbar)
  add(oem.category(text(R.string.section_features)))
  feature.isClickable=false; feature.isFocusable=false
  feature.setOnCheckedChangeListener { _, value -> if(!refreshing) activity.setFeatureEnabled(value) }
  val featureRow=oem.preference(text(R.string.feature_title),text(R.string.checking),feature).also { status=it.findViewById(android.R.id.summary); it.setOnClickListener { if(feature.isEnabled) feature.toggle() } }; add(featureRow)
  column.addView(oem.spacing())
  val detailsRow=oem.preference(text(R.string.explanation)," ").also {
   details=it.findViewById<TextView>(android.R.id.summary).apply { setSingleLine(false); maxLines=Int.MAX_VALUE; ellipsize=null }
   it.setOnClickListener { if(ModuleApplication.scopeState==3) ModuleApplication.synchronizeScope(true) }
  }; add(detailsRow)
  column.addView(oem.spacing())
  add(oem.category(text(R.string.section_about)))
  val versionRow=oem.layout("coui_preference_assignment_in_right")
  versionRow.findViewById<TextView>(android.R.id.title).text=text(R.string.version)
  versionRow.findViewById<TextView>(android.R.id.summary).apply { text=BuildConfig.VERSION_NAME; visibility=View.VISIBLE }
  check=versionRow.findViewById(oem.id("id","assignment"))
  check.apply { setTextColor(oem.updateTextColors()); text=activity.getString(R.string.check); visibility=View.VISIBLE; setOnClickListener { UpdateManager.checkManually() } }
  val assignmentParent=check.parent as android.view.ViewGroup
  val position=assignmentParent.indexOfChild(check)
  val params=check.layoutParams
  assignmentParent.removeView(check)
  val slot=FrameLayout(oem.context).apply { minimumWidth=maxOf(dp(64),check.paint.measureText(text(R.string.check)).toInt()+dp(8)); minimumHeight=dp(48) }
  slot.addView(check,FrameLayout.LayoutParams(-2,-2,Gravity.CENTER_VERTICAL or Gravity.END))
  slot.addView(progress,FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER))
  assignmentParent.addView(slot,position,params)
  versionRow.setOnClickListener { UpdateManager.checkManually() }
  add(versionRow)
  val sourceRow=oem.preference(text(R.string.source), widget=oem.layout("coui_preference_widget_jump")).apply { setOnClickListener { activity.openSource() } }; add(sourceRow)
  oem.group(listOf(versionRow,sourceRow))
  column.addView(oem.spacing())
  add(oem.category(text(R.string.section_appearance)))
  val uiSwitch=(oem.widget("com.coui.appcompat.couiswitch.COUISwitch") as CompoundButton).apply {
   isChecked=true; isClickable=false; isFocusable=false
   setOnCheckedChangeListener { _, value -> activity.setSystemUi(value) }
  }
  add(oem.preference(text(R.string.system_ui),widget=uiSwitch).apply { setOnClickListener { uiSwitch.toggle() } })

  scope.launch {
   val decision=withContext(Dispatchers.IO) { activity.inspectDevice() }
   while(isActive) {
    if(!activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) { delay(250); continue }
    val state=withContext(Dispatchers.IO) { activity.receiptState() }
    val enabled=activity.featureEnabled()
    val eligible=decision==NativeSupportPolicy.Decision.ELIGIBLE
    refreshing=true; feature.isEnabled=eligible; if(feature.isChecked!=(eligible && enabled)) feature.isChecked=eligible && enabled; refreshing=false
    status.text=text(activity.statusText(decision,state,enabled))
    details.text=activity.explanationText(decision,state,enabled)
    val checking=UpdateManager.checkState==CheckState.Checking
    check.visibility=if(checking) View.INVISIBLE else View.VISIBLE
    check.text=(if(UpdateManager.hasUpdateBadge) "• " else "")+text(R.string.check)
    progress.visibility=if(checking) View.VISIBLE else View.GONE
    val next=UpdateManager.manualMessage
    if(next!=null && next!=message) activity.toast(next)
    message=next
    updateDialog()
    delay(250)
   }
  }
  scope.launch { delay(2000); UpdateManager.checkAutomatically() }
 }
 fun close() { scope.cancel(); dialog?.dismiss(); dialog=null }
 private fun updateDialog() {
  val update=UpdateManager.dialogUpdate
  if(update==null) { dialog?.dismiss(); dialog=null; dialogKey=null; return }
  val key=update.digest
  if(dialog==null || dialogKey!=key) {
   dialog?.dismiss()
   dialogKey=key
   val content=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(24),dp(8),dp(24),dp(8)) }
   val info=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
   info.addView(label().apply { text=text(R.string.version)+" "+update.versionName })
   update.publishedAt?.let(::formatPublishedAt)?.let { date -> info.addView(label().apply { text=activity.getString(R.string.published,date) }) }
   info.addView(label().apply { text=NativeMarkdown.render(update.changelog); setTextIsSelectable(true); movementMethod=android.text.method.LinkMovementMethod.getInstance() })
   errorLabel=label().also { info.addView(it) }
   content.addView(ScrollView(activity).apply { addView(info) },LinearLayout.LayoutParams(-1,(activity.resources.displayMetrics.heightPixels*.4f).toInt()))
   downloadProgress=ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal).also { content.addView(it,LinearLayout.LayoutParams(-1,dp(8))) }
   val required=update.isRequired(BuildConfig.VERSION_CODE.toLong())
   dialog=oem.dialog(text(if(required) R.string.required_update else R.string.new_update),content,text(R.string.download_install),if(required) null else text(R.string.ignore),if(required) null else text(R.string.skip)).also { current ->
    current.setOnCancelListener { UpdateManager.ignore(update) }
    current.show()
    current.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
     when(val state=UpdateManager.downloadState) {
      is DownloadState.ReadyToInstall -> if(state.versionCode==update.versionCode) UpdateManager.install(activity,update)
      is DownloadState.NeedsAuthorization -> if(state.versionCode==update.versionCode) UpdateManager.install(activity,update)
      else -> UpdateManager.download(update)
     }
    }
    current.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener { UpdateManager.ignore(update) }
    current.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { UpdateManager.skip(update) }
   }
  }
  val state=UpdateManager.downloadState
  val busy=state is DownloadState.Downloading || state==DownloadState.Verifying
  val required=update.isRequired(BuildConfig.VERSION_CODE.toLong())
  dialog?.setCancelable(!required && !busy); dialog?.setCanceledOnTouchOutside(!required && !busy)
  dialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled=!busy
  dialog?.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled=!busy
  dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
   isEnabled=!busy && state !is DownloadState.LaunchingInstaller
   text=when(state) {
    is DownloadState.Downloading -> state.percent?.let { "$it%" } ?: text(R.string.downloading)
    DownloadState.Verifying -> text(R.string.verifying)
    is DownloadState.ReadyToInstall -> text(R.string.install)
    is DownloadState.NeedsAuthorization -> text(R.string.install_continue)
    is DownloadState.LaunchingInstaller -> text(R.string.opening)
    is DownloadState.Failed -> text(R.string.retry)
    else -> text(R.string.download_install)
   }
  }
  errorLabel?.apply { setText(text(R.string.download_failed)); visibility=if(state is DownloadState.Failed) View.VISIBLE else View.GONE }
  downloadProgress?.apply {
   visibility=if(busy) View.VISIBLE else View.INVISIBLE
   isIndeterminate=state !is DownloadState.Downloading || state.percent==null
   if(state is DownloadState.Downloading) progress=state.percent ?: 0
  }
 }
}

internal object NativeMarkdown {
 fun render(source: String): android.text.Spanned {
  val output=android.text.SpannableStringBuilder()
  var code=false
  val token=Regex("\\[([^]]+)]\\((https?://[^ )]+)\\)|\\*\\*([^*]+)\\*\\*|`([^`]+)`|(?<!\\*)\\*([^*]+)\\*(?!\\*)")
  source.lines().forEach { raw ->
   if(raw.trim().startsWith("```")) { code=!code; return@forEach }
   val heading=raw.takeWhile { it=='#' }.length.takeIf { it in 1..6 && raw.getOrNull(it)==' ' }
   val line=when { code->raw; heading!=null->raw.drop(heading+1); raw.startsWith("> ")->"❯ "+raw.drop(2); raw.matches(Regex("^[-*+] .*"))->"• "+raw.drop(2); else->raw }
   val start=output.length
   if(code) output.append(line) else {
    var cursor=0
    token.findAll(line).forEach { match ->
     output.append(line.substring(cursor,match.range.first))
     val from=output.length
     val group=when { match.groups[1]!=null->1;match.groups[3]!=null->3;match.groups[4]!=null->4;else->5 }
     output.append(match.groupValues[group])
     val span:Any=when(group) {
      1->android.text.style.URLSpan(match.groupValues[2])
      3->android.text.style.StyleSpan(Typeface.BOLD)
      4->android.text.style.TypefaceSpan("monospace")
      else->android.text.style.StyleSpan(Typeface.ITALIC)
     }
     output.setSpan(span,from,output.length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
     cursor=match.range.last+1
    }
    output.append(line.substring(cursor))
   }
   if(code || heading!=null) output.setSpan(if(code) android.text.style.TypefaceSpan("monospace") else android.text.style.StyleSpan(Typeface.BOLD),start,output.length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
   output.append("\n")
  }
  return output
 }
}
