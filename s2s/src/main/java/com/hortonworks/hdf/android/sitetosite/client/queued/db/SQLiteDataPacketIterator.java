/*
 * Copyright 2017 Hortonworks, Inc.
 * All rights reserved.
 *
 *   Hortonworks, Inc. licenses this file to you under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License. You may obtain a copy of the License at
 *   http://www.apache.org/licenses/LICENSE-2.0
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 * See the associated NOTICE file for additional information regarding copyright ownership.
 */

package com.hortonworks.hdf.android.sitetosite.client.queued.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import com.hortonworks.hdf.android.sitetosite.client.persistence.SQLiteIOException;
import com.hortonworks.hdf.android.sitetosite.client.persistence.SiteToSiteDB;
import com.hortonworks.hdf.android.sitetosite.packet.ByteArrayDataPacket;
import com.hortonworks.hdf.android.sitetosite.packet.DataPacket;
import com.hortonworks.hdf.android.sitetosite.util.Charsets;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static com.hortonworks.hdf.android.sitetosite.client.persistence.SiteToSiteDBConstants.*;

public class SQLiteDataPacketIterator {
    public static final String CANONICAL_NAME = SQLiteDataPacketIterator.class.getCanonicalName();
    public static final String MARK_ROWS_FOR_TRANSACTION_QUERY = new StringBuilder("UPDATE ").append(DATA_PACKET_QUEUE_TABLE_NAME)
            .append(" SET ").append(DATA_PACKET_QUEUE_TRANSACTION_COLUMN).append(" = ?")
            .append(" WHERE ").append(ID_COLUMN).append(" IN ")
            .append("(SELECT ").append(ID_COLUMN).append(" FROM ").append(DATA_PACKET_QUEUE_TABLE_NAME)
            .append(" WHERE ").append(EXPIRATION_MILLIS_COLUMN).append(" > ?")
            .append(" AND ").append(DATA_PACKET_QUEUE_TRANSACTION_COLUMN).append(" IS NULL")
            .append(" ORDER BY ").append(DATA_PACKET_QEUE_PRIORITY_COLUMN).append(" DESC, ").append(CREATED_COLUMN).append(" DESC, ").append(ID_COLUMN).append(" DESC")
            .append(" LIMIT ?)").toString();

    private final SiteToSiteDB siteToSiteDB;
    private final long transactionId;
    private final SQLiteDatabase readableDatabase;
    private final Cursor cursor;
    private final int attributesIndex;
    private final int contentIndex;
    private boolean hasNext;

    public SQLiteDataPacketIterator(SiteToSiteDB siteToSiteDB, int limit, long expirationMillis) throws SQLiteIOException {
        this.siteToSiteDB = siteToSiteDB;
        SQLiteDatabase writableDatabase = siteToSiteDB.getWritableDatabase();
        writableDatabase.beginTransaction();
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put(EXPIRATION_MILLIS_COLUMN, expirationMillis);
            transactionId = writableDatabase.insert(DATA_PACKET_QUEUE_TRANSACTIONS_TABLE_NAME, null, contentValues);
            writableDatabase.execSQL(MARK_ROWS_FOR_TRANSACTION_QUERY, new Object[] {transactionId, new Date().getTime(), limit});
            writableDatabase.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new SQLiteIOException("Unable to create transaction.", e);
        } finally {
            writableDatabase.endTransaction();
            writableDatabase.close();
        }
        this.readableDatabase = siteToSiteDB.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = readableDatabase.query(false, DATA_PACKET_QUEUE_TABLE_NAME, new String[]{DATA_PACKET_QUEUE_ATTRIBUTES_COLUMN, CONTENT_COLUMN},
                    DATA_PACKET_QUEUE_TRANSACTION_COLUMN + " = ?", new String[]{Long.toString(transactionId)}, null, null,
                    DATA_PACKET_QEUE_PRIORITY_COLUMN + " DESC, " + CREATED_COLUMN + " DESC, " + ID_COLUMN + " DESC", null);
            this.cursor = cursor;
            this.attributesIndex = cursor.getColumnIndex(DATA_PACKET_QUEUE_ATTRIBUTES_COLUMN);
            this.contentIndex = cursor.getColumnIndex(CONTENT_COLUMN);
        } catch (SQLiteException e){
            if (cursor != null) {
                cursor.close();
            }
            readableDatabase.close();
            throw new SQLiteIOException("Unable to query " + DATA_PACKET_QUEUE_TABLE_NAME + " for queued packets.", e);
        }
        hasNext = cursor.moveToNext();
        if (!hasNext) {
            close();
        }
    }

    public boolean hasNext() {
        return hasNext;
    }

    public DataPacket next() throws SQLiteIOException {
        try {
            String json = new String(cursor.getBlob(attributesIndex), Charsets.UTF_8);
            Map<String, String> attributes = new HashMap<>();
            try {
                JSONObject attributesObject = new JSONObject(json);
                Iterator<String> keys = attributesObject.keys();
                while (keys.hasNext()) {
                    String name = keys.next();
                    attributes.put(name, attributesObject.getString(name));
                }
            } catch (JSONException e) {
                Log.w(CANONICAL_NAME, "JSON errors shouldn't happen here as same library was responsible for inserting well-formed JSON: " + json, e);
            }
            byte[] data = cursor.getBlob(contentIndex);
            hasNext = cursor.moveToNext();
            return new ByteArrayDataPacket(attributes, data);
        } catch (SQLiteException e) {
            throw new SQLiteIOException("Unable to read data packet from cursor.", e);
        }
    }

    public void transactionComplete() throws SQLiteIOException {
        close();
        SQLiteDatabase writableDatabase = siteToSiteDB.getWritableDatabase();
        writableDatabase.beginTransaction();
        try {
            writableDatabase.delete(DATA_PACKET_QUEUE_TABLE_NAME, DATA_PACKET_QUEUE_TRANSACTION_COLUMN + " = ?", new String[] {Long.toString(transactionId)});
            writableDatabase.delete(DATA_PACKET_QUEUE_TRANSACTIONS_TABLE_NAME, DATA_PACKET_QUEUE_TRANSACTION_COLUMN + " = ?", new String[] {Long.toString(transactionId)});
            writableDatabase.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new SQLiteIOException("Unable to delete sent data packets, data may be duplicated.", e);
        } finally {
            writableDatabase.endTransaction();
            writableDatabase.close();
        }
    }

    public void transactionFailed() throws SQLiteIOException {
        close();
        SQLiteDatabase writableDatabase = siteToSiteDB.getWritableDatabase();
        writableDatabase.beginTransaction();
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.putNull(DATA_PACKET_QUEUE_TRANSACTION_COLUMN);
            writableDatabase.update(DATA_PACKET_QUEUE_TABLE_NAME, contentValues, DATA_PACKET_QUEUE_TRANSACTION_COLUMN + " = ?", new String[] {Long.toString(transactionId)});
            writableDatabase.delete(DATA_PACKET_QUEUE_TRANSACTIONS_TABLE_NAME, DATA_PACKET_QUEUE_TRANSACTION_COLUMN + " = ?", new String[] {Long.toString(transactionId)});
            writableDatabase.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new SQLiteIOException("Unable to clear transaction from failed data packets.", e);
        } finally {
            writableDatabase.endTransaction();
            writableDatabase.close();
        }
    }

    private void close() {
        cursor.close();
        readableDatabase.close();
    }
}
