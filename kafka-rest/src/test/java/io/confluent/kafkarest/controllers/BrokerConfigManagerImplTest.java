/*
 * Copyright 2020 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.kafkarest.controllers;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.fail;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import io.confluent.kafkarest.TestUtils;
import io.confluent.kafkarest.entities.BrokerConfig;
import io.confluent.kafkarest.entities.Cluster;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.NotFoundException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConfigsResult;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.DescribeConfigsResult;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.ConfigResource.Type;
import org.easymock.EasyMockRule;
import org.easymock.Mock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BrokerConfigManagerImplTest {

  private static final String CLUSTER_ID = "cluster-1";
  private static final int BROKER_ID = 1;

  private static final Cluster CLUSTER =
      new Cluster(CLUSTER_ID, null, emptyList());

  private static final BrokerConfig CONFIG_1 =
      new BrokerConfig(
          CLUSTER_ID,
          BROKER_ID,
          "config-1",
          "value-1",
          /* isDefault= */true,
          /* isReadOnly= */false,
          /* isSensitive= */false);
  private static final BrokerConfig CONFIG_2 =
      new BrokerConfig(
          CLUSTER_ID,
          BROKER_ID,
          "config-2",
          "value-2",
          /* isDefault= */false,
          /* isReadOnly= */true,
          /* isSensitive= */false);
  private static final BrokerConfig CONFIG_3 =
      new BrokerConfig(
          CLUSTER_ID,
          BROKER_ID,
          "config-3",
          "value-3",
          /* isDefault= */false,
          /* isReadOnly= */false,
          /* isSensitive= */true);

  private static final Config CONFIG =
      new Config(
          Arrays.asList(
              new ConfigEntry(
                  CONFIG_1.getName(),
                  CONFIG_1.getValue(),
                  CONFIG_1.isDefault(),
                  CONFIG_1.isSensitive(),
                  CONFIG_1.isReadOnly()),
              new ConfigEntry(
                  CONFIG_2.getName(),
                  CONFIG_2.getValue(),
                  CONFIG_2.isDefault(),
                  CONFIG_2.isSensitive(),
                  CONFIG_2.isReadOnly()),
              new ConfigEntry(
                  CONFIG_3.getName(),
                  CONFIG_3.getValue(),
                  CONFIG_3.isDefault(),
                  CONFIG_3.isSensitive(),
                  CONFIG_3.isReadOnly())));

  @Rule
  public final EasyMockRule mocks = new EasyMockRule(this);

  @Mock
  private Admin adminClient;

  @Mock
  private ClusterManager clusterManager;

  @Mock
  private DescribeConfigsResult describeConfigsResult;

  @Mock
  private AlterConfigsResult alterConfigsResult;

  private BrokerConfigManagerImpl brokerConfigManager;

  @Before
  public void setUp() {
    brokerConfigManager = new BrokerConfigManagerImpl(adminClient, clusterManager);
  }

  @Test
  public void listBrokerConfigs_existingBroker_returnsConfigs() throws Exception {
    expect(clusterManager.getCluster(CLUSTER_ID)).andReturn(completedFuture(Optional.of(CLUSTER)));
    expect(
        adminClient.describeConfigs(
            singletonList(new ConfigResource(Type.BROKER, String.valueOf(BROKER_ID)))))
            .andReturn(describeConfigsResult);
    expect(describeConfigsResult.values())
        .andReturn(
            singletonMap(
                new ConfigResource(Type.BROKER, String.valueOf(BROKER_ID)),
                KafkaFuture.completedFuture(CONFIG)));
    replay(adminClient, clusterManager, describeConfigsResult);

    List<BrokerConfig> configs = brokerConfigManager.listBrokerConfigs(CLUSTER_ID, BROKER_ID).get();

    assertEquals(new HashSet<>(Arrays.asList(CONFIG_1, CONFIG_2, CONFIG_3)),
        new HashSet<>(configs));
  }

  @Test
  public void listBrokerConfigs_nonExistingBroker_throwsNotFound() throws Exception {
    expect(clusterManager.getCluster(CLUSTER_ID)).andReturn(completedFuture(Optional.of(CLUSTER)));
    expect(
        adminClient.describeConfigs(
            singletonList(new ConfigResource(Type.BROKER, String.valueOf(BROKER_ID)))))
        .andReturn(describeConfigsResult);
    expect(describeConfigsResult.values())
        .andReturn(
            singletonMap(
                new ConfigResource(Type.BROKER, String.valueOf(BROKER_ID)),
                TestUtils.failedFuture(new NotFoundException("Broker not found."))));
    replay(clusterManager, adminClient, describeConfigsResult);

    try {
      brokerConfigManager.listBrokerConfigs(CLUSTER_ID, BROKER_ID).get();
      fail();
    } catch (ExecutionException e) {
      assertEquals(NotFoundException.class, e.getCause().getClass());
    }
  }

  @Test
  public void listBrokerConfigs_nonExistingCluster_throwsNotFound() throws Exception {
    expect(clusterManager.getCluster(CLUSTER_ID)).andReturn(completedFuture(Optional.empty()));
    replay(clusterManager);

    try {
      brokerConfigManager.listBrokerConfigs(CLUSTER_ID, BROKER_ID).get();
      fail();
    } catch (ExecutionException e) {
      assertEquals(NotFoundException.class, e.getCause().getClass());
    }
  }

  @Test
  public void getBrokerConfig_existingConfig_returnsConfig() throws Exception {
    expect(clusterManager.getCluster(CLUSTER_ID)).andReturn(completedFuture(Optional.of(CLUSTER)));
    expect(adminClient.describeConfigs(
        singletonList(new ConfigResource(Type.BROKER, String.valueOf(BROKER_ID)))))
        .andReturn(describeConfigsResult);
    expect(describeConfigsResult.values())
        .andReturn(
            singletonMap(
                new ConfigResource(Type.BROKER, String.valueOf(BROKER_ID)),
                KafkaFuture.completedFuture(CONFIG)));
    replay(adminClient, clusterManager, describeConfigsResult);

    BrokerConfig config =
        brokerConfigManager.getBrokerConfig(
            CLUSTER_ID, BROKER_ID, CONFIG_1.getName())
            .get()
            .get();

    assertEquals(CONFIG_1, config);
  }

  @Test
  public void getBrokerConfig_nonExistingConfig_returnsEmpty() throws Exception {
    expect(clusterManager.getCluster(CLUSTER_ID)).andReturn(completedFuture(Optional.of(CLUSTER)));
    expect(
        adminClient.describeConfigs(
            singletonList(new ConfigResource(Type.BROKER, String.valueOf(BROKER_ID)))))
        .andReturn(describeConfigsResult);
    expect(describeConfigsResult.values())
        .andReturn(
            singletonMap(
                new ConfigResource(Type.BROKER, String.valueOf(BROKER_ID)),
                KafkaFuture.completedFuture(CONFIG)));
    replay(adminClient, clusterManager, describeConfigsResult);

    Optional<BrokerConfig> config = brokerConfigManager.getBrokerConfig(CLUSTER_ID, BROKER_ID,
        "foobar").get();

    assertFalse(config.isPresent());
  }

  @Test
  public void getBrokerConfig_nonExistingBroker_throwsNotFound() throws Exception {
    expect(clusterManager.getCluster(CLUSTER_ID)).andReturn(completedFuture(Optional.of(CLUSTER)));
    expect(
        adminClient.describeConfigs(
            singletonList(new ConfigResource(Type.BROKER, String.valueOf(BROKER_ID)))))
        .andReturn(describeConfigsResult);
    expect(describeConfigsResult.values())
        .andReturn(
            singletonMap(
                new ConfigResource(Type.BROKER, String.valueOf(BROKER_ID)),
                TestUtils.failedFuture(new NotFoundException("Broker not found."))));
    replay(clusterManager, adminClient, describeConfigsResult);

    try {
      brokerConfigManager.getBrokerConfig(
          CLUSTER_ID, BROKER_ID, CONFIG_1.getName()).get();
      fail();
    } catch (ExecutionException e) {
      assertEquals(NotFoundException.class, e.getCause().getClass());
    }
  }

  @Test
  public void getBrokerConfig_nonExistingCluster_throwsNotFound() throws Exception {
    expect(clusterManager.getCluster(CLUSTER_ID)).andReturn(completedFuture(Optional.empty()));
    replay(clusterManager);

    try {
      brokerConfigManager.getBrokerConfig(CLUSTER_ID, BROKER_ID, CONFIG_1.getName()).get();
      fail();
    } catch (ExecutionException e) {
      assertEquals(NotFoundException.class, e.getCause().getClass());
    }
  }

}
