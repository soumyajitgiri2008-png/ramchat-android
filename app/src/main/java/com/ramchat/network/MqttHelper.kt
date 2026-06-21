package com.ramchat.network

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.ramchat.crypto.CryptoHelper
import com.ramchat.model.Message
import com.ramchat.model.MessageType
import com.ramchat.model.MqttPayload
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey

class MqttHelper(
    private val serverUri: String = "tcp://broker.hivemq.com:1883",
    private val displayName: String,
    private val roomId: String,
    private val secretKey: SecretKey,
    private val onMessageReceived: (Message) -> Unit,
    private val onConnectionStatusChanged: (Boolean, String?) -> Unit
) {
    private val tag = "MqttHelper"
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var mqttClient: MqttClient? = null
    private val clientId = "RAMChat_" + UUID.randomUUID().toString().substring(0, 8)
    private val topic = "ramchat/rooms/" + hashRoomId(roomId)

    // In-memory buffer for assembling chunked media files. Maps fileId -> (chunkIndex -> chunkBytes)
    private val fileChunksBuffer = ConcurrentHashMap<String, TreeMap<Int, ByteArray>>()
    // Keeps track of the total number of chunks expected for each fileId
    private val fileChunksExpected = ConcurrentHashMap<String, Int>()

    companion object {
        // 90 KB chunk size (well under public broker limits of 256KB-1MB)
        private const val CHUNK_SIZE = 90 * 1024
        
        fun hashRoomId(roomId: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(roomId.trim().lowercase().toByteArray(Charsets.UTF_8))
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
    }

    fun connect() {
        try {
            mqttClient = MqttClient(serverUri, clientId, MemoryPersistence())
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 15
                keepAliveInterval = 60
                isAutomaticReconnect = true
            }

            mqttClient?.setCallback(object : MqttCallbackExtended {
                override fun connectionLost(cause: Throwable?) {
                    Log.d(tag, "Connection lost: ${cause?.message}")
                    postStatus(false, cause?.message ?: "Connection lost")
                }

                override fun messageArrived(topicReceived: String?, mqttMessage: MqttMessage?) {
                    if (topicReceived != topic || mqttMessage == null) return
                    handleIncomingMqttMessage(mqttMessage)
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}

                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Log.d(tag, "Connect complete. Reconnect: $reconnect")
                    subscribeToTopic()
                    postStatus(true, null)
                }
            })

            Log.d(tag, "Connecting to broker $serverUri...")
            mqttClient?.connect(options)
        } catch (e: Exception) {
            Log.e(tag, "Connection failed", e)
            postStatus(false, e.message ?: "Failed to connect")
        }
    }

    private fun subscribeToTopic() {
        try {
            mqttClient?.subscribe(topic, 1)
            Log.d(tag, "Subscribed to topic: $topic")
        } catch (e: Exception) {
            Log.e(tag, "Subscription failed", e)
        }
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect()
            mqttClient?.close()
            Log.d(tag, "Disconnected successfully")
        } catch (e: Exception) {
            Log.e(tag, "Disconnect error", e)
        } finally {
            fileChunksBuffer.clear()
            fileChunksExpected.clear()
        }
    }

    /**
     * Encrypts and publishes a plain text message.
     */
    fun sendTextMessage(text: String) {
        if (mqttClient?.isConnected != true) return
        
        try {
            val encryptedText = CryptoHelper.encryptText(text, secretKey)
            val payload = MqttPayload(
                id = UUID.randomUUID().toString(),
                sender = displayName,
                type = "TEXT",
                encryptedText = encryptedText
            )
            publishJson(payload)
        } catch (e: Exception) {
            Log.e(tag, "Error sending text message", e)
        }
    }

    /**
     * Encrypts, chunks, and publishes media files (images or videos).
     */
    fun sendMediaMessage(mediaBytes: ByteArray, isVideo: Boolean) {
        if (mqttClient?.isConnected != true) return
        
        // Run in background thread to avoid blocking UI during encryption and chunking
        Thread {
            try {
                val encryptedBytes = CryptoHelper.encryptBytes(mediaBytes, secretKey)
                val totalSize = encryptedBytes.size
                val fileId = UUID.randomUUID().toString()
                
                val numChunks = if (totalSize % CHUNK_SIZE == 0) {
                    totalSize / CHUNK_SIZE
                } else {
                    (totalSize / CHUNK_SIZE) + 1
                }

                val fileType = if (isVideo) "VIDEO" else "IMAGE"
                Log.d(tag, "Sending $fileType fileId: $fileId, total size: $totalSize bytes, chunks: $numChunks")

                for (i in 0 until numChunks) {
                    val offset = i * CHUNK_SIZE
                    val length = kotlin.math.min(CHUNK_SIZE, totalSize - offset)
                    val chunk = ByteArray(length)
                    System.arraycopy(encryptedBytes, offset, chunk, 0, length)
                    
                    val chunkDataBase64 = Base64.encodeToString(chunk, Base64.NO_WRAP)
                    
                    val payload = MqttPayload(
                        id = UUID.randomUUID().toString(),
                        sender = displayName,
                        type = "CHUNK",
                        fileId = fileId,
                        chunkIndex = i,
                        totalChunks = numChunks,
                        encryptedChunkData = chunkDataBase64,
                        fileType = fileType
                    )
                    
                    publishJson(payload)
                    
                    // Small delay to prevent flooding the public broker and dropping packets
                    Thread.sleep(80)
                }
                Log.d(tag, "Finished sending all chunks for fileId: $fileId")
            } catch (e: Exception) {
                Log.e(tag, "Error sending media message", e)
            }
        }.start()
    }

    private fun publishJson(payload: MqttPayload) {
        try {
            val jsonString = gson.toJson(payload)
            val message = MqttMessage(jsonString.toByteArray(Charsets.UTF_8)).apply {
                qos = 1
                isRetained = false
            }
            mqttClient?.publish(topic, message)
        } catch (e: Exception) {
            Log.e(tag, "MQTT Publish failed", e)
        }
    }

    private fun handleIncomingMqttMessage(mqttMessage: MqttMessage) {
        try {
            val jsonString = String(mqttMessage.payload, Charsets.UTF_8)
            val payload = gson.fromJson(jsonString, MqttPayload::class.java) ?: return

            if (payload.type == "TEXT") {
                val decryptedText = CryptoHelper.decryptText(payload.encryptedText ?: "", secretKey)
                val isFromMe = payload.sender == displayName
                
                val message = Message(
                    id = payload.id,
                    sender = payload.sender,
                    type = MessageType.TEXT,
                    textContent = decryptedText,
                    timestamp = payload.timestamp,
                    isFromMe = isFromMe
                )
                postMessage(message)
            } else if (payload.type == "CHUNK") {
                val fileId = payload.fileId ?: return
                val chunkIndex = payload.chunkIndex
                val totalChunks = payload.totalChunks
                val encryptedChunkDataBase64 = payload.encryptedChunkData ?: return
                val fileType = payload.fileType ?: "IMAGE"
                
                // Initialize tree map and expected chunk count
                fileChunksExpected.putIfAbsent(fileId, totalChunks)
                val treeMap = fileChunksBuffer.putIfAbsent(fileId, TreeMap()) ?: fileChunksBuffer[fileId]!!

                val chunkBytes = Base64.decode(encryptedChunkDataBase64, Base64.NO_WRAP)
                synchronized(treeMap) {
                    treeMap[chunkIndex] = chunkBytes
                    
                    Log.d(tag, "Received chunk $chunkIndex/$totalChunks for fileId $fileId")

                    if (treeMap.size == totalChunks) {
                        Log.d(tag, "All chunks received for fileId $fileId. Reassembling and decrypting...")
                        
                        // Reassemble
                        var totalBytesSize = 0
                        for (bytes in treeMap.values) {
                            totalBytesSize += bytes.size
                        }
                        
                        val encryptedFileBytes = ByteArray(totalBytesSize)
                        var currentOffset = 0
                        for (bytes in treeMap.values) {
                            System.arraycopy(bytes, 0, encryptedFileBytes, currentOffset, bytes.size)
                            currentOffset += bytes.size
                        }

                        // Decrypt file
                        try {
                            val decryptedMediaBytes = CryptoHelper.decryptBytes(encryptedFileBytes, secretKey)
                            val isFromMe = payload.sender == displayName
                            
                            val message = Message(
                                id = fileId,
                                sender = payload.sender,
                                type = if (fileType == "VIDEO") MessageType.VIDEO else MessageType.IMAGE,
                                mediaBytes = decryptedMediaBytes,
                                timestamp = payload.timestamp,
                                isFromMe = isFromMe
                            )
                            
                            postMessage(message)
                        } catch (decryptEx: Exception) {
                            Log.e(tag, "Failed to decrypt media fileId: $fileId", decryptEx)
                        } finally {
                            // Clear buffers immediately to release RAM
                            fileChunksBuffer.remove(fileId)
                            fileChunksExpected.remove(fileId)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error handling incoming MQTT message", e)
        }
    }

    private fun postMessage(message: Message) {
        mainHandler.post {
            onMessageReceived(message)
        }
    }

    private fun postStatus(isConnected: Boolean, errorMessage: String?) {
        mainHandler.post {
            onConnectionStatusChanged(isConnected, errorMessage)
        }
    }
}
