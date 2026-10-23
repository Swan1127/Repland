package com.swan1127.repland.ui.agent

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swan1127.repland.domain.model.AgentProfile
import com.swan1127.repland.domain.model.UserProfile

/**
 * The team's management surface: one place to change who each agent is.
 *
 * It is intentionally a sheet rather than a screen inside the tab bar — it is only
 * reachable from the group chat header, and the user should return to the conversation
 * they were editing, not be dropped into a separate navigation stack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentManagementSheet(
    agents: List<AgentProfile>,
    user: UserProfile,
    onSaveAgents: (List<AgentProfile>) -> Unit,
    onSaveUser: (UserProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    var editingAgent by remember { mutableStateOf<AgentProfile?>(null) }
    var editingUser by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("团队成员", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("点头像可以改名字、自我介绍、提示词和口癖", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "关闭") }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    AgentRow(
                        avatar = user.avatar,
                        name = "${user.name}（你）",
                        subtitle = "群聊里显示你的头像",
                        onClick = { editingUser = true },
                    )
                }
                items(agents, key = { it.id }) { agent ->
                    AgentRow(
                        avatar = agent.avatar,
                        name = agent.name,
                        subtitle = "${agent.selfIntro}  「${agent.catchphrase}」",
                        onClick = { editingAgent = agent },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    editingAgent?.let { agent ->
        AgentEditDialog(
            agent = agent,
            onDismiss = { editingAgent = null },
            onSave = { updated ->
                onSaveAgents(agents.map { if (it.id == updated.id) updated else it })
                editingAgent = null
            },
        )
    }

    if (editingUser) {
        UserEditDialog(
            user = user,
            onDismiss = { editingUser = false },
            onSave = { updated ->
                onSaveUser(updated)
                editingUser = false
            },
        )
    }
}

@Composable
private fun AgentRow(avatar: String, name: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp, 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape, modifier = Modifier.size(44.dp)) {
                Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(avatar, fontSize = 22.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Text("编辑", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AgentEditDialog(
    agent: AgentProfile,
    onDismiss: () -> Unit,
    onSave: (AgentProfile) -> Unit,
) {
    var name by remember(agent.id) { mutableStateOf(agent.name) }
    var avatar by remember(agent.id) { mutableStateOf(agent.avatar) }
    var selfIntro by remember(agent.id) { mutableStateOf(agent.selfIntro) }
    var introPrompt by remember(agent.id) { mutableStateOf(agent.introPrompt) }
    var catchphrase by remember(agent.id) { mutableStateOf(agent.catchphrase) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑 ${agent.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AvatarField(avatar, onChange = { avatar = it })
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = catchphrase, onValueChange = { catchphrase = it }, singleLine = true, label = { Text("口癖（每次发言的开头）") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = selfIntro, onValueChange = { selfIntro = it }, minLines = 2, label = { Text("自我介绍") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = introPrompt, onValueChange = { introPrompt = it }, minLines = 3, label = { Text("提示词（角色设定）") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        agent.copy(
                            name = name.trim().ifBlank { agent.name },
                            avatar = avatar.trim().ifBlank { agent.avatar },
                            selfIntro = selfIntro.trim(),
                            introPrompt = introPrompt.trim(),
                            catchphrase = catchphrase.trim(),
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun UserEditDialog(
    user: UserProfile,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit,
) {
    var name by remember { mutableStateOf(user.name) }
    var avatar by remember { mutableStateOf(user.avatar) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("我的头像") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AvatarField(avatar, onChange = { avatar = it })
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("昵称") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(UserProfile(name.trim().ifBlank { "我" }, avatar.trim().ifBlank { "🙂" })) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AvatarField(avatar: String, onChange: (String) -> Unit) {
    val choices = listOf("🧩", "⏰", "🌱", "🛡️", "🗺️", "🧠", "📚", "⚡", "🎯", "🙂", "🐱", "🚀")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape, modifier = Modifier.size(48.dp)) {
                Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(avatar, fontSize = 24.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(value = avatar, onValueChange = { onChange(it.take(4)) }, singleLine = true, label = { Text("头像") }, modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.forEach { emoji ->
                Surface(onClick = { onChange(emoji) }, color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                    Text(emoji, modifier = Modifier.padding(8.dp), fontSize = 18.sp)
                }
            }
        }
    }
}
