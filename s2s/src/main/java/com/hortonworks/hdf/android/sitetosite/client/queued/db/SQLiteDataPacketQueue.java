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
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.hortonworks.hdf.android.sitetosite.client.SiteToSiteClient;
import com.hortonworks.hdf.android.sitetosite.client.SiteToSiteClientConfig;
import com.hortonworks.hdf.android.sitetosite.client.Transaction;
import com.hortonworks.hdf.android.sitetosite.client.TransactionResult;
import com.hortonworks.hdf.android.sitetosite.client.persistence.SQLiteIOException;
import com.hortonworks.hdf.android.sitetosite.client.persistence.SiteToSiteDB;
import com.hortonworks.hdf.android.sitetosite.client.queued.AbstractQueuedSiteToSiteClient;
import com.hortonworks.hdf.android.sitetosite.client.queued.DataPacketPrioritizer;
import com.hortonworks.hdf.android.sitetosite.packet.DataPacket;
import com.hortonworks.hdf.android.sitetosite.util.Charsets;
import com.hortonworks.hdf.android.sitetosite.util.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.hortonworks.hdf.android.sitetosite.client.persistence.SiteToSiteDBConstants.*;

public class SQLiteDataPacketQueue extends AbstractQueuedSiteToSiteClient {
    private static final String CANONICAL_NAME = SQLiteDataPacketQueue.class.getCanonicalName();
    private static final String AGE_OFF_ROW_COUNT_QUERY = new StringBuilder("DELETE FROM ").append(DATA_PACKET_QUEUE_TABLE_NAME)
            .append(" WHERE ").append(ID_COLUMN)
            .append(" IN (SELECT ").append(ID_COLUMN)
            .append(" FROM ").append(DATA_PACKET_QUEUE_TABLE_NAME)
            .append(" ORDER BY ").append(DATA_PACKET_QEUE_PRIORITY_COLUMN).append(" ASC, ").append(CREATED_COLUMN).append(" ASC, ").append(ID_COLUMN).append(" ASC")
            .append(" LIMIT ?)").toString();

    private final SiteToSiteClientConfig siteToSiteClientConfig;
    private final SiteToSiteDB siteToSiteDB;
    private final DataPacketPrioritizer dataPacketPrioritizer;
    private final long maxRows;
    private final long maxSize;
    private final int iteratorSizeLimit;
    private final long maxTransactionTimeMillis;

    public SQLiteDataPacketQueue(SiteToSiteClientConfig siteToSiteClientConfig, SiteToSiteDB siteToSiteDB, DataPacketPrioritizer dataPacketPrioritizer, long maxRows, long maxSize, long maxTransactionTimeMillis) {
        this.siteToSiteClientConfig = siteToSiteClientConfig;
        this.siteToSiteDB = siteToSiteDB;
        this.dataPacketPrioritizer = dataPacketPrioritizer;
        this.maxRows = maxRows;
        this.maxSize = maxSize;
        this.iteratorSizeLimit = siteToSiteClientConfig.getPreferredBatchCount();
        this.maxTransactionTimeMillis = maxTransactionTimeMillis;
    }

    private static SQLiteStatement buildDeleteQuery(SQLiteDatabase database, int numIds) {
        StringBuilder queryBuilder = new StringBuilder("DELETE FROM ").append(DATA_PACKET_QUEUE_TABLE_NAME).append(" WHERE ").append(ID_COLUMN).append(" IN (");
        for (int i = 0; i < numIds; i++) {
            queryBuilder.append("?, ");
        }
        queryBuilder.setLength(queryBuilder.length() - 2);
        return database.compileStatement(queryBuilder.append(")").toString());
    }

    private static int executeDeleteQuery(SQLiteStatement sqLiteStatement, long[] ids, int numIds) {
        sqLiteStatement.clearBindings();
        for (int i = 0; i < numIds; i++) {
            sqLiteStatement.bindLong(i + 1, ids[i]);
        }
        return sqLiteStatement.executeUpdateDelete();
    }

