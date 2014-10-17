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

import com.facebook.presto.block.BlockAssertions;
import com.facebook.presto.spi.block.Block;
import com.facebook.presto.spi.block.BlockBuilder;
import com.facebook.presto.spi.block.BlockBuilderStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.io.OutputSupplier;
import io.airlift.slice.DynamicSliceOutput;
import io.airlift.slice.Slice;
import org.testng.annotations.Test;

import java.util.List;

import static com.facebook.presto.raptor.block.BlocksFileReader.readBlocks;
import static com.facebook.presto.spi.type.VarcharType.VARCHAR;
import static com.facebook.presto.testing.TestingBlockEncodingManager.createTestingBlockEncodingManager;
import static org.testng.Assert.assertEquals;

public class TestBlocksFile
{
    private final List<String> expectedValues = ImmutableList.of(
            "alice",
            "bob",
            "charlie",
            "dave",
            "alice",
            "bob",
            "charlie",
            "dave",
            "alice",
            "bob",
            "charlie",
            "dave");

    private final Block expectedBlock;

    public TestBlocksFile()
    {
        BlockBuilder expectedBlockBuilder = VARCHAR.createBlockBuilder(new BlockBuilderStatus());
        VARCHAR.writeString(expectedBlockBuilder, "alice");
        VARCHAR.writeString(expectedBlockBuilder, "bob");
        VARCHAR.writeString(expectedBlockBuilder, "charlie");
        VARCHAR.writeString(expectedBlockBuilder, "dave");
        expectedBlock = expectedBlockBuilder.build();
    }

    @Test
    public void testRoundTrip()
    {
        DynamicSliceOutputSupplier sliceOutput = new DynamicSliceOutputSupplier(1024);
        // write 3 copies the expected block
        BlocksFileWriter fileWriter = new BlocksFileWriter(VARCHAR, createTestingBlockEncodingManager(), sliceOutput);
        fileWriter.append(expectedBlock);
        fileWriter.append(expectedBlock);
        fileWriter.append(expectedBlock);
        fileWriter.close();
        // read the block
        Slice slice = sliceOutput.getLastSlice();
        BlocksFileReader actualBlocks = readBlocks(createTestingBlockEncodingManager(), slice);
        List<Object> actualValues = BlockAssertions.toValues(VARCHAR, actualBlocks);
        assertEquals(actualValues, expectedValues);
    }

    private static class DynamicSliceOutputSupplier
            implements OutputSupplier<DynamicSliceOutput>
    {
        private final int estimatedSize;
        private DynamicSliceOutput lastOutput;

        public DynamicSliceOutputSupplier(int estimatedSize)
        {
            this.estimatedSize = estimatedSize;
        }

        public Slice getLastSlice()
        {
            return lastOutput.slice();
        }

        @Override
        public DynamicSliceOutput getOutput()
        {
            lastOutput = new DynamicSliceOutput(estimatedSize);
            return lastOutput;
        }
    }
}
