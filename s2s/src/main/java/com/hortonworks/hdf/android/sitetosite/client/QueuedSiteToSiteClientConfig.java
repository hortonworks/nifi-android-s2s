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

package com.hortonworks.hdf.android.sitetosite.client;

import android.content.Context;
import android.os.Parcel;

import com.hortonworks.hdf.android.sitetosite.client.persistence.SiteToSiteDB;
import com.hortonworks.hdf.android.sitetosite.client.queued.DataPacketPrioritizer;
import com.hortonworks.hdf.android.sitetosite.client.queued.NoOpDataPacketPrioritizer;
import com.hortonworks.hdf.android.sitetosite.client.queued.db.SQLiteDataPacketQueue;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class QueuedSiteToSiteClientConfig extends SiteToSiteClientConfig {
    public enum QueueType {
        DB("SQLite DB", new QueuedSiteToSiteClientFactory() {
            @Override
            public QueuedSiteToSiteClient create(Context context, QueuedSiteToSiteClientConfig queuedSiteToSiteClientConfig) throws IOException {
                return new SQLiteDataPacketQueue(queuedSiteToSiteClientConfig, new SiteToSiteDB(context), queuedSiteToSiteClientConfig.dataPacketPrioritizer,
                        queuedSiteToSiteClientConfig.maxRows, queuedSiteToSiteClientConfig.maxSize, queuedSiteToSiteClientConfig.maxTransactionTimeMillis);
            }
        });
        private final String displayName;
        private final QueuedSiteToSiteClientFactory factory;

        QueueType(String displayName, QueuedSiteToSiteClientFactory factory) {
            this.displayName = displayName;
            this.factory = factory;
        }
    }
    private long maxRows = 10000;
    private long maxSize = 1024 * maxRows;
    private long maxTransactionTimeMillis = TimeUnit.MINUTES.toMillis(10);
    private DataPacketPrioritizer dataPacketPrioritizer = new NoOpDataPacketPrioritizer();
    private QueueType queueType = QueueType.DB;

    public static final Creator<QueuedSiteToSiteClientConfig> CREATOR = new Creator<QueuedSiteToSiteClientConfig>() {
        @Override
        public QueuedSiteToSiteClientConfig createFromParcel(Parcel source) {
            QueuedSiteToSiteClientConfig result = new QueuedSiteToSiteClientConfig(SiteToSiteClientConfig.CREATOR.createFromParcel(source));
            result.maxRows = source.readLong();
            result.maxSize = source.readLong();
            result.maxTransactionTimeMillis = source.readLong();
            result.dataPacketPrioritizer = source.readParcelable(QueuedSiteToSiteClientConfig.class.getClassLoader());
            result.queueType = QueueType.valueOf(source.readString());
            return result;
        }

        @Override
        public QueuedSiteToSiteClientConfig[] newArray(int size) {
            return new QueuedSiteToSiteClientConfig[size];
        }
    };

    public QueuedSiteToSiteClientConfig() {

    }

    public QueuedSiteToSiteClientConfig(SiteToSiteClientConfig siteToSiteClientConfig) {
        super(siteToSiteClientConfig);
    }

    public long getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(long maxRows) {
        this.maxRows = maxRows;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
    }

    public DataPacketPrioritizer getDataPacketPrioritizer() {
        return dataPacketPrioritizer;
    }

    public void setDataPacketPrioritizer(DataPacketPrioritizer dataPacketPrioritizer) {
        this.dataPacketPrioritizer = dataPacketPrioritizer;
    }

    public QueueType getQueueType() {
        return queueType;
    }

    public void setQueueType(QueueType queueType) {
        this.queueType = queueType;
    }

    public long getMaxTransactionTime(TimeUnit timeUnit) {
        return timeUnit.convert(maxTransactionTimeMillis, TimeUnit.MILLISECONDS);
    }

    public void setMaxTransactionTime(long maxTransactionTime, TimeUnit duration) {
        this.maxTransactionTimeMillis = duration.toMillis(maxTransactionTime);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeLong(maxRows);
        dest.writeLong(maxSize);
        dest.writeLong(maxTransactionTimeMillis);
        dest.writeParcelable(dataPacketPrioritizer, 0);
        dest.writeString(queueType.name());
    }

    public QueuedSiteToSiteClient createQueuedClient(Context context) throws IOException {
        return queueType.factory.create(context, this);
    }
}
