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

package enmasse.config.service.amqp;

import enmasse.config.service.model.ResourceDatabase;
import enmasse.config.service.model.Subscriber;
import io.vertx.proton.ProtonMessageHandler;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.message.Message;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class AMQPServerTest {
    private AMQPServer server;
    private ResourceDatabase database;
    private TestClient client;

    @Before
    public void setup() throws InterruptedException {
        database = mock(ResourceDatabase.class);
        when(database.subscribe(any(), any(), any())).thenReturn(true);
        server = new AMQPServer("localhost", 0, database);
        server.run();
        int port = waitForPort(server);
        System.out.println("Server running on port " + server.port());
        client = new TestClient("localhost", port);
    }

    private int waitForPort(AMQPServer server) throws InterruptedException {
        int port = server.port();
        while (port == 0) {
            Thread.sleep(100);
            port = server.port();
        }
        return port;
    }

    @After
    public void teardown() {
        client.close();
        server.close();
    }

    @Test
    public void testSubscribe() {
        ProtonMessageHandler msgHandler = mock(ProtonMessageHandler.class);
        client.subscribe("foo", msgHandler);

        ArgumentCaptor<Subscriber> subCapture = ArgumentCaptor.forClass(Subscriber.class);
        ArgumentCaptor<Map<String, String>> mapCapture = ArgumentCaptor.forClass(Map.class);
        verify(database, timeout(10000)).subscribe(anyString(), mapCapture.capture(), subCapture.capture());

        Map<String, String> filter = mapCapture.getValue();
        assertThat(filter.size(), is(1));
        assertTrue(filter.containsKey("my"));
        assertThat(filter.get("my"), is("label"));

        Subscriber sub = subCapture.getValue();
        Message testMessage = Message.Factory.create();
        testMessage.setBody(new AmqpValue("test1"));
        sub.resourcesUpdated(testMessage);

        ArgumentCaptor<Message> msgCapture = ArgumentCaptor.forClass(Message.class);
        verify(msgHandler, timeout(10000)).handle(any(), msgCapture.capture());
        String value = (String) ((AmqpValue)msgCapture.getValue().getBody()).getValue();
        assertThat(value, is("test1"));
    }
}

