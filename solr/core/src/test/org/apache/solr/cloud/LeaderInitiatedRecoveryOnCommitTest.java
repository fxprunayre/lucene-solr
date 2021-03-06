package org.apache.solr.cloud;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.http.NoHttpResponseException;
import org.apache.solr.client.solrj.embedded.JettySolrRunner;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.cloud.Replica;
import org.junit.Test;

import java.io.File;
import java.util.List;

public class LeaderInitiatedRecoveryOnCommitTest extends BasicDistributedZkTest {

  private static final long sleepMsBeforeHealPartition = 2000L;

  public LeaderInitiatedRecoveryOnCommitTest() {
    super();
    sliceCount = 1;
    fixShardCount(4);
  }

  @Override
  @Test
  public void test() throws Exception {
    oneShardTest();
    multiShardTest();
  }

  private void multiShardTest() throws Exception {

    log.info("Running multiShardTest");

    // create a collection that has 2 shard and 2 replicas
    String testCollectionName = "c8n_2x2_commits";
    createCollection(testCollectionName, 2, 2, 1);
    cloudClient.setDefaultCollection(testCollectionName);

    List<Replica> notLeaders =
        ensureAllReplicasAreActive(testCollectionName, "shard1", 2, 2, 30);
    assertTrue("Expected 1 replicas for collection " + testCollectionName
            + " but found " + notLeaders.size() + "; clusterState: "
            + printClusterStateInfo(),
        notLeaders.size() == 1);

    log.info("All replicas active for "+testCollectionName);

    // let's put the leader in its own partition, no replicas can contact it now
    Replica leader = cloudClient.getZkStateReader().getLeaderRetry(testCollectionName, "shard1");
    log.info("Creating partition to leader at "+leader.getCoreUrl());
    SocketProxy leaderProxy = getProxyForReplica(leader);
    leaderProxy.close();

    // let's find the leader of shard2 and ask him to commit
    Replica shard2Leader = cloudClient.getZkStateReader().getLeaderRetry(testCollectionName, "shard2");
    sendCommitWithRetry(shard2Leader);

    Thread.sleep(sleepMsBeforeHealPartition);

    cloudClient.getZkStateReader().updateClusterState(true); // get the latest state
    leader = cloudClient.getZkStateReader().getLeaderRetry(testCollectionName, "shard1");
    assertSame("Leader was not active", Replica.State.ACTIVE, leader.getState());

    log.info("Healing partitioned replica at "+leader.getCoreUrl());
    leaderProxy.reopen();
    Thread.sleep(sleepMsBeforeHealPartition);

    // try to clean up
    try {
      CollectionAdminRequest.Delete req = new CollectionAdminRequest.Delete();
      req.setCollectionName(testCollectionName);
      req.process(cloudClient);
    } catch (Exception e) {
      // don't fail the test
      log.warn("Could not delete collection {} after test completed", testCollectionName);
    }

    log.info("multiShardTest completed OK");
  }

  private void oneShardTest() throws Exception {
    log.info("Running oneShardTest");

    // create a collection that has 1 shard and 3 replicas
    String testCollectionName = "c8n_1x3_commits";
    createCollection(testCollectionName, 1, 3, 1);
    cloudClient.setDefaultCollection(testCollectionName);

    List<Replica> notLeaders =
        ensureAllReplicasAreActive(testCollectionName, "shard1", 1, 3, 30);
    assertTrue("Expected 2 replicas for collection " + testCollectionName
            + " but found " + notLeaders.size() + "; clusterState: "
            + printClusterStateInfo(),
        notLeaders.size() == 2);

    log.info("All replicas active for "+testCollectionName);

    // let's put the leader in its own partition, no replicas can contact it now
    Replica leader = cloudClient.getZkStateReader().getLeaderRetry(testCollectionName, "shard1");
    log.info("Creating partition to leader at "+leader.getCoreUrl());
    SocketProxy leaderProxy = getProxyForReplica(leader);
    leaderProxy.close();

    Replica replica = notLeaders.get(0);
    sendCommitWithRetry(replica);
    Thread.sleep(sleepMsBeforeHealPartition);

    cloudClient.getZkStateReader().updateClusterState(true); // get the latest state
    leader = cloudClient.getZkStateReader().getLeaderRetry(testCollectionName, "shard1");
    assertSame("Leader was not active", Replica.State.ACTIVE, leader.getState());

    log.info("Healing partitioned replica at "+leader.getCoreUrl());
    leaderProxy.reopen();
    Thread.sleep(sleepMsBeforeHealPartition);

    // try to clean up
    try {
      CollectionAdminRequest.Delete req = new CollectionAdminRequest.Delete();
      req.setCollectionName(testCollectionName);
      req.process(cloudClient);
    } catch (Exception e) {
      // don't fail the test
      log.warn("Could not delete collection {} after test completed", testCollectionName);
    }

    log.info("oneShardTest completed OK");
  }

  /**
   * Overrides the parent implementation to install a SocketProxy in-front of the Jetty server.
   */
  @Override
  public JettySolrRunner createJetty(File solrHome, String dataDir,
                                     String shardList, String solrConfigOverride, String schemaOverride)
      throws Exception {
    return createProxiedJetty(solrHome, dataDir, shardList, solrConfigOverride, schemaOverride);
  }

  protected void sendCommitWithRetry(Replica replica) throws Exception {
    String replicaCoreUrl = replica.getCoreUrl();
    log.info("Sending commit request to: "+replicaCoreUrl);
    long startMs = System.currentTimeMillis();
    try (HttpSolrClient client = new HttpSolrClient(replicaCoreUrl)) {
      try {
        client.commit();

        long tookMs = System.currentTimeMillis() - startMs;
        log.info("Sent commit request to "+replicaCoreUrl+" OK, took: "+tookMs);
      } catch (Exception exc) {
        Throwable rootCause = SolrException.getRootCause(exc);
        if (rootCause instanceof NoHttpResponseException) {
          log.warn("No HTTP response from sending commit request to "+replicaCoreUrl+
              "; will re-try after waiting 3 seconds");
          Thread.sleep(3000);
          client.commit();
          log.info("Second attempt at sending commit to "+replicaCoreUrl+" succeeded.");
        } else {
          throw exc;
        }
      }
    }
  }

}
