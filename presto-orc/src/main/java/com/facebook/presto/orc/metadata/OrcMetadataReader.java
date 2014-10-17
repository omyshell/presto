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
package com.facebook.presto.orc.metadata;

import com.facebook.presto.orc.metadata.ColumnEncoding.ColumnEncodingKind;
import com.facebook.presto.orc.metadata.Stream.StreamKind;
import com.facebook.presto.orc.metadata.OrcType.OrcTypeKind;
import com.facebook.presto.hive.shaded.com.google.protobuf.CodedInputStream;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Ints;
import org.apache.hadoop.hive.ql.io.orc.OrcProto;
import org.apache.hadoop.hive.ql.io.orc.OrcProto.RowIndexEntry;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static com.facebook.presto.orc.metadata.CompressionKind.SNAPPY;
import static com.facebook.presto.orc.metadata.CompressionKind.UNCOMPRESSED;
import static com.facebook.presto.orc.metadata.CompressionKind.ZLIB;

public class OrcMetadataReader
        implements MetadataReader
{
    @Override
    public PostScript readPostScript(byte[] data, int offset, int length)
            throws IOException
    {
        CodedInputStream input = CodedInputStream.newInstance(data, offset, length);
        OrcProto.PostScript postScript = OrcProto.PostScript.parseFrom(input);

        return new PostScript(
                postScript.getVersionList(),
                postScript.getFooterLength(),
                postScript.getMetadataLength(),
                toCompression(postScript.getCompression()),
                postScript.getCompressionBlockSize());
    }

    @Override
    public Metadata readMetadata(InputStream inputStream)
            throws IOException
    {
        CodedInputStream input = CodedInputStream.newInstance(inputStream);
        OrcProto.Metadata metadata = OrcProto.Metadata.parseFrom(input);
        return new Metadata(toStripeStatistics(metadata.getStripeStatsList()));
    }

    private static List<StripeStatistics> toStripeStatistics(List<OrcProto.StripeStatistics> types)
    {
        return ImmutableList.copyOf(Iterables.transform(types, new Function<OrcProto.StripeStatistics, StripeStatistics>()
        {
            @Override
            public StripeStatistics apply(OrcProto.StripeStatistics type)
            {
                return toStripeStatistics(type);
            }
        }));
    }

    private static StripeStatistics toStripeStatistics(OrcProto.StripeStatistics stripeStatistics)
    {
        return new StripeStatistics(toColumnStatistics(stripeStatistics.getColStatsList()));
    }

    @Override
    public Footer readFooter(InputStream inputStream)
            throws IOException
    {
        CodedInputStream input = CodedInputStream.newInstance(inputStream);
        OrcProto.Footer footer = OrcProto.Footer.parseFrom(input);
        return new Footer(
                footer.getNumberOfRows(),
                footer.getRowIndexStride(),
                toStripeInformation(footer.getStripesList()),
                toType(footer.getTypesList()),
                toColumnStatistics(footer.getStatisticsList()));
    }

    private static List<StripeInformation> toStripeInformation(List<OrcProto.StripeInformation> types)
    {
        return ImmutableList.copyOf(Iterables.transform(types, new Function<OrcProto.StripeInformation, StripeInformation>()
        {
            @Override
            public StripeInformation apply(OrcProto.StripeInformation type)
            {
                return toStripeInformation(type);
            }
        }));
    }

    private static StripeInformation toStripeInformation(OrcProto.StripeInformation stripeInformation)
    {
        return new StripeInformation(
                Ints.checkedCast(stripeInformation.getNumberOfRows()),
                stripeInformation.getOffset(),
                stripeInformation.getIndexLength(),
                stripeInformation.getDataLength(),
                stripeInformation.getFooterLength());
    }

    @Override
    public StripeFooter readStripeFooter(List<OrcType> types, InputStream inputStream)
            throws IOException
    {
        CodedInputStream input = CodedInputStream.newInstance(inputStream);
        OrcProto.StripeFooter stripeFooter = OrcProto.StripeFooter.parseFrom(input);
        return new StripeFooter(toStream(stripeFooter.getStreamsList()), toColumnEncoding(stripeFooter.getColumnsList()));
    }

    private static Stream toStream(OrcProto.Stream stream)
    {
        return new Stream(stream.getColumn(), toStreamKind(stream.getKind()), Ints.checkedCast(stream.getLength()), true);
    }

    private static List<Stream> toStream(List<OrcProto.Stream> streams)
    {
        return ImmutableList.copyOf(Iterables.transform(streams, new Function<OrcProto.Stream, Stream>()
        {
            @Override
            public Stream apply(OrcProto.Stream stream)
            {
                return toStream(stream);
            }
        }));
    }

    private static ColumnEncoding toColumnEncoding(OrcProto.ColumnEncoding columnEncoding)
    {
        return new ColumnEncoding(toColumnEncodingKind(columnEncoding.getKind()), columnEncoding.getDictionarySize());
    }

    private static List<ColumnEncoding> toColumnEncoding(List<OrcProto.ColumnEncoding> columnEncodings)
    {
        return ImmutableList.copyOf(Iterables.transform(columnEncodings, new Function<OrcProto.ColumnEncoding, ColumnEncoding>()
        {
            @Override
            public ColumnEncoding apply(OrcProto.ColumnEncoding columnEncoding)
            {
                return toColumnEncoding(columnEncoding);
            }
        }));
    }

    @Override
    public List<RowGroupIndex> readRowIndexes(InputStream inputStream)
            throws IOException
    {
        CodedInputStream input = CodedInputStream.newInstance(inputStream);
        OrcProto.RowIndex rowIndex = OrcProto.RowIndex.parseFrom(input);
        return ImmutableList.copyOf(Iterables.transform(rowIndex.getEntryList(), new Function<RowIndexEntry, RowGroupIndex>()
        {
            @Override
            public RowGroupIndex apply(OrcProto.RowIndexEntry rowIndexEntry)
            {
                return toRowGroupIndex(rowIndexEntry);
            }
        }));
    }

    private static RowGroupIndex toRowGroupIndex(RowIndexEntry rowIndexEntry)
    {
        return new RowGroupIndex(rowIndexEntry.getPositionsList(), toColumnStatistics(rowIndexEntry.getStatistics()));
    }

    private static ColumnStatistics toColumnStatistics(OrcProto.ColumnStatistics statistics)
    {
        return new ColumnStatistics(
                statistics.getNumberOfValues(),
                toBucketStatistics(statistics.getBucketStatistics()),
                toIntegerStatistics(statistics.getIntStatistics()),
                toDoubleStatistics(statistics.getDoubleStatistics()),
                toStringStatistics(statistics.getStringStatistics()),
                toDateStatistics(statistics.getDateStatistics()));
    }

    private static List<ColumnStatistics> toColumnStatistics(List<OrcProto.ColumnStatistics> columnStatistics)
    {
        if (columnStatistics == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(Iterables.transform(columnStatistics, new Function<OrcProto.ColumnStatistics, ColumnStatistics>()
        {
            @Override
            public ColumnStatistics apply(OrcProto.ColumnStatistics columnStatistics)
            {
                return toColumnStatistics(columnStatistics);
            }
        }));
    }

    private static BucketStatistics toBucketStatistics(OrcProto.BucketStatistics bucketStatistics)
    {
        return new BucketStatistics(bucketStatistics.getCountList());
    }

    private static IntegerStatistics toIntegerStatistics(OrcProto.IntegerStatistics integerStatistics)
    {
        return new IntegerStatistics(
                integerStatistics.hasMinimum() ? integerStatistics.getMinimum() : null,
                integerStatistics.hasMaximum() ? integerStatistics.getMaximum() : null);
    }

    private static DoubleStatistics toDoubleStatistics(OrcProto.DoubleStatistics doubleStatistics)
    {
        return new DoubleStatistics(
                doubleStatistics.hasMinimum() ? doubleStatistics.getMinimum() : null,
                doubleStatistics.hasMaximum() ? doubleStatistics.getMaximum() : null);
    }

    private static StringStatistics toStringStatistics(OrcProto.StringStatistics stringStatistics)
    {
        return new StringStatistics(
                stringStatistics.hasMinimum() ? stringStatistics.getMinimum() : null,
                stringStatistics.hasMaximum() ? stringStatistics.getMaximum() : null);
    }

    private static DateStatistics toDateStatistics(OrcProto.DateStatistics dateStatistics)
    {
        return new DateStatistics(
                dateStatistics.hasMinimum() ? dateStatistics.getMinimum() : null,
                dateStatistics.hasMaximum() ? dateStatistics.getMaximum() : null);
    }

    private static OrcType toType(OrcProto.Type type)
    {
        return new OrcType(toTypeKind(type.getKind()), type.getSubtypesList(), type.getFieldNamesList());
    }

    private static List<OrcType> toType(List<OrcProto.Type> types)
    {
        return ImmutableList.copyOf(Iterables.transform(types, new Function<OrcProto.Type, OrcType>()
        {
            @Override
            public OrcType apply(OrcProto.Type type)
            {
                return toType(type);
            }
        }));
    }

    private static OrcTypeKind toTypeKind(OrcProto.Type.Kind typeKind)
    {
        switch (typeKind) {
            case BOOLEAN:
                return OrcTypeKind.BOOLEAN;
            case BYTE:
                return OrcTypeKind.BYTE;
            case SHORT:
                return OrcTypeKind.SHORT;
            case INT:
                return OrcTypeKind.INT;
            case LONG:
                return OrcTypeKind.LONG;
            case FLOAT:
                return OrcTypeKind.FLOAT;
            case DOUBLE:
                return OrcTypeKind.DOUBLE;
            case STRING:
                return OrcTypeKind.STRING;
            case BINARY:
                return OrcTypeKind.BINARY;
            case TIMESTAMP:
                return OrcTypeKind.TIMESTAMP;
            case LIST:
                return OrcTypeKind.LIST;
            case MAP:
                return OrcTypeKind.MAP;
            case STRUCT:
                return OrcTypeKind.STRUCT;
            case UNION:
                return OrcTypeKind.UNION;
            case DECIMAL:
                return OrcTypeKind.DECIMAL;
            case DATE:
                return OrcTypeKind.DATE;
            case VARCHAR:
                return OrcTypeKind.VARCHAR;
            case CHAR:
                return OrcTypeKind.CHAR;
            default:
                throw new IllegalStateException(typeKind + " stream type not implemented yet");
        }
    }

    private static StreamKind toStreamKind(OrcProto.Stream.Kind streamKind)
    {
        switch (streamKind) {
            case PRESENT:
                return StreamKind.PRESENT;
            case DATA:
                return StreamKind.DATA;
            case LENGTH:
                return StreamKind.LENGTH;
            case DICTIONARY_DATA:
                return StreamKind.DICTIONARY_DATA;
            case DICTIONARY_COUNT:
                return StreamKind.DICTIONARY_COUNT;
            case SECONDARY:
                return StreamKind.SECONDARY;
            case ROW_INDEX:
                return StreamKind.ROW_INDEX;
            default:
                throw new IllegalStateException(streamKind + " stream type not implemented yet");
        }
    }

    private static ColumnEncodingKind toColumnEncodingKind(OrcProto.ColumnEncoding.Kind columnEncodingKind)
    {
        switch (columnEncodingKind) {
            case DIRECT:
                return ColumnEncodingKind.DIRECT;
            case DIRECT_V2:
                return ColumnEncodingKind.DIRECT_V2;
            case DICTIONARY:
                return ColumnEncodingKind.DICTIONARY;
            case DICTIONARY_V2:
                return ColumnEncodingKind.DICTIONARY_V2;
            default:
                throw new IllegalStateException(columnEncodingKind + " stream encoding not implemented yet");
        }
    }

    private static CompressionKind toCompression(OrcProto.CompressionKind compression)
    {
        switch (compression) {
            case NONE:
                return UNCOMPRESSED;
            case ZLIB:
                return ZLIB;
            case SNAPPY:
                return SNAPPY;
            default:
                throw new IllegalStateException(compression + " compression not implemented yet");
        }
    }
}
