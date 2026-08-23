package com.example.callruleblocker.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.callruleblocker.R
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TextMessageBubble(m: UniversalMessage) {
    Text(m.text, color = ShynaDesign.colors.TextPrimary, fontSize = 16.sp)
}

@Composable
fun ImageMessageBubble(m: UniversalMessage) {
    Column {
        SubcomposeAsyncImage(
            model = m.metadata,
            contentDescription = null,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth()
                .wrapContentHeight()
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = ShynaDesign.colors.BrandGreen)
                }
            }
        )
        if (!m.caption.isNullOrEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(m.caption, color = ShynaDesign.colors.TextPrimary, fontSize = 15.sp)
        }
    }
}

@Composable
fun VideoMessageBubble(m: UniversalMessage) {
    Column {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val thumbnailUrl = remember(m.metadata) {
                if (m.metadata?.startsWith("http") == true && m.metadata.contains("/video/upload/")) {
                    m.metadata.replace("/video/upload/", "/video/upload/w_500,h_500,c_fill,so_0/").replace(".mp4", ".jpg")
                } else {
                    m.metadata
                }
            }
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Surface(
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                color = Color.Black.copy(0.5f)
            ) {
                Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.padding(10.dp))
            }
            // Duration overlay
            val dur = remember(m.durationMs) {
                if (m.durationMs > 0) {
                    val sec = m.durationMs / 1000
                    String.format(Locale.getDefault(), "%d:%02d", sec / 60, sec % 60)
                } else "0:00"
            }
            Text(
                dur, 
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 4f))
            )
        }
        if (!m.caption.isNullOrEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(m.caption, color = ShynaDesign.colors.TextPrimary, fontSize = 15.sp)
        }
    }
}

@Composable
fun VoiceMessageBubble(m: UniversalMessage) {
    BuiltInAudioPlayer(m.metadata)
}

@Composable
fun AudioMessageBubble(m: UniversalMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ShynaDesign.colors.SurfaceBg)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.AudioFile, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.fileName ?: "Audio File", color = ShynaDesign.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatFileSize(m.fileSize), color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp)
        }
        IconButton(onClick = { /* Play */ }) {
            Icon(Icons.Default.PlayArrow, null, tint = ShynaDesign.colors.BrandGreen)
        }
    }
}

