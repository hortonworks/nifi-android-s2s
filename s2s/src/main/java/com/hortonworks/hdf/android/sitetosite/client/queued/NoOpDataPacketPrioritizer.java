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

import android.os.Parcel;

import com.hortonworks.hdf.android.sitetosite.packet.DataPacket;

/**
 * An example implementation of a @{@link DataPacketPrioritizer}.
 */
public class NoOpDataPacketPrioritizer implements DataPacketPrioritizer {
    public static Creator<NoOpDataPacketPrioritizer> CREATOR = new Creator<NoOpDataPacketPrioritizer>() {
        @Override
        public NoOpDataPacketPrioritizer createFromParcel(Parcel source) {
            return new NoOpDataPacketPrioritizer();
        }

        @Override
        public NoOpDataPacketPrioritizer[] newArray(int size) {
            return new NoOpDataPacketPrioritizer[size];
        }
    };

    @Override
    public long getPriority(DataPacket dataPacket) {
        return 0;
    }

    @Override
    public long getTtl(DataPacket dataPacket) {
        return -1;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {

    }
}
