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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import static com.hortonworks.hdf.android.sitetosite.client.persistence.SiteToSiteDBConstants.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SiteToSiteDBTestUtil {
    public static SiteToSiteDB getCleanSiteToSiteDB(Context context) {
        SiteToSiteDB siteToSiteDB = new SiteToSiteDB(context);
        SQLiteDatabase writableDatabase = siteToSiteDB.getWritableDatabase();
        try {
            writableDatabase.delete(DATA_PACKET_QUEUE_TABLE_NAME, null, null);
            writableDatabase.delete(PEER_STATUSES_TABLE_NAME, null, null);
        } finally {
            writableDatabase.close();
        }
        return siteToSiteDB;
    }

    public static void assertNoQueuedPackets(SiteToSiteDB siteToSiteDB) {
        assertQueuedPacketCount(siteToSiteDB, 0);
    }

    public static void assertQueuedPacketCount(SiteToSiteDB siteToSiteDB, long count) {
        assertEquals(count, getQueuedPacketCount(siteToSiteDB));
    }

    private static long getQueuedPacketCount(SiteToSiteDB siteToSiteDB) {
        SQLiteDatabase readableDatabase = siteToSiteDB.getReadableDatabase();
        try {
            Cursor query = readableDatabase.query(DATA_PACKET_QUEUE_TABLE_NAME, new String[]{"count(*) as numRows"}, null, null, null, null, null);
            try {
                assertTrue(query.moveToNext());
                return query.getLong(query.getColumnIndex("numRows"));
            } finally {
                query.close();
            }
        } finally {
            readableDatabase.close();
        }
    }
}
