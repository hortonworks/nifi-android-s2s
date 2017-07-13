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

package com.hortonworks.hdf.android.sitetosite.collectors;

import android.content.Context;
import android.os.Parcelable;

import com.hortonworks.hdf.android.sitetosite.client.QueuedSiteToSiteClientConfig;
import com.hortonworks.hdf.android.sitetosite.client.SiteToSiteClientConfig;
import com.hortonworks.hdf.android.sitetosite.packet.DataPacket;
import com.hortonworks.hdf.android.sitetosite.service.ParcelableQueuedOperationResultCallback;
import com.hortonworks.hdf.android.sitetosite.service.ParcelableTransactionResultCallback;

/**
 * Fetches data packets to send via site-to-site
 *
 * @see com.hortonworks.hdf.android.sitetosite.service.SiteToSiteRepeating
 * @see com.hortonworks.hdf.android.sitetosite.service.SiteToSiteRepeating#createSendPendingIntent(Context, DataCollector, SiteToSiteClientConfig, ParcelableTransactionResultCallback)
 * @see com.hortonworks.hdf.android.sitetosite.service.SiteToSiteRepeating#createEnqueuePendingIntent(Context, DataCollector, QueuedSiteToSiteClientConfig, ParcelableQueuedOperationResultCallback)
 */
public interface DataCollector extends Parcelable {

    /**
     * Returns the data packets
     *
     * @return the data packets
     */
    Iterable<DataPacket> getDataPackets();
}
