package com.ramchat.model

enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO
}

data class Message(
    val id: String,
    val sender: String,
    val type: MessageType,
    val textContent: String? = null,
    val mediaBytes: ByteArray? = null, // Purely in RAM
    val timestamp: Long = System.currentTimeMillis(),
    val isFromMe: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Message

        if (id != other.id) return false
        if (sender != other.sender) return false
        if (type != other.type) return false
        if (textContent != other.textContent) return false
        if (mediaBytes != null) {
            if (other.mediaBytes == null) return false
            if (!mediaBytes.contentEquals(other.mediaBytes)) return false
        } else if (other.mediaBytes != null) return false
        if (timestamp != other.timestamp) return false
        if (isFromMe != other.isFromMe) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + sender.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + (textContent?.hashCode() ?: 0)
        result = 31 * result + (mediaBytes?.contentHashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + isFromMe.hashCode()
        return result
    }
}

data class MqttPayload(
    val id: String,
    val sender: String,
    val type: String, // "TEXT" or "CHUNK"
    val timestamp: Long = System.currentTimeMillis(),
    
    // Used for TEXT type (contains Base64-encoded encrypted text)
    val encryptedText: String? = null,
    
    // Used for CHUNK type (represents a slice of a larger encrypted media file)
    val fileId: String? = null,
    val chunkIndex: Int = 0,
    val totalChunks: Int = 1,
    val encryptedChunkData: String? = null, // Base64 encoded encrypted chunk
    val fileType: String? = null // "IMAGE" or "VIDEO"
)
