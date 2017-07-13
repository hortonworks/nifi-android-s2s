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

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.ResultReceiver;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import com.hortonworks.hdf.android.sitetosite.client.QueuedSiteToSiteClient;
import com.hortonworks.hdf.android.sitetosite.client.QueuedSiteToSiteClientConfig;
import com.hortonworks.hdf.android.sitetosite.client.SiteToSiteClient;
import com.hortonworks.hdf.android.sitetosite.client.SiteToSiteClientConfig;
import com.hortonworks.hdf.android.sitetosite.client.Transaction;
import com.hortonworks.hdf.android.sitetosite.client.TransactionResult;
import com.hortonworks.hdf.android.sitetosite.client.persistence.SiteToSiteDB;
import com.hortonworks.hdf.android.sitetosite.client.protocol.ResponseCode;
import com.hortonworks.hdf.android.sitetosite.packet.DataPacket;
import com.hortonworks.hdf.android.sitetosite.util.SerializationUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This SiteToSiteService provides a high-level interface to the NiFi SiteToSite (s2s) library functionality, including
 * functionality for sending batches of flow file data packets to a remote NiFi asynchronously with local, persistent queuing.
 */
public class SiteToSiteService extends IntentService {
    public static final String IO_EXCEPTION = "IOException";
    public static final String RESULT = "RESULT";
    public static final String INTENT_TYPE = "INTENT_TYPE";
    public static final String DATA_PACKETS = "DATA_PACKETS";
    public static final String TRANSACTION_RESULT_CALLBACK = "TRANSACTION_RESULT_CALLBACK";
    public static final String SHOULD_COMPLETE_WAKEFUL_INTENT = "SHOULD_COMPLETE_WAKEFUL_INTENT";
    public static final String SITE_TO_SITE_CONFIG = "SITE_TO_SITE_CONFIG";
    public static final String CANONICAL_NAME = SiteToSiteService.class.getCanonicalName();

    /**
     * Create an instance of SiteToSiteService
     */
    public SiteToSiteService() {
        super(SiteToSiteService.class.getName());
    }

    /**
     * Send many @{@link DataPacket}s to a remote NiFi instance or cluster asynchronously. The packets will be sent in a SiteToSite transaction, which guarantees that either all data packets will
     * arrive at the remote NiFi peer or none will arrive (the transaction will be cancelled without the flow files being committed and made available for processing on the remote end).
     *
     * @param context The current application environment @{@link Context} from which this service is being called.
     * @param packets The data packets to be sent.
     * @param siteToSiteClientConfig The configuration for the SiteToSiteClient that will be created and used in order to send these @{@link DataPacket}s.
     * @param transactionResultCallback An object that implements the @{@link TransactionResultCallback} interface, which will be invoked accordingly by the SiteToSiteService upon success/exception
     *                                  of this sendDataPackets operation
     *
     * @see SiteToSiteClientConfig
     * @see TransactionResultCallback
     * @see #sendDataPacket(Context, DataPacket, SiteToSiteClientConfig, TransactionResultCallback)
     */
    public static void sendDataPackets(Context context, Iterable<DataPacket> packets, SiteToSiteClientConfig siteToSiteClientConfig, TransactionResultCallback transactionResultCallback) {
        context.startService(getSendIntent(context, packets, siteToSiteClientConfig, transactionResultCallback, false));
    }

    /**
     * Send a single @{@link DataPacket} to a remote NiFi instance or cluster asynchronously.
     *
     * The semantics are the same as @{@link #sendDataPackets(Context, Iterable, SiteToSiteClientConfig, TransactionResultCallback) sendDataPackets}, but for a single @{@link DataPacket}.
     *
     * @see #sendDataPackets(Context, Iterable, SiteToSiteClientConfig, TransactionResultCallback)
     */
    public static void sendDataPacket(Context context, DataPacket packet, SiteToSiteClientConfig siteToSiteClientConfig, TransactionResultCallback transactionResultCallback) {
        context.startService(getSendIntent(context, Collections.singletonList(packet), siteToSiteClientConfig, transactionResultCallback, false));
    }

