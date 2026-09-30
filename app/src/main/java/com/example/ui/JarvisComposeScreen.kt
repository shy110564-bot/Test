package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

data class ChatMessageItem(
    val id: Long = System.nanoTime(),
    val role: String, // "you" or "jarvis"
    val text: String,
    val time: String
)

data class JarvisUiState(
    val currentScreen: String = "home", // "home" or "settings"
    val orbState: String = "listening", // "idle", "listening", "thinking", "speaking"
    val audioLevel: Float = 0f,
    val partialTranscript: String = "",
    val isPowerOnline: Boolean = true,
    val isMicMuted: Boolean = false,
    val isGfMode: Boolean = false,
    val isScreenShareActive: Boolean = false,
    val screenShareSummary: String = "Tap 'Share Screen' so Jarvis can see and control your entire screen",
    val activeVoice: String = "Sweet Girl Voice",
    val voiceSpeed: Float = 1.00f,
    val voicePitch: Float = 1.06f,
    val apiKey: String = "",
    val model: String = "gemini-3-flash-preview",
    val personality: String = "Sweet Girl Voice",
    val edgeLightingEnabled: Boolean = false,
    val installedAppsCount: Int = 0,
    val messages: List<ChatMessageItem> = emptyList(),
    val permAudio: Boolean = false,
    val permCamera: Boolean = false,
    val permPhone: Boolean = false,
    val permContacts: Boolean = false,
    val permLocation: Boolean = false,
    val permNotifications: Boolean = false,
    val permOverlay: Boolean = false,
    val permAccessibility: Boolean = false,
    val permWhatsAppReader: Boolean = false,
    val permSettings: Boolean = false
)

private val BgDeep = Color(0xFF070B14)
private val CardSurface = Color(0xFF111827)
private val CardBorder = Color(0xFF1F2937)
private val NeonCyan = Color(0xFF00E5FF)
private val NeonRose = Color(0xFFFF4D8D)
private val NeonPurple = Color(0xFFA855F7)
private val NeonEmerald = Color(0xFF10B981)
private val TextPrimary = Color(0xFFF9FAFB)
private val TextSecondary = Color(0xFF9CA3AF)

