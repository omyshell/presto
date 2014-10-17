/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.connector.system;

import com.facebook.presto.spi.Connector;
import com.facebook.presto.spi.ConnectorHandleResolver;
import com.facebook.presto.spi.ConnectorIndexResolver;
import com.facebook.presto.spi.ConnectorMetadata;
import com.facebook.presto.spi.ConnectorPageSourceProvider;
import com.facebook.presto.spi.ConnectorRecordSetProvider;
import com.facebook.presto.spi.ConnectorRecordSinkProvider;
import com.facebook.presto.spi.ConnectorSplitManager;
import com.google.common.base.Preconditions;

import javax.inject.Inject;

public class SystemConnector
        implements Connector
{
    public static final String CONNECTOR_ID = "system";

    private final SystemTablesMetadata metadata;
    private final SystemSplitManager splitManager;
    private final SystemRecordSetProvider recordSetProvider;

    @Inject
    public SystemConnector(
            SystemTablesMetadata metadata,
            SystemSplitManager splitManager,
            SystemRecordSetProvider recordSetProvider)
    {
        this.metadata = Preconditions.checkNotNull(metadata, "metadata is null");
        this.splitManager = Preconditions.checkNotNull(splitManager, "splitManager is null");
        this.recordSetProvider = Preconditions.checkNotNull(recordSetProvider, "recordSetProvider is null");
    }

    @Override
    public ConnectorMetadata getMetadata()
    {
        return metadata;
    }

    @Override
    public ConnectorSplitManager getSplitManager()
    {
        return splitManager;
    }

    @Override
    public ConnectorHandleResolver getHandleResolver()
    {
        return new SystemHandleResolver();
    }

    @Override
    public ConnectorPageSourceProvider getPageSourceProvider()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ConnectorRecordSetProvider getRecordSetProvider()
    {
        return recordSetProvider;
    }

    @Override
    public ConnectorRecordSinkProvider getRecordSinkProvider()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ConnectorIndexResolver getIndexResolver()
    {
        throw new UnsupportedOperationException();
    }
}
