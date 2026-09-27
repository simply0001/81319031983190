package com.pocketpass.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BoardsMigrationTest {
    @Test fun realVersionTwentySchemaRetainsProfilesConversationsAndMessages() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="boards-migration-${java.util.UUID.randomUUID()}.db"
        val json=InstrumentationRegistry.getInstrumentation().context.assets.open("boards-schema-20.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val entities=json.getJSONArray("entities")
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object: SupportSQLiteOpenHelper.Callback(20) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                for(i in 0 until entities.length()) {
                    val entity=entities.getJSONObject(i);val table=entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
                    val indexes=entity.optJSONArray("indices") ?: org.json.JSONArray()
                    for(j in 0 until indexes.length()) db.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
                    if(table in listOf("profiles","conversations","messages")) {
                        val fields=entity.getJSONArray("fields")
                        val names=(0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }
                        val values: List<Any> = (0 until fields.length()).map {
                            val field=fields.getJSONObject(it)
                            if(field.getString("affinity")=="TEXT") "preserved-${field.getString("columnName")}" else 7L
                        }
                        db.execSQL("INSERT INTO `$table` (${names.joinToString { "`$it`" }}) VALUES (${names.joinToString { "?" }})",values.toTypedArray())
                    }
                }
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion:Int,newVersion:Int)=Unit
        }).build())
        fun snapshot(db:SupportSQLiteDatabase)=listOf("profiles","conversations","messages").associateWith { table ->
            db.query("SELECT * FROM `$table`").use { cursor ->
                assertTrue(cursor.moveToFirst()); (0 until cursor.columnCount).map { cursor.getString(it) }
            }
        }
        try {
            val before=snapshot(helper.writableDatabase)
            helper.close()
            val migrated=Room.databaseBuilder(context,PocketPassDatabase::class.java,name).addMigrations(PocketPassDatabase.Migration20To21).build()
            try {
                val db=migrated.openHelper.writableDatabase
                assertEquals(before,snapshot(db))
                db.execSQL("INSERT INTO board_drafts VALUES ('a','draft','board','{}',1)")
                db.execSQL("INSERT INTO board_drafts VALUES ('b','draft','board','{}',2)")
                db.query("SELECT count(*) FROM board_drafts").use { assertTrue(it.moveToFirst());assertEquals(2,it.getInt(0)) }
                db.query("PRAGMA user_version").use { assertTrue(it.moveToFirst());assertEquals(21,it.getInt(0)) }
            } finally { migrated.close() }
        } finally { helper.close();context.deleteDatabase(name) }
    }
}
