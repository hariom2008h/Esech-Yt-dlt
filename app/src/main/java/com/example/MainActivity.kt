package com.example

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
    var url by remember { mutableStateOf(initialUrl) }
    var step by remember { mutableIntStateOf(0) } // 0: Input, 1: Preview, 2: Downloading
    var isAnalyzing by remember { mutableStateOf(false) }
    
    var platform by remember { mutableStateOf("") }
    var thumbnailUrl by remember { mutableStateOf<String?>(null) }
    var videoTitle by remember { mutableStateOf("") }
    
    var selectedFormat by remember { mutableStateOf("Video") }
    var selectedQuality by remember { mutableStateOf("1080p") }
    var expandedFormat by remember { mutableStateOf(false) }
    var expandedQuality by remember { mutableStateOf(false) }
    
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadStatus by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val qualities = if (selectedFormat == "Video") listOf("1080p HD", "720p HD", "480p SD", "360p SD") else listOf("320 kbps (High)", "128 kbps (Standard)")
    val formats = listOf("Video", "Audio")

    fun extractYoutubeId(link: String): String? {
        val regex = "(?<=watch\\?v=|/videos/|embed\\/|youtu.be\\/|\\/v\\/|\\/e\\/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%\u200C\u200B2F|youtu.be%2F|%2Fv%2F)[^#\\&\\?\\n]*"
        val pattern = java.util.regex.Pattern.compile(regex)
        val matcher = pattern.matcher(link)
        return if (matcher.find()) matcher.group() else null
    }

    fun analyzeLink() {
        if (url.isBlank()) {
            isError = true
            return
        }
        isError = false
        isAnalyzing = true
        
        val lowerUrl = url.lowercase()
        platform = when {
            lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be") -> "YouTube"
            lowerUrl.contains("instagram.com") -> "Instagram"
            lowerUrl.contains("pinterest.com") || lowerUrl.contains("pin.it") -> "Pinterest"
            lowerUrl.contains("facebook.com") || lowerUrl.contains("fb.watch") -> "Facebook"
            lowerUrl.contains("tiktok.com") -> "TikTok"
            else -> "Website"
        }
        
        coroutineScope.launch {
            delay(1500) // Mock network delay
            if (platform == "YouTube") {
                val ytid = extractYoutubeId(url)
                if (ytid != null) {
                    thumbnailUrl = "https://img.youtube.com/vi/$ytid/hqdefault.jpg"
                } else {
                    thumbnailUrl = null
                }
            } else {
                thumbnailUrl = null // Generic thumbnail
            }
            videoTitle = "$platform Media Content"
            
            isAnalyzing = false
            step = 1
        }
    }

    fun simulateDownload() {
        step = 2
        downloadProgress = 0f
        downloadStatus = "Connecting to $platform..."
        
        coroutineScope.launch {
            delay(1000)
            downloadStatus = "Fetching streams..."
            delay(800)
            
            for (i in 1..100) {
                downloadProgress = i / 100f
                downloadStatus = "Downloading $selectedFormat ($selectedQuality) ... $i%"
                delay((10..40).random().toLong())
            }
            
            delay(500)
            downloadStatus = "Merging and saving to Gallery..."
            delay(1500)
            downloadStatus = "Download completed successfully!"
            delay(2000)
            
            // Reset
            url = ""
            step = 0
            thumbnailUrl = null
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
                                url = it
                                isError = false
                            },
                            label = { Text("Paste Link Here") },
                            placeholder = { Text("https://...") },
                            isError = isError,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    if (clipboard.hasPrimaryClip() && clipboard.primaryClip?.itemCount!! > 0) {
                                        url = clipboard.primaryClip?.getItemAt(0)?.text.toString()
                                        isError = false
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste, 
                                        contentDescription = "Paste from clipboard"
                                    )
                                }
                            },
                            supportingText = if (isError) {
                                { Text("Please enter a valid link") }
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
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
                                    label = { Text("Format") },
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
                                                if (selectionOption == "Video" && qualities.contains("1080p HD")) {
                                                    selectedQuality = "1080p HD"
                                                } else if (selectionOption == "Audio") {
                                                    selectedQuality = "320 kbps (High)"
                                                }
                                                expandedFormat = false
                                            }
                                        )
                                    }
                                }
                            }
                            
                            // Quality Dropdown
                            ExposedDropdownMenuBox(
                                expanded = expandedQuality,
                                onExpandedChange = { expandedQuality = !expandedQuality },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = selectedQuality,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Quality") },
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
                            onClick = { simulateDownload() },
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
                        Spacer(modifier = Modifier.height(64.dp))
                        
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.size(120.dp),
                                strokeWidth = 8.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                text = "${(downloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
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
