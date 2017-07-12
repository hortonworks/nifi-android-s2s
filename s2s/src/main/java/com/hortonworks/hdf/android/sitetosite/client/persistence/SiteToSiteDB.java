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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Parcel;

import com.hortonworks.hdf.android.sitetosite.client.SiteToSiteClientConfig;
import com.hortonworks.hdf.android.sitetosite.client.SiteToSiteRemoteCluster;
import com.hortonworks.hdf.android.sitetosite.client.peer.PeerStatus;
import static com.hortonworks.hdf.android.sitetosite.client.persistence.SiteToSiteDBConstants.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Easily save and load state useful for site-to-site communication
 */
public class SiteToSiteDB {
    private static final int VERSION = 1;

    private static SQLiteOpenHelper sqLiteOpenHelper;

    //private final Context context;

    public SiteToSiteDB(Context context) {
        //this.context = context;
        synchronized (SiteToSiteDB.class) {
            if (sqLiteOpenHelper == null) {
                sqLiteOpenHelper = new SiteToSiteSQLiteOpenHelper(context, SiteToSiteDB.class.getSimpleName() + ".db", null, VERSION);
            }
        }
    }

    /**
     * Saves the peer status for a given url set and proxy
     *
     * @param siteToSiteClientConfig the configuration to save the peer status for
     */
    public void savePeerStatus(SiteToSiteClientConfig siteToSiteClientConfig) throws SQLiteIOException {
        SQLiteDatabase writableDatabase = sqLiteOpenHelper.getWritableDatabase();
        writableDatabase.beginTransaction();
        try {
            writableDatabase.execSQL("DELETE FROM " + PEER_STATUSES_TABLE_NAME + " WHERE " + EXPIRATION_MILLIS_COLUMN + " <= ?", new Object[]{new Date().getTime()});
            for (SiteToSiteRemoteCluster siteToSiteRemoteCluster : siteToSiteClientConfig.getRemoteClusters()) {
                PeerStatus peerStatus = siteToSiteRemoteCluster.getPeerStatus();
                if (peerStatus == null) {
                    continue;
                }

                Parcel parcel = Parcel.obtain();
                peerStatus.writeToParcel(parcel, 0);
                parcel.setDataPosition(0);
                byte[] bytes = parcel.marshall();

                String urlsString = getPeerUrlsString(siteToSiteRemoteCluster.getUrls());
                String proxyHost = siteToSiteRemoteCluster.getProxyHost();
                int proxyPort = siteToSiteRemoteCluster.getProxyPort();

                writableDatabase.delete(PEER_STATUSES_TABLE_NAME, PEER_STATUS_WHERE_CLAUSE, new String[]{urlsString, proxyHost, Integer.toString(proxyPort)});
                ContentValues values = new ContentValues();
                values.put(PEER_STATUS_URLS_COLUMN, urlsString);
                if (proxyHost == null || proxyHost.isEmpty()) {
                    values.putNull(PEER_STATUS_PROXY_HOST_COLUMN);
                    values.putNull(PEER_STATUS_PROXY_PORT_COLUMN);
                } else {
                    values.put(PEER_STATUS_PROXY_HOST_COLUMN, proxyHost);
                    values.put(PEER_STATUS_PROXY_PORT_COLUMN, proxyPort);
                }
                values.put(CONTENT_COLUMN, bytes);
                values.put(EXPIRATION_MILLIS_COLUMN, new Date().getTime() + siteToSiteClientConfig.getPeerUpdateInterval(TimeUnit.MILLISECONDS));
                writableDatabase.insertOrThrow(PEER_STATUSES_TABLE_NAME, null, values);
            }
            writableDatabase.setTransactionSuccessful();
        } catch (SQLiteException e) {
            throw new SQLiteIOException("Unable to store peer status in database.", e);
        } finally {
            writableDatabase.endTransaction();
            writableDatabase.close();
        }
    }

    /**
     * Gets the peer status for a given url set and proxy
     *
     * @param siteToSiteClientConfig the config to get peer status for
     */
    public void updatePeerStatusOnConfig(SiteToSiteClientConfig siteToSiteClientConfig) throws SQLiteIOException {
        SQLiteDatabase readableDatabase = sqLiteOpenHelper.getReadableDatabase();
        try {
            for (SiteToSiteRemoteCluster siteToSiteRemoteCluster : siteToSiteClientConfig.getRemoteClusters()) {
                PeerStatus origPeerStatus = siteToSiteRemoteCluster.getPeerStatus();

                List<String> parameters = new ArrayList<>();

                String peerUrlsString = getPeerUrlsString(siteToSiteRemoteCluster.getUrls());
                StringBuilder queryString = new StringBuilder(PEER_STATUS_URLS_COLUMN).append(" = ? AND ").append(PEER_STATUS_PROXY_HOST_COLUMN);
                parameters.add(peerUrlsString);

                String proxyHost = siteToSiteRemoteCluster.getProxyHost();
                if (proxyHost == null || proxyHost.isEmpty()) {
                    queryString.append(" IS NULL AND ").append(PEER_STATUS_PROXY_PORT_COLUMN).append(" IS NULL");
                } else {
                    queryString.append(" = ? AND ").append(PEER_STATUS_PROXY_PORT_COLUMN).append(" = ?");
                    parameters.add(proxyHost);
                    parameters.add(Integer.toString(siteToSiteRemoteCluster.getProxyPort()));
                }
                Cursor cursor;
                cursor = readableDatabase.query(false, PEER_STATUSES_TABLE_NAME, new String[]{CONTENT_COLUMN}, queryString.toString(),
                        parameters.toArray(new String[parameters.size()]), null, null, null, null);
                try {
                    int contentIndex = cursor.getColumnIndexOrThrow(CONTENT_COLUMN);
                    while (cursor.moveToNext()) {
                        byte[] bytes = cursor.getBlob(contentIndex);
                        Parcel parcel = Parcel.obtain();
                        parcel.unmarshall(bytes, 0, bytes.length);
                        parcel.setDataPosition(0);
                        PeerStatus dbPeerStatus = PeerStatus.CREATOR.createFromParcel(parcel);
                        if (dbPeerStatus != null && (origPeerStatus == null || origPeerStatus.getLastPeerUpdate() < dbPeerStatus.getLastPeerUpdate())) {
                            siteToSiteRemoteCluster.setPeerStatus(dbPeerStatus);
                            origPeerStatus = dbPeerStatus;
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (SQLiteException e) {
            throw new SQLiteIOException("Unable to read peer status from database.", e);
        } finally {
            readableDatabase.close();
        }
    }

    private String getPeerUrlsString(Set<String> peerUrlsPreference) {
        List<String> orderedUrls = new ArrayList<>(peerUrlsPreference);
        Collections.sort(orderedUrls);
        StringBuilder stringBuilder = new StringBuilder();
        for (String orderedUrl : orderedUrls) {
            stringBuilder.append(orderedUrl);
            stringBuilder.append(",");
        }
        if (orderedUrls.size() > 0) {
            stringBuilder.setLength(stringBuilder.length() - 1);
        }
        return stringBuilder.toString();
    }

    /**
     * Returns a readable sqlite db
     *
     * @return a readable sqlite db
     */
    public SQLiteDatabase getReadableDatabase() {
        return sqLiteOpenHelper.getReadableDatabase();
    }

    /**
     * Returns a writable sqlite db
     *
     * @return a writable sqlite db
     */
    public SQLiteDatabase getWritableDatabase() {
        return sqLiteOpenHelper.getWritableDatabase();
    }
}