    /**
     * Create an @{@link Intent} for a send packets operation with the same semantics as @{@link #sendDataPackets(Context, Iterable, SiteToSiteClientConfig, TransactionResultCallback)}.
     *
     * This can be used with the Android Intent's APIs, such as @{@link Context#startService(Intent)}.
     *
     * Normally, one would just use the @{@link #sendDataPackets(Context, Iterable, SiteToSiteClientConfig, TransactionResultCallback)} method; however, this method is provided in case
     * the user of the SiteToSiteService wants more control over what to do with the Intent.
     *
     * @param completeWakefulIntent A flag that says if this intent will be used with a @{@link WakefulBroadcastReceiver}
     *
     * For the other parameters:
     * @see #sendDataPackets(Context, Iterable, SiteToSiteClientConfig, TransactionResultCallback)
     *
     * @return An @{@link Intent} for a send packets operation.
     */
    public static Intent getSendIntent(Context context, Iterable<DataPacket> packets, SiteToSiteClientConfig siteToSiteClientConfig, TransactionResultCallback transactionResultCallback, boolean completeWakefulIntent) {
        return getIntent(context, IntentType.SEND, packets, siteToSiteClientConfig, transactionResultCallback, completeWakefulIntent);
    }

    /**
     * Enqueue many @{@link DataPacket}s to a local, persistent queue to be sent to a remote NiFi instance or cluster at some point in the future asynchronously in a prioritized manner.
     *
     * The @{@link QueuedSiteToSiteClientConfig} parameter includes a @{@link com.hortonworks.hdf.android.sitetosite.client.queued.DataPacketPrioritizer}, which is used by the SiteToSiteService to
     * determine the priority and TTL of each @{@link DataPacket} when being added to the persistent queue.
     *
     * @param context The current application environment @{@link Context} from which this service is being called.
     * @param packets The data packets to be queued for future transmission.
     * @param queuedSiteToSiteClientConfig The configuration for persistent data packet queue, including a @{@link com.hortonworks.hdf.android.sitetosite.client.queued.DataPacketPrioritizer}.
     * @param queuedOperationResultCallback An object that implements the @{@link QueuedOperationResultCallback} interface, which will be invoked accordingly by the SiteToSiteService upon completion
     *                                      of enqueuing the DataPackets. Note, this is called at the end of the queueing operation, not when those Packets are processed at some point in the future.
     *
     * @see QueuedSiteToSiteClientConfig
     * @see com.hortonworks.hdf.android.sitetosite.client.queued.DataPacketPrioritizer
     */
    public static void enqueueDataPackets(Context context, Iterable<DataPacket> packets, QueuedSiteToSiteClientConfig queuedSiteToSiteClientConfig, QueuedOperationResultCallback queuedOperationResultCallback) {
        context.startService(getEnqueueIntent(context, packets, queuedSiteToSiteClientConfig, queuedOperationResultCallback, false));
    }

    /**
     * Enqueue a single @{@link DataPacket} to a local, persistent queue to be sent to a remote NiFi instance or cluster at some point in the future asynchronously in a prioritized manner.
     *
     * The semantics are the same as @{@link #enqueueDataPackets(Context, Iterable, QueuedSiteToSiteClientConfig, QueuedOperationResultCallback)}, but for a single @{@link DataPacket}.
     *
     * @see #enqueueDataPackets(Context, Iterable, QueuedSiteToSiteClientConfig, QueuedOperationResultCallback)
     */
    public static void enqueueDataPacket(Context context, DataPacket packet, QueuedSiteToSiteClientConfig siteToSiteClientConfig, QueuedOperationResultCallback queuedOperationResultCallback) {
        context.startService(getEnqueueIntent(context, Collections.singletonList(packet), siteToSiteClientConfig, queuedOperationResultCallback, false));
    }

    /**
     * Create an @{@link Intent} for an enqueue packets operation with the same semantics as @{@link #enqueueDataPackets(Context, Iterable, QueuedSiteToSiteClientConfig, QueuedOperationResultCallback)}.
     *
     * This can be used with the Android Intent's APIs, such as @{@link Context#startService(Intent)}.
     *
     * Normally, one would just use the @{@link #enqueueDataPackets(Context, Iterable, QueuedSiteToSiteClientConfig, QueuedOperationResultCallback)} method; however, this method is provided in case
     * the user of the SiteToSiteService wants more control over what to do with the Intent.
     *
     * @param completeWakefulIntent A flag that says if this intent will be used with a @{@link WakefulBroadcastReceiver}
     *
     * For the other parameters:
     * @see #enqueueDataPackets(Context, Iterable, QueuedSiteToSiteClientConfig, QueuedOperationResultCallback)
     *
     * @return An @{@link Intent} for an enqueue packets operation.
     */
    public static Intent getEnqueueIntent(Context context, Iterable<DataPacket> packets, QueuedSiteToSiteClientConfig queuedSiteToSiteClientConfig, QueuedOperationResultCallback queuedOperationResultCallback, boolean completeWakefulIntent) {
        return getIntent(context, IntentType.ENQUEUE, packets, queuedSiteToSiteClientConfig, queuedOperationResultCallback, completeWakefulIntent);
    }

