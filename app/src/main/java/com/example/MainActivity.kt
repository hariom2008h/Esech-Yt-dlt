package com.example

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import com.yausername.ffmpeg.FFmpeg

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            YoutubeDL.getInstance().init(applicationContext)
            FFmpeg.getInstance().init(applicationContext)
        } catch (e: Throwable) {
            Log.e("MainActivity", "Failed to initialize YoutubeDL", e)
        }

        var sharedText = ""
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DownloaderScreen(
                        initialUrl = sharedText,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderScreen(initialUrl: String, modifier: Modifier = Modifier) {
    var url by remember { mutableStateOf(initialUrl) }
    var step by remember { mutableIntStateOf(0) } // 0: Input, 1: Preview, 2: Downloading
    var isAnalyzing by remember { mutableStateOf(false) }
    
    var platform by remember { mutableStateOf("") }
    var thumbnailUrl by remember { mutableStateOf<String?>(null) }
    var videoTitle by remember { mutableStateOf("") }
    
    var selectedFormat by remember { mutableStateOf("Video") }
    var selectedExtension by remember { mutableStateOf("MP4") }
    var selectedQuality by remember { mutableStateOf("1080p") }
    
    var expandedFormat by remember { mutableStateOf(false) }
    var expandedExtension by remember { mutableStateOf(false) }
    var expandedQuality by remember { mutableStateOf(false) }
    
    var isPlaylist by remember { mutableStateOf(false) }

    
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadStatus by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "download_channel",
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download progress"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> }

    val formats = listOf("Video", "Audio", "Thumbnail")
    
    val extensions = when (selectedFormat) {
        "Video" -> listOf("MP4", "WEBM", "MKV")
        "Audio" -> listOf("MP3", "M4A", "WAV", "FLAC")
        else -> listOf("JPG", "PNG")
    }

    val qualities = when (selectedFormat) {
        "Video" -> listOf("4320p 8K", "2160p 4K", "1440p 2K", "1080p HD", "720p HD", "480p SD", "360p SD", "240p SD")
        "Audio" -> listOf("320 kbps", "256 kbps", "192 kbps", "128 kbps", "64 kbps")
        else -> listOf("Best")
    }

    fun analyzeLink() {
        url = url.trim()
        if (url.isBlank()) {
            errorMessage = "URL is blank. Please paste a link."
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        
        if (url.contains("youtube.com/shorts/")) {
            val videoId = url.substringAfter("youtube.com/shorts/").substringBefore("?")
            url = "https://www.youtube.com/watch?v=$videoId"
        } else if (url.contains("youtu.be/")) {
            val videoId = url.substringAfter("youtu.be/").substringBefore("?")
            url = "https://www.youtube.com/watch?v=$videoId"
        }

        errorMessage = null
        isAnalyzing = true
        
        coroutineScope.launch {
            try {
                var info: VideoInfo? = null
                withContext(Dispatchers.IO) {
                    try {
                        val request = YoutubeDLRequest(url)
                        info = YoutubeDL.getInstance().getInfo(request)
                    } catch (e: Exception) {
                        Log.e("Downloader", "Failed to get info, attempting NIGHTLY update", e)
                        try {
                            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
                        } catch (updateEx: Exception) {
                            Log.e("Downloader", "NIGHTLY update failed, trying STABLE", updateEx)
                            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                        }
                        val request = YoutubeDLRequest(url)
                        info = YoutubeDL.getInstance().getInfo(request)
                    }
                }
                
                if (info != null) {
                    thumbnailUrl = info!!.thumbnail
                    videoTitle = info!!.title ?: "Unknown Title"
                    platform = info!!.extractor ?: "Unknown"
                    step = 1
                } else {
                    errorMessage = "Failed to parse link: info is null."
                }
            } catch (e: Exception) {
                Log.e("Downloader", "Failed to get info even after update", e)
                errorMessage = "DL Error: ${e.message}"
            } finally {
                isAnalyzing = false
            }
        }
    }

    fun simulateDownload() {
        step = 2
        downloadProgress = 0f
        downloadStatus = "Connecting to $platform..."
        
        val notificationId = url.hashCode()
        val builder = NotificationCompat.Builder(context, "download_channel")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading ${videoTitle}")
            .setContentText("Connecting...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, 0, false)
            
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
        
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    val request = YoutubeDLRequest(url)
                    
                    if (isPlaylist) {
                        request.addOption("--yes-playlist")
                        request.addOption("-o", downloadDir.absolutePath + "/%(playlist)s/%(playlist_index)s - %(title)s.%(ext)s")
                    } else {
                        request.addOption("--no-playlist")
                        request.addOption("-o", downloadDir.absolutePath + "/%(title)s.%(ext)s")
                    }
                    
                    if (selectedFormat == "Thumbnail") {
                        request.addOption("--write-thumbnail")
                        request.addOption("--skip-download")
                        if (selectedExtension.lowercase() == "jpg") {
                            request.addOption("--convert-thumbnails", "jpg")
                        } else {
                            request.addOption("--convert-thumbnails", "png")
                        }
                    } else if (selectedFormat == "Audio") {
                        val ext = selectedExtension.lowercase()
                        request.addOption("-x")
                        request.addOption("--audio-format", ext)
                        val qualityValue = selectedQuality.substringBefore(" ") // 320, 256, etc.
                        request.addOption("--audio-quality", qualityValue + "K")
                    } else {
                        // Video
                        val res = selectedQuality.substringBefore("p")
                        val ext = selectedExtension.lowercase()
                        
                        // Default to the requested extension, filter to the requested resolution
                        // Using yt-dlp's dynamic format sorting
                        request.addOption("-f", "bv*[ext=$ext][res<=${res}]+ba[ext=m4a]/b[ext=$ext][res<=${res}] / bv*[res<=${res}]+ba/b[res<=${res}]/b")
                        request.addOption("--merge-output-format", ext)
                    }
                    
                    YoutubeDL.getInstance().execute(request, "Task") { progress, etaInSeconds, _ ->
                        // Pass update back to Main
                        launch(Dispatchers.Main) {
                            downloadProgress = progress / 100f
                            downloadStatus = "Downloading $selectedFormat... ${progress.toInt()}%"
                            
                            builder.setContentText("${progress.toInt()}%")
                                .setProgress(100, progress.toInt(), false)
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                NotificationManagerCompat.from(context).notify(notificationId, builder.build())
                            }
                        }
                    }
                }
                
                downloadStatus = "Download completed successfully!"
                builder.setContentText("Download Complete")
                    .setOngoing(false)
                    .setProgress(0, 0, false)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    NotificationManagerCompat.from(context).notify(notificationId, builder.build())
                }
                
                delay(3000)
                
                // Reset
                url = ""
                step = 0
                thumbnailUrl = null
            } catch (e: Exception) {
                Log.e("Downloader", "Download failed", e)
                downloadStatus = "Download failed: ${e.message}"
                
                builder.setContentText("Download Failed")
                    .setOngoing(false)
                    .setProgress(0, 0, false)
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    NotificationManagerCompat.from(context).notify(notificationId, builder.build())
                }
                
                delay(3000)
                step = 1 // Go back to preview on failure
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 0 && !isAnalyzing && step != 2) {
                IconButton(onClick = { step = 0 }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }
            Text(
                text = "Universal Downloader",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(48.dp))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        AnimatedContent(
            targetState = step,
            label = "StepAnimation"
        ) { currentStep ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (currentStep) {
                    0 -> {
                        // Input Step
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Downloader",
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Download videos & audio from YouTube, Instagram, Pinterest and more.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        OutlinedTextField(
                            value = url,
                            onValueChange = { 
                                url = it.trim()
                                isPlaylist = url.contains("list=")
                                errorMessage = null
                            },
                            label = { Text("Paste Link Here") },
                            placeholder = { Text("https://...") },
                            isError = errorMessage != null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    if (clipboard.hasPrimaryClip() && clipboard.primaryClip?.itemCount!! > 0) {
                                        url = clipboard.primaryClip?.getItemAt(0)?.text.toString().trim()
                                        isPlaylist = url.contains("list=")
                                        errorMessage = null
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste, 
                                        contentDescription = "Paste from clipboard"
                                    )
                                }
                            },
                            supportingText = if (errorMessage != null) {
                                { Text(errorMessage!!) }
                            } else null
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Button(
                            onClick = { analyzeLink() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isAnalyzing
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Analyzing link...")
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyze Link")
                            }
                        }
                    }
                    
                    1 -> {
                        // Preview & Select Step
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (thumbnailUrl != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(thumbnailUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Thumbnail",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.VideoFile,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .align(Alignment.Center),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    )
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .align(Alignment.Center)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play preview",
                                        tint = Color.White,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = videoTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        if (url.contains("list=")) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isPlaylist,
                                    onCheckedChange = { isPlaylist = it }
                                )
                                Text("Download complete playlist")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Format Dropdown
                            ExposedDropdownMenuBox(
                                expanded = expandedFormat,
                                onExpandedChange = { expandedFormat = !expandedFormat },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = selectedFormat,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Format", maxLines=1) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFormat) },
                                    modifier = Modifier.menuAnchor(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedFormat,
                                    onDismissRequest = { expandedFormat = false }
                                ) {
                                    formats.forEach { selectionOption ->
                                        DropdownMenuItem(
                                            text = { Text(selectionOption) },
                                            onClick = {
                                                selectedFormat = selectionOption
                                                
                                                val newExtensions = when (selectionOption) {
                                                    "Video" -> listOf("MP4", "WEBM", "MKV")
                                                    "Audio" -> listOf("MP3", "M4A", "WAV", "FLAC")
                                                    else -> listOf("JPG", "PNG")
                                                }
                                                val newQualities = when (selectionOption) {
                                                    "Video" -> listOf("4320p 8K", "2160p 4K", "1440p 2K", "1080p HD", "720p HD", "480p SD", "360p SD", "240p SD")
                                                    "Audio" -> listOf("320 kbps", "256 kbps", "192 kbps", "128 kbps", "64 kbps")
                                                    else -> listOf("Best")
                                                }
                                                
                                                if (!newExtensions.contains(selectedExtension)) selectedExtension = newExtensions.first()
                                                if (!newQualities.contains(selectedQuality)) selectedQuality = newQualities.first()
                                                
                                                expandedFormat = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Extension Dropdown (MP4, MP3, etc)
                            ExposedDropdownMenuBox(
                                expanded = expandedExtension,
                                onExpandedChange = { expandedExtension = !expandedExtension },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = selectedExtension,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Ext", maxLines=1) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedExtension) },
                                    modifier = Modifier.menuAnchor(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedExtension,
                                    onDismissRequest = { expandedExtension = false }
                                ) {
                                    extensions.forEach { selectionOption ->
                                        DropdownMenuItem(
                                            text = { Text(selectionOption) },
                                            onClick = {
                                                selectedExtension = selectionOption
                                                expandedExtension = false
                                            }
                                        )
                                    }
                                }
                            }
                            
                            // Quality Dropdown
                            ExposedDropdownMenuBox(
                                expanded = expandedQuality,
                                onExpandedChange = { expandedQuality = !expandedQuality },
                                modifier = Modifier.weight(1.3f)
                            ) {
                                OutlinedTextField(
                                    value = selectedQuality,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Quality", maxLines=1) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedQuality) },
                                    modifier = Modifier.menuAnchor(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedQuality,
                                    onDismissRequest = { expandedQuality = false }
                                ) {
                                    qualities.forEach { selectionOption ->
                                        DropdownMenuItem(
                                            text = { Text(selectionOption) },
                                            onClick = {
                                                selectedQuality = selectionOption
                                                expandedQuality = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Button(
                            onClick = { 
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                simulateDownload() 
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download", fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    2 -> {
                        // Download Progress Step
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(150.dp)) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload, 
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Text(
                            text = downloadStatus,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
