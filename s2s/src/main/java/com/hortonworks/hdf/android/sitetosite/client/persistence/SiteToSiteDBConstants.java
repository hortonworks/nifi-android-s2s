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

public class SiteToSiteDBConstants {
    public static final String ID_COLUMN = "ID";
    public static final String CONTENT_COLUMN = "CONTENT";
    public static final String CREATED_COLUMN = "CREATED";
    public static final String EXPIRATION_MILLIS_COLUMN = "EXPIRATION_MILLIS";

    public static final String PEER_STATUSES_TABLE_NAME = "APACHE_NIFI_SITE_TO_SITE_PEER_STATUSES";
    public static final String PEER_STATUS_URLS_COLUMN = "URLS";
    public static final String PEER_STATUS_PROXY_HOST_COLUMN = "PROXY_HOST";
    public static final String PEER_STATUS_PROXY_PORT_COLUMN = "PROXY_PORT";
    public static final String PEER_STATUS_WHERE_CLAUSE = PEER_STATUS_URLS_COLUMN + " = ? AND " + PEER_STATUS_PROXY_HOST_COLUMN + " = ? AND " + PEER_STATUS_PROXY_PORT_COLUMN + " = ?";

    public static final String DATA_PACKET_QUEUE_TABLE_NAME = "APACHE_NIFI_SITE_TO_SITE_QUEUE";
    public static final String DATA_PACKET_QEUE_PRIORITY_COLUMN = "PRIORITY";
    public static final String DATA_PACKET_QUEUE_ATTRIBUTES_COLUMN = "ATTRIBUTES";
    public static final String DATA_PACKET_QUEUE_TRANSACTION_COLUMN = "TRANSACTION_ID";

    public static final String DATA_PACKET_QUEUE_TRANSACTIONS_TABLE_NAME = "APACHE_NIFI_SITE_TO_SITE_QUEUE_TRANSACTIONS";
}
