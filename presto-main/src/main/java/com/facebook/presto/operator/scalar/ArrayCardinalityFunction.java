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
package com.facebook.presto.operator.scalar;

import com.facebook.presto.metadata.FunctionInfo;
import com.facebook.presto.metadata.ParametricScalar;
import com.facebook.presto.metadata.Signature;
import com.facebook.presto.spi.type.StandardTypes;
import com.facebook.presto.spi.type.Type;
import com.facebook.presto.spi.type.TypeManager;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import io.airlift.slice.Slice;

import java.lang.invoke.MethodHandle;
import java.util.Map;

import static com.facebook.presto.metadata.Signature.typeParameter;
import static com.facebook.presto.spi.type.TypeSignature.parseTypeSignature;
import static com.facebook.presto.type.TypeUtils.parameterizedTypeName;
import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.invoke.MethodHandles.lookup;

public final class ArrayCardinalityFunction
        extends ParametricScalar
{
    public static final ArrayCardinalityFunction ARRAY_CARDINALITY = new ArrayCardinalityFunction();
    private static final Signature SIGNATURE = new Signature("cardinality", ImmutableList.of(typeParameter("E")), "bigint", ImmutableList.of("array<E>"), false, false);
    private static final MethodHandle METHOD_HANDLE;

    static {
        MethodHandle result;
        try {
            result = lookup().unreflect(JsonFunctions.class.getMethod("jsonArrayLength", Slice.class));
        }
        catch (IllegalAccessException | NoSuchMethodException e) {
            throw Throwables.propagate(e);
        }
        METHOD_HANDLE = result;
    }

    @Override
    public Signature getSignature()
    {
        return SIGNATURE;
    }

    @Override
    public boolean isHidden()
    {
        return false;
    }

    @Override
    public boolean isDeterministic()
    {
        return false;
    }

    @Override
    public String getDescription()
    {
        return null;
    }

    @Override
    public FunctionInfo specialize(Map<String, Type> types, int arity, TypeManager typeManager)
    {
        checkArgument(types.size() == 1, "Cardinality expects only one argument");
        Type type = types.get("E");
        return new FunctionInfo(new Signature("cardinality", parseTypeSignature(StandardTypes.BIGINT), parameterizedTypeName("array", type.getTypeSignature())), "Returns the cardinality (length) of the array", false, METHOD_HANDLE, true, true, ImmutableList.of(false));
    }
}
