// PhoneDatabaseSnapshot.kt

package moe.rgsekai.sekaitune.link.phone

import android.database.sqlite.SQLiteDatabase
import java.io.File

// turns a copied wal-mode database into one plain file, so it can be zipped or moved on its own without losing the newest changes
internal fun foldIntoOneFile(db: SQLiteDatabase) {
    val mode = db.rawQuery("PRAGMA journal_mode=DELETE", null).use { if (it.moveToFirst()) it.getString(0) else null }
    if (mode.equals("delete", ignoreCase = true)) return
    db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
        check(cursor.moveToFirst() && cursor.getInt(0) == 0) { "couldn't fold the wal into ${db.path}" }
    }
}

// copies a live database while holding its write lock, then folds its wal into one plain file
internal fun snapshotDatabase(source: File, target: File, begin: () -> Unit, end: () -> Unit) {
    val targetWal = File(target.path + "-wal")
    target.delete()
    targetWal.delete()
    begin()
    try {
        source.copyTo(target, overwrite = true)
        File(source.path + "-wal").takeIf { it.exists() }?.copyTo(targetWal, overwrite = true)
    } finally {
        end()
    }
    SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READWRITE).use { copy ->
        foldIntoOneFile(copy)
        copy.rawQuery("PRAGMA quick_check", null).use { cursor ->
            check(cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)) { "snapshot of ${source.name} failed its check" }
        }
    }
    listOf("-wal", "-shm", "-journal").forEach { File(target.path + it).delete() }
}

