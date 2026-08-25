package com.example.callruleblocker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import java.util.UUID

enum class LinkTab { CHATS, UPDATES, COMMUNITIES, CALLS, YOU }

enum class MessageType { 
    TEXT, IMAGE, VIDEO, AUDIO, VOICE, DOC, LINK, CONTACT, LOCATION, LIVE_LOCATION, 
    GIF, STICKER, POLL, EVENT, SYSTEM, VIEW_ONCE, CALL 
}

enum class MessageStatus { SENDING, SENT, DELIVERED, READ, FAILED }

data class RealUser(
    val uid: String, 
    val userId: String = "", // Custom human-readable ID
    val name: String, 
    val email: String, 
    val phone: String = "", 
    val isOnline: Boolean = false, 
    val lastSeen: Long? = null, 
    val photoUrl: String? = null,
    val followedChannels: List<String> = emptyList(),
    // Discovery Base
    val district: String? = null,
    val pincode: String? = null,
    val state: String? = null,
    val country: String? = null
)

data class UniversalMessage(
    val id: String = UUID.randomUUID().toString(),
    val clientMessageId: String = UUID.randomUUID().toString(),
    val chatId: String = "",
    val senderId: String = "",
    val text: String = "",
    val caption: String? = null,
    val messageType: MessageType = MessageType.TEXT,
    val time: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENDING,
    val sentAt: Long? = null,
    val deliveredAt: Long? = null,
    val readAt: Long? = null,
    val replyToMessageId: String? = null,
    val replyToText: String? = null,
    val isForwarded: Boolean = false,
    val editedAt: Long? = null,
    val reactions: Map<String, String> = emptyMap(), // userId -> emoji
    val isPinned: Boolean = false,
    val isStarred: Boolean = false,
    val metadata: String? = null,
    val isMine: Boolean = false,
    val isDeleted: Boolean = false,
    val deleteForEveryone: Boolean = false,
    val deletedFor: List<String> = emptyList(), // list of userIds
    val liveLocationExpiry: Long? = null,
    val isRead: Boolean = false,
    // Professional Attachment Metadata
    val fileName: String? = null,
    val fileSize: Long = 0,
    val mimeType: String? = null,
    val thumbnailUrl: String? = null,
    val durationMs: Long = 0,
    val fileLocalPath: String? = null, // For sender-side local cache tracking
    // Poll Data
    val pollQuestion: String? = null,
    val pollOptions: List<String> = emptyList(),
    val pollVotes: Map<String, List<String>> = emptyMap(), // optionIndex -> list of userIds
    val allowMultipleAnswers: Boolean = false,
    // Event Data
    val eventTitle: String? = null,
    val eventDescription: String? = null,
    val eventStartAt: Long = 0,
    val eventLocation: String? = null,
    val eventRSVPs: Map<String, List<String>> = emptyMap(), // status -> list of userIds
    val interactionAttempts: Map<String, Int> = emptyMap(), // userId -> count
    val lastInteractionTime: Map<String, Long> = emptyMap(), // userId -> timestamp
    // Call Data
    val callId: String? = null,
    val callType: String? = null, // VOICE, VIDEO
    val callStatus: String? = null, // RINGING, CONNECTED, ENDED, MISSED, REJECTED
    val callDuration: Long = 0
)

data class ChatRowItem(
    val id: String, 
    val peerUid: String,
    val lastMessage: String, 
    val time: Long,
    val unreadCount: Int,
    val isPinned: Boolean,
    val isGroup: Boolean = false,
    val messageType: MessageType = MessageType.TEXT,
    val groupName: String? = null,
    val lastMessageStatus: MessageStatus = MessageStatus.SENT,
    val lastMessageMine: Boolean = false
)

data class UserStatus(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val mediaUrl: String,
    val caption: String? = null,
    val type: MessageType = MessageType.IMAGE,
    val timestamp: Long = System.currentTimeMillis(),
    val seenBy: List<String> = emptyList()
)

data class ShynaChannel(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val photoUrl: String? = null,
    val followersCount: Int = 0,
    val lastUpdateTime: Long = System.currentTimeMillis(),
    val lastMessage: String = ""
)

