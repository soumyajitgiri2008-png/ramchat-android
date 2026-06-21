package com.ramchat.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxWidth 
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.ramchat.model.Message
import com.ramchat.model.MessageType
import com.ramchat.ui.theme.*
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    displayName: String,
    roomId: String,
    messagesList: List<Message>,
    connectionStatus: Boolean,
    connectionError: String?,
    onSendMessage: (String) -> Unit,
    onSendMedia: (ByteArray, Boolean) -> Unit,
    onDisconnect: () -> Unit,
    onClearChat: () -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    
    // User configurable limits
    var maxMessagesLimit by remember { mutableStateOf(50) }
    var maxRamLimitMB by remember { mutableStateOf(20) } // Default 20MB
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Media Dialog Viewer
    var activeFullscreenImageMessage by remember { mutableStateOf<Message?>(null) }
    
    // Photo Picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            processSelectedMedia(context, uri, isVideo = false) { bytes ->
                onSendMedia(bytes, false)
            }
        }
    }
    
    // Video Picker
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            processSelectedMedia(context, uri, isVideo = true) { bytes ->
                onSendMedia(bytes, true)
            }
        }
    }

    // Dynamic RAM calculation
    val currentBytesUsed = remember(messagesList) {
        var bytes = 0L
        for (m in messagesList) {
            bytes += (m.textContent?.length ?: 0) * 2L
            bytes += m.mediaBytes?.size ?: 0
        }
        bytes
    }
    val ramUsedMB = currentBytesUsed.toDouble() / (1024.0 * 1024.0)
    val ramPercentage = (ramUsedMB / maxRamLimitMB).toFloat().coerceIn(0f, 1f)

    // Check and enforce limits reactively
    LaunchedEffect(messagesList, maxMessagesLimit, maxRamLimitMB) {
        // We trigger a callback to MainActivity/caller to prune messages if limits are exceeded
        var listSize = messagesList.size
        var totalBytes = currentBytesUsed
        if (listSize > maxMessagesLimit || totalBytes > maxRamLimitMB * 1024 * 1024) {
            onClearChat() // Trigger a clean of state to enforce limits
            Toast.makeText(context, "RAM Limit exceeded: Chat history auto-trimmed.", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Connection indicator
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(if (connectionStatus) Color(0xFF10B981) else Color(0xFFEF4444), CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Room: $roomId",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (connectionStatus) "Connected as $displayName" else "Connecting...",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                // Settings icon
                IconButton(onClick = { showSettingsDialog = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextPrimary)
                }

                // Leave / Purge Button
                IconButton(
                    onClick = {
                        onDisconnect()
                        Toast.makeText(context, "RAM Wiped. Room Left.", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = "Leave", tint = PrimaryGradientEnd)
                }
            }

            // Connection Error Alert
            AnimatedVisibility(visible = connectionError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF7F1D1D))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = connectionError ?: "",
                        color = Color.White,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // RAM and Limit Meter Bar (Glassmorphic)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = BackgroundCard)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "RAM Usage: ${String.format("%.2f", ramUsedMB)} MB / $maxRamLimitMB MB",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "${messagesList.size} / $maxMessagesLimit Messages",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        // Progress bar with primary gradient
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFFFFF))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(ramPercentage)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(PrimaryGradientStart, PrimaryGradientEnd)
                                        )
                                    )
                            )
                        }
                    }
                }
            }

            // Chat Messages List
            val listState = rememberLazyListState()
            LaunchedEffect(messagesList.size) {
                if (messagesList.isNotEmpty()) {
                    listState.animateScrollToItem(messagesList.size - 1)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messagesList, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onImageClicked = { activeFullscreenImageMessage = it }
                    )
                }
            }

            // Bottom Input Section (Glassmorphic)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderColor, RoundedCornerShape(0.dp)),
                colors = CardDefaults.cardColors(containerColor = BackgroundCard)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Attachment Plus Button
                    var showAttachmentMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showAttachmentMenu = true }) {
                            Icon(Icons.Default.AddCircle, contentDescription = "Attach", tint = TextPrimary)
                        }
                        DropdownMenu(
                            expanded = showAttachmentMenu,
                            onDismissRequest = { showAttachmentMenu = false },
                            modifier = Modifier.background(BackgroundDeep).border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Send Image", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = PrimaryGradientStart) },
                                onClick = {
                                    showAttachmentMenu = false
                                    imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Send Video", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = PrimaryGradientEnd) },
                                onClick = {
                                    showAttachmentMenu = false
                                    videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                }
                            )
                        }
                    }

                    // Input Text Field
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Type message... (RAM encrypted)", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = BackgroundCardDark,
                            unfocusedContainerColor = BackgroundCardDark
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        maxLines = 4
                    )

                    // Send Button
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendMessage(inputText)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank()) PrimaryGradientStart else TextSecondary
                        )
                    }
                }
            }
        }

        // Fullscreen Image Dialog
        if (activeFullscreenImageMessage != null) {
            Dialog(
                onDismissRequest = { activeFullscreenImageMessage = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.9f))
                        .clickable { activeFullscreenImageMessage = null },
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = remember(activeFullscreenImageMessage) {
                        activeFullscreenImageMessage?.mediaBytes?.let { bytes ->
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }
                    }
                    bitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.8f),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }

        // Settings / Thresholds Dialog
        if (showSettingsDialog) {
            Dialog(onDismissRequest = { showSettingsDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderColor, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = BackgroundDeep)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "RAM Storage Settings",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Max Message Selector
                        Text(
                            text = "Max Messages in RAM: $maxMessagesLimit",
                            fontSize = 14.sp,
                            color = TextPrimary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                        Slider(
                            value = maxMessagesLimit.toFloat(),
                            onValueChange = { maxMessagesLimit = it.toInt() },
                            valueRange = 10f..200f,
                            steps = 19,
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryGradientStart,
                                activeTrackColor = PrimaryGradientStart
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Max RAM Size Selector
                        Text(
                            text = "Max Media RAM Limit: $maxRamLimitMB MB",
                            fontSize = 14.sp,
                            color = TextPrimary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                        Slider(
                            value = maxRamLimitMB.toFloat(),
                            onValueChange = { maxRamLimitMB = it.toInt() },
                            valueRange = 5f..100f,
                            steps = 19,
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryGradientEnd,
                                activeTrackColor = PrimaryGradientEnd
                            )
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { showSettingsDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(PrimaryGradientStart, PrimaryGradientEnd)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Apply Settings", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    onImageClicked: (Message) -> Unit
) {
    val alignment = if (message.isFromMe) Alignment.End else Alignment.Start
    val bubbleBg = if (message.isFromMe) {
        Brush.horizontalGradient(listOf(PrimaryGradientStart, PrimaryGradientEnd))
    } else {
        Brush.horizontalGradient(listOf(PeerMessageBg, PeerMessageBg))
    }
    
    val shape = if (message.isFromMe) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        // Sender Alias Label (only if it's from peer)
        if (!message.isFromMe) {
            Text(
                text = message.sender,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }

        // Message bubble content
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(bubbleBg, shape)
                .border(1.dp, BorderColor.copy(alpha = 0.5f), shape)
                .padding(12.dp)
        ) {
            Column {
                when (message.type) {
                    MessageType.TEXT -> {
                        Text(
                            text = message.textContent ?: "",
                            color = Color.White,
                            fontSize = 15.sp
                        )
                    }
                    MessageType.IMAGE -> {
                        val bitmap = remember(message.mediaBytes) {
                            message.mediaBytes?.let { bytes ->
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Decrypted RAM Image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onImageClicked(message) },
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text("Corrupted image in RAM", color = Color.Red, fontSize = 13.sp)
                        }
                    }
                    MessageType.VIDEO -> {
                        val mediaBytes = message.mediaBytes
                        if (mediaBytes != null) {
                            MemoryVideoPlayer(
                                mediaBytes = mediaBytes,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Text("Corrupted video in RAM", color = Color.Red, fontSize = 13.sp)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Timestamp
                val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                Text(
                    text = formatter.format(Date(message.timestamp)),
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

/**
 * ExoPlayer playing raw video data directly from RAM using ByteArrayDataSource.
 * Zero footprint on Disk.
 */
@Composable
fun MemoryVideoPlayer(mediaBytes: ByteArray, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    
    val exoPlayer = remember(mediaBytes) {
        ExoPlayer.Builder(context).build().apply {
            val dataSource = ByteArrayDataSource(mediaBytes)
            val factory = DataSource.Factory { dataSource }
            val mediaSource = ProgressiveMediaSource.Factory(factory)
                .createMediaSource(MediaItem.fromUri("ram://video.mp4")) // Dummy URI
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = modifier
    )
}

/**
 * Helper function to read file bytes from content URI into RAM with 10MB limit check.
 */
private fun processSelectedMedia(
    context: Context,
    uri: Uri,
    isVideo: Boolean,
    onBytesProcessed: (ByteArray) -> Unit
) {
    try {
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(uri) ?: return
        val byteBuffer = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var len: Int
        var totalBytesRead = 0
        val maxSizeBytes = 10 * 1024 * 1024 // 10MB limit

        while (inputStream.read(buffer).also { len = it } != -1) {
            totalBytesRead += len
            if (totalBytesRead > maxSizeBytes) {
                Toast.makeText(context, "File size exceeds 10MB limit. Select a smaller file.", Toast.LENGTH_LONG).show()
                inputStream.close()
                return
            }
            byteBuffer.write(buffer, 0, len)
        }
        
        inputStream.close()
        onBytesProcessed(byteBuffer.toByteArray())
    } catch (e: Exception) {
        Toast.makeText(context, "Error reading media: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
