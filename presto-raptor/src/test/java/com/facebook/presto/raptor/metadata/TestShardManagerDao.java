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
package com.facebook.presto.raptor.metadata;

import com.facebook.presto.type.TypeRegistry;
import com.google.common.collect.ImmutableSet;
import io.airlift.dbpool.H2EmbeddedDataSource;
import io.airlift.dbpool.H2EmbeddedDataSourceConfig;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.exceptions.UnableToExecuteStatementException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.sql.DataSource;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static com.facebook.presto.raptor.metadata.ShardManagerDaoUtils.createShardTablesWithRetry;
import static io.airlift.testing.Assertions.assertInstanceOf;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@Test(singleThreaded = true)
public class TestShardManagerDao
{
    private ShardManagerDao dao;
    private Handle handle;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        H2EmbeddedDataSourceConfig dataSourceConfig = new H2EmbeddedDataSourceConfig().setFilename("mem:");
        DataSource dataSource = new H2EmbeddedDataSource(dataSourceConfig);
        DBI h2Dbi = new DBI(dataSource);
        h2Dbi.registerMapper(new PartitionKey.Mapper(new TypeRegistry()));
        handle = h2Dbi.open();
        dao = handle.attach(ShardManagerDao.class);

        createShardTablesWithRetry(dao);
    }

    @AfterMethod
    public void teardown()
    {
        handle.close();
    }

    @Test
    public void testExternalBatches()
    {
        assertFalse(dao.externalBatchExists("foo"));
        assertFalse(dao.externalBatchExists("bar"));

        dao.insertExternalBatch("foo");

        assertTrue(dao.externalBatchExists("foo"));
        assertFalse(dao.externalBatchExists("bar"));

        try {
            dao.insertExternalBatch("foo");
            fail("expected exception");
        }
        catch (UnableToExecuteStatementException e) {
            assertInstanceOf(e.getCause(), SQLException.class);
            assertTrue(((SQLException) e.getCause()).getSQLState().startsWith("23"));
        }
    }

    @Test
    public void testTableCreation()
            throws Exception
    {
        assertEquals(dao.getAllNodesInUse(), ImmutableSet.of());
    }

    @Test
    public void testNodeInsert()
            throws Exception
    {
        assertEquals(dao.getAllNodesInUse(), ImmutableSet.of());

        String nodeName = UUID.randomUUID().toString();
        dao.insertNode(nodeName);

        assertEquals(dao.getAllNodesInUse(), ImmutableSet.of(nodeName));
    }

    @Test
    public void testShardSelection()
            throws Exception
    {
        assertEquals(dao.getAllNodesInUse(), ImmutableSet.of());

        String nodeName1 = UUID.randomUUID().toString();
        dao.insertNode(nodeName1);
        Long nodeId1 = dao.getNodeId(nodeName1);
        assertNotNull(nodeId1);

        assertEquals(dao.getAllNodesInUse(), ImmutableSet.of(nodeName1));

        String nodeName2 = UUID.randomUUID().toString();
        dao.insertNode(nodeName2);
        Long nodeId2 = dao.getNodeId(nodeName2);
        assertNotNull(nodeId2);

        assertEquals(dao.getAllNodesInUse(), ImmutableSet.of(nodeName1, nodeName2));

        long tableId = 1;

        UUID shardUuid1 = UUID.randomUUID();
        UUID shardUuid2 = UUID.randomUUID();
        UUID shardUuid3 = UUID.randomUUID();
        UUID shardUuid4 = UUID.randomUUID();

        long shardId1 = dao.insertShard(shardUuid1);
        long shardId2 = dao.insertShard(shardUuid2);
        long shardId3 = dao.insertShard(shardUuid3);
        long shardId4 = dao.insertShard(shardUuid4);

        dao.insertShardNode(shardId1, nodeId1);
        dao.insertShardNode(shardId1, nodeId2);
        dao.insertShardNode(shardId2, nodeId1);
        dao.insertShardNode(shardId3, nodeId1);
        dao.insertShardNode(shardId4, nodeId1);
        dao.insertShardNode(shardId4, nodeId2);

        dao.insertTableShard(tableId, shardId1);
        dao.insertTableShard(tableId, shardId2);
        dao.insertTableShard(tableId, shardId3);
        dao.insertTableShard(tableId, shardId4);

        List<ShardNode> shardNodes = dao.getShardNodes(tableId);
        assertEquals(shardNodes.size(), 6);

        assertContainsShardNode(shardNodes, nodeName1, shardUuid1);
        assertContainsShardNode(shardNodes, nodeName2, shardUuid1);
        assertContainsShardNode(shardNodes, nodeName1, shardUuid2);
        assertContainsShardNode(shardNodes, nodeName1, shardUuid3);
        assertContainsShardNode(shardNodes, nodeName1, shardUuid4);
        assertContainsShardNode(shardNodes, nodeName2, shardUuid4);
    }

    private static void assertContainsShardNode(List<ShardNode> nodes, String nodeName, UUID shardUuid)
    {
        assertTrue(nodes.contains(new ShardNode(shardUuid, nodeName)));
    }
}
