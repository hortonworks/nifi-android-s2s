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

package com.hortonworks.hdf.android.sitetosite.service;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Parcelable;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.hortonworks.hdf.android.sitetosite.client.QueuedSiteToSiteClientConfig;
import com.hortonworks.hdf.android.sitetosite.client.SiteToSiteClientConfig;
import com.hortonworks.hdf.android.sitetosite.client.TransactionResult;
import com.hortonworks.hdf.android.sitetosite.collectors.DataCollector;
import com.hortonworks.hdf.android.sitetosite.packet.DataPacket;
import com.hortonworks.hdf.android.sitetosite.service.SiteToSiteService.IntentType;
import com.hortonworks.hdf.android.sitetosite.util.SerializationUtils;

import java.io.IOException;
import java.util.Random;

public class SiteToSiteRepeating extends WakefulBroadcastReceiver {
    public static final String DATA_COLLECTOR = "DATA_COLLECTOR";
    public static final String REQUEST_CODE = "REQUEST_CODE";
    private static final Random random = new Random();

    @Override
    public void onReceive(final Context context, Intent intent) {
        DataCollector dataCollector = SerializationUtils.getParcelable(intent, DATA_COLLECTOR);
        SiteToSiteClientConfig siteToSiteClientConfig = SerializationUtils.getParcelable(intent, SiteToSiteService.SITE_TO_SITE_CONFIG);

        IntentType intentType = getIntentType(intent);
        Parcelable callback = SerializationUtils.getParcelable(intent, SiteToSiteService.TRANSACTION_RESULT_CALLBACK);
        int requestCode = getRequestCode(intent);

        Intent repeatingIntent;
        Iterable<DataPacket> dataPackets = null;
        // Update the pending intent with any state change in data collector
        if (dataCollector != null) {
            dataPackets = dataCollector.getDataPackets();
            repeatingIntent = getIntent(context, intentType, dataCollector, siteToSiteClientConfig, requestCode, callback);
            PendingIntent.getBroadcast(context, requestCode, repeatingIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            repeatingIntent = getIntent(context, intentType, dataCollector, siteToSiteClientConfig, requestCode, callback);
        }

        final Intent packetIntent;
        if (intentType == IntentType.SEND) {
            final ParcelableTransactionResultCallback transactionResultCallback = (ParcelableTransactionResultCallback) callback;
            packetIntent = SiteToSiteService.getSendIntent(context, dataPackets, siteToSiteClientConfig, new TransactionResultCallback() {
                @Override
                public Handler getHandler() {
                    return null;
                }

                @Override
                public void onSuccess(TransactionResult transactionResult) {
                    transactionResultCallback.onSuccess(context, transactionResult);
                }

                @Override
                public void onException(IOException exception) {
                    transactionResultCallback.onException(context, exception);
                }
            }, true);
        } else {
            final ParcelableQueuedOperationResultCallback parcelableQueuedOperationResultCallback = (ParcelableQueuedOperationResultCallback) callback;
            packetIntent = SiteToSiteService.getIntent(context, intentType, dataPackets, (QueuedSiteToSiteClientConfig) siteToSiteClientConfig, new QueuedOperationResultCallback() {
                @Override
                public Handler getHandler() {
                    return null;
                }

                @Override
                public void onSuccess() {
                    parcelableQueuedOperationResultCallback.onSuccess(context);
                }

                @Override
                public void onException(IOException exception) {
                    parcelableQueuedOperationResultCallback.onException(context, exception);
                }
            }, true);
        }
        startWakefulService(context, packetIntent);
    }

    /**
     * Creates a pending intent suitable for use with AlarmManager to schedule repeating site-to-site send packet operations
     *
     * @param context The current application environment @{@link Context} from which this service is being called.
     * @param dataCollector A DataCollector capable of providing data packets to use for the send intent when invoked
     * @param siteToSiteClientConfig The configuration for the SiteToSiteClient.
     * @param parcelableTransactionResultCallback A callback to be invoked whenever a transaction completes
     * @return a repeatable intent with enough metadata to save and reload it if desired
     *
     * @see SiteToSiteService#sendDataPackets(Context, Iterable, SiteToSiteClientConfig, TransactionResultCallback)
     */
    public static SiteToSiteRepeatableIntent createSendPendingIntent(Context context, DataCollector dataCollector, SiteToSiteClientConfig siteToSiteClientConfig, ParcelableTransactionResultCallback parcelableTransactionResultCallback) {
        return createPendingIntent(context, IntentType.SEND, dataCollector, siteToSiteClientConfig, parcelableTransactionResultCallback);
    }

    /**
     * Creates a pending intent suitable for use with AlarmManager to schedule repeating site-to-site enqueue packet operations
     *
     * @param context The current application environment @{@link Context} from which this service is being called.
     * @param dataCollector A DataCollector capable of providing data packets to use for the enqueue intent when invoked
     * @param queuedSiteToSiteClientConfig The configuration for the SiteToSiteClient and the DataPacket Queue.
     * @param parcelableQueuedOperationResultCallback A callback to be invoked whenever a enqueue operation completes
     * @return a repeatable intent with enough metadata to save and reload it if desired
     *
     * @see SiteToSiteService#enqueueDataPackets(Context, Iterable, QueuedSiteToSiteClientConfig, QueuedOperationResultCallback)
     */
    public static SiteToSiteRepeatableIntent createEnqueuePendingIntent(Context context, DataCollector dataCollector, QueuedSiteToSiteClientConfig queuedSiteToSiteClientConfig, ParcelableQueuedOperationResultCallback parcelableQueuedOperationResultCallback) {
        return createPendingIntent(context, IntentType.ENQUEUE, dataCollector, queuedSiteToSiteClientConfig, parcelableQueuedOperationResultCallback);
    }

    /**
     * Creates a pending intent suitable for use with AlarmManager to schedule repeating site-to-site process queued packet operations
     *
     * @param context The current application environment @{@link Context} from which this service is being called.
     * @param queuedSiteToSiteClientConfig The configuration for the SiteToSiteClient and the DataPacket Queue.
     * @param parcelableQueuedOperationResultCallback A callback to be invoked whenever a process operation completes
     * @return a repeatable intent with enough metadata to save and reload it if desired
     *
     * @see SiteToSiteService#processQueuedPackets(Context, QueuedSiteToSiteClientConfig, QueuedOperationResultCallback)
     */
    public static SiteToSiteRepeatableIntent createProcessQueuePendingIntent(Context context, QueuedSiteToSiteClientConfig queuedSiteToSiteClientConfig, ParcelableQueuedOperationResultCallback parcelableQueuedOperationResultCallback) {
        return createPendingIntent(context, IntentType.PROCESS, null, queuedSiteToSiteClientConfig, parcelableQueuedOperationResultCallback);
    }

    /**
     * Creates a pending intent suitable for use with AlarmManager to schedule repeating site-to-site cleanup queued packet operations
     *
     * @param context The current application environment @{@link Context} from which this service is being called.
     * @param queuedSiteToSiteClientConfig The configuration for the SiteToSiteClient and the DataPacket Queue.
     * @param parcelableQueuedOperationResultCallback A callback to be invoked whenever a cleanup operation completes
     * @return a repeatable intent with enough metadata to save and reload it if desired
     *
     * @see SiteToSiteService#cleanupQueuedPackets(Context, QueuedSiteToSiteClientConfig, QueuedOperationResultCallback)
     */
    public static SiteToSiteRepeatableIntent createCleanupQueuePendingIntent(Context context, QueuedSiteToSiteClientConfig queuedSiteToSiteClientConfig, ParcelableQueuedOperationResultCallback parcelableQueuedOperationResultCallback) {
        return createPendingIntent(context, IntentType.CLEANUP, null, queuedSiteToSiteClientConfig, parcelableQueuedOperationResultCallback);
    }

    synchronized static SiteToSiteRepeatableIntent createPendingIntent(Context context, IntentType intentType, DataCollector dataCollector, SiteToSiteClientConfig siteToSiteClientConfig, Parcelable parcelableTransactionResultCallback) {
        Intent intent = getIntent(context, intentType, dataCollector, siteToSiteClientConfig, null, parcelableTransactionResultCallback);
        int requestCode = getRequestCode(intent);
        return new SiteToSiteRepeatableIntent(requestCode, intent, PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT));
    }

