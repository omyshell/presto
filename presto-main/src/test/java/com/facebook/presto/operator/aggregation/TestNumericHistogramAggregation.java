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
package com.facebook.presto.operator.aggregation;

import com.facebook.presto.metadata.FunctionRegistry;
import com.facebook.presto.spi.Page;
import com.facebook.presto.spi.PageBuilder;
import com.facebook.presto.spi.block.Block;
import com.facebook.presto.sql.tree.QualifiedName;
import com.facebook.presto.type.TypeRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import io.airlift.json.ObjectMapperProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Map;

import static com.facebook.presto.spi.type.BigintType.BIGINT;
import static com.facebook.presto.spi.type.DoubleType.DOUBLE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestNumericHistogramAggregation
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapperProvider().get();
    private final AccumulatorFactory factory;
    private final Page input;

    public TestNumericHistogramAggregation()
    {
        FunctionRegistry functionRegistry = new FunctionRegistry(new TypeRegistry(), true);
        InternalAggregationFunction function = functionRegistry.resolveFunction(QualifiedName.of("numeric_histogram"), ImmutableList.of(BIGINT.getTypeSignature(), DOUBLE.getTypeSignature(), DOUBLE.getTypeSignature()), false).getAggregationFunction();
        factory = function.bind(ImmutableList.of(0, 1, 2), Optional.<Integer>absent(), Optional.<Integer>absent(), 1.0);

        int numberOfBuckets = 10;

        PageBuilder builder = new PageBuilder(ImmutableList.of(BIGINT, DOUBLE, DOUBLE));

        for (int i = 0; i < 100; i++) {
            BIGINT.writeLong(builder.getBlockBuilder(0), numberOfBuckets);
            DOUBLE.writeDouble(builder.getBlockBuilder(1), i); // value
            DOUBLE.writeDouble(builder.getBlockBuilder(2), 1); // weight
        }

        input = builder.build();
    }

    @Test
    public void test()
            throws Exception
    {
        Accumulator singleStep = factory.createAccumulator();
        singleStep.addInput(input);
        Block expected = singleStep.evaluateFinal();

        Accumulator partialStep = factory.createAccumulator();
        partialStep.addInput(input);

        Accumulator finalStep = factory.createAccumulator();
        finalStep.addIntermediate(partialStep.evaluateIntermediate());
        Block actual = finalStep.evaluateFinal();

        assertEquals(extractSingleValue(actual), extractSingleValue(expected));
    }

    @Test
    public void testMerge()
            throws Exception
    {
        Accumulator singleStep = factory.createAccumulator();
        singleStep.addInput(input);
        Block singleStepResult = singleStep.evaluateFinal();

        Accumulator partialStep = factory.createAccumulator();
        partialStep.addInput(input);

        Accumulator finalStep = factory.createAccumulator();
        Block intermediate = partialStep.evaluateIntermediate();

        finalStep.addIntermediate(intermediate);
        finalStep.addIntermediate(intermediate);
        Block actual = finalStep.evaluateFinal();

        Map<String, Double> expected = Maps.transformValues(extractSingleValue(singleStepResult), new Function<Double, Double>()
        {
            @Override
            public Double apply(Double input)
            {
                return input * 2;
            }
        });

        assertEquals(extractSingleValue(actual), expected);
    }

    @Test
    public void testNull()
            throws Exception
    {
        Accumulator accumulator = factory.createAccumulator();
        Block result = accumulator.evaluateFinal();

        assertTrue(result.getPositionCount() == 1);
        assertTrue(result.isNull(0));
    }

    private static Map<String, Double> extractSingleValue(Block block)
            throws IOException
    {
        String json = block.getSlice(0, 0, block.getLength(0)).toStringUtf8();
        return OBJECT_MAPPER.readValue(json, Map.class);
    }
}
