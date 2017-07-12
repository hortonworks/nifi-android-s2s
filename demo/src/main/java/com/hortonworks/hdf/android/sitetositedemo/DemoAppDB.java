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

package com.hortonworks.hdf.android.sitetositedemo;

import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Parcel;

import com.hortonworks.hdf.android.sitetosite.client.TransactionResult;
import com.hortonworks.hdf.android.sitetosite.service.SiteToSiteRepeatableIntent;
import com.hortonworks.hdf.android.sitetosite.util.SerializationUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.hortonworks.hdf.android.sitetosite.client.persistence.SiteToSiteDBConstants.*;

public class DemoAppDB {
    public static final String TRANSACTION_LOG_ENTRY_SAVED = DemoAppDB.class.getCanonicalName() + ".save(transactionLogEntry)";

    public static final String PENDING_INTENT_TABLE_NAME = "APACHE_NIFI_SITE_TO_SITE_PENDING_INTENTS";
    public static final String PENDING_INTENT_REQUEST_CODE = "REQUEST_CODE";

    public static final String S2S_TABLE_NAME = "APACHE_NIFI_SITE_TO_SITE_LOG";
    public static final String S2S_TRANSACTION_RESULT_COLUMN = "TRANSACTION_RESULT";
    public static final String S2S_IO_EXCEPTION_COLUMN = "IO_EXCEPTION";

    public static final int VERSION = 1;

    private static SQLiteOpenHelper sqLiteOpenHelper;

    private final Context context;

