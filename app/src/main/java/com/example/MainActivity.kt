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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    var isInitialized by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(context.applicationContext)
                FFmpeg.getInstance().init(context.applicationContext)
                isInitialized = true
            } catch (e: Throwable) {
                Log.e("Downloader", "Failed to init", e)
            }
        }
    }

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

    // Moved LaunchedEffect

    var showHistory by remember { mutableStateOf(false) }

    var dynamicVideoQualities by remember { mutableStateOf(listOf("Base")) }
    var dynamicAudioQualities by remember { mutableStateOf(listOf("Base")) }

    var cachedInfo by remember { mutableStateOf<com.yausername.youtubedl_android.mapper.VideoInfo?>(null) }
    var dlSpeed by remember { mutableStateOf("") }
    var dlSize by remember { mutableStateOf("") }
    
    val formats = listOf("Video", "Audio", "Thumbnail")

    
    val extensions = when (selectedFormat) {
        "Video" -> listOf("MP4", "WEBM", "MKV")
        "Audio" -> listOf("MP3", "M4A", "WAV", "FLAC")
        else -> listOf("JPG", "PNG")
    }

    val qualities = when (selectedFormat) {
        "Video" -> dynamicVideoQualities
        "Audio" -> dynamicAudioQualities
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
                        if (isPlaylist) {
                            request.addOption("--flat-playlist")
                        } else {
                            request.addOption("--no-playlist")
                        }
                        info = YoutubeDL.getInstance().getInfo(request)
                    } catch (e: Exception) {
                        Log.e("Downloader", "Failed to get info, attempting NIGHTLY update", e)
                        launch(Dispatchers.Main) { errorMessage = "Updating engine... Please wait (may take a minute)" }
                        try {
                            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
                        } catch (updateEx: Exception) {
                            Log.e("Downloader", "NIGHTLY update failed, trying STABLE", updateEx)
                            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                        }
                        launch(Dispatchers.Main) { errorMessage = "Retrying analysis..." }
                        val request = YoutubeDLRequest(url)
                        if (isPlaylist) {
                            request.addOption("--flat-playlist")
                        } else {
                            request.addOption("--no-playlist")
                        }
                        info = YoutubeDL.getInstance().getInfo(request)
                    }
                }
                
                if (info != null) {
                    cachedInfo = info
                    thumbnailUrl = info!!.thumbnail
                    videoTitle = info!!.title ?: "Unknown Title"
                    platform = info!!.extractor ?: "Unknown"
                    
                    val formatsList = info!!.formats
                    if (formatsList != null) {
                        val vQualities = mutableSetOf<String>()
                        val aQualities = mutableSetOf<String>()
                        
                        for (f in formatsList) {
                            val vcodec = f.vcodec ?: ""
                            val acodec = f.acodec ?: ""
                            val height = f.height
                            val ext = f.ext ?: ""
                            
                            if (vcodec != "none" && vcodec.isNotEmpty()) {
                                if (height > 0) {
                                    val size = f.fileSize
                                    val finalSize = if (size > 0) size else 0L
                                    val sizeStr = if (finalSize > 0) " (${finalSize / 1024 / 1024} MB)" else ""
                                    vQualities.add("${height}p$sizeStr")
                                }
                            }
                            if (acodec != "none" && acodec.isNotEmpty() && (vcodec == "none" || vcodec.isEmpty())) {
                                val abr = f.abr
                                if (abr > 0) {
                                    val size = f.fileSize
                                    val finalSize = if (size > 0) size else 0L
                                    val sizeStr = if (finalSize > 0) " (${finalSize / 1024 / 1024} MB)" else ""
                                    aQualities.add("${abr.toInt()} kbps$sizeStr")
                                }
                            }
                        }
                        
                        dynamicVideoQualities = vQualities.toList().sortedByDescending { it.substringBefore("p").toIntOrNull() ?: 0 }.ifEmpty { listOf("1080p") }
                        dynamicAudioQualities = aQualities.toList().sortedByDescending { it.substringBefore(" ").toIntOrNull() ?: 0 }.ifEmpty { listOf("128 kbps") }
                    } else {
                        dynamicVideoQualities = listOf("1080p", "720p", "480p", "360p")
                        dynamicAudioQualities = listOf("320 kbps", "128 kbps")
                    }
                    
                    selectedQuality = if (selectedFormat == "Video") dynamicVideoQualities.first() else if (selectedFormat == "Audio") dynamicAudioQualities.first() else "Best"
                    
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
                        launch(Dispatchers.Main) { downloadStatus = "Downloading Thumbnail(s)..." }
                        
                        request.addOption("--write-thumbnail")
                        request.addOption("--skip-download")
                        val ext = selectedExtension.lowercase()
                        request.addOption("--convert-thumbnails", ext)
                        // Force FFmpeg to overwrite just in case it's hanging on prompt
                        request.addOption("--postprocessor-args", "ffmpeg:-y")
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
                    
                    // Fake progress since thumbnail extraction doesn't trigger progress
                    var fakeProgressJob: kotlinx.coroutines.Job? = null
                    if (selectedFormat == "Thumbnail") {
                        fakeProgressJob = launch(Dispatchers.Main) {
                            var p = 0f
                            while (p < 0.95f) {
                                delay(500)
                                p += 0.05f
                                downloadProgress = p
                            }
                        }
                    }
                    
                    var downloadedFilePath: String? = null
                    YoutubeDL.getInstance().execute(request, "Task") { progress, etaInSeconds, line ->
                        // Pass update back to Main
                        launch(Dispatchers.Main) {
                            downloadProgress = progress / 100f
                            
                            val speedRegex = """at\s+([0-9.]+[a-zA-Z]+/s)""".toRegex()
                            val sizeRegex = """of\s+~?([0-9.]+[a-zA-Z]+)""".toRegex()
                            val destRegex = """Destination:\s+(.*)""".toRegex()
                            val mergeRegex = """Merging formats into "(.*)"""".toRegex()
                            
                            val matchSpeed = speedRegex.find(line)
                            val matchSize = sizeRegex.find(line)
                            val matchDest = destRegex.find(line)
                            val matchMerge = mergeRegex.find(line)
                            
                            if (matchSpeed != null) dlSpeed = matchSpeed.groupValues[1]
                            if (matchSize != null) dlSize = matchSize.groupValues[1]
                            if (matchDest != null) downloadedFilePath = matchDest.groupValues[1]
                            if (matchMerge != null) downloadedFilePath = matchMerge.groupValues[1]
                            
                            downloadStatus = "Downloading $selectedFormat... ${progress.toInt()}%\nSpeed: $dlSpeed | Size: $dlSize"
                            
                            builder.setContentText("${progress.toInt()}% - $dlSpeed")
                                .setProgress(100, progress.toInt(), false)
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                NotificationManagerCompat.from(context).notify(notificationId, builder.build())
                            }
                        }
                    }
                    fakeProgressJob?.cancel()
                    
                    if (downloadedFilePath != null) {
                        try {
                            val db = AppDatabase.getDatabase(context)
                            val file = java.io.File(downloadedFilePath!!)
                            db.downloadHistoryDao().insert(
                                DownloadHistory(
                                    title = videoTitle,
                                    url = url,
                                    filePath = downloadedFilePath!!,
                                    fileSizeBytes = if (file.exists()) file.length() else 0L,
                                    mediaType = selectedFormat
                                )
                            )
                            
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.applicationContext.packageName}.provider", file)
                            val openIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                val mimeType = when(selectedFormat) {
                                    "Video" -> "video/*"
                                    "Audio" -> "audio/*"
                                    else -> "image/*"
                                }
                                setDataAndType(uri, mimeType)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            val pendingIntent = android.app.PendingIntent.getActivity(context, 0, openIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                            builder.setContentIntent(pendingIntent)
                        } catch (e: Exception) {
                            Log.e("Downloader", "Failed to add history or intent", e)
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

    LaunchedEffect(url, isInitialized) {
        if (isInitialized && url.isNotEmpty() && step == 0) {
            analyzeLink()
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
                IconButton(onClick = { showHistory = !showHistory }) {
                    Icon(Icons.Default.History, contentDescription = "History")
                }
            }
            Text(
                text = if (showHistory) "Download History" else "Universal Downloader",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(48.dp))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (showHistory) {
            var historyList by remember { mutableStateOf(emptyList<DownloadHistory>()) }
            val db = AppDatabase.getDatabase(context)
            
            LaunchedEffect(Unit) {
                historyList = db.downloadHistoryDao().getAllHistory()
            }
            
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(historyList) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                val file = java.io.File(item.filePath)
                                if (file.exists()) {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.applicationContext.packageName}.provider", file)
                                    val openIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        val mimeType = when(item.mediaType) {
                                            "Video" -> "video/*"
                                            "Audio" -> "audio/*"
                                            else -> "image/*"
                                        }
                                        setDataAndType(uri, mimeType)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(openIntent)
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines=2)
                            Spacer(modifier=Modifier.height(4.dp))
                            Text("Type: ${item.mediaType} | Size: ${item.fileSizeBytes / 1024 / 1024} MB", style = MaterialTheme.typography.bodySmall)
                            Text(item.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        } else {
            AnimatedContent(
                targetState = if (step == 1) 0 else step,
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
                            enabled = !isAnalyzing && isInitialized
                        ) {
                            if (!isInitialized) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha=0.5f),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Initializing Engine...")
                            } else if (isAnalyzing) {
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
    
    if (step == 1) {
        ModalBottomSheet(
            onDismissRequest = { step = 0 },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .padding(bottom = 32.dp)
            ) {
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
                                            "Video" -> dynamicVideoQualities
                                            "Audio" -> dynamicAudioQualities
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
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
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
                        modifier = Modifier.weight(1.4f)
                    ) {
                        OutlinedTextField(
                            value = selectedQuality,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
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
        }
    }
}
