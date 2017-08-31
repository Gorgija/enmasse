/*
 * Copyright 2016 Red Hat Inc.
 *
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

package io.enmasse.queue.scheduler;

import io.enmasse.address.model.Address;
import io.enmasse.address.model.types.standard.StandardType;
import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.collections.Sets;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.enmasse.queue.scheduler.TestUtils.waitForPort;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class QueueSchedulerTest {

    private static final String POOLED_INMEMORY = "pooled-inmemory";
    private static final String POOLED_PERSISTED = "pooled-persisted";

    private static final String INMEMORY = "inmemory";
    private Vertx vertx;
    private TestBrokerFactory brokerFactory;
    private QueueScheduler scheduler;

    @Before
    public void setup() throws Exception {
        vertx = Vertx.vertx();
        brokerFactory = new TestBrokerFactory(vertx, "localhost");
        scheduler = new QueueScheduler(brokerFactory, 0, null);
        TestUtils.deployVerticle(vertx, scheduler);
        int schedulerPort = waitForPort(() -> scheduler.getPort(), 1, TimeUnit.MINUTES);
        System.out.println("Scheduler port is " + schedulerPort);
        brokerFactory.setSchedulerPort(schedulerPort);
    }

    @After
    public void teardown() throws InterruptedException {
        vertx.close();
    }

    @Test
    public void testAddressAddedBeforeBroker() throws InterruptedException, ExecutionException, TimeoutException {
        scheduler.addressesChanged(Collections.singletonMap(POOLED_INMEMORY, Sets.newSet(createQueue("queue1", true, false), createQueue("queue2", true, false))));

        TestBroker br1 = deployBroker(POOLED_INMEMORY);

        waitForAddresses(br1, 2);
        assertThat(br1.getQueueNames(), hasItem("queue1"));
        assertThat(br1.getQueueNames(), hasItem("queue2"));
    }

    @Test
    public void testAddressAdded() throws InterruptedException {
        TestBroker br1 = deployBroker(POOLED_INMEMORY);
        scheduler.addressesChanged(Collections.singletonMap(POOLED_INMEMORY, Sets.newSet(createQueue("queue1", true, false), createQueue("queue2", true, false))));

        waitForAddresses(br1, 2);
        assertThat(br1.getQueueNames(), hasItem("queue1"));
        assertThat(br1.getQueueNames(), hasItem("queue2"));

        scheduler.addressesChanged(Collections.singletonMap(POOLED_INMEMORY, Sets.newSet(createQueue("queue1", true, false), createQueue("queue2", true, false), createQueue("queue3", true, false))));

        waitForAddresses(br1, 3);
        assertThat(br1.getQueueNames(), hasItem("queue1"));
        assertThat(br1.getQueueNames(), hasItem("queue2"));
        assertThat(br1.getQueueNames(), hasItem("queue3"));
    }

    @Test
    public void testAddressRemoved() throws InterruptedException {
        TestBroker br1 = deployBroker(POOLED_INMEMORY);
        scheduler.addressesChanged(Collections.singletonMap(POOLED_INMEMORY, Sets.newSet(createQueue("queue1", true, false), createQueue("queue2", true, false))));
        waitForAddresses(br1, 2);
        assertThat(br1.getQueueNames(), hasItem("queue1"));
        assertThat(br1.getQueueNames(), hasItem("queue2"));

        scheduler.addressesChanged(Collections.singletonMap(POOLED_INMEMORY, Sets.newSet(createQueue("queue1", true, false))));
        waitForAddresses(br1, 1);
        assertThat(br1.getQueueNames(), hasItem("queue1"));
    }

    @Test
    public void testGroupDeleted() throws InterruptedException {
        TestBroker br1 = deployBroker(POOLED_INMEMORY);
        TestBroker br2 = deployBroker(POOLED_PERSISTED);

        scheduler.addressesChanged(createMap(POOLED_INMEMORY, POOLED_PERSISTED, createQueue("queue1", true, false), createQueue("queue2", true, true)));

        waitForAddresses(br1, 1);
        waitForAddresses(br2, 1);

        assertThat(br1.getQueueNames(), hasItem("queue1"));
        assertThat(br2.getQueueNames(), hasItem("queue2"));

        scheduler.addressesChanged(Collections.singletonMap(POOLED_PERSISTED, Sets.newSet(createQueue("queue2", true, true))));
        waitForAddresses(br1, 1);
        waitForAddresses(br2, 1);
        assertThat(br1.getQueueNames(), hasItem("queue1"));
        assertThat(br2.getQueueNames(), hasItem("queue2"));
    }

    private Map<String, Set<Address>> createMap(String key1, String key2, Address value1, Address value2) {
        Map<String, Set<Address>> map = new HashMap<>();
        map.put(key1, Sets.newSet(value1));
        map.put(key2, Sets.newSet(value2));
        return map;
    }

    @Test
    public void testBrokerAdded() throws InterruptedException {
        scheduler.addressesChanged(createMap(POOLED_INMEMORY, POOLED_PERSISTED, createQueue("queue1", true, false), createQueue("queue2", true, true)));

        TestBroker br1 = deployBroker(POOLED_INMEMORY);
        waitForAddresses(br1, 1);
        assertThat(br1.getQueueNames(), hasItem("queue1"));

        TestBroker br2 = deployBroker(POOLED_PERSISTED);
        waitForAddresses(br2, 1);
        assertThat(br2.getQueueNames(), hasItem("queue2"));
    }

    @Test
    public void testBrokerRemoved() throws InterruptedException, TimeoutException, ExecutionException {
        scheduler.addressesChanged(createMap(POOLED_INMEMORY, POOLED_PERSISTED, createQueue("queue1", true, false), createQueue("queue2", true, true)));

        TestBroker br1 = deployBroker(POOLED_INMEMORY);
        TestBroker br2 = deployBroker(POOLED_PERSISTED);

        waitForAddresses(br1, 1);
        waitForAddresses(br2, 1);

        br2.close();

        br2 = deployBroker(POOLED_PERSISTED);
        waitForAddresses(br2, 1);
        assertThat(br2.getQueueNames(), hasItem("queue2"));
    }

    @Test
    public void testBrokerReconnected() throws InterruptedException, TimeoutException, ExecutionException {
        TestBroker br1 = deployBroker(POOLED_INMEMORY);
        scheduler.addressesChanged(Collections.singletonMap(POOLED_INMEMORY, Sets.newSet(createQueue("queue1", true, false))));

        waitForAddresses(br1, 1);
        assertThat(br1.getQueueNames(), hasItem("queue1"));

        br1.close();

        br1 = deployBroker(POOLED_INMEMORY);
        waitForAddresses(br1, 1);
        assertThat(br1.getQueueNames(), hasItem("queue1"));
    }

    private static void waitForAddresses(TestBroker broker, long numAddresses) throws InterruptedException {
        waitForAddresses(broker, numAddresses, 1, TimeUnit.MINUTES);
    }

    private static void waitForAddresses(TestBroker broker, long numAddresses, long timeout, TimeUnit timeUnit) throws InterruptedException {
        long endTime = System.currentTimeMillis() + timeUnit.toMillis(timeout);
        long actualSize = broker.getNumQueues();
        while (System.currentTimeMillis() < endTime && actualSize != numAddresses) {
            actualSize = broker.getNumQueues();
            Thread.sleep(1000);
        }
        assertThat(actualSize, is(numAddresses));
    }

    private TestBroker deployBroker(String id) throws InterruptedException {
        return brokerFactory.deployBroker(id);
    }

    private Address createQueue(String name, boolean pooled, boolean persisted) {
        Address.Builder builder = new Address.Builder();
        builder.setName(name);
        builder.setType(StandardType.QUEUE);
        builder.setPlan(StandardType.QUEUE.getPlans().stream().filter(p -> p.getName().equals(pooled ? persisted ? POOLED_PERSISTED : POOLED_INMEMORY : INMEMORY) ).findFirst().get());
        return builder.build();
    }
}