    public DemoAppDB(Context context) {
        this.context = context;
        synchronized (DemoAppDB.class) {
            if (sqLiteOpenHelper == null) {
                sqLiteOpenHelper = new SQLiteOpenHelper(context, DemoAppDB.class.getSimpleName() + ".db", null, VERSION) {
                    @Override
                    public void onCreate(SQLiteDatabase db) {
                        db.execSQL("CREATE TABLE " + S2S_TABLE_NAME + " (" +
                                ID_COLUMN + " INTEGER PRIMARY KEY, " +
                                CREATED_COLUMN + " INTEGER, " +
                                S2S_TRANSACTION_RESULT_COLUMN + " BLOB, " +
                                S2S_IO_EXCEPTION_COLUMN + " BLOB)");
                        db.execSQL("CREATE TABLE " + PENDING_INTENT_TABLE_NAME + " (" +
                                ID_COLUMN + " INTEGER PRIMARY KEY, " +
                                PENDING_INTENT_REQUEST_CODE + " INTEGER, " +
                                CONTENT_COLUMN + " BLOB)");
                    }

                    @Override
                    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

                    }
                };
            }
        }
    }

    /**
     * Saves the transactionLogEntry
     *
     * @param transactionLogEntry the transactionLogEntry
     */
    public void save(TransactionLogEntry transactionLogEntry) {
        SQLiteDatabase writableDatabase = sqLiteOpenHelper.getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(CREATED_COLUMN, transactionLogEntry.getCreated().getTime());
            values.put(S2S_TRANSACTION_RESULT_COLUMN, SerializationUtils.marshallParcelable(transactionLogEntry.getTransactionResult()));
            values.put(S2S_IO_EXCEPTION_COLUMN, SerializationUtils.marshallSerializable(transactionLogEntry.getIoException()));
            transactionLogEntry.setId(writableDatabase.insertOrThrow(S2S_TABLE_NAME, null, values));
            context.sendBroadcast(new Intent(TRANSACTION_LOG_ENTRY_SAVED));
        } finally {
            writableDatabase.close();
        }
    }

    /**
     * Gets all transactionLogEntries with a timestamp after the timestamp parameter
     *
     * @param lastTimestamp the timestamp
     * @return all transactionLogEntries after the timestamp
     */
    public List<TransactionLogEntry> getLogEntries(long lastTimestamp) {
        List<TransactionLogEntry> transactionLogEntries = new ArrayList<>();
        SQLiteDatabase readableDatabase = sqLiteOpenHelper.getReadableDatabase();
        try {
            Cursor cursor = readableDatabase.query(false, S2S_TABLE_NAME, new String[]{ID_COLUMN, CREATED_COLUMN, S2S_TRANSACTION_RESULT_COLUMN, S2S_IO_EXCEPTION_COLUMN}, CREATED_COLUMN + " > ?", new String[]{Long.toString(lastTimestamp)}, null, null, "CREATED", null);
            try {
                int idIndex = cursor.getColumnIndexOrThrow(ID_COLUMN);
                int createdIndex = cursor.getColumnIndexOrThrow(CREATED_COLUMN);
                int transactionResultIndex = cursor.getColumnIndexOrThrow(S2S_TRANSACTION_RESULT_COLUMN);
                int ioeIndex = cursor.getColumnIndexOrThrow(S2S_IO_EXCEPTION_COLUMN);
                while (cursor.moveToNext()) {
                    transactionLogEntries.add(new TransactionLogEntry(cursor.getLong(idIndex),
                            new Date(cursor.getLong(createdIndex)),
                            SerializationUtils.unmarshallParcelable(cursor.getBlob(transactionResultIndex), TransactionResult.class),
                            SerializationUtils.<IOException>unmarshallSerializable(cursor.getBlob(ioeIndex))));
                }
            } finally {
                cursor.close();
            }
        } finally {
            readableDatabase.close();
        }
        return transactionLogEntries;
    }


    /**
     * Saves the repeatable intent (useful for later cancelling an alarm after the application has restarted)
     *
     * @param siteToSiteRepeatableIntent the repeatable intent
     */
    public void save(SiteToSiteRepeatableIntent siteToSiteRepeatableIntent) {
        Parcel parcel = Parcel.obtain();
        siteToSiteRepeatableIntent.getIntent().writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        byte[] bytes = parcel.marshall();
        SQLiteDatabase writableDatabase = sqLiteOpenHelper.getWritableDatabase();
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put(PENDING_INTENT_REQUEST_CODE, siteToSiteRepeatableIntent.getRequestCode());
            contentValues.put(CONTENT_COLUMN, bytes);
            writableDatabase.insertOrThrow(PENDING_INTENT_TABLE_NAME, null, contentValues);
        } finally {
            writableDatabase.close();
        }
    }

    /**
     * Deletes the pending intent with the given id
     *
     * @param id the id
     */
    public void deletePendingIntent(long id) {
        SQLiteDatabase writableDatabase = sqLiteOpenHelper.getWritableDatabase();
        try {
            writableDatabase.delete(PENDING_INTENT_TABLE_NAME, "ID = ?",  new String[]{Long.toString(id)});
        } finally {
            writableDatabase.close();
        }
    }

    /**
     * Retreives all pending intents that have been saved
     *
     * @return the pending intents
     */
    public List<PendingIntentWrapper> getPendingIntents() {
        List<PendingIntentWrapper> pendingIntents = new ArrayList<>();
        SQLiteDatabase readableDatabase = sqLiteOpenHelper.getReadableDatabase();
        try {
            Cursor cursor = readableDatabase.query(false, PENDING_INTENT_TABLE_NAME, new String[]{ID_COLUMN, PENDING_INTENT_REQUEST_CODE, CONTENT_COLUMN}, null, null, null, null, null, null);
            try {
                int idIndex = cursor.getColumnIndexOrThrow(ID_COLUMN);
                int requestCodeIndex = cursor.getColumnIndexOrThrow(PENDING_INTENT_REQUEST_CODE);
                int contentIndex = cursor.getColumnIndexOrThrow(CONTENT_COLUMN);
                while (cursor.moveToNext()) {
                    Parcel parcel = Parcel.obtain();
                    byte[] bytes = cursor.getBlob(contentIndex);
                    parcel.unmarshall(bytes, 0, bytes.length);
                    parcel.setDataPosition(0);
                    int requestCode = cursor.getInt(requestCodeIndex);
                    Intent intent = Intent.CREATOR.createFromParcel(parcel);
                    pendingIntents.add(new PendingIntentWrapper(cursor.getLong(idIndex), PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_NO_CREATE)));
                }
            } finally {
                cursor.close();
            }
        } finally {
            readableDatabase.close();
        }
        return pendingIntents;
    }
}
