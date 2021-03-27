package com.axzae.homeassistant.provider

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.axzae.homeassistant.model.DatabaseException
import com.axzae.homeassistant.model.Entity
import com.axzae.homeassistant.model.Group
import com.axzae.homeassistant.model.HomeAssistantServer
import com.axzae.homeassistant.model.Widget
import com.axzae.homeassistant.util.CommonUtil
import com.google.gson.Gson
import java.util.ArrayList
import java.util.Collections

class DatabaseManager private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(database: SQLiteDatabase) {
        onCreateVer1(database)
        onCreateVer2(database)
        onCreateVer5(database)
        onCreateVer6(database)
        onCreateVer7(database)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.d("YouQi", "Upgrading database from version $oldVersion to $newVersion")
        when (oldVersion) {
            1, 2, 3, 4 -> {
                onCreateVer1(database)
                onCreateVer5(database)
                onCreateVer6(database)
                onCreateVer7(database)
                onCreateVer2(database)
            }
            5 -> {
                onCreateVer6(database)
                onCreateVer7(database)
                onCreateVer2(database)
            }
            6 -> {
                onCreateVer7(database)
                onCreateVer2(database)
            }
            7 -> onCreateVer2(database)
        }
    }

    private fun onCreateVer1(database: SQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS " + TABLE_ENTITY)
        var sql = ""
        sql += """
            
            CREATE TABLE $TABLE_ENTITY
            """.trimIndent()
        sql += """
            
            (
            """.trimIndent()
        sql += """
	ENTITY_ID VARCHAR,"""
        sql += """
	FRIENDLY_NAME VARCHAR,"""
        sql += """
	DOMAIN VARCHAR,"""
        sql += """
	RAW_JSON VARCHAR,"""
        sql += """
	CHECKSUM VARCHAR,"""
        sql += """
	UNIQUE (ENTITY_ID) ON CONFLICT REPLACE"""
        sql += """
            
            );
            """.trimIndent()
        database.execSQL(sql)
    }

    private fun onCreateVer2(database: SQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS " + TABLE_DASHBOARD)
        var sql = ""
        sql += """
            
            CREATE TABLE $TABLE_DASHBOARD
            """.trimIndent()
        sql += """
            
            (
            """.trimIndent()
        sql += """
	ENTITY_ID VARCHAR,"""
        sql += """
	DASHBOARD_ID INTEGER,"""
        sql += """
	CUSTOM_NAME VARCHAR,"""
        sql += """
	DISPLAY_ORDER INTEGER,"""
        sql += """
	UNIQUE (ENTITY_ID, DASHBOARD_ID) ON CONFLICT REPLACE"""
        sql += """
            
            );
            """.trimIndent()
        database.execSQL(sql)
        Log.d("YouQi", "onCreateVer2")
    }

    private fun onCreateVer5(database: SQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS " + TABLE_GROUP)
        var sql = ""
        sql += """
            
            CREATE TABLE $TABLE_GROUP
            """.trimIndent()
        sql += """
            
            (
            """.trimIndent()
        sql += """
	GROUP_ID INTEGER,"""
        sql += """
	GROUP_ENTITY_ID VARCHAR,"""
        sql += """
	GROUP_NAME VARCHAR,"""
        sql += """
	RAW_JSON VARCHAR,"""
        sql += """
	GROUP_DISPLAY_ORDER INTEGER,"""
        sql += """
	SORT_KEY INTEGER,"""
        sql += """
	UNIQUE (GROUP_ID) ON CONFLICT REPLACE"""
        sql += """
            
            );
            """.trimIndent()
        database.execSQL(sql)
    }

    private fun onCreateVer6(database: SQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS " + TABLE_WIDGET)
        var sql = ""
        sql += """
            
            CREATE TABLE $TABLE_WIDGET
            """.trimIndent()
        sql += """
            
            (
            """.trimIndent()
        sql += """
	WIDGET_ID INTEGER,"""
        sql += """
	ENTITY_ID VARCHAR,"""
        sql += """
	FRIENDLY_STATE VARCHAR,"""
        sql += """
	FRIENDLY_NAME VARCHAR,"""
        sql += """
	LAST_UPDATED INTEGER,"""
        sql += """
	UNIQUE (WIDGET_ID) ON CONFLICT REPLACE"""
        sql += """
            
            );
            """.trimIndent()
        database.execSQL(sql)
    }

    private fun onCreateVer7(database: SQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS " + TABLE_CONNECTION)
        var sql = ""
        sql += """
            
            CREATE TABLE $TABLE_CONNECTION
            """.trimIndent()
        sql += """
            
            (
            """.trimIndent()
        sql += """
	CONNECTION_ID INTEGER PRIMARY KEY AUTOINCREMENT,"""
        sql += """
	CONNECTION_NAME VARCHAR,"""
        sql += """
	BASE_URL VARCHAR,"""
        sql += """
	PASSWORD VARCHAR,"""
        sql += """
	UNIQUE (CONNECTION_ID) ON CONFLICT REPLACE"""
        sql += """
            
            );
            """.trimIndent()
        database.execSQL(sql)
    }

    fun forceCreate(): DatabaseManager {
        val db = this.writableDatabase
        return this
    }

    fun clear() {
        val db = this.writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_ENTITY, null, null)
            db.delete(TABLE_DASHBOARD, null, null)
            db.delete(TABLE_GROUP, null, null)
            db.delete(TABLE_CONNECTION, null, null)
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            db.endTransaction()
        }
    }

    @Throws(DatabaseException::class)
    fun updateDashboard(groupId: Int, entities: ArrayList<Entity>?) {
        val db = this.writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_DASHBOARD, "DASHBOARD_ID=?", arrayOf(Integer.toString(groupId)))
            if (entities != null && entities.size > 0) {
                //db.delete("entities", null, null);
                for (i in entities.indices) {
                    val entityItem = entities[i]
                    val initialValues = ContentValues()
                    initialValues.put("ENTITY_ID", entityItem.entityId)
                    initialValues.put("CUSTOM_NAME", entityItem.friendlyName)
                    initialValues.put("DASHBOARD_ID", groupId)
                    initialValues.put("DISPLAY_ORDER", i)
                    db.insert(TABLE_DASHBOARD, null, initialValues)
                }
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            e.printStackTrace()
            throw DatabaseException(e.message)
        } finally {
            db.endTransaction()
        }
    }

    @Throws(DatabaseException::class)
    fun updateTables(entities: ArrayList<Entity>?) {
        val db = this.writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_ENTITY, null, null)
            if (entities != null && entities.size > 0) {
                //db.delete("entities", null, null);
                Collections.sort(entities) { lhs, rhs ->
                    val a = lhs.domainRanking - rhs.domainRanking //ascending order
                    if (a == 0) {
                        lhs.friendlyName.compareTo(rhs.friendlyName) //ascending order
                    } else a
                }
                for (entity in entities) {
                    if ("" == entity.friendlyName) {
                        continue
                    }
                    db.insert(TABLE_ENTITY, null, entity.contentValues)
                    //task.addProgress(1);
                }
                for (i in entities.indices) {
                    val entity = entities[i]
                    if ("" == entity.friendlyName || entity.isHidden) {
                        continue
                    }
                    val initialValues = ContentValues()
                    initialValues.put("ENTITY_ID", entity.entityId)
                    initialValues.put("CUSTOM_NAME", entity.friendlyName)
                    initialValues.put("DASHBOARD_ID", 1)
                    initialValues.put("DISPLAY_ORDER", i)
                    db.insert(TABLE_DASHBOARD, null, initialValues)
                }
            }
            val groups = ArrayList<Group>()
            db.delete(TABLE_GROUP, null, null)
            if (entities != null && entities.size > 0) {
                var count = 0
                run {
                    ++count
                    val initialValues = ContentValues()
                    initialValues.put("GROUP_ID", count)
                    initialValues.put("GROUP_ENTITY_ID", "")
                    initialValues.put("GROUP_NAME", "HOME")
                    initialValues.put("RAW_JSON", "")
                    initialValues.put("GROUP_DISPLAY_ORDER", -10)
                    initialValues.put("SORT_KEY", 0)
                    db.insert(TABLE_GROUP, null, initialValues)
                }
                for (i in entities.indices) {
                    val entity = entities[i]
                    if (entity.isGroup && entity.attributes.isView) {
                        ++count
                        val group = Group.getInstance(entity, count)
                        //Override for default_view
                        if (entity.entityId == "group.default_view") {
                            group.groupId = 1
                            db.delete(TABLE_GROUP, "GROUP_ID=?", arrayOf(Integer.toString(-10)))
                            db.delete(TABLE_DASHBOARD, "DASHBOARD_ID=?", arrayOf(Integer.toString(1)))
                        }
                        val initialValues = ContentValues()
                        initialValues.put("GROUP_ID", group.groupId)
                        initialValues.put("GROUP_ENTITY_ID", group.entityId)
                        initialValues.put("GROUP_NAME", group.friendlyName)
                        initialValues.put("RAW_JSON", CommonUtil.deflate(entity))
                        initialValues.put("GROUP_DISPLAY_ORDER", group.attributes.order)
                        initialValues.put("SORT_KEY", 0)
                        groups.add(group)
                        db.insert(TABLE_GROUP, null, initialValues)
                    }
                }
            }
            for (group in groups) {
                if (group.attributes.entityIds != null && group.attributes.entityIds.size > 0) {
                    for (i in group.attributes.entityIds.indices) {
                        val entityId = group.attributes.entityIds[i]
                        val initialValues = ContentValues()
                        initialValues.put("ENTITY_ID", entityId)
                        initialValues.put("CUSTOM_NAME", "custom")
                        initialValues.put("DASHBOARD_ID", group.groupId)
                        initialValues.put("DISPLAY_ORDER", i)
                        db.insert(TABLE_DASHBOARD, null, initialValues)
                    }
                }
            }
            //db.execSQL("DELETE FROM " + TABLE_DASHBOARD + " WHERE ENTITY_NAME NO ");
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            e.printStackTrace()
            throw DatabaseException(e.message)
        } finally {
            db.endTransaction()
        }
    }

    // Select All Query
    val entities:

    // looping through all rows and adding to list
        ArrayList<Entity>
        get() {
            val results = ArrayList<Entity>()
            // Select All Query
            val selectQuery = "SELECT * from " + TABLE_ENTITY + " ORDER BY FRIENDLY_NAME ASC, DOMAIN ASC"
            val db = this.readableDatabase
            val cursor = db.rawQuery(selectQuery, null)

            // looping through all rows and adding to list
            if (cursor.moveToFirst()) {
                do {
                    results.add(Entity.getInstance(cursor))
                } while (cursor.moveToNext())
            }
            cursor.close()
            return results
        }

    fun updateSortKeyForGroup(sortKey: Int, groupId: Int): Int {
        val db = this.writableDatabase
        val initialValues = ContentValues()
        initialValues.put("SORT_KEY", sortKey)
        return db.update(TABLE_GROUP, initialValues, "GROUP_ID = ?", arrayOf(Integer.toString(groupId)))
    }

    fun addConnection(server: HomeAssistantServer): Long {
        val db = this.writableDatabase
        val initialValues = ContentValues()
        if (server.connectionId != null) {
            initialValues.put("CONNECTION_ID", server.connectionId)
        }
        initialValues.put("CONNECTION_NAME", server.getName())
        initialValues.put("BASE_URL", server.baseurl)
        initialValues.put("PASSWORD", server.password)
        return db.insert(TABLE_CONNECTION, null, initialValues)
    }

    // Select All Query
    val deviceLocations:

    // looping through all rows and adding to list

    // closing connection
        ArrayList<Entity>
        get() {
            val results = ArrayList<Entity>()
            // Select All Query
            val selectQuery =
                "SELECT * from entities WHERE DOMAIN IN ('zone', 'device_tracker') ORDER BY DOMAIN ASC, ENTITY_ID ASC"
            val db = this.readableDatabase
            val cursor = db.rawQuery(selectQuery, null)
            val gson = Gson()

            // looping through all rows and adding to list
            if (cursor.moveToFirst()) {
                do {
                    results.add(Entity.getInstance(cursor))
                } while (cursor.moveToNext())
            }

            // closing connection
            cursor.close()
            return results
        }

    // Select All Query
    val connections:

    // looping through all rows and adding to list

    // closing connection
        ArrayList<HomeAssistantServer>
        get() {
            val results = ArrayList<HomeAssistantServer>()
            // Select All Query
            val selectQuery = "SELECT * from " + TABLE_CONNECTION + " ORDER BY CONNECTION_ID ASC"
            val db = this.readableDatabase
            val cursor = db.rawQuery(selectQuery, null)
            val gson = Gson()

            // looping through all rows and adding to list
            if (cursor.moveToFirst()) {
                do {
                    results.add(HomeAssistantServer.getInstance(cursor))
                } while (cursor.moveToNext())
            }

            // closing connection
            cursor.close()
            return results
        }

    fun getEntitiesByGroup(groupId: Int): ArrayList<Entity> {
        val results = ArrayList<Entity>()
        val selectQuery =
            "SELECT b.*, a.DISPLAY_ORDER FROM dashboards a LEFT JOIN entities b ON a.ENTITY_ID=b.ENTITY_ID WHERE a.DASHBOARD_ID=$groupId AND b.ENTITY_ID IS NOT NULL ORDER BY a.DISPLAY_ORDER ASC"
        val db = this.readableDatabase
        val cursor = db.rawQuery(selectQuery, null)

        //Log.d("YouQi", "query: " + selectQuery);
        // looping through all rows and adding to list
        if (cursor.moveToFirst()) {
            do {
                val entity = Entity.getInstance(cursor)
                if (entity != null) results.add(entity)
            } while (cursor.moveToNext())
        }

        // closing connection
        cursor.close()
        return results
    }

    // Select All Query
    val groups:

    // looping through all rows and adding to list

    // closing connection
        ArrayList<Group>
        get() {
            val results = ArrayList<Group>()
            // Select All Query
            val selectQuery = "SELECT * from " + TABLE_GROUP + " ORDER BY GROUP_DISPLAY_ORDER ASC"
            val db = this.readableDatabase
            val cursor = db.rawQuery(selectQuery, null)

            // looping through all rows and adding to list
            if (cursor.moveToFirst()) {
                do {
                    results.add(Group.getInstance(cursor))
                } while (cursor.moveToNext())
            }

            // closing connection
            cursor.close()
            return results
        }
    val dashboardCount: Int
        get() {
            val selectQuery = "SELECT COUNT(*) AS TOTAL from " + TABLE_DASHBOARD
            val db = this.readableDatabase
            val cursor = db.rawQuery(selectQuery, null)
            cursor.moveToFirst()
            val result = cursor.getInt(cursor.getColumnIndex("TOTAL"))
            cursor.close()
            return result
        }

    fun housekeepWidgets(appWidgetIds: ArrayList<String>) {
        val db = this.writableDatabase
        db.beginTransaction()
        val idList = appWidgetIds.toString()
        val csv = idList.substring(1, idList.length - 1)
        val sql = "DELETE FROM " + TABLE_WIDGET + " WHERE WIDGET_ID NOT IN(" + csv + " )"
        Log.d("YouQi", "Housekeep: " + "DELETE FROM " + TABLE_WIDGET + " WHERE WIDGET_ID NOT IN(" + csv + " )")
        try {
            db.execSQL(sql)
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            db.endTransaction()
        }
    }

    fun insertWidget(widgetId: Int, entity: Entity) {
        val db = this.writableDatabase
        db.beginTransaction()
        try {
            val initialValues = ContentValues()
            initialValues.put("WIDGET_ID", widgetId)
            initialValues.put("ENTITY_ID", entity.entityId)
            initialValues.put("FRIENDLY_STATE", entity.friendlyName)
            initialValues.put("FRIENDLY_NAME", entity.friendlyName)
            initialValues.put("LAST_UPDATED", 1)
            db.insert(TABLE_WIDGET, null, initialValues)
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            db.endTransaction()
        }
    }

    fun getEntityById(entityId: String): Entity? {
        //String selectQuery = "SELECT * from " + TABLE_ENTITY + " WHERE ENTITY_ID='" + entityId + "'";
        val selectQuery = "SELECT * FROM entities WHERE ENTITY_ID='$entityId'"
        val db = this.readableDatabase
        val cursor = db.rawQuery(selectQuery, null)
        var entity: Entity? = null
        if (cursor.moveToFirst()) {
            entity = Entity.getInstance(cursor)
        }
        cursor.close()
        return entity
    }

    fun getWidgetById(appWidgetId: Int): Widget? {
        //String selectQuery = "SELECT * from " + TABLE_ENTITY + " WHERE ENTITY_ID='" + entityId + "'";
        val selectQuery =
            "SELECT a.*, b.RAW_JSON FROM widgets a LEFT JOIN entities b ON a.ENTITY_ID=b.ENTITY_ID WHERE a.WIDGET_ID='$appWidgetId' AND b.ENTITY_ID IS NOT NULL"
        val db = this.readableDatabase
        val cursor = db.rawQuery(selectQuery, null)
        var widget: Widget? = null
        if (cursor.moveToFirst()) {
            widget = Widget.getInstance(cursor)
            widget.appWidgetId = appWidgetId
        }
        cursor.close()
        return widget
    }

    fun getWidgetIdsByEntityId(entityId: String): ArrayList<Int> {
        //String selectQuery = "SELECT * from " + TABLE_ENTITY + " WHERE ENTITY_ID='" + entityId + "'";
        val selectQuery = "SELECT WIDGET_ID FROM widgets WHERE ENTITY_ID='$entityId'"
        val db = this.readableDatabase
        val cursor = db.rawQuery(selectQuery, null)
        val results = ArrayList<Int>()
        if (cursor.moveToFirst()) {
            val widgetId = cursor.getInt(cursor.getColumnIndex("WIDGET_ID"))
            results.add(widgetId)
        }
        cursor.close()
        return results
    }

    //String selectQuery = "SELECT * from " + TABLE_ENTITY + " WHERE ENTITY_ID='" + entityId + "'";
    val widgets: ArrayList<Widget>?
        get() {
            //String selectQuery = "SELECT * from " + TABLE_ENTITY + " WHERE ENTITY_ID='" + entityId + "'";
            val selectQuery =
                "SELECT a.*, b.RAW_JSON FROM widgets a LEFT JOIN entities b ON a.ENTITY_ID=b.ENTITY_ID WHERE b.ENTITY_ID IS NOT NULL"
            val db = this.readableDatabase
            val cursor = db.rawQuery(selectQuery, null)
            val widgets: ArrayList<Widget>? = null
            if (cursor.moveToFirst()) {
                val widget = Widget.getInstance(cursor)
                if (widget != null) widgets!!.add(widget)
            }
            cursor.close()
            return widgets
        }

    fun getDashboard(groupId: Int): ArrayList<Entity> {
        val results = ArrayList<Entity>()
        val db = this.readableDatabase
        val selectQuery =
            "SELECT b.*, a.DISPLAY_ORDER FROM dashboards a LEFT JOIN entities b ON a.ENTITY_ID=b.ENTITY_ID WHERE a.DASHBOARD_ID=$groupId AND b.ENTITY_ID IS NOT NULL ORDER BY a.DISPLAY_ORDER ASC"
        val cursor = db.rawQuery(selectQuery, null)
        val gson = Gson()

        // looping through all rows and adding to list
        if (cursor.moveToFirst()) {
            do {
                val entity = Entity.getInstance(cursor)
                if (entity != null) results.add(entity)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return results
    }

    companion object {
        private const val DATABASE_VERSION = 8
        private const val DATABASE_NAME = "HOMEASSISTANT"
        private var sInstance: DatabaseManager? = null
        private const val TABLE_CONNECTION = "connections"
        private const val TABLE_DASHBOARD = "dashboards"
        private const val TABLE_ENTITY = "entities"
        private const val TABLE_GROUP = "groups"
        private const val TABLE_WIDGET = "widgets"
        @Synchronized
        fun getInstance(context: Context): DatabaseManager? {
            if (sInstance == null) {
                sInstance = DatabaseManager(context.applicationContext)
            }
            return sInstance
        }
    }
}