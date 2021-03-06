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
package com.facebook.presto.raptor.block;

import com.facebook.presto.spi.block.Block;
import com.facebook.presto.spi.block.BlockEncoding;
import com.facebook.presto.spi.block.BlockEncodingSerde;
import com.facebook.presto.spi.type.Type;
import com.google.common.base.Throwables;
import com.google.common.io.OutputSupplier;
import io.airlift.slice.OutputStreamSliceOutput;
import io.airlift.slice.SliceOutput;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class BlocksFileWriter
        implements Closeable
{
    private final BlockEncodingSerde blockEncodingSerde;
    private final OutputSupplier<? extends OutputStream> outputSupplier;
    private final Type type;
    private Encoder encoder;
    private SliceOutput sliceOutput;
    private boolean closed;

    public BlocksFileWriter(Type type, BlockEncodingSerde blockEncodingSerde, OutputSupplier<? extends OutputStream> outputSupplier)
    {
        this.type = checkNotNull(type, "type is null");
        this.blockEncodingSerde = checkNotNull(blockEncodingSerde, "blockEncodingManager is null");
        this.outputSupplier = checkNotNull(outputSupplier, "outputSupplier is null");
    }

    public BlocksFileWriter append(Block block)
    {
        checkNotNull(block, "block is null");
        if (encoder == null) {
            open();
        }
        encoder.append(block);
        return this;
    }

    private void open()
    {
        try {
            OutputStream outputStream = outputSupplier.getOutput();
            if (outputStream instanceof SliceOutput) {
                sliceOutput = (SliceOutput) outputStream;
            }
            else {
                sliceOutput = new OutputStreamSliceOutput(outputStream);
            }
            encoder = new UncompressedEncoder(type, sliceOutput);
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void close()
    {
        if (closed) {
            return;
        }
        closed = true;

        if (encoder == null) {
            // No rows were written, so create an empty file. We need to keep
            // the empty files in order to tell the difference between a
            // missing file and a file that legitimately has no rows.
            createEmptyFile();
            return;
        }

        BlockEncoding blockEncoding = encoder.finish();

        int startingIndex = sliceOutput.size();

        // write file encoding
        blockEncodingSerde.writeBlockEncoding(sliceOutput, blockEncoding);

        // write footer size
        int footerSize = sliceOutput.size() - startingIndex;
        checkState(footerSize > 0);
        sliceOutput.writeInt(footerSize);

        try {
            sliceOutput.close();
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private void createEmptyFile()
    {
        try {
            outputSupplier.getOutput().close();
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}