    private static Intent getIntent(Context context, IntentType intentType, DataCollector dataCollector, SiteToSiteClientConfig siteToSiteClientConfig, Integer requestCode, Parcelable transactionResultCallback) {
        Intent intent = new Intent(context, SiteToSiteRepeating.class);
        if (dataCollector != null) {
            intent.setExtrasClassLoader(dataCollector.getClass().getClassLoader());
        } else if (transactionResultCallback != null) {
            intent.setExtrasClassLoader(transactionResultCallback.getClass().getClassLoader());
        } else {
            intent.setExtrasClassLoader(siteToSiteClientConfig.getClass().getClassLoader());
        }
        intent.putExtra(SiteToSiteService.INTENT_TYPE, intentType.name());

        if (requestCode == null) {
            // Find unused requestCode
            PendingIntent existing;
            do {
                requestCode = random.nextInt();
                existing = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_NO_CREATE);
            } while (existing != null);
        }

        intent.putExtra(REQUEST_CODE, requestCode);
        SerializationUtils.putParcelable(dataCollector, intent, DATA_COLLECTOR);
        SerializationUtils.putParcelable(transactionResultCallback, intent, SiteToSiteService.TRANSACTION_RESULT_CALLBACK);
        SerializationUtils.putParcelable(siteToSiteClientConfig, intent, SiteToSiteService.SITE_TO_SITE_CONFIG);
        return intent;
    }

    private static int getRequestCode(Intent intent) {
        return intent.getExtras().getInt(REQUEST_CODE);
    }

    private static IntentType getIntentType(Intent intent) {
        return IntentType.valueOf(intent.getStringExtra(SiteToSiteService.INTENT_TYPE));
    }
}
