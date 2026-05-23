package com.example.ui.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.DocDao
import com.example.data.Document
import com.example.data.Page
import com.example.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DocumentViewModel(private val dao: DocDao, private val context: Context) : ViewModel() {
    val allDocuments: Flow<List<Document>> = dao.getAllDocuments()
    val favoriteDocuments: Flow<List<Document>> = dao.getFavoriteDocuments()
    val recentDocuments: Flow<List<Document>> = dao.getRecentDocuments()

    fun createDocumentFromUris(uris: List<Uri>, onCreated: (Long) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val docId = dao.insertDocument(Document(title = "Imported Document", timestamp = now, updatedAt = now, type = "IMAGE"))
            var order = 0
            for (uri in uris) {
                val file = copyUriToFile(context, uri)
                if (file != null) {
                    dao.insertPage(Page(documentId = docId, imagePath = file.absolutePath, pageOrder = order++))
                }
            }
            withContext(Dispatchers.Main) { onCreated(docId) }
        }
    }

    fun renameDocument(id: Long, title: String) {
        val safeTitle = title.trim().ifBlank { "Untitled Scan" }
        viewModelScope.launch(Dispatchers.IO) { dao.renameDocument(id, safeTitle) }
    }

    fun toggleFavorite(document: Document) {
        viewModelScope.launch(Dispatchers.IO) { dao.setFavorite(document.id, !document.isFavorite) }
    }

    fun deleteDocument(id: Long, onDeleted: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteDocumentById(id)
            withContext(Dispatchers.Main) { onDeleted() }
        }
    }

    private fun copyUriToFile(context: Context, uri: Uri): File? {
        return try {
            val file = FileUtils.createUniqueImageFile(context)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

class DocumentViewModelFactory(private val dao: DocDao, private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DocumentViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DocumentViewModel(dao, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
