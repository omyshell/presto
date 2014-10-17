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
package com.facebook.presto.hive;

import com.facebook.presto.spi.ConnectorSession;
import com.facebook.presto.spi.PrestoException;

import static com.facebook.presto.spi.StandardErrorCode.INVALID_SESSION_PROPERTY;

public final class HiveSessionProperties
{
    public static final String STORAGE_FORMAT_PROPERTY = "storage_format";
    private static final String OPTIMIZED_READER_ENABLED = "optimized_reader_enabled";

    private HiveSessionProperties()
    {
    }

    public static HiveStorageFormat getHiveStorageFormat(ConnectorSession session, HiveStorageFormat defaultValue)
    {
        String storageFormatString = session.getProperties().get(STORAGE_FORMAT_PROPERTY);
        if (storageFormatString == null) {
            return defaultValue;
        }

        try {
            return HiveStorageFormat.valueOf(storageFormatString.toUpperCase());
        }
        catch (IllegalArgumentException e) {
            throw new PrestoException(INVALID_SESSION_PROPERTY, "Hive storage-format is invalid: " + storageFormatString);
        }
    }

    public static boolean isOptimizedReaderEnabled(ConnectorSession session, boolean defaultValue)
    {
        return isEnabled(OPTIMIZED_READER_ENABLED, session, defaultValue);
    }

    private static boolean isEnabled(String propertyName, ConnectorSession session, boolean defaultValue)
    {
        String enabled = session.getProperties().get(propertyName);
        if (enabled == null) {
            return defaultValue;
        }

        return Boolean.valueOf(enabled);
    }
}