@Composable
fun LocationMessageBubble(m: UniversalMessage, isSelectionActive: Boolean = false) {
    val context = LocalContext.current
    val loc = m.metadata ?: "0,0"
    val parts = loc.split(",")
    val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
    val lon = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0

    Column(
        modifier = Modifier
            .width(250.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.LightGray)
            .clickable(enabled = !isSelectionActive) {
                val gmmIntentUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                context.startActivity(mapIntent)
            }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val mapUrl = "https://maps.googleapis.com/maps/api/staticmap?center=$lat,$lon&zoom=15&size=500x400&markers=color:red%7C$lat,$lon&key=YOUR_API_KEY"
            AsyncImage(
                model = mapUrl,
                contentDescription = "Map",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Icon(Icons.Default.LocationOn, null, tint = Color.Red, modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
fun LiveLocationMessageBubble(m: UniversalMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFE8F5E9))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.LocationOn, null, tint = Color(0xFF2E7D32))
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Live Location", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
            Text("Sharing until ${formatChatDate(m.liveLocationExpiry ?: 0)}", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun LinkMessageBubble(m: UniversalMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ShynaDesign.colors.SurfaceBg)
            .padding(12.dp)
    ) {
        Text(m.text, color = Color(0xFF00A5F4), textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
    }
}

@Composable
fun DocMessageBubble(m: UniversalMessage, isSelectionActive: Boolean = false, onMediaClick: (UniversalMessage) -> Unit = {}) {
    val mContext = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }

    val ext = remember(m.fileName) { m.fileName?.substringAfterLast(".", "")?.lowercase() ?: "" }
    val isImage = ext in listOf("jpg", "jpeg", "png", "webp", "gif")
    val isVideo = ext in listOf("mp4", "mov", "avi")

    fun openFile(file: File) {
        FileOpener.open(mContext, file, m.mimeType)
    }

    fun downloadAndOpen() {
        val url = m.metadata ?: return
        val rawName = m.fileName ?: "attachment_${System.currentTimeMillis()}"
        val sanitizedName = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        // Use message ID to prevent cache collisions between different files with same name
        val uniqueName = "${m.id.takeLast(6)}_$sanitizedName"
        val file = File(mContext.cacheDir, uniqueName)

        if (file.exists() && file.length() > 0) {
            openFile(file)
            return
        }

        isDownloading = true
        scope.launch(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                connection.connect()
                val length = connection.contentLength
                var totalBytesDownloaded = 0L
                connection.getInputStream().use { input ->
                    FileOutputStream(file).use { output ->
                        val data = ByteArray(4096)
                        var count: Int
                        while (input.read(data).also { count = it } != -1) {
                            totalBytesDownloaded += count
                            if (length > 0) downloadProgress = totalBytesDownloaded.toFloat() / length
                            output.write(data, 0, count)
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    isDownloading = false
                    com.example.callruleblocker.data.NetworkUsageTracker.track(mContext, "media", received = totalBytesDownloaded)
                    openFile(file)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isDownloading = false
                    Toast.makeText(mContext, "Download failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .width(250.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ShynaDesign.colors.SurfaceBg.copy(alpha = 0.5f))
            .clickable(enabled = !isSelectionActive) {
                if (isImage || isVideo) onMediaClick(m)
                else downloadAndOpen()
            }
            .padding(12.dp)
    ) {
        if (isImage || isVideo) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) {
                val thumbnailUrl = remember(m.metadata) {
                    if (m.metadata?.startsWith("http") == true && m.metadata.contains("/video/upload/")) {
                        m.metadata.replace("/video/upload/", "/video/upload/w_400,h_300,c_fill,so_0/").replace(".mp4", ".jpg")
                    } else {
                        m.metadata
                    }
                }
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (isVideo) Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = when {
                ext == "pdf" -> Icons.Default.PictureAsPdf
                ext in listOf("doc", "docx") -> Icons.Default.Description
                ext in listOf("xls", "xlsx") -> Icons.Default.TableChart
                ext == "apk" -> Icons.Default.Android
                else -> Icons.Default.InsertDriveFile
            }
            Icon(icon, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.fileName ?: "Document", color = ShynaDesign.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("${ext.uppercase()} • ${formatFileSize(m.fileSize)}", color = ShynaDesign.colors.TextSecondary, fontSize = 11.sp)
            }
            if (isDownloading) {
                CircularProgressIndicator(progress = { downloadProgress }, modifier = Modifier.size(24.dp), color = ShynaDesign.colors.BrandGreen, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Download, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun ContactMessageBubble(m: UniversalMessage) {
    val parts = m.metadata?.split("|") ?: listOf("Contact", "")
    val name = parts.getOrNull(0) ?: "Contact"
    val phone = parts.getOrNull(1) ?: ""

    Column(
        modifier = Modifier
            .width(230.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ShynaDesign.colors.SurfaceBg)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, modifier = Modifier.size(40.dp), color = ShynaDesign.colors.BrandGreen.copy(alpha = 0.1f)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(name, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(phone, fontSize = 12.sp, color = ShynaDesign.colors.TextSecondary)
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = ShynaDesign.colors.DividerColor)
        TextButton(
            onClick = { /* Add to contacts or message */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Message", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PollMessageBubble(m: UniversalMessage, userId: String, onVote: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ShynaDesign.colors.SurfaceBg)
            .padding(12.dp)
    ) {
        Text(m.pollQuestion ?: "Poll", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(if(m.allowMultipleAnswers) "Select one or more" else "Select one", color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))

        val totalVotes = m.pollVotes.values.sumOf { it.size }.coerceAtLeast(1)
        val attempts = m.interactionAttempts[userId] ?: 0
        val isBlocked = attempts >= 2
        
        m.pollOptions.forEachIndexed { index, option ->
            val voters = m.pollVotes[index.toString()] ?: emptyList()
            val hasVoted = voters.contains(userId)
            val percentage = (voters.size.toFloat() / totalVotes)
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isBlocked) { onVote(index) }
                    .padding(vertical = 6.dp)
                    .graphicsLayer { alpha = if (isBlocked && !hasVoted) 0.6f else 1.0f }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = hasVoted, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = ShynaDesign.colors.BrandGreen))
                    Spacer(Modifier.width(8.dp))
                    Text(option, color = ShynaDesign.colors.TextPrimary, modifier = Modifier.weight(1f))
                    Text(voters.size.toString(), color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { percentage },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = if(hasVoted) ShynaDesign.colors.BrandGreen else Color.Gray.copy(alpha = 0.3f),
                    trackColor = Color.Gray.copy(alpha = 0.1f)
                )
            }
        }
    }
}

@Composable
fun EventMessageBubble(m: UniversalMessage, userId: String, onRSVP: () -> Unit) {
    val goingCount = m.eventRSVPs["going"]?.size ?: 0
    val maybeCount = m.eventRSVPs["maybe"]?.size ?: 0
    val currentUserStatus = when {
        m.eventRSVPs["going"]?.contains(userId) == true -> "Going"
        m.eventRSVPs["maybe"]?.contains(userId) == true -> "Maybe"
        m.eventRSVPs["not_going"]?.contains(userId) == true -> "Not Going"
        else -> null
    }
    val attempts = m.interactionAttempts[userId] ?: 0
    val isBlocked = attempts >= 2

    Column(
        modifier = Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFFF8E1))
            .clickable { onRSVP() }
            .padding(12.dp)
            .graphicsLayer { alpha = if (isBlocked) 0.8f else 1.0f }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Event, null, tint = Color(0xFFFBC02D), modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(m.eventTitle ?: "Event", fontWeight = FontWeight.Bold, color = Color.Black)
                val dateStr = remember(m.eventStartAt) { SimpleDateFormat("dd MMM, yyyy • h:mm a", Locale.getDefault()).format(Date(m.eventStartAt)) }
                Text(dateStr, fontSize = 12.sp, color = Color.DarkGray)
            }
        }
        if (!m.eventDescription.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(m.eventDescription, color = Color.DarkGray, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (!m.eventLocation.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(m.eventLocation, color = Color.Gray, fontSize = 12.sp)
            }
        }
        
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = Color.Black.copy(alpha = 0.1f))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("Going: $goingCount", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Maybe: $maybeCount", color = Color.Black, fontSize = 12.sp)
        }
        if (currentUserStatus != null) {
            Text("Your status: $currentUserStatus", color = Color(0xFF2E7D32), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun CallMessageBubble(m: UniversalMessage, onCallAgain: () -> Unit) {
    val isVideo = m.callType == "VIDEO"
    val status = m.callStatus ?: "ENDED"
    
    val icon = when (status) {
        "MISSED" -> Icons.Default.PhoneMissed
        "REJECTED" -> Icons.Default.Block
        else -> if (isVideo) Icons.Default.Videocam else Icons.Default.Call
    }
    
    val color = if (status == "MISSED") Color.Red else ShynaDesign.colors.BrandGreen
    
    val text = when (status) {
        "MISSED" -> if (isVideo) "Missed video call" else "Missed audio call"
        "REJECTED" -> if (isVideo) "Video call rejected" else "Audio call rejected"
        else -> {
            val duration = formatCallDuration(m.callDuration)
            if (isVideo) "Video call · $duration" else "Audio call · $duration"
        }
    }

    Surface(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCallAgain() },
        color = ShynaDesign.colors.SurfaceBg,
        border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(text, color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Tap to call back", color = ShynaDesign.colors.TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

private fun formatCallDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%d:%02d", m, s)
}

@Composable
private fun BuiltInAudioPlayer(url: String?) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val mediaPlayer = remember { MediaPlayer() }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose { mediaPlayer.release() }
    }

    Row(
        modifier = Modifier
            .width(250.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(ShynaDesign.colors.SurfaceBg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            if (isPlaying) {
                mediaPlayer.pause()
                isPlaying = false
            } else {
                if (progress == 0f) {
                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(url)
                    mediaPlayer.prepareAsync()
                    mediaPlayer.setOnPreparedListener { 
                        it.start()
                        isPlaying = true
                        scope.launch {
                            while (isPlaying && isActive) {
                                progress = mediaPlayer.currentPosition.toFloat() / mediaPlayer.duration
                                delay(100)
                            }
                        }
                    }
                    mediaPlayer.setOnCompletionListener { isPlaying = false; progress = 0f }
                } else {
                    mediaPlayer.start()
                    isPlaying = true
                }
            }
        }) {
            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = ShynaDesign.colors.BrandGreen)
        }
        
        Slider(
            value = progress,
            onValueChange = { /* seek */ },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = ShynaDesign.colors.BrandGreen, activeTrackColor = ShynaDesign.colors.BrandGreen)
        )
        
        Icon(Icons.Default.Mic, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(16.dp).padding(end = 4.dp))
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatChatDate(time: Long): String {
    if (time == 0L) return ""
    return SimpleDateFormat("dd/MM/yy, HH:mm", Locale.getDefault()).format(Date(time))
}

object FileOpener {
    fun open(context: Context, file: File, mimeType: String?) {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }
}
