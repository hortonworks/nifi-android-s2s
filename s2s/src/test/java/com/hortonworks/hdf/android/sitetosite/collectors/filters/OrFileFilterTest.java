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

package com.hortonworks.hdf.android.sitetosite.collectors.filters;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class OrFileFilterTest {
    @Mock
    private ParcelableFileFilter delegate1;

    @Mock
    private ParcelableFileFilter delegate2;

    @Mock
    private File file;

    private OrFileFilter orFileFilter;

    @Before
    public void setup() {
        orFileFilter = new OrFileFilter(delegate1, delegate2);
    }

    @Test
    public void testBothAccept() {
        when(delegate1.accept(file)).thenReturn(true);
        assertTrue(orFileFilter.accept(file));
        verify(delegate1).accept(file);
        verify(delegate2, never()).accept(file);
    }

    @Test
    public void testFirstDoesntAccept() {
        when(delegate1.accept(file)).thenReturn(false);
        when(delegate2.accept(file)).thenReturn(true);
        assertTrue(orFileFilter.accept(file));
        verify(delegate1).accept(file);
        verify(delegate2).accept(file);
    }

    @Test
    public void testNeitherAccept() {
        when(delegate1.accept(file)).thenReturn(false);
        when(delegate2.accept(file)).thenReturn(false);
        assertFalse(orFileFilter.accept(file));
        verify(delegate1).accept(file);
        verify(delegate2).accept(file);
    }
}
