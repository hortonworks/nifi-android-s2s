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

package com.hortonworks.hdf.android.sitetosite.packet;

import android.os.Parcel;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Data packet with empty payload
 */
public class EmptyDataPacket implements DataPacket {
    private final Map<String, String> attributes;

    public static final Creator<EmptyDataPacket> CREATOR = new Creator<EmptyDataPacket>() {
        @Override
        public EmptyDataPacket createFromParcel(Parcel in) {
            Map<String, String> attributes = new HashMap<>();
            int numAttributes = in.readInt();
            for (int i = 0; i < numAttributes; i++) {
                attributes.put(in.readString(), in.readString());
            }
            return new EmptyDataPacket(attributes);
        }

        @Override
        public EmptyDataPacket[] newArray(int size) {
            return new EmptyDataPacket[size];
        }
    };

    /**
     * Create an attribtues-only @{@link DataPacket}.
     *
     * @param attributes The key-value map for the Data Packet attributes.
     */
    public EmptyDataPacket(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    @Override
    public Map<String, String> getAttributes() {
        return attributes;
    }

    @Override
    public InputStream getData() {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public long getSize() {
        return 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(attributes.size());
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            dest.writeString(entry.getKey());
            dest.writeString(entry.getValue());
        }
    }
}
