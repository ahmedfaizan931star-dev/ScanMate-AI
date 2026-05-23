package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppDatabase
import com.example.data.DocumentWithPages
import com.example.utils.FileUtils
import com.example.utils.OcrHelper
import com.example.utils.PdfExportQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(docId: Long, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).docDao() }
    val docFlow = remember(docId) { dao.getDocumentWithPages(docId) }
    val documentWithPages by docFlow.collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var renameTitle by remember { mutableStateOf("") }
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    LaunchedEffect(documentWithPages?.document?.title) {
        renameTitle = documentWithPages?.document?.title.orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(documentWithPages?.document?.title ?: "Document") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    documentWithPages?.let { dwp ->
                        IconButton(onClick = {
                            coroutineScope.launch(Dispatchers.IO) { dao.setFavorite(dwp.document.id, !dwp.document.isFavorite) }
                        }) {
                            Icon(if (dwp.document.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite")
                        }
                    }
                    IconButton(onClick = { showRenameDialog = true }) { Icon(Icons.Default.DriveFileRenameOutline, "Rename") }
                    IconButton(onClick = { showExportDialog = true }) { Icon(Icons.Default.PictureAsPdf, "Export PDF") }
                    IconButton(onClick = { extractOcr(documentWithPages, context, dao, clipboardManager) { isProcessing = it } }) {
                        Icon(Icons.AutoMirrored.Filled.TextSnippet, "OCR Extract")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Default.Delete, "Delete") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (isProcessing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            val dwp = documentWithPages
            if (dwp == null) {
                LoadingDocumentState()
            } else {
                DocumentPreview(dwp)
                QuickActionRow(dwp = dwp, onShareFirstImage = {
                    val firstFile = dwp.pages.firstOrNull()?.imagePath?.let { File(it) }
                    if (firstFile != null && firstFile.exists()) {
                        FileUtils.shareFile(context, firstFile, FileUtils.mimeTypeFor(firstFile))
                    } else {
                        Toast.makeText(context, "No image file found to share", Toast.LENGTH_SHORT).show()
                    }
                }, onExport = { showExportDialog = true })

                OcrCard(dwp = dwp, clipboardManager = clipboardManager, context = context)
                PageThumbnails(dwp)
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename document") },
            text = {
                OutlinedTextField(
                    value = renameTitle,
                    onValueChange = { renameTitle = it },
                    singleLine = true,
                    label = { Text("Document name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    coroutineScope.launch(Dispatchers.IO) { dao.renameDocument(docId, renameTitle.trim().ifBlank { "Untitled Scan" }) }
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete this document?") },
            text = { Text("This removes the document record from ScanMate. Export or share anything important first.") },
            confirmButton = {
                Button(onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        dao.deleteDocumentById(docId)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                            showDeleteDialog = false
                            onNavigateBack()
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export PDF quality") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfExportQuality.entries.forEach { quality ->
                        OutlinedButton(onClick = {
                            showExportDialog = false
                            exportPdf(documentWithPages, context, quality) { isProcessing = it }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                                Text(quality.label, fontWeight = FontWeight.Bold)
                                Text(quality.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun LoadingDocumentState() {
    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text("Loading document...")
    }
}

@Composable
private fun DocumentPreview(dwp: DocumentWithPages) {
    val firstPath = dwp.pages.firstOrNull()?.imagePath
    val bitmap = remember(firstPath) { firstPath?.let { FileUtils.decodeSampledBitmap(it, 1400, 1400) } }
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Document Page",
                modifier = Modifier.fillMaxWidth().height(420.dp).padding(12.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth().height(220.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.ImageNotSupported, null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("No preview available", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QuickActionRow(dwp: DocumentWithPages, onShareFirstImage: () -> Unit, onExport: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(onClick = {}, label = { Text("${dwp.pages.size} page${if (dwp.pages.size == 1) "" else "s"}") })
        AssistChip(onClick = onShareFirstImage, leadingIcon = { Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp)) }, label = { Text("Share image") })
        AssistChip(onClick = onExport, leadingIcon = { Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(16.dp)) }, label = { Text("Export PDF") })
    }
}

@Composable
private fun OcrCard(dwp: DocumentWithPages, clipboardManager: ClipboardManager, context: Context) {
    val text = dwp.document.ocrText
    if (!text.isNullOrBlank()) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Extracted Text", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("Extracted Text", text))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy") }
                    TextButton(onClick = { FileUtils.shareText(context, text) }) { Text("Share") }
                    TextButton(onClick = {
                        kotlinx.coroutines.MainScope().launch {
                            val file = FileUtils.saveTextFile(context, text, "OCR_${dwp.document.id}_${System.currentTimeMillis()}")
                            if (file != null) FileUtils.shareFile(context, file, "text/plain")
                        }
                    }) { Text("Save TXT") }
                }
            }
        }
    }
}

@Composable
private fun PageThumbnails(dwp: DocumentWithPages) {
    if (dwp.pages.isNotEmpty()) {
        Text("Pages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        LazyRow(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(dwp.pages, key = { it.id }) { page ->
                val bitmap = remember(page.imagePath) { FileUtils.decodeSampledBitmap(page.imagePath, 360, 360) }
                Card(shape = RoundedCornerShape(16.dp)) {
                    if (bitmap != null) {
                        Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Thumbnail", modifier = Modifier.size(112.dp), contentScale = ContentScale.Crop)
                    } else {
                        Column(modifier = Modifier.size(112.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.ImageNotSupported, null)
                        }
                    }
                }
            }
        }
    }
}

private fun exportPdf(dwp: DocumentWithPages?, context: Context, quality: PdfExportQuality, setProcessing: (Boolean) -> Unit) {
    if (dwp == null) return
    kotlinx.coroutines.MainScope().launch {
        setProcessing(true)
        val pdfFile = FileUtils.generatePdfFromPaths(
            context = context,
            imagePaths = dwp.pages.map { it.imagePath },
            filename = "ScanMate_${dwp.document.id}_${System.currentTimeMillis()}",
            quality = quality
        )
        setProcessing(false)
        if (pdfFile != null) {
            Toast.makeText(context, "PDF exported", Toast.LENGTH_SHORT).show()
            FileUtils.shareFile(context, pdfFile, "application/pdf")
        } else {
            Toast.makeText(context, "PDF export failed", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun extractOcr(
    dwp: DocumentWithPages?,
    context: Context,
    dao: com.example.data.DocDao,
    clipboardManager: ClipboardManager,
    setProcessing: (Boolean) -> Unit
) {
    if (dwp == null || dwp.pages.isEmpty()) return
    kotlinx.coroutines.MainScope().launch {
        setProcessing(true)
        val bitmap = withContext(Dispatchers.IO) { FileUtils.decodeSampledBitmap(dwp.pages.first().imagePath, 1600, 1600) }
        if (bitmap == null) {
            setProcessing(false)
            Toast.makeText(context, "Could not read image for OCR", Toast.LENGTH_SHORT).show()
            return@launch
        }
        val text = OcrHelper.extractTextFromBitmap(bitmap)
        withContext(Dispatchers.IO) { dao.updateDocument(dwp.document.copy(ocrText = text, updatedAt = System.currentTimeMillis())) }
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Extracted Text", text))
        setProcessing(false)
        Toast.makeText(context, "OCR completed and copied", Toast.LENGTH_SHORT).show()
    }
}
