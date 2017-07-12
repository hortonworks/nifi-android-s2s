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

package com.hortonworks.hdf.android.sitetosite.client.persistence;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import static com.hortonworks.hdf.android.sitetosite.client.persistence.SiteToSiteDBConstants.*;

class SiteToSiteSQLiteOpenHelper extends SQLiteOpenHelper {

    SiteToSiteSQLiteOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version, null);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + PEER_STATUSES_TABLE_NAME + " (" +
                PEER_STATUS_URLS_COLUMN + " TEXT, " +
                PEER_STATUS_PROXY_HOST_COLUMN + " TEXT, " +
                PEER_STATUS_PROXY_PORT_COLUMN + " INTEGER, " +
                CONTENT_COLUMN + " BLOB, " +
                EXPIRATION_MILLIS_COLUMN + " INTEGER, " +
                "PRIMARY KEY(" + PEER_STATUS_URLS_COLUMN + ", " + PEER_STATUS_PROXY_HOST_COLUMN + ", " + PEER_STATUS_PROXY_PORT_COLUMN + "))");
        db.execSQL("CREATE INDEX " + PEER_STATUSES_TABLE_NAME + "_" + EXPIRATION_MILLIS_COLUMN + "_index ON " + PEER_STATUSES_TABLE_NAME + "(" + EXPIRATION_MILLIS_COLUMN + ")");

        db.execSQL("CREATE TABLE " + DATA_PACKET_QUEUE_TABLE_NAME + "(" +
                ID_COLUMN + " INTEGER PRIMARY KEY, " +
                CREATED_COLUMN + " INTEGER, " +
                DATA_PACKET_QEUE_PRIORITY_COLUMN + " INTEGER, " +
                DATA_PACKET_QUEUE_ATTRIBUTES_COLUMN + " BLOB, " +
                CONTENT_COLUMN + " BLOB, " +
                DATA_PACKET_QUEUE_TRANSACTION_COLUMN + " INTEGER, " +
                EXPIRATION_MILLIS_COLUMN + " INTEGER)");
        db.execSQL("CREATE INDEX " + DATA_PACKET_QUEUE_TABLE_NAME + "_" + DATA_PACKET_QUEUE_TRANSACTION_COLUMN + "_index ON " + DATA_PACKET_QUEUE_TABLE_NAME + "(" + DATA_PACKET_QUEUE_TRANSACTION_COLUMN + ")");
        db.execSQL("CREATE INDEX " + DATA_PACKET_QUEUE_TABLE_NAME + "_" + EXPIRATION_MILLIS_COLUMN + "_index ON " + DATA_PACKET_QUEUE_TABLE_NAME + "(" + EXPIRATION_MILLIS_COLUMN + ")");
        db.execSQL("CREATE INDEX " + DATA_PACKET_QUEUE_TABLE_NAME + "_sort_index ON " + DATA_PACKET_QUEUE_TABLE_NAME + "(" + DATA_PACKET_QEUE_PRIORITY_COLUMN + ", " + CREATED_COLUMN + ", " + ID_COLUMN + ")");

        db.execSQL("CREATE TABLE " + DATA_PACKET_QUEUE_TRANSACTIONS_TABLE_NAME + "(" +
                DATA_PACKET_QUEUE_TRANSACTION_COLUMN + " INTEGER PRIMARY KEY, " +
                EXPIRATION_MILLIS_COLUMN + " INTEGER)");
        db.execSQL("CREATE INDEX " + DATA_PACKET_QUEUE_TRANSACTIONS_TABLE_NAME + "_" + EXPIRATION_MILLIS_COLUMN + "_index ON " + DATA_PACKET_QUEUE_TRANSACTIONS_TABLE_NAME + "(" + EXPIRATION_MILLIS_COLUMN + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
