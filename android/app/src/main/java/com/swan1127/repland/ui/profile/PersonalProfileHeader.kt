package com.swan1127.repland.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swan1127.repland.data.profile.PersonalProfile
import com.swan1127.repland.domain.model.PersonalProfileName
import com.swan1127.repland.ui.components.DesignButton
import com.swan1127.repland.ui.components.DesignClickableSurface
import com.swan1127.repland.ui.components.DesignIcons
import com.swan1127.repland.ui.theme.LocalReducedMotion
import com.swan1127.repland.ui.theme.replandSettingsAccent

@Composable
fun PersonalProfileSection(viewModel: PersonalProfileViewModel) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.saveAvatar(uri)
    }
    PersonalProfileHeader(profile, operation, onPickAvatar = {
        viewModel.dismissError()
        try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        catch (_: Exception) { viewModel.pickerFailed() }
    }, onResetAvatar = viewModel::removeAvatar, onSaveUsername = viewModel::saveUsername,
        onDismissError = viewModel::dismissError, onRetryLoad = viewModel::load)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalProfileHeader(
    profile: PersonalProfile?,
    operation: PersonalProfileOperation,
    onPickAvatar: () -> Unit,
    onResetAvatar: () -> Unit,
    onSaveUsername: (String, () -> Unit) -> Unit,
    onDismissError: () -> Unit = {},
    onRetryLoad: () -> Unit = {},
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var validation by rememberSaveable { mutableStateOf<String?>(null) }
    val accent = replandSettingsAccent(0)
    val enabled = profile != null && !operation.loading && !operation.working
    val reduced = LocalReducedMotion.current
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag("personal-profile-card")) {
        Column(Modifier.background(Brush.linearGradient(listOf(accent.surface, MaterialTheme.colorScheme.surfaceContainerLow)))
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DesignClickableSurface(onClick = onPickAvatar, enabled = enabled, shape = RectangleShape, color = Color.Transparent,
                    modifier = Modifier.size(80.dp).testTag("personal-profile-avatar").semantics { contentDescription = "更换头像" }, pressedScale = .94f) {
                    Box(Modifier.background(accent.highlight, CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape), contentAlignment = Alignment.Center) {
                        AnimatedContent(targetState = profile?.avatar, label = "avatar replacement",
                            transitionSpec = { fadeIn(tween(if (reduced) 0 else 160)) togetherWith fadeOut(tween(if (reduced) 0 else 100)) }) { avatar ->
                            if (avatar == null) Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                                Icon(DesignIcons.Profile, null, Modifier.size(36.dp), tint = accent.ink)
                            } else Image(avatar.asImageBitmap(), "当前头像", Modifier.size(80.dp).clip(CircleShape).testTag("personal-profile-photo"), contentScale = ContentScale.Crop)
                        }
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).size(24.dp)) {
                            Icon(DesignIcons.Camera, null, Modifier.padding(5.dp), tint = accent.ink)
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(profile?.username ?: if (operation.loading) "正在读取资料…" else PersonalProfileName.Default,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("personal-profile-name"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onPickAvatar, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = accent.ink),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("personal-profile-change-avatar")) { Text("更换头像") }
                        TextButton(onClick = {
                            draft = profile?.username.orEmpty(); validation = null; onDismissError(); editing = true
                        }, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = accent.ink),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("personal-profile-edit-name")) {
                            Icon(DesignIcons.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("编辑用户名")
                        }
                    }
                }
            }
            if (operation.working) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("personal-profile-saving"),
                color = accent.ink, trackColor = accent.surface)
            if (profile?.avatar != null) TextButton(onClick = onResetAvatar, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).testTag("personal-profile-reset-avatar")) { Text("恢复默认头像", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (operation.error != null && !editing) {
                Text(operation.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (profile == null) TextButton(onClick = onRetryLoad) { Text("重新读取") }
            }
        }
    }
    if (editing) {
        val save = {
            validation = PersonalProfileName.error(draft)
            if (validation == null && enabled) onSaveUsername(draft) { editing = false }
        }
        val closeEditor = { if (!operation.working) { editing = false; onDismissError() } }
        val nameField: @Composable (Boolean) -> Unit = { compact ->
                OutlinedTextField(value = draft, onValueChange = { draft = it; validation = null; onDismissError() },
                    enabled = !operation.working, singleLine = true,
                    label = { Text(if (compact) "用户名（${PersonalProfileName.length(draft.trim())} / 24）" else "用户名") },
                    isError = validation != null || operation.error != null,
                    supportingText = if (compact && validation == null && operation.error == null) null
                        else { { Text(validation ?: operation.error ?: "1–24 个字，可使用中文、字母或表情") } },
                    modifier = Modifier.fillMaxWidth().testTag("personal-profile-name-input"),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { save() }))
        }
        val confirm: @Composable () -> Unit = {
            DesignButton(onClick = { save() }, enabled = !operation.working,
                modifier = Modifier.testTag("personal-profile-name-save")) { Text(if (operation.working) "保存中…" else "保存") }
        }
        val cancel: @Composable () -> Unit = {
            TextButton(onClick = closeEditor, enabled = !operation.working,
                modifier = Modifier.heightIn(min = 48.dp).testTag("personal-profile-name-cancel")) { Text("取消") }
        }
        // A landscape IME leaves too little height for a title, form and footer stacked vertically.
        // Keep the real field and both actions side by side, above the keyboard.
        if (LocalConfiguration.current.screenHeightDp < 480) {
            Dialog(onDismissRequest = closeEditor,
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                Box(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center) {
                    Surface(Modifier.widthIn(max = 640.dp).fillMaxWidth().semantics { paneTitle = "编辑用户名" },
                        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { nameField(true) }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                                confirm(); cancel()
                            }
                        }
                    }
                }
            }
        } else AlertDialog(onDismissRequest = closeEditor,
            modifier = Modifier.imePadding(),
            properties = DialogProperties(decorFitsSystemWindows = false),
            title = { Text("编辑用户名") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                nameField(false)
                Text("${PersonalProfileName.length(draft.trim())} / 24", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } },
            confirmButton = confirm, dismissButton = cancel)
    }
}
