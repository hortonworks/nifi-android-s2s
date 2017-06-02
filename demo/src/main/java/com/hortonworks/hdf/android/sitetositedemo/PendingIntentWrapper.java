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

package com.hortonworks.hdf.android.sitetositedemo;

import android.app.PendingIntent;

/**
 * Wrapper for PendingIntent and rowId
 */
public class PendingIntentWrapper {
    private final long rowId;
    private final PendingIntent pendingIntent;

    public PendingIntentWrapper(long rowId, PendingIntent pendingIntent) {
        this.rowId = rowId;
        this.pendingIntent = pendingIntent;
    }

    /**
     * Get the rowId
     *
     * @return the rowId
     */
    public long getRowId() {
        return rowId;
    }

    /**
     * Get the pending intent
     *
     * @return the pending intent
     */
    public PendingIntent getPendingIntent() {
        return pendingIntent;
    }
}