    @Override
    public void enqueue(Iterator<DataPacket> dataPackets) throws IOException {
        if (!dataPackets.hasNext()) {
            return;
        }
        SQLiteDatabase writableDatabase = siteToSiteDB.getWritableDatabase();
        try {
            writableDatabase.beginTransaction();
            try {
                while (dataPackets.hasNext()) {
                    DataPacket dataPacket = dataPackets.next();
                    ContentValues contentValues = new ContentValues();
                    long createdTime = new Date().getTime();
                    contentValues.put(CREATED_COLUMN, createdTime);
                    contentValues.put(DATA_PACKET_QEUE_PRIORITY_COLUMN, dataPacketPrioritizer.getPriority(dataPacket));
                    contentValues.put(DATA_PACKET_QUEUE_ATTRIBUTES_COLUMN, getAttributesBytes(dataPacket));
                    InputStream inputStream = dataPacket.getData();
                    try {
                        contentValues.put(CONTENT_COLUMN, IOUtils.readInputStream(inputStream));
                    } finally {
                        inputStream.close();
                    }
                    long ttl = dataPacketPrioritizer.getTtl(dataPacket);
                    if (ttl < 0) {
                        contentValues.put(EXPIRATION_MILLIS_COLUMN, Long.MAX_VALUE);
                    } else {
                        contentValues.put(EXPIRATION_MILLIS_COLUMN, createdTime + ttl);
                    }
                    writableDatabase.insertOrThrow(DATA_PACKET_QUEUE_TABLE_NAME, null, contentValues);
                }
                writableDatabase.setTransactionSuccessful();
            } finally {
                writableDatabase.endTransaction();
            }
        } catch (SQLiteException e) {
            throw new SQLiteIOException("Error inserting data packets.", e);
        } finally {
            writableDatabase.close();
        }
    }

    protected byte[] getAttributesBytes(DataPacket dataPacket) throws IOException {
        JSONObject attributesObject = new JSONObject();
        for (Map.Entry<String, String> entry : dataPacket.getAttributes().entrySet()) {
            try {
                attributesObject.put(entry.getKey(), entry.getValue());
            } catch (JSONException e) {
                throw new IOException("Unable to put attribute value of \"" + entry.getValue() + "\" for key \"" + entry.getKey() + "\"");
            }
        }
        return attributesObject.toString().getBytes(Charsets.UTF_8);
    }

    @Override
    public void cleanup() throws SQLiteIOException {
        SQLiteDatabase writableDatabase = siteToSiteDB.getWritableDatabase();
        try {
            writableDatabase.beginTransaction();
            try {
                ageOffTtl(writableDatabase);
                ageOffRowCount(writableDatabase);
                ageOffSize(writableDatabase);
                writableDatabase.setTransactionSuccessful();
            } finally {
                writableDatabase.endTransaction();
            }
        } catch (SQLiteException e) {
            throw new SQLiteIOException("Unable to cleanup queued data packets.", e);
        } finally {
            writableDatabase.close();
        }
    }

    protected void ageOffTtl(SQLiteDatabase writableDatabase) {
        writableDatabase.execSQL("DELETE FROM " + DATA_PACKET_QUEUE_TABLE_NAME + " WHERE " + EXPIRATION_MILLIS_COLUMN + " <= ?", new Object[]{new Date().getTime()});
    }

    protected void ageOffRowCount(SQLiteDatabase writableDatabase) {
        if (maxRows > 0) {
            long rows = getNumRows(writableDatabase);
            if (rows > maxRows) {
                writableDatabase.execSQL(AGE_OFF_ROW_COUNT_QUERY, new Object[]{rows - maxRows});
            }
        }
    }

    protected long getNumRows(SQLiteDatabase writableDatabase) {
        Cursor cursor = writableDatabase.query(DATA_PACKET_QUEUE_TABLE_NAME, new String[]{"count(*) as rows"}, null, null, null, null, null);
        try {
            cursor.moveToNext();
            return cursor.getLong(cursor.getColumnIndex("rows"));
        } finally {
            cursor.close();
        }
    }

