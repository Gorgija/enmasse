package enmasse.systemtest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.vertx.core.json.Json.mapper;

public class AddressApiClient {
    private final HttpClient httpClient;
    private final Endpoint endpoint;
    private final boolean isMultitenant;
    private final Vertx vertx;

    public AddressApiClient(Endpoint endpoint, boolean isMultitenant) {
        this.vertx = VertxFactory.create();
        this.httpClient = vertx.createHttpClient();
        this.endpoint = endpoint;
        this.isMultitenant = isMultitenant;
    }

    public void close() {
        httpClient.close();
        vertx.close();
    }

    public void deployInstance(String instanceName) throws JsonProcessingException, InterruptedException {
        if (isMultitenant) {
            ObjectNode config = mapper.createObjectNode();
            config.put("apiVersion", "v3");
            config.put("kind", "Instance");
            ObjectNode metadata = config.putObject("metadata");
            metadata.put("name", instanceName);
            ObjectNode spec = config.putObject("spec");
            spec.put("namespace", instanceName);

            CountDownLatch latch = new CountDownLatch(1);
            HttpClientRequest request;
            request = httpClient.post(endpoint.getPort(), endpoint.getHost(), "/v3/instance");
            request.putHeader("content-type", "application/json");
            request.handler(event -> {
                if (event.statusCode() >= 200 && event.statusCode() < 300) {
                    latch.countDown();
                }
            });
            request.end(Buffer.buffer(mapper.writeValueAsBytes(config)));
            latch.await(30, TimeUnit.SECONDS);
        }
    }


    /**
     * give you JsonObject with AddressesList or Address kind
     *
     * @param instanceName name of instance, this is used only if isMultitenant is set to true
     * @param addressName  name of address
     * @return
     * @throws Exception
     */
    public JsonObject getAddresses(String instanceName, Optional<String> addressName) throws Exception {
        HttpClientRequest request;
        String path = isMultitenant ? "/v1/addresses/" + instanceName + "/" : "/v1/addresses/default/";
        path += addressName.isPresent() ? addressName.get() : "";

        CountDownLatch latch = new CountDownLatch(2);

        request = httpClient.request(HttpMethod.GET, endpoint.getPort(), endpoint.getHost(), path);
        request.setTimeout(10_000);
        request.exceptionHandler(event -> {
            Logging.log.warn("Exception while performing request", event.getCause());
        });

        final JsonObject[] responseArray = new JsonObject[1];
        request.handler(event -> {
            event.bodyHandler(responseData -> {
                responseArray[0] = responseData.toJsonObject();
                latch.countDown();
            });
            if (event.statusCode() >= 200 && event.statusCode() < 300) {
                latch.countDown();
            } else {
                Logging.log.warn("Error when getting addresses: " + event.statusCode() + ": " + event.statusMessage());
            }
        });
        request.end();
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new RuntimeException("Timeout getting address config");
        }
        return responseArray[0];
    }

    /**
     * delete addresses via reset api
     *
     * @param instanceName name of instance
     * @param destinations variable count of destinations that you can delete
     * @throws Exception
     */
    public void deleteAddresses(String instanceName, Destination... destinations) throws Exception {
        if (isMultitenant) {
            doDelete("/v1/addresses/" + instanceName + "/");
        } else {
            for (Destination destination : destinations) {
                doDelete("/v1/addresses/default/" + destination.getAddress());
            }
        }
    }

    private void doDelete(String path) throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        HttpClientRequest request = httpClient.request(HttpMethod.DELETE, endpoint.getPort(), endpoint.getHost(), path);
        request.setTimeout(10_000);
        request.exceptionHandler(event -> {
            Logging.log.warn("Exception while performing request", event.getCause());
        });
        request.handler(event -> {
            event.bodyHandler(responseData -> {
                latch.countDown();
            });
            if (event.statusCode() >= 200 && event.statusCode() < 300) {
                latch.countDown();
            } else {
                Logging.log.warn("Error during deleting addresses: " + event.statusCode() + ": " + event.statusMessage());
            }
        });
        request.end();
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new RuntimeException("Timeout deleting addresses");
        }
    }

    /**
     * deploying addresses via rest api
     *
     * @param instanceName name of instance
     * @param httpMethod   PUT and POST method are supported
     * @param destinations variable count of destinations that you can put or append
     * @throws Exception
     */
    public void deploy(String instanceName, HttpMethod httpMethod, Destination... destinations) throws Exception {
        ObjectNode config = mapper.createObjectNode();
        config.put("apiVersion", "v1");
        config.put("kind", "AddressList");
        ArrayNode items = config.putArray("items");
        for (Destination destination : destinations) {
            ObjectNode entry = items.addObject();
            ObjectNode metadata = entry.putObject("metadata");
            metadata.put("name", destination.getAddress());
            ObjectNode spec = entry.putObject("spec");
            spec.put("address", destination.getAddress());
            spec.put("type", destination.getType());
            destination.getPlan().ifPresent(e -> spec.put("plan", e));
        }

        CountDownLatch latch = new CountDownLatch(1);
        HttpClientRequest request;
        if (isMultitenant) {
            request = httpClient.request(httpMethod, endpoint.getPort(), endpoint.getHost(), "/v1/addresses/" + instanceName + "/");
        } else {
            request = httpClient.request(httpMethod, endpoint.getPort(), endpoint.getHost(), "/v1/addresses/default/");
        }
        request.setTimeout(30_000);
        request.putHeader("content-type", "application/json");
        request.exceptionHandler(event -> {
            Logging.log.warn("Exception while performing request", event.getCause());
        });
        request.handler(event -> {
            if (event.statusCode() >= 200 && event.statusCode() < 300) {
                latch.countDown();
            } else {
                Logging.log.warn("Error when deploying addresses: " + event.statusCode() + ": " + event.statusMessage());
            }
        });
        request.end(Buffer.buffer(mapper.writeValueAsBytes(config)));
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new RuntimeException("Timeout deploying address config");
        }
    }
}
