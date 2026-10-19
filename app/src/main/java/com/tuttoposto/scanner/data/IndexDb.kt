package com.tuttoposto.scanner.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * The index is a cache: everything in it can be rebuilt from the folder. It holds page counts
 * (so the library doesn't open every PDF to draw itself) and recognized text for search.
 */
@Entity(tableName = "docs")
data class DocEntity(
    @PrimaryKey val id: String,
    val name: String,
    val size: Long,
    val modified: Long,
    val pageCount: Int,
    /** Width / height of page 1, for laying out the card before the thumbnail exists. */
    val aspect: Float,
    /** The [modified] value whose text is in page_text; differs from [modified] when stale. */
    val textVersion: Long = NO_TEXT,
) {
    companion object { const val NO_TEXT = -1L }
}

@Entity(tableName = "page_text", primaryKeys = ["docId", "page"])
data class PageTextEntity(
    val docId: String,
    val page: Int,
    val text: String,
    /** [com.tuttoposto.scanner.logic.OcrLines]-encoded lines with their positions. */
    val lines: String,
)

@Dao
interface IndexDao {
    @Query("SELECT * FROM docs")
    fun observeDocs(): Flow<List<DocEntity>>

    @Query("SELECT * FROM docs")
    suspend fun allDocs(): List<DocEntity>

    @Query("SELECT * FROM docs WHERE id = :id")
    suspend fun doc(id: String): DocEntity?

    @Upsert
    suspend fun upsert(docs: List<DocEntity>)

    @Query("DELETE FROM docs WHERE id IN (:ids)")
    suspend fun deleteDocRows(ids: List<String>)

    @Query("DELETE FROM page_text WHERE docId IN (:ids)")
    suspend fun deleteText(ids: List<String>)

    @Transaction
    suspend fun delete(ids: List<String>) {
        deleteDocRows(ids)
        deleteText(ids)
    }

    @Query("UPDATE docs SET id = :newId, name = :name WHERE id = :oldId")
    suspend fun renameDocRow(oldId: String, newId: String, name: String)

    @Query("UPDATE page_text SET docId = :newId WHERE docId = :oldId")
    suspend fun renameText(oldId: String, newId: String)

    @Transaction
    suspend fun rename(oldId: String, newId: String, name: String) {
        if (oldId != newId) deleteText(listOf(newId))
        renameDocRow(oldId, newId, name)
        renameText(oldId, newId)
    }

    @Query("SELECT * FROM docs WHERE textVersion != modified AND pageCount > 0 ORDER BY modified DESC")
    suspend fun needingText(): List<DocEntity>

    @Query("SELECT * FROM page_text WHERE docId = :id ORDER BY page")
    fun observeText(id: String): Flow<List<PageTextEntity>>

    @Query("SELECT * FROM page_text WHERE docId = :id ORDER BY page")
    suspend fun text(id: String): List<PageTextEntity>

    @Query("SELECT * FROM page_text WHERE docId = :id AND page = :page")
    suspend fun pageText(id: String, page: Int): PageTextEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putText(rows: List<PageTextEntity>)

    @Query("UPDATE docs SET textVersion = :version WHERE id = :id")
    suspend fun markText(id: String, version: Long)

    @Transaction
    suspend fun replaceText(id: String, version: Long, rows: List<PageTextEntity>) {
        deleteText(listOf(id))
        putText(rows)
        markText(id, version)
    }

    @Query("UPDATE docs SET textVersion = -1")
    suspend fun invalidateAllText()

    @Query("SELECT * FROM page_text WHERE text LIKE :pattern ESCAPE '\\'")
    suspend fun searchText(pattern: String): List<PageTextEntity>
}

@Database(entities = [DocEntity::class, PageTextEntity::class], version = 1, exportSchema = true)
abstract class IndexDb : RoomDatabase() {
    abstract fun dao(): IndexDao

    companion object {
        fun open(context: Context): IndexDb =
            Room.databaseBuilder(context, IndexDb::class.java, "index.db")
                // A cache: if the schema ever changes, rebuilding from the folder is correct.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