    /**
     * Have the SiteToSiteService to process previously enqueued @{@link DataPacket}s. Packets will be processed in priority order. The QueuedSiteToSiteClientConfig will be used to create a
     * SiteToSiteClient and prepare a batch of packets to send in a transaction to a remote NiFi instance or cluster. This should be invoked when the app determines it is an opportune time
     * to transmit data packets that have been queued locally. If no packets have been queued, no work is done. Not all queued packets are guaranteed to be processed depending on the configured
     * batch size.
     *
     * The process operation is done asynchronously, and the specified @{@link QueuedOperationResultCallback} is invoked upon completion.
     *
     * @param context The current application environment @{@link Context} from which this service is being called.
     * @param queuedSiteToSiteClientConfig The configuration for persistent data packet queue and the SiteToSite client.
     * @param queuedOperationResultCallback An object that implements the @{@link QueuedOperationResultCallback} interface, which will be invoked accordingly by the SiteToSiteService upon completion
     *                                      of processing the queued DataPackets.
     */
    public static void processQueuedPackets(Context context, QueuedSiteToSiteClientConfig queuedSiteToSiteClientConfig, QueuedOperationResultCallback queuedOperationResultCallback) {
        context.startService(getProcessIntent(context, queuedSiteToSiteClientConfig, queuedOperationResultCallback, false));
    }

    /**
     * Create an @{@link Intent} for an process queued packets operation with the same semantics as @{@link #processQueuedPackets(Context, QueuedSiteToSiteClientConfig, QueuedOperationResultCallback)}.
     *
     * This can be used with the Android Intent's APIs, such as @{@link Context#startService(Intent)}.
     *
     * Normally, one would just use the @{@link #processQueuedPackets(Context, QueuedSiteToSiteClientConfig, QueuedOperationResultCallback)} method; however, this method is provided in case
     * the user of the SiteToSiteService wants more control over what to do with the Intent.
     *
     * @param completeWakefulIntent A flag that says if this intent will be used with a @{@link WakefulBroadcastReceiver}
     *
     * For the other parameters:
     * @see #processQueuedPackets(Context, QueuedSiteToSiteClientConfig, QueuedOperationResultCallback)
     *
     * @return An @{@link Intent} for an enqueue packets operation.
     */
    public static Intent getProcessIntent(Context context, QueuedSiteToSiteClientConfig queuedSiteToSiteClientConfig, QueuedOperationResultCallback queuedOperationResultCallback, boolean completeWakefulIntent) {
        return getIntent(context, IntentType.PROCESS, null, queuedSiteToSiteClientConfig, queuedOperationResultCallback, completeWakefulIntent);
    }

    /**
     * Have the SiteToSiteService cleanup previously enqueued @{@link DataPacket}s. Packets will be deleted based on age, number of queued packets (vs. @{@link QueuedSiteToSiteClientConfig#maxRows}),
     * size of all queued packets (vs. @{@link QueuedSiteToSiteClientConfig#maxSize}. If any packets need to be cleaned up, the lowest priority packets will be removed and the highest priorty packets
     * will be left remaining.
     *
     * Note that packet TTL and priority is based on values produced by the @{@link com.hortonworks.hdf.android.sitetosite.client.queued.DataPacketPrioritizer} at the time packets were enqueued, not
     * at the time of cleanup. This means that if a new @{@link com.hortonworks.hdf.android.sitetosite.client.queued.DataPacketPrioritizer} is passed to the cleanup method (i.e., it contains different
     * logic than the one used at enqueue time), packet TTL and priority will NOT be reevaluated.
     *
     * The cleanup operation is done asynchronously, and the specified @{@link QueuedOperationResultCallback} is invoked upon completion.
     *
     * @param context The current application environment @{@link Context} from which this service is being called.
     * @param queuedSiteToSiteClientConfig The configuration for persistent data packet queue including maxRows and maxSize.
     * @param queuedOperationResultCallback An object that implements the @{@link QueuedOperationResultCallback} interface, which will be invoked accordingly by the SiteToSiteService upon completion
     *                                      of cleaning up the queued DataPackets.
     */
    public static void cleanupQueuedPackets(Context context, QueuedSiteToSiteClientConfig queuedSiteToSiteClientConfig, QueuedOperationResultCallback queuedOperationResultCallback) {
        context.startService(getCleanupIntent(context, queuedSiteToSiteClientConfig, queuedOperationResultCallback, false));
    }

