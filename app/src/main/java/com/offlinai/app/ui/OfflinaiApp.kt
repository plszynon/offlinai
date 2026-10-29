package com.offlinai.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.offlinai.app.data.Catalog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflinaiApp(vm: AppViewModel) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val screen by vm.screen.collectAsState()
    val chats by vm.chats.collectAsState()
    val active by vm.activeModel.collectAsState()
    val currentId by vm.currentChatId.collectAsState()

    LaunchedEffect(screen) { vm.refreshInstalled() }
    fun go(s: Screen) { vm.screen.value = s; scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("offlinai", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp))
                NavigationDrawerItem(
                    label = { Text("New chat") }, icon = { Icon(Icons.Default.Edit, null) },
                    selected = false, onClick = { vm.newChat(); go(Screen.Chat) })
                NavigationDrawerItem(
                    label = { Text("Model library") }, icon = { Icon(Icons.Default.Add, null) },
                    selected = screen == Screen.Library, onClick = { go(Screen.Library) })
                NavigationDrawerItem(
                    label = { Text("Installed models") }, icon = { Icon(Icons.Default.Settings, null) },
                    selected = screen == Screen.Installed, onClick = { go(Screen.Installed) })
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Chat history", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(chats, key = { it.id }) { chat ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { vm.openChat(chat.id); go(Screen.Chat) }
                                .padding(start = 20.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(chat.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (chat.id == currentId) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface)
                            IconButton(onClick = { vm.deleteChat(chat.id) }) {
                                Icon(Icons.Default.Delete, "Delete chat")
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(when (screen) {
                                Screen.Chat -> "offlinai"
                                Screen.Library -> "Model library"
                                Screen.Installed -> "Installed models"
                            })
                            if (screen == Screen.Chat) {
                                Text(active ?: "No model selected", style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                    }
                )
            }
        ) { pad ->
            when (screen) {
                Screen.Chat -> ChatScreen(vm, pad)
                Screen.Library -> LibraryScreen(vm, pad)
                Screen.Installed -> InstalledScreen(vm, pad)
            }
        }
    }
}

@Composable
fun ChatScreen(vm: AppViewModel, pad: PaddingValues) {
    val messages by vm.messages.collectAsState()
    val generating by vm.generating.collectAsState()
    val loading by vm.modelLoading.collectAsState()
    val active by vm.activeModel.collectAsState()
    val error by vm.error.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.lastOrNull()?.content, messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
        if (loading) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Loading model…", style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (active == null) "No model yet. Open the menu, tap Model library and install one."
                    else "What can I help with?",
                    style = MaterialTheme.typography.headlineSmall)
            }
        } else {
            LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(messages, key = { it.id }) { m ->
                    if (m.role == "user") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Surface(shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.widthIn(max = 300.dp)) {
                                Text(m.content, Modifier.padding(12.dp))
                            }
                        }
                    } else {
                        Text(m.content.ifEmpty { "…" }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f), placeholder = { Text("Message") },
                shape = RoundedCornerShape(24.dp), maxLines = 5)
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { if (generating) vm.stop() else { vm.send(input); input = "" } },
                enabled = generating || (input.isNotBlank() && !loading)
            ) {
                Icon(if (generating) Icons.Default.Close else Icons.AutoMirrored.Filled.Send,
                    if (generating) "Stop" else "Send")
            }
        }
    }
}

@Composable
fun ModelActions(vm: AppViewModel, file: String, url: String, installed: Boolean, active: Boolean, busy: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (installed) {
            Button(onClick = { vm.selectModel(file) }, enabled = !active) {
                Text(if (active) "Selected" else "Select")
            }
            OutlinedButton(onClick = { vm.uninstall(file) }) { Text("Uninstall") }
        } else {
            Button(onClick = { vm.install(url, file) }, enabled = !busy) { Text("Install") }
        }
    }
}

@Composable
fun LibraryScreen(vm: AppViewModel, pad: PaddingValues) {
    val installed by vm.installed.collectAsState()
    val active by vm.activeModel.collectAsState()
    val downloading by vm.downloading.collectAsState()
    val progress by vm.progress.collectAsState()
    val error by vm.error.collectAsState()
    val results by vm.searchResults.collectAsState()
    val searching by vm.searching.collectAsState()
    val searched by vm.searched.collectAsState()
    val expanded by vm.expandedRepo.collectAsState()
    val files by vm.repoFiles.collectAsState()
    var query by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf("") }
    val have = installed.map { it.name }.toSet()
    val busy = downloading != null

    LazyColumn(Modifier.padding(pad).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            downloading?.let {
                Column {
                    Text("Downloading $it")
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; if (it.isBlank()) vm.clearSearch() },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search Hugging Face (any GGUF model)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search(query) }))
                IconButton(onClick = { vm.search(query) }) { Icon(Icons.Default.Search, "Search") }
            }
        }
        if (searching) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }

        if (searched) {
            item { Text("Results", style = MaterialTheme.typography.titleMedium) }
            if (results.isEmpty()) item { Text("No models found") }
            items(results, key = { it.id }) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        Column(Modifier.fillMaxWidth().clickable { vm.toggleRepo(r.id) }.padding(12.dp)) {
                            Text(r.id, style = MaterialTheme.typography.titleSmall)
                            Text("${r.downloads} downloads", style = MaterialTheme.typography.bodySmall)
                        }
                        if (expanded == r.id) {
                            val list = files[r.id]
                            when {
                                list == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(12.dp))
                                list.isEmpty() -> Text("No .gguf files found", Modifier.padding(12.dp))
                                else -> list.forEach { f ->
                                    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                        Text(f.path, style = MaterialTheme.typography.bodyMedium)
                                        Text("%.2f GB".format(f.bytes / 1e9), style = MaterialTheme.typography.bodySmall)
                                        ModelActions(vm, f.path, vm.repo.url(r.id, f.path),
                                            f.path in have, active == f.path, busy)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item { Text("Featured", style = MaterialTheme.typography.titleMedium) }
            items(Catalog.featured, key = { it.file }) { m ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(m.title, style = MaterialTheme.typography.titleMedium)
                        Text("%.1f GB".format(m.gb), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        ModelActions(vm, m.file, vm.repo.url(m.repo, m.file),
                            m.file in have, active == m.file, busy)
                    }
                }
            }
        }
        item {
            Text("Direct link", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            OutlinedTextField(custom, { custom = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("https://.../model.gguf") })
            Button(
                onClick = { vm.install(custom.trim(), custom.trim().substringAfterLast('/').substringBefore('?')); custom = "" },
                enabled = !busy && (custom.trim().endsWith(".gguf") || custom.contains(".gguf?")),
                modifier = Modifier.padding(vertical = 8.dp)) { Text("Download") }
        }
    }
}

@Composable
fun InstalledScreen(vm: AppViewModel, pad: PaddingValues) {
    val installed by vm.installed.collectAsState()
    val active by vm.activeModel.collectAsState()
    val error by vm.error.collectAsState()

    Column(Modifier.padding(pad).fillMaxSize()) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
        if (installed.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No models installed yet") }
        } else {
            LazyColumn(Modifier.padding(horizontal = 8.dp)) {
                items(installed, key = { it.name }) { f ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = active == f.name, onClick = { vm.selectModel(f.name) })
                        Column(Modifier.weight(1f)) {
                            Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("%.2f GB".format(f.length() / 1e9), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { vm.uninstall(f.name) }) { Icon(Icons.Default.Delete, "Uninstall") }
                    }
                }
            }
        }
    }
}
