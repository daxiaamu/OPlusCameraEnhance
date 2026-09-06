package com.daxiaamu.opluscameraenhance

import android.os.Bundle
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daxiaamu.opluscameraenhance.update.CheckState
import com.daxiaamu.opluscameraenhance.update.DownloadState
import com.daxiaamu.opluscameraenhance.update.UpdateManager
import com.daxiaamu.opluscameraenhance.update.UpdateManifest
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun UpdateDialog(activity: MainActivity, update: UpdateManifest) {
    val required = update.isRequired(BuildConfig.VERSION_CODE.toLong())
    val download = UpdateManager.downloadState
    val busy = download is DownloadState.Downloading || download == DownloadState.Verifying
    val readyForCurrent = (download as? DownloadState.ReadyToInstall)?.versionCode == update.versionCode
    val authorizationForCurrent = (download as? DownloadState.NeedsAuthorization)?.versionCode == update.versionCode
    val launchingForCurrent = (download as? DownloadState.LaunchingInstaller)?.versionCode == update.versionCode
    BackHandler(required || busy) {}
    Dialog(
        onDismissRequest = { if (!required && !busy) UpdateManager.ignore(update) },
        properties = DialogProperties(dismissOnBackPress = !required && !busy, dismissOnClickOutside = !required && !busy)
    ) {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color.White) {
            Column(Modifier.padding(20.dp)) {
                Text(if (required) stringResource(R.string.required_update) else stringResource(R.string.new_update), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                SelectionContainer {
                    Column(Modifier.fillMaxWidth().heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.45f).dp).verticalScroll(rememberScrollState())) {
                        Text(stringResource(R.string.version) + " " + update.versionName, fontWeight = FontWeight.SemiBold)
                        update.publishedAt?.let(::formatPublishedAt)?.let { Text(stringResource(R.string.published,it), style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                        Spacer(Modifier.height(12.dp))
                        SafeMarkdown(update.changelog)
                        (download as? DownloadState.Failed)?.let {
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.download_failed), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!required) {
                        TextButton(onClick = { UpdateManager.skip(update) }, enabled = !busy, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(stringResource(R.string.skip)) }
                        TextButton(onClick = { UpdateManager.ignore(update) }, enabled = !busy, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(stringResource(R.string.ignore)) }
                    }

                    Button(
                        onClick = {
                            if (readyForCurrent || authorizationForCurrent) {
                                UpdateManager.install(activity, update)
                            } else {
                                UpdateManager.download(update)
                            }
                        },
                        enabled = !busy && !launchingForCurrent,
                        modifier = Modifier.defaultMinSize(minWidth = 116.dp, minHeight = 48.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        when (download) {
                            is DownloadState.Downloading -> {
                                if (download.percent == null) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else CircularProgressIndicator({ download.percent / 100f }, Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp)); Text(download.percent?.let { "$it%" } ?: stringResource(R.string.downloading))
                            }
                            DownloadState.Verifying -> { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.verifying)) }
                            is DownloadState.ReadyToInstall -> Text(if (readyForCurrent) stringResource(R.string.install) else stringResource(R.string.download_install))
                            is DownloadState.NeedsAuthorization -> Text(if (authorizationForCurrent) stringResource(R.string.install_continue) else stringResource(R.string.download_install))
                            is DownloadState.Failed -> Text(stringResource(R.string.retry))
                            is DownloadState.LaunchingInstaller -> Text(if (launchingForCurrent) stringResource(R.string.opening) else stringResource(R.string.download_install))
                            else -> Text(stringResource(R.string.download_install))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SafeMarkdown(markdown: String) {
    var inCode = false
    markdown.lines().forEach { raw ->
        if (raw.trim().startsWith("```")) { inCode = !inCode; return@forEach }
        val line = raw.trimEnd()
        if (line.matches(Regex("-{3,}"))) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp)); return@forEach
        }
        val heading = line.takeWhile { it == '#' }.length.takeIf { it in 1..6 && line.getOrNull(it) == ' ' }
        val display = when {
            heading != null -> line.drop(heading + 1)
            line.startsWith("> ") -> "❯ ${line.drop(2)}"
            line.matches(Regex("^[-*+] .*")) -> "• ${line.drop(2)}"
            else -> line
        }
        Text(
            text = inlineMarkdown(display),
            modifier = Modifier.padding(vertical = if (display.isBlank()) 4.dp else 2.dp),
            fontWeight = if (heading != null) FontWeight.Bold else null,
            style = if (heading != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontFamily = if (inCode) FontFamily.Monospace else null
        )
    }
}

private fun inlineMarkdown(text: String) = buildAnnotatedString {
    val token = Regex("\\[([^]]+)]\\((https?://[^ )]+)\\)|\\*\\*([^*]+)\\*\\*|`([^`]+)`|(?<!\\*)\\*([^*]+)\\*(?!\\*)")
    var cursor = 0
    token.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        when {
            match.groups[1] != null -> withLink(LinkAnnotation.Url(match.groupValues[2])) {
                withStyle(SpanStyle(color = Accent, textDecoration = TextDecoration.Underline)) { append(match.groupValues[1]) }
            }
            match.groups[3] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[3]) }
            match.groups[4] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFFECEFF4))) { append(match.groupValues[4]) }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groupValues[5]) }
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}


private val Accent = Color(0xFF176B61)
internal fun formatPublishedAt(value: String): String? = runCatching {
 java.time.OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault())
  .format(DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT))
}.getOrNull()