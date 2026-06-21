package com.ramchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.ramchat.crypto.CryptoHelper
import com.ramchat.model.Message
import com.ramchat.network.MqttHelper
import com.ramchat.ui.ChatScreen
import com.ramchat.ui.LoginScreen
import com.ramchat.ui.theme.RAMChatTheme
import javax.crypto.SecretKey

enum class AppScreen {
    LOGIN,
    CHAT
}

class MainActivity : ComponentActivity() {
    
    // Session state (stored purely in RAM)
    private var currentScreen = mutableStateOf(AppScreen.LOGIN)
    private var displayName = mutableStateOf("")
    private var roomId = mutableStateOf("")
    private var secretKey: SecretKey? = null
    
    // Chat state (stored purely in RAM)
    private val messagesList = mutableStateListOf<Message>()
    private var connectionStatus = mutableStateOf(false)
    private var connectionError = mutableStateOf<String?>(null)
    
    // Network helper
    private var mqttHelper: MqttHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            RAMChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentScreen.value) {
                        AppScreen.LOGIN -> {
                            LoginScreen(
                                onJoinClicked = { name, room, keyInput ->
                                    displayName.value = name
                                    roomId.value = room
                                    joinRoom(keyInput)
                                }
                            )
                        }
                        AppScreen.CHAT -> {
                            ChatScreen(
                                displayName = displayName.value,
                                roomId = roomId.value,
                                messagesList = messagesList,
                                connectionStatus = connectionStatus.value,
                                connectionError = connectionError.value,
                                onSendMessage = { text ->
                                    mqttHelper?.sendTextMessage(text)
                                },
                                onSendMedia = { bytes, isVideo ->
                                    mqttHelper?.sendMediaMessage(bytes, isVideo)
                                },
                                onDisconnect = {
                                    leaveRoom()
                                },
                                onClearChat = {
                                    pruneMessages()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun joinRoom(password: String) {
        // Derive cryptographic key in RAM
        val key = CryptoHelper.deriveKey(password)
        secretKey = key

        // Initialize MQTT Client
        mqttHelper = MqttHelper(
            displayName = displayName.value,
            roomId = roomId.value,
            secretKey = key,
            onMessageReceived = { message ->
                // Add to list and enforce local RAM limits
                messagesList.add(message)
                pruneMessages()
            },
            onConnectionStatusChanged = { isConnected, error ->
                connectionStatus.value = isConnected
                connectionError.value = error
            }
        )

        mqttHelper?.connect()
        currentScreen.value = AppScreen.CHAT
    }

    private fun leaveRoom() {
        // Disconnect network helper
        mqttHelper?.disconnect()
        mqttHelper = null
        
        // Wipe all state from RAM
        displayName.value = ""
        roomId.value = ""
        secretKey = null
        messagesList.clear()
        connectionStatus.value = false
        connectionError.value = null
        
        // Force transition to login
        currentScreen.value = AppScreen.LOGIN
    }

    private fun pruneMessages() {
        // Pruning limits: Max 50 messages, Max 20MB of text/media payload size
        val maxMessages = 50
        val maxBytes = 20 * 1024 * 1024L // 20 MB

        while (messagesList.size > maxMessages || calculateTotalBytes() > maxBytes) {
            if (messagesList.isNotEmpty()) {
                messagesList.removeAt(0)
            } else {
                break
            }
        }
    }

    private fun calculateTotalBytes(): Long {
        var bytes = 0L
        for (m in messagesList) {
            bytes += (m.textContent?.length ?: 0) * 2L
            bytes += m.mediaBytes?.size ?: 0
        }
        return bytes
    }

    override fun onDestroy() {
        super.onDestroy()
        // Wipes all data instantly if Android kills the process or activity
        leaveRoom()
    }
}