    protected void ageOffSize(SQLiteDatabase writableDatabase) {
        if (maxSize > 0) {
            Cursor cursor = null;
            try {
                cursor = writableDatabase.query(DATA_PACKET_QUEUE_TABLE_NAME,
                        new String[]{"sum(length(" + DATA_PACKET_QUEUE_ATTRIBUTES_COLUMN + ")) as attributesSize", "sum(length(" + CONTENT_COLUMN + ")) as contentSize"}, null, null, null, null, null);
                if (!cursor.moveToNext()) {
                    return;
                }
                long currentSize = cursor.getLong(cursor.getColumnIndex("attributesSize")) + cursor.getLong(cursor.getColumnIndex("contentSize"));
                cursor.close();

                while (currentSize > maxSize) {
                    cursor = writableDatabase.query(DATA_PACKET_QUEUE_TABLE_NAME, new String[]{ID_COLUMN, "length(" + DATA_PACKET_QUEUE_ATTRIBUTES_COLUMN + ") + length(" + CONTENT_COLUMN + ") as rowSize"},
                            null, null, null, null, DATA_PACKET_QEUE_PRIORITY_COLUMN + " ASC, " + CREATED_COLUMN + " ASC");

                    int idIndex = cursor.getColumnIndex(ID_COLUMN);
                    int rowSizeIndex = cursor.getColumnIndex("rowSize");

                    List<long[]> ids = new ArrayList<>();
                    boolean done = false;
                    for (int i = 0; i < 1000 && !done; i++) {
                        int i1 = 0;
                        long[] idArray = new long[100];
                        for (; i1 < 100 && !done; i1++) {
                            done = !(currentSize > maxSize && cursor.moveToNext());
                            if (!done) {
                                idArray[i1] = cursor.getLong(idIndex);
                                currentSize -= cursor.getLong(rowSizeIndex);
                            }
                        }
                        if (i1 == 100) {
                            ids.add(idArray);
                        } else {
                            ids.add(Arrays.copyOf(idArray, i1));
                        }
                    }
                    cursor.close();
                    SQLiteStatement deleteQuery = null;
                    int lastLength = 0;
                    for (long[] idArray : ids) {
                        if (deleteQuery == null || lastLength != idArray.length) {
                            deleteQuery = buildDeleteQuery(writableDatabase, idArray.length);
                            lastLength = idArray.length;
                        }
                        executeDeleteQuery(deleteQuery, idArray, idArray.length);
                    }
                }
            } finally {
                if (cursor != null && !cursor.isClosed()) {
                    cursor.close();
                }
            }
        }
    }

    @Override
    public void process() throws IOException {
        SQLiteDatabase writableDatabase = siteToSiteDB.getWritableDatabase();
        writableDatabase.beginTransaction();
        try {
            long currentTime = new Date().getTime();
            writableDatabase.execSQL("UPDATE " + DATA_PACKET_QUEUE_TABLE_NAME +
                    " SET " + DATA_PACKET_QUEUE_TRANSACTION_COLUMN + " = NULL" +
                    " WHERE " + DATA_PACKET_QUEUE_TRANSACTION_COLUMN +
                    " IN (SELECT " + DATA_PACKET_QUEUE_TRANSACTION_COLUMN +
                    " FROM " + DATA_PACKET_QUEUE_TRANSACTIONS_TABLE_NAME +
                    " WHERE " + EXPIRATION_MILLIS_COLUMN + " < ?)", new Object[]{currentTime});
            writableDatabase.delete(DATA_PACKET_QUEUE_TRANSACTIONS_TABLE_NAME, EXPIRATION_MILLIS_COLUMN + " < ?", new String[]{Long.toString(currentTime)});
            writableDatabase.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new SQLiteIOException("Unable to clear expired transactions.", e);
        } finally {
            writableDatabase.endTransaction();
            writableDatabase.close();
        }
        SiteToSiteClient siteToSiteClient = siteToSiteClientConfig.createClient();
        while (doProcess(siteToSiteClient)) {
            Log.d(CANONICAL_NAME, " processed batch of transactions");
        }
    }

    protected boolean doProcess(SiteToSiteClient siteToSiteClient) throws IOException {
        SQLiteDataPacketIterator sqLiteDataPacketIterator = getSqLiteDataPacketIterator();
        if (!sqLiteDataPacketIterator.hasNext()) {
            return false;
        }
        TransactionResult transactionResult = null;
        Transaction transaction = siteToSiteClient.createTransaction();
        try {
            while (sqLiteDataPacketIterator.hasNext()) {
                transaction.send(sqLiteDataPacketIterator.next());
            }
            transaction.confirm();
            transactionResult = transaction.complete();
        } catch (IOException e) {
            sqLiteDataPacketIterator.transactionFailed();
            throw e;
        }
        if (transactionResult != null) {
            sqLiteDataPacketIterator.transactionComplete();
            return true;
        }
        return false;
    }

    protected SQLiteDataPacketIterator getSqLiteDataPacketIterator() throws SQLiteIOException {
        return new SQLiteDataPacketIterator(siteToSiteDB, iteratorSizeLimit, new Date().getTime() + maxTransactionTimeMillis);
    }
}
