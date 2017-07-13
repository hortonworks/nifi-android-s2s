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

package com.hortonworks.hdf.android.sitetosite.client.queued;

import android.os.Parcelable;

import com.hortonworks.hdf.android.sitetosite.packet.DataPacket;

/**
 * An interface that can be implemented to be used with the SiteToSiteService to provide custom logic for prioritizing and expiring packets in
 * the local queue / buffer prior to sending them over SiteToSite to a remote NiFi instance or cluster.
 *
 * @see com.hortonworks.hdf.android.sitetosite.service.SiteToSiteService
 * @see com.hortonworks.hdf.android.sitetosite.service.SiteToSiteRepeating
 */
public interface DataPacketPrioritizer extends Parcelable {

    /**
     * Determine the priority of this @{@link DataPacket} relative to other packets.
     *
     * A lower value indicates a higher priority. That is, packetA results in a value of '0'
     * and packetB results in a value of '5', packetA is to be treated as the higher priority packet.
     *
     * @param dataPacket The data packet for which priorty needs to be determined.
     * @return an integer value of the @{@link DataPacket}'s priority. A lower value indicates a higher priority.
     */
    long getPriority(DataPacket dataPacket);

    /**
     * Determine the TTL (in milliseconds) of this @{@link DataPacket}, or how long it should be kept persisted before it is no longer worth sending.
     *
     * This is useful in the case that the remote NiFi cluster is temporarily unavailable or applies backpressure such packets
     * can not be sent at the rate they are produced. This is also useful if a DataPacket is time sensitive and offers no value
     * after a certain time, in which case the client should not even bother sending it.
     *
     * @param dataPacket The data packet for which priorty needs to be determined.
     * @return The TTL, in milliseconds
     */
    long getTtl(DataPacket dataPacket);
}
