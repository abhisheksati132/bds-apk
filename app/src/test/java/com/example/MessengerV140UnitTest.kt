package com.example

import com.example.data.crypto.CryptoHelper
import com.example.data.model.MessageEntity
import com.example.data.model.MessageStatus
import com.example.data.model.MessageType
import com.example.ui.screens.formatFileSize
import org.junit.Assert.*
import org.junit.Test

class MessengerV140UnitTest {

    @Test
    fun testMessageEntity_withV140Fields() {
        val message = MessageEntity(
            id = 100L,
            conversationId = 1L,
            senderId = "me",
            senderName = "You",
            text = "quarterly_report.pdf",
            timestamp = System.currentTimeMillis(),
            isMe = true,
            status = MessageStatus.SENT,
            type = MessageType.DOCUMENT,
            isPinned = true,
            isEdited = false,
            fileName = "quarterly_report.pdf",
            fileSizeBytes = 2_450_000L
        )

        assertTrue(message.isPinned)
        assertFalse(message.isEdited)
        assertEquals(MessageType.DOCUMENT, message.type)
        assertEquals("quarterly_report.pdf", message.fileName)
        assertEquals(2_450_000L, message.fileSizeBytes)
    }

    @Test
    fun testEditedMessage_encryptionAndDecryption() {
        val originalText = "Meet me at 5 PM"
        val cipher = CryptoHelper.encrypt(originalText)
        val decrypted = CryptoHelper.decrypt(cipher)
        assertEquals(originalText, decrypted)

        // Edit message
        val editedText = "Meet me at 6:30 PM (Updated)"
        val editedCipher = CryptoHelper.encrypt(editedText)
        val editedDecrypted = CryptoHelper.decrypt(editedCipher)
        assertEquals(editedText, editedDecrypted)
        assertNotEquals(cipher, editedCipher)
    }

    @Test
    fun testFormatFileSize() {
        assertEquals("0 B", formatFileSize(0L))
        assertEquals("500.0 B", formatFileSize(500L))
        assertEquals("1.0 KB", formatFileSize(1024L))
        assertEquals("2.5 MB", formatFileSize(2_621_440L))
        assertEquals("1.0 GB", formatFileSize(1_073_741_824L))
    }

    @Test
    fun testAudioPlaybackSpeedCycle() {
        var currentSpeed = 1.0f

        fun getNextSpeed(speed: Float): Float {
            return when {
                speed < 1.25f -> 1.5f
                speed < 1.75f -> 2.0f
                else -> 1.0f
            }
        }

        currentSpeed = getNextSpeed(currentSpeed)
        assertEquals(1.5f, currentSpeed, 0.01f)

        currentSpeed = getNextSpeed(currentSpeed)
        assertEquals(2.0f, currentSpeed, 0.01f)

        currentSpeed = getNextSpeed(currentSpeed)
        assertEquals(1.0f, currentSpeed, 0.01f)
    }
}
