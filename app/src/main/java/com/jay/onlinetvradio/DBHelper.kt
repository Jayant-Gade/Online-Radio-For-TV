package com.jay.onlinetvradio

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONObject
import kotlin.contracts.Returns

class DBHelper(context: Context, factory: SQLiteDatabase.CursorFactory?) :
    SQLiteOpenHelper(context, DATABASE_NAME, factory, DATABASE_VERSION) {

    // Called when the database is created for the first time
    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $ID_COL INTEGER PRIMARY KEY AUTOINCREMENT,
                $NAME_COL TEXT,
                $ICONURL_COL TEXT,
                $LINK_COL TEXT,
                $META_COL TEXT
            )
        """.trimIndent()

        db.execSQL(createTableQuery)
    }

    // Called when the database needs to be upgraded
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }
    fun addStationDB(name: String, iconUrl: String?, link: String, meta: JSONObject){
        val values = ContentValues().apply {
            put(NAME_COL, name)
            put(ICONURL_COL, iconUrl?:"")
            put(LINK_COL,link)
            put(META_COL,meta.toString())
        }
        Log.d("database",iconUrl?:"")
        writableDatabase.use { db ->
            db.insert(TABLE_NAME, null, values)
        }
    }
    fun getStationsDB(): Cursor {
        return readableDatabase.rawQuery("SELECT * FROM $TABLE_NAME", null)
    }
    fun isStationExists(link: String): Boolean {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_NAME WHERE $LINK_COL = ?",
            arrayOf(link)
        )
        cursor.use {
            if (it.moveToFirst()) {
                val count = it.getInt(0)
                return count > 0
            }
        }
        return false
    }
    fun removeStationsDB(link: String): String?{
        var name : String? = null
            val cursor = readableDatabase.query(
            TABLE_NAME,
            arrayOf(NAME_COL), // all columns
            "$LINK_COL = ?",
            arrayOf(link),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                name = it.getString(it.getColumnIndexOrThrow(NAME_COL))
            }
        }

        readableDatabase.delete(
            TABLE_NAME,
            "$LINK_COL = ?",
            arrayOf(link)
        )
        return name?.take(10)
    }
    // Inserts a new record into the database
    /*fun addName(name: String, age: String) {
        val values = ContentValues().apply {
            put(NAME_COL, name)
            put(AGE_COL, age)
        }

        writableDatabase.use { db ->
            db.insert(TABLE_NAME, null, values)
        }
    }

    // Retrieves all records from the database
    fun getName(): Cursor {
        return readableDatabase.rawQuery("SELECT * FROM $TABLE_NAME", null)
    }
*/
    companion object {
        private const val DATABASE_NAME = "OnlineRadio"
        private const val DATABASE_VERSION = 1
        const val TABLE_NAME = "fav_station_table"
        const val ID_COL = "id"

        const val NAME_COL = "name"
        const val ICONURL_COL = "iconurl"
        const val LINK_COL = "link"
        const val META_COL = "meta"
    }
}
