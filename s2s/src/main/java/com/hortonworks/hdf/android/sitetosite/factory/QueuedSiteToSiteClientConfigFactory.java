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

package com.hortonworks.hdf.android.sitetosite.factory;

import com.hortonworks.hdf.android.sitetosite.client.QueuedSiteToSiteClientConfig;

public interface QueuedSiteToSiteClientConfigFactory<InputType> extends SiteToSiteClientConfigFactory<InputType> {
    QueuedSiteToSiteClientConfig create(InputType input) throws SiteToSiteClientConfigCreationException;
}