    /**
     * Create an @{@link Intent} for a cleanup queued packets operation with the same semantics as @{@link #cleanupQueuedPackets(Context, QueuedSiteToSiteClientConfig, QueuedOperationResultCallback)}.
     *
     * This can be used with the Android Intent's APIs, such as @{@link Context#startService(Intent)}.
     *
     * Normally, one would just use the @{@link #enqueueDataPackets(Context, Iterable, QueuedSiteToSiteClientConfig, QueuedOperationResultCallback)} method; however, this method is provided in case
     * the user of the SiteToSiteService wants more control over what to do with the Intent.
     *
     * @param completeWakefulIntent A flag that says if this intent will be used with a @{@link WakefulBroadcastReceiver}
     *
     * For the other parameters:
     * @see #cleanupQueuedPackets(Context, QueuedSiteToSiteClientConfig, QueuedOperationResultCallback)
     *
     * @return An @{@link Intent} for a cleanup queued packets operation.
     */
    public static Intent getCleanupIntent(Context context, QueuedSiteToSiteClientConfig queuedSiteToSiteClientConfig, QueuedOperationResultCallback queuedOperationResultCallback, boolean completeWakefulIntent) {
        return getIntent(context, IntentType.CLEANUP, null, queuedSiteToSiteClientConfig, queuedOperationResultCallback, completeWakefulIntent);
    }

    private static Intent getIntent(Context context, IntentType intentType, Iterable<DataPacket> packets, SiteToSiteClientConfig siteToSiteClientConfig, TransactionResultCallback transactionResultCallback, boolean completeWakefulIntent) {
        Intent intent = getIntent(context, intentType, packets, completeWakefulIntent);
        if (transactionResultCallback != null) {
            intent.putExtra(TRANSACTION_RESULT_CALLBACK, TransactionResultCallback.Receiver.wrap(transactionResultCallback));
        }
        SerializationUtils.putParcelable(siteToSiteClientConfig, intent, SITE_TO_SITE_CONFIG);
        return intent;
    }

    static Intent getIntent(Context context, IntentType intentType, Iterable<DataPacket> packets, QueuedSiteToSiteClientConfig queuedSiteToSiteClientConfig, QueuedOperationResultCallback queuedOperationResultCallback, boolean completeWakefulIntent) {
        Intent intent = getIntent(context, intentType, packets, completeWakefulIntent);
        if (queuedOperationResultCallback != null) {
            intent.putExtra(TRANSACTION_RESULT_CALLBACK, QueuedOperationResultCallback.Receiver.wrap(queuedOperationResultCallback));
        }
        SerializationUtils.putParcelable(queuedSiteToSiteClientConfig, intent, SITE_TO_SITE_CONFIG);
        return intent;
    }

    static Intent getIntent(Context context, IntentType intentType, Iterable<DataPacket> packets, boolean completeWakefulIntent) {
        Intent intent = new Intent(context, SiteToSiteService.class);
        intent.putExtra(INTENT_TYPE, intentType.name());
        if (packets != null) {
            ArrayList<DataPacket> packetList = new ArrayList<>();
            for (DataPacket packet : packets) {
                packetList.add(packet);
            }
            intent.putParcelableArrayListExtra(DATA_PACKETS, packetList);
        }
        if (completeWakefulIntent) {
            intent.putExtra(SHOULD_COMPLETE_WAKEFUL_INTENT, true);
        }
        return intent;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, CANONICAL_NAME);
        wakeLock.acquire();