data class UserPrivacySettings(
    val lastSeen: String = "Everyone", // Everyone, My Contacts, Nobody
    val profilePhoto: String = "Everyone",
    val about: String = "Everyone",
    val groups: String = "Everyone",
    val status: String = "My Contacts",
    val readReceipts: Boolean = true,
    val disappearingMessages: Int = 0 // 0 = off, 24, 7, 90 (days)
)

data class UserStorageSettings(
    val mobileDataMedia: Set<String> = emptySet(), // photo, video, audio, doc
    val wifiMedia: Set<String> = setOf("photo", "video", "audio", "doc"),
    val roamingMedia: Set<String> = emptySet(),
    val saveToGallery: Boolean = true
)

data class CustomChatList(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val chatIds: List<String>
)

enum class MessageAction { REACT, REPLY, COPY, EDIT, FORWARD, STAR, PIN, INFO, DOWNLOAD, DELETE }

object PermissionEngine {
    fun canEdit(msg: UniversalMessage): Boolean {
        val fifteenMinutes = 15 * 60 * 1000L
        return msg.isMine && (System.currentTimeMillis() - msg.time < fifteenMinutes) && !msg.isDeleted
    }
    
    fun canDeleteForEveryone(msg: UniversalMessage): Boolean {
        val twoDays = 2 * 24 * 60 * 60 * 1000L
        return msg.isMine && (System.currentTimeMillis() - msg.time < twoDays) && !msg.isDeleted
    }
}

object MessageActionController {
    fun getAvailableActions(msg: UniversalMessage): List<MessageAction> {
        val actions = mutableListOf(MessageAction.REPLY, MessageAction.FORWARD, MessageAction.STAR)
        if (msg.messageType == MessageType.TEXT) actions.add(MessageAction.COPY)
        if (PermissionEngine.canEdit(msg)) actions.add(MessageAction.EDIT)
        actions.add(MessageAction.INFO)
        actions.add(MessageAction.DELETE)
        return actions
    }
}

// --- PREMIUM UNIVERSAL THEME SYSTEM ---
data class ShynaColors(
    val PrimaryBg: Color, val SurfaceBg: Color, val HeaderBg: Color,
    val IncomingBubble: Color, val OutgoingBubble: Color,
    val TextPrimary: Color, val TextSecondary: Color,
    val BrandGreen: Color, val DividerColor: Color,
    val SelectionOverlay: Color, val isDark: Boolean,
    val AuthAccent: Color
)

val ShynaDarkPalette = ShynaColors(
    PrimaryBg = Color(0xFF0A0A0A), SurfaceBg = Color(0xFF121B22), HeaderBg = Color(0xFF121B22),
    IncomingBubble = Color(0xFF202C33), OutgoingBubble = Color(0xFF005C4B),
    TextPrimary = Color(0xFFE9EDEF), TextSecondary = Color(0xFF8696A0),
    BrandGreen = Color(0xFFD4A017), DividerColor = Color(0xFF222D34),
    SelectionOverlay = Color(0xFFD4A017).copy(alpha = 0.2f), isDark = true,
    AuthAccent = Color(0xFF25D366)
)

val ShynaLightPalette = ShynaColors(
    PrimaryBg = Color(0xFFF7F7F7), SurfaceBg = Color(0xFFFFFFFF), HeaderBg = Color(0xFFF7F7F7),
    IncomingBubble = Color(0xFFFFFFFF), OutgoingBubble = Color(0xFFE7FFDB),
    TextPrimary = Color(0xFF000000), TextSecondary = Color(0xFF667781),
    BrandGreen = Color(0xFFD4A017), DividerColor = Color(0xFFEEEEEE),
    SelectionOverlay = Color(0xFFD4A017).copy(alpha = 0.1f), isDark = false,
    AuthAccent = Color(0xFFD35400)
)

val LocalShynaColors = staticCompositionLocalOf { ShynaDarkPalette }

@Composable
fun ShynaTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val darkTheme = if (mode == ThemeMode.SYSTEM) isSystemInDarkTheme() else mode == ThemeMode.DARK
    val colors = if (darkTheme) ShynaDarkPalette else ShynaLightPalette
    CompositionLocalProvider(LocalShynaColors provides colors) { content() }
}

object ShynaDesign {
    val colors: ShynaColors @Composable get() = LocalShynaColors.current
    @Composable fun premiumGradient() = Brush.verticalGradient(listOf(colors.HeaderBg, colors.PrimaryBg))
}

enum class ThemeMode { LIGHT, DARK, SYSTEM }
