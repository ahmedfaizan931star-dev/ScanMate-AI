package com.example.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class DocumentWithPages(
    @Embedded val document: Document,
    @Relation(
        parentColumn = "id",
        entityColumn = "documentId"
    )
    val pages: List<Page>
)

@Dao
interface DocDao {
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC, timestamp DESC")
    fun getAllDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM documents ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentDocuments(limit: Int = 8): Flow<List<Document>>

    @Query("""
        SELECT * FROM documents
        WHERE title LIKE '%' || :query || '%'
           OR IFNULL(ocrText, '') LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC, timestamp DESC
    """)
    fun searchDocuments(query: String): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun getDocument(id: Long): Flow<Document?>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :id")
    fun getDocumentWithPages(id: Long): Flow<DocumentWithPages?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: Document): Long

    @Update
    suspend fun updateDocument(document: Document)

    @Query("UPDATE documents SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameDocument(id: Long, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET isFavorite = :isFavorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: Page): Long

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun deletePageById(id: Long)

    @Query("SELECT * FROM pages WHERE documentId = :docId ORDER BY pageOrder ASC")
    fun getPagesForDocument(docId: Long): Flow<List<Page>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQrHistory(item: QrHistory): Long

    @Query("SELECT * FROM qr_history ORDER BY timestamp DESC LIMIT :limit")
    fun getQrHistory(limit: Int = 25): Flow<List<QrHistory>>

    @Query("DELETE FROM qr_history")
    suspend fun clearQrHistory()
}