        try {
            if (intent.getBooleanExtra(SHOULD_COMPLETE_WAKEFUL_INTENT, false)) {
                WakefulBroadcastReceiver.completeWakefulIntent(intent);
            }
            IntentType intentType = IntentType.valueOf(intent.getStringExtra(INTENT_TYPE));
            Context context = getApplicationContext();
            SiteToSiteDB siteToSiteDB = new SiteToSiteDB(context);
            if (intentType.isQueueOperation()) {
                ResultReceiver queuedOperationResultCallback = intent.getExtras().getParcelable(TRANSACTION_RESULT_CALLBACK);
                QueuedSiteToSiteClientConfig queuedSiteToSiteClientConfig = SerializationUtils.getParcelable(intent, SITE_TO_SITE_CONFIG);
                try {
                    siteToSiteDB.updatePeerStatusOnConfig(queuedSiteToSiteClientConfig);
                    QueuedSiteToSiteClient queuedSiteToSiteClient = queuedSiteToSiteClientConfig.createQueuedClient(context);
                    if (intentType == IntentType.ENQUEUE) {
                        List<DataPacket> packets = intent.getExtras().getParcelableArrayList(DATA_PACKETS);
                        if (packets != null && packets.size() > 0) {
                            queuedSiteToSiteClient.enqueue(packets.iterator());
                        }
                    } else if (intentType == IntentType.PROCESS) {
                        queuedSiteToSiteClient.process();
                        siteToSiteDB.savePeerStatus(queuedSiteToSiteClientConfig);
                    } else if (intentType == IntentType.CLEANUP) {
                        queuedSiteToSiteClient.cleanup();
                    } else {
                        Log.e(CANONICAL_NAME, "Unexpected intent type: " + intentType);
                    }
                    QueuedOperationResultCallback.Receiver.onSuccess(queuedOperationResultCallback);
                } catch (IOException e) {
                    Log.d(CANONICAL_NAME, "Performing queue operation.", e);
                    if (intentType == IntentType.PROCESS) {
                        siteToSiteDB.savePeerStatus(queuedSiteToSiteClientConfig);
                    }
                    QueuedOperationResultCallback.Receiver.onException(queuedOperationResultCallback, e);
                }
            } else if (intentType == IntentType.SEND) {
                List<DataPacket> packets = intent.getExtras().getParcelableArrayList(DATA_PACKETS);
                ResultReceiver transactionResultCallback = intent.getExtras().getParcelable(TRANSACTION_RESULT_CALLBACK);
                SiteToSiteClientConfig siteToSiteClientConfig = SerializationUtils.getParcelable(intent, SITE_TO_SITE_CONFIG);
                if (packets != null && packets.size() > 0) {
                    try {
                        siteToSiteDB.updatePeerStatusOnConfig(siteToSiteClientConfig);
                        SiteToSiteClient client = siteToSiteClientConfig.createClient();
                        Transaction transaction = client.createTransaction();
                        for (DataPacket packet : packets) {
                            transaction.send(packet);
                        }
                        transaction.confirm();
                        TransactionResult transactionResult = transaction.complete();
                        siteToSiteDB.savePeerStatus(siteToSiteClientConfig);
                        if (transactionResultCallback != null) {
                            TransactionResultCallback.Receiver.onSuccess(transactionResultCallback, transactionResult);
                        }
                    } catch (IOException e) {
                        Log.d(CANONICAL_NAME, "Error sending packets.", e);
                        if (transactionResultCallback != null) {
                            siteToSiteDB.savePeerStatus(siteToSiteClientConfig);
                            TransactionResultCallback.Receiver.onException(transactionResultCallback, e);
                        }
                    }
                } else {
                    TransactionResultCallback.Receiver.onSuccess(transactionResultCallback, new TransactionResult(0, ResponseCode.CONFIRM_TRANSACTION, "No-op due to empty packet list."));
                }
            }
        } catch (Exception e) {
            Log.e(CANONICAL_NAME, "Unexpected error processing intent: " + intent, e);
        } finally {
            wakeLock.release();
        }
    }

    /**
     * Intent Types specific to the @{@link SiteToSiteService}. To be used as an argument passed to @{@link #getIntent(Context, IntentType, Iterable, QueuedSiteToSiteClientConfig, QueuedOperationResultCallback, boolean)}
     * or @{@link #getIntent(Context, IntentType, Iterable, SiteToSiteClientConfig, TransactionResultCallback, boolean)}.
     *
     * @see #getIntent(Context, IntentType, Iterable, QueuedSiteToSiteClientConfig, QueuedOperationResultCallback, boolean)
     * @see #getIntent(Context, IntentType, Iterable, SiteToSiteClientConfig, TransactionResultCallback, boolean)
     * @see #sendDataPackets(Context, Iterable, SiteToSiteClientConfig, TransactionResultCallback)
     * @see #enqueueDataPackets(Context, Iterable, QueuedSiteToSiteClientConfig, QueuedOperationResultCallback)
     * @see #processQueuedPackets(Context, QueuedSiteToSiteClientConfig, QueuedOperationResultCallback)
     * @see #cleanupQueuedPackets(Context, QueuedSiteToSiteClientConfig, QueuedOperationResultCallback)
     */
    enum IntentType {
        SEND(false), ENQUEUE(true), PROCESS(true), CLEANUP(true);

        private final boolean queueOperation;

        IntentType(boolean queueOperation) {
            this.queueOperation = queueOperation;
        }

        public boolean isQueueOperation() {
            return queueOperation;
        }
    }
}