@Composable
fun JarvisMainScreen(
    uiState: JarvisUiState,
    onOrbTap: () -> Unit,
    onStopSpeaking: () -> Unit,
    onToggleMute: () -> Unit,
    onTogglePower: () -> Unit,
    onToggleScreenShare: () -> Unit,
    onSendCommand: (String) -> Unit,
    onSelectVoice: (String) -> Unit,
    onToggleGfMode: (Boolean) -> Unit,
    onTestVoice: () -> Unit,
    onSpeakMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onSaveSettings: (apiKey: String, model: String, voice: String, speed: Float, pitch: Float, edgeLight: Boolean) -> Unit,
    onPasteApiKey: () -> Unit,
    onPreviewVoice: (voice: String, speed: Float, pitch: Float) -> Unit,
    onRequestPermission: (String) -> Unit,
    onRequestAllPermissions: () -> Unit,
    onOpenTelegram: () -> Unit,
    onOpenHistoryDialog: () -> Unit
) {
    if (uiState.currentScreen == "settings") {
        BackHandler { onCloseSettings() }
        JarvisSettingsScreen(
            uiState = uiState,
            onBack = onCloseSettings,
            onSaveSettings = onSaveSettings,
            onPasteApiKey = onPasteApiKey,
            onPreviewVoice = onPreviewVoice,
            onRequestPermission = onRequestPermission,
            onRequestAllPermissions = onRequestAllPermissions,
            onOpenTelegram = onOpenTelegram,
            onOpenHistoryDialog = onOpenHistoryDialog
        )
    } else {
        JarvisHomeDashboard(
            uiState = uiState,
            onOrbTap = onOrbTap,
            onStopSpeaking = onStopSpeaking,
            onToggleMute = onToggleMute,
            onTogglePower = onTogglePower,
            onToggleScreenShare = onToggleScreenShare,
            onSendCommand = onSendCommand,
            onSelectVoice = onSelectVoice,
            onToggleGfMode = onToggleGfMode,
            onTestVoice = onTestVoice,
            onSpeakMessage = onSpeakMessage,
            onClearChat = onClearChat,
            onOpenSettings = onOpenSettings
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JarvisHomeDashboard(
    uiState: JarvisUiState,
    onOrbTap: () -> Unit,
    onStopSpeaking: () -> Unit,
    onToggleMute: () -> Unit,
    onTogglePower: () -> Unit,
    onToggleScreenShare: () -> Unit,
    onSendCommand: (String) -> Unit,
    onSelectVoice: (String) -> Unit,
    onToggleGfMode: (Boolean) -> Unit,
    onTestVoice: () -> Unit,
    onSpeakMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var commandInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BgDeep,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    !uiState.isPowerOnline -> Color.Gray
                                    uiState.isMicMuted -> Color(0xFFF59E0B)
                                    uiState.isGfMode -> NeonRose
                                    else -> NeonEmerald
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (uiState.isGfMode) "JARVIS • SWEETHEART AI" else "JARVIS AI",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${uiState.activeVoice} • Instant Barge-In Active",
                            color = if (uiState.isGfMode) NeonRose else NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onTestVoice,
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("test_voice_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Test Natural Girl Voice",
                            tint = NeonRose
                        )
                    }

                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("mic_mute_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (uiState.isMicMuted) "Unmute Microphone" else "Mute Microphone",
                            tint = if (uiState.isMicMuted) Color(0xFFEF4444) else NeonCyan
                        )
                    }

                    IconButton(
                        onClick = onTogglePower,
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("power_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Toggle Online Power",
                            tint = if (uiState.isPowerOnline) NeonEmerald else Color.Gray
                        )
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Open Settings",
                            tint = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Interactive Holographic AI Orb & Barge-In Control
            HolographicVoiceOrbSection(
                orbState = uiState.orbState,
                audioLevel = uiState.audioLevel,
                partialTranscript = uiState.partialTranscript,
                isGfMode = uiState.isGfMode,
                isMicMuted = uiState.isMicMuted,
                isPowerOnline = uiState.isPowerOnline,
                onOrbTap = onOrbTap,
                onStopSpeaking = onStopSpeaking
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 2B. Live AI Screen Share & Full Screen Control Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isScreenShareActive) NeonEmerald.copy(alpha = 0.12f) else CardSurface
                ),
                border = BorderStroke(
                    1.dp,
                    if (uiState.isScreenShareActive) NeonEmerald else NeonCyan.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (uiState.isScreenShareActive) {
                                    "🖥️ ${uiState.screenShareSummary}"
                                } else {
                                    "🖥️ WhatsApp-Style Screen Share & AI Control"
                                },
                                color = if (uiState.isScreenShareActive) NeonEmerald else NeonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (uiState.isScreenShareActive) {
                                    "Video Call + Screen Share ON • Notifications Visible"
                                } else {
                                    "Say: 'Screen share karo Rahul ke saath' or tap Share Screen"
                                },
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onToggleScreenShare,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.isScreenShareActive) NeonRose else NeonCyan,
                                contentColor = BgDeep
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("toggle_screen_share_button")
                        ) {
                            Text(
                                text = if (uiState.isScreenShareActive) "Stop Share" else "Share Screen",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSendCommand("Screen share karo Rahul ke saath") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            border = BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.6f))
                        ) {
                            Text("📞 Share Screen: Rahul", color = TextPrimary, fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { onSendCommand("YouTube pe Shah Rukh Khan ka new movie trailer dikhao") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            border = BorderStroke(1.dp, NeonRose.copy(alpha = 0.5f))
                        ) {
                            Text("🎬 YT: SRK Trailer", color = TextPrimary, fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { onSendCommand("YouTube pe Arijit Singh ke gaane search karo") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
                        ) {
                            Text("🎵 YT: Arijit Songs", color = TextPrimary, fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { onSendCommand("WhatsApp pe Mummy ko message karo 'main aa raha hun'") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.5f))
                        ) {
                            Text("💬 WA: Mummy Msg", color = TextPrimary, fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { onSendCommand("Instagram pe Reels dekho") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            border = BorderStroke(1.dp, NeonRose.copy(alpha = 0.5f))
                        ) {
                            Text("📱 IG Reels", color = TextPrimary, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Quick Action & Voice Directive Chips
            val quickScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .horizontalScroll(quickScroll),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = false,
                    onClick = { onSendCommand("Jarvis YouTube open karke AK EXPLOITS search karo") },
                    label = { Text("YouTube: AK EXPLOITS", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.SmartDisplay,
                            contentDescription = null,
                            tint = Color(0xFFFF4444),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = CardSurface,
                        labelColor = TextPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = false,
                        borderColor = NeonCyan.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.testTag("quick_youtube_ak_exploits")
                )

                FilterChip(
                    selected = uiState.isGfMode,
                    onClick = { onToggleGfMode(!uiState.isGfMode) },
                    label = {
                        Text(
                            text = if (uiState.isGfMode) "💖 Sweetheart Mode ON" else "💖 Sweetheart Mode",
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = NeonRose,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = if (uiState.isGfMode) NeonRose.copy(alpha = 0.2f) else CardSurface,
                        selectedContainerColor = NeonRose.copy(alpha = 0.25f),
                        labelColor = TextPrimary,
                        selectedLabelColor = TextPrimary
                    ),
                    modifier = Modifier.testTag("quick_gf_mode")
                )

                FilterChip(
                    selected = false,
                    onClick = onTestVoice,
                    label = { Text("Meethi Aawaz Test", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = CardSurface,
                        labelColor = TextPrimary
                    ),
                    modifier = Modifier.testTag("quick_test_voice")
                )

                FilterChip(
                    selected = false,
                    onClick = { onSendCommand("Flashlight on karo") },
                    label = { Text("Flashlight", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.FlashlightOn,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = CardSurface,
                        labelColor = TextPrimary
                    )
                )

                FilterChip(
                    selected = false,
                    onClick = { onSendCommand("Tum kitni intelligent ho aur kya kar sakti ho?") },
                    label = { Text("AI Capabilities", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = NeonPurple,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = CardSurface,
                        labelColor = TextPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Conversation History Header + Feed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE VOICE CONVERSATION (${uiState.messages.size})",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                if (uiState.messages.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onClearChat() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("clear_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Conversation",
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear", color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Chat Messages List
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.85f)),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                if (uiState.messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Boliye: \"Jarvis YouTube open karke AK EXPLOITS search karo\"",
                                color = NeonCyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Main aapki baatein lagatar sun rahi hoon, turant jawab dungi, aur aapke bolte hi chup ho jaungi.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.messages, key = { it.id }) { msg ->
                            ChatBubbleItem(
                                item = msg,
                                isGfMode = uiState.isGfMode,
                                onReplay = { onSpeakMessage(msg.text) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 5. Instant Command Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = commandInput,
                    onValueChange = { commandInput = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("command_input_field"),
                    placeholder = {
                        Text(
                            text = "Bolkar ya likhkar poochiye (e.g. YouTube AK EXPLOITS)...",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardSurface,
                        unfocusedContainerColor = CardSurface,
                        focusedBorderColor = if (uiState.isGfMode) NeonRose else NeonCyan,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            val trimmed = commandInput.trim()
                            if (trimmed.isNotEmpty()) {
                                onSendCommand(trimmed)
                                commandInput = ""
                            }
                        }
                    )
                )

                Surface(
                    onClick = {
                        val trimmed = commandInput.trim()
                        if (trimmed.isNotEmpty()) {
                            onSendCommand(trimmed)
                            commandInput = ""
                        } else {
                            onOrbTap()
                        }
                    },
                    shape = CircleShape,
                    color = if (uiState.isGfMode) NeonRose else NeonCyan,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("send_or_speak_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (commandInput.isNotBlank()) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                            contentDescription = "Send or Speak Command",
                            tint = Color(0xFF070B14)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HolographicVoiceOrbSection(
    orbState: String,
    audioLevel: Float,
    partialTranscript: String,
    isGfMode: Boolean,
    isMicMuted: Boolean,
    isPowerOnline: Boolean,
    onOrbTap: () -> Unit,
    onStopSpeaking: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val primaryAccent = when {
        !isPowerOnline -> Color.Gray
        isMicMuted -> Color(0xFFEF4444)
        orbState == "speaking" -> NeonRose
        orbState == "thinking" -> NeonPurple
        isGfMode -> NeonRose
        else -> NeonCyan
    }

    val statusLabel = when {
        !isPowerOnline -> "Standby Sleep Mode • Tap Orb to Wake Up JARVIS"
        isMicMuted -> "Microphone Muted • Tap Orb or Mic to Unmute"
        orbState == "speaking" -> "JARVIS Pyar Se Bol Rahi Hai 💕 • Tap to Pause"
        orbState == "thinking" -> "Hmm… Ji, JARVIS Soch Rahi Hai..."
        partialTranscript.isNotBlank() -> "Hmm… Ji… Acha… (JARVIS Puri Baat Sun Rahi Hai)"
        else -> "Ji… Sun Rahi Hun • Boliye \"YouTube pe AK EXPLOITS search karo\""
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (orbState == "speaking") {
                            onStopSpeaking()
                        } else {
                            onOrbTap()
                        }
                    }
                    .testTag("jarvis_orb_button"),
                contentAlignment = Alignment.Center
            ) {
                val dynamicBoost = (audioLevel * 0.28f).coerceIn(0f, 0.35f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerRadius = size.minDimension / 2f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryAccent.copy(alpha = 0.36f),
                                NeonRose.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        ),
                        radius = centerRadius * (pulse + dynamicBoost).coerceAtMost(1.35f)
                    )
                    drawCircle(
                        color = primaryAccent.copy(alpha = 0.65f),
                        radius = centerRadius * (0.78f + dynamicBoost * 0.5f),
                        style = Stroke(width = 3.dp.toPx())
                    )
                    drawCircle(
                        color = NeonRose.copy(alpha = 0.45f),
                        radius = centerRadius * (0.88f + (pulse - 0.92f) * 0.5f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                Image(
                    painter = painterResource(id = R.drawable.img_ai_avatar),
                    contentDescription = "Jarvis AI Holographic Core",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .border(2.dp, primaryAccent, CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = statusLabel,
                color = primaryAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            AnimatedVisibility(visible = partialTranscript.isNotBlank()) {
                Surface(
                    color = BgDeep,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "🎤 \"$partialTranscript\"",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            AnimatedVisibility(visible = orbState == "speaking") {
                OutlinedButton(
                    onClick = onStopSpeaking,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, NeonRose),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRose),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .height(34.dp)
                        .testTag("barge_in_stop_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.StopCircle,
                        contentDescription = "Stop Jarvis Speaking",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chup Karo / Bolna Shuru Karein", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(
    item: ChatMessageItem,
    isGfMode: Boolean,
    onReplay: () -> Unit
) {
    val isUser = item.role == "you"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) {
                NeonCyan.copy(alpha = 0.16f)
            } else if (isGfMode) {
                NeonRose.copy(alpha = 0.16f)
            } else {
                Color(0xFF1E293B)
            },
            border = BorderStroke(
                1.dp,
                if (isUser) NeonCyan.copy(alpha = 0.4f)
                else if (isGfMode) NeonRose.copy(alpha = 0.4f)
                else CardBorder
            ),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "YOU" else if (isGfMode) "💖 SWEETHEART JARVIS" else "✨ JARVIS (GIRL VOICE)",
                        color = if (isUser) NeonCyan else NeonRose,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.time,
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                        if (!isUser) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Replay voice",
                                tint = NeonRose,
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable { onReplay() }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.text,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun JarvisSettingsScreen(
    uiState: JarvisUiState,
    onBack: () -> Unit,
    onSaveSettings: (apiKey: String, model: String, voice: String, speed: Float, pitch: Float, edgeLight: Boolean) -> Unit,
    onPasteApiKey: () -> Unit,
    onPreviewVoice: (voice: String, speed: Float, pitch: Float) -> Unit,
    onRequestPermission: (String) -> Unit,
    onRequestAllPermissions: () -> Unit,
    onOpenTelegram: () -> Unit,
    onOpenHistoryDialog: () -> Unit
) {
    var apiKey by remember(uiState.apiKey) { mutableStateOf(uiState.apiKey) }
    var selectedModel by remember(uiState.model) { mutableStateOf(uiState.model) }
    var selectedVoice by remember(uiState.activeVoice) { mutableStateOf(uiState.activeVoice) }
    var voiceSpeed by remember(uiState.voiceSpeed) { mutableFloatStateOf(uiState.voiceSpeed) }
    var voicePitch by remember(uiState.voicePitch) { mutableFloatStateOf(uiState.voicePitch) }
    var edgeLighting by remember(uiState.edgeLightingEnabled) { mutableStateOf(uiState.edgeLightingEnabled) }

    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BgDeep,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home",
                            tint = TextPrimary
                        )
                    }
                    Text(
                        text = "Jarvis Voice & AI Settings",
                        color = TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        onSaveSettings(apiKey, selectedModel, selectedVoice, voiceSpeed, voicePitch, edgeLighting)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = BgDeep),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("save_settings_button")
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Natural Girl Voice Tuning Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, NeonRose.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🎙️ Natural Girl Voice Profile",
                        color = NeonRose,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Uses high-definition Indian Hindi (hi-IN) & English (en-IN) neural female TTS voices with natural human pitch.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val voices = listOf("Sweet Girl Voice", "Girlfriend Mode", "Calm", "Energetic", "Classic")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        voices.forEach { v ->
                            FilterChip(
                                selected = selectedVoice.equals(v, ignoreCase = true),
                                onClick = {
                                    selectedVoice = v
                                    onPreviewVoice(v, voiceSpeed, voicePitch)
                                },
                                label = { Text(v) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonRose.copy(alpha = 0.25f),
                                    selectedLabelColor = TextPrimary,
                                    containerColor = BgDeep,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Voice Speed: ${"%.2f".format(voiceSpeed)}x",
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                    Slider(
                        value = voiceSpeed,
                        onValueChange = { voiceSpeed = it },
                        valueRange = 0.80f..1.35f,
                        colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                    )

                    Text(
                        text = "Natural Girl Pitch: ${"%.2f".format(voicePitch)}x",
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                    Slider(
                        value = voicePitch,
                        onValueChange = { voicePitch = it },
                        valueRange = 0.85f..1.35f,
                        colors = SliderDefaults.colors(thumbColor = NeonRose, activeTrackColor = NeonRose)
                    )

                    OutlinedButton(
                        onClick = { onPreviewVoice(selectedVoice, voiceSpeed, voicePitch) },
                        border = BorderStroke(1.dp, NeonRose),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRose),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Preview Meethi Girl Voice")
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Gemini AI Intelligence Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🧠 Gemini AI Super-Intelligence",
                        color = NeonCyan,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("Gemini API Key (Optional if set in Secrets)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = CardBorder
                            )
                        )
                        IconButton(onClick = onPasteApiKey) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste API Key",
                                tint = NeonCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("RGB Corner Edge Lighting", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Glowing neon border overlay across screen", color = TextSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = edgeLighting,
                            onCheckedChange = {
                                edgeLighting = it
                                onSaveSettings(apiKey, selectedModel, selectedVoice, voiceSpeed, voicePitch, it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Permissions & Device Automation Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🛡️ Permissions & Automation (${uiState.installedAppsCount} Apps)",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = onRequestAllPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = BgDeep),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Grant All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    PermissionRow("Microphone (Voice & Barge-In)", uiState.permAudio) { onRequestPermission("mic") }
                    PermissionRow("Screen Control (Accessibility)", uiState.permAccessibility) { onRequestPermission("accessibility") }
                    PermissionRow("WhatsApp Message Reader", uiState.permWhatsAppReader) { onRequestPermission("whatsapp_reader") }
                    PermissionRow("Phone & Contacts Calling", uiState.permPhone) { onRequestPermission("phone") }
                    PermissionRow("Camera & Flashlight", uiState.permCamera) { onRequestPermission("camera") }
                    PermissionRow("Display Over Other Apps", uiState.permOverlay) { onRequestPermission("overlay") }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. History & Creator Links
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenHistoryDialog,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, NeonCyan),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Memory Log")
                }

                Button(
                    onClick = onOpenTelegram,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRose, contentColor = Color.White)
                ) {
                    Text("AK EXPLOITS Telegram", fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    onGrantClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Security,
                contentDescription = null,
                tint = if (granted) NeonEmerald else Color(0xFFF59E0B),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = title, color = TextPrimary, fontSize = 13.sp)
        }
        if (granted) {
            Text("ACTIVE", color = NeonEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        } else {
            OutlinedButton(
                onClick = onGrantClick,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp),
                border = BorderStroke(1.dp, NeonCyan)
            ) {
                Text("Enable", color = NeonCyan, fontSize = 11.sp)
            }
        }
    }
}
