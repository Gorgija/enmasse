/*
 * Copyright 2017 Red Hat Inc.
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
package io.enmasse.address.model.v1;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.*;
import io.enmasse.address.model.AddressSpace;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Serializer for AddressSpace V1 format
 */
class AddressSpaceV1Serializer extends JsonSerializer<AddressSpace> {

    @Override
    public void serialize(AddressSpace addressSpace, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException, JsonProcessingException {
        ObjectNode root = (ObjectNode) jsonGenerator.getCodec().createObjectNode();
        root.put(Fields.API_VERSION, "enmasse.io/v1");
        root.put(Fields.KIND, "AddressSpace");
        serialize(addressSpace, root);
        root.serialize(jsonGenerator, serializerProvider);
    }

    static void serialize(AddressSpace addressSpace, ObjectNode root) {
        ObjectNode metadata = root.putObject(Fields.METADATA);
        ObjectNode spec = root.putObject(Fields.SPEC);
        ObjectNode status = root.putObject(Fields.STATUS);

        metadata.put(Fields.NAME, addressSpace.getName());
        metadata.put(Fields.NAMESPACE, addressSpace.getNamespace());

        spec.put(Fields.TYPE, addressSpace.getType().getName());
        spec.put(Fields.PLAN, addressSpace.getPlan().getName());

        if (!addressSpace.getEndpoints().isEmpty()) {
            ArrayNode endpoints = spec.putArray(Fields.ENDPOINTS);
            for (io.enmasse.address.model.Endpoint endpoint : addressSpace.getEndpoints()) {
                ObjectNode e = endpoints.addObject();
                e.put(Fields.NAME, endpoint.getName());
                e.put(Fields.SERVICE, endpoint.getService());
                endpoint.getHost().ifPresent(h -> e.put(Fields.HOST, h));
                endpoint.getCertProvider().ifPresent(provider -> {
                    ObjectNode p = e.putObject(Fields.CERT_PROVIDER);
                    p.put(Fields.NAME, provider.getName());
                    p.put(Fields.SECRET_NAME, provider.getSecretName());
                });
            }
        }

        ObjectNode authenticationService = spec.putObject(Fields.AUTHENTICATION_SERVICE);
        authenticationService.put(Fields.TYPE, addressSpace.getAuthenticationService().getType().getName());
        ObjectNode authDetails = authenticationService.putObject(Fields.DETAILS);
        Map<String, Object> details = addressSpace.getAuthenticationService().getDetails();

        for (Map.Entry<String, Class> detailsFields : addressSpace.getAuthenticationService().getType().getDetailsFields().entrySet()) {
            if (details.containsKey(detailsFields.getKey())) {
                authDetails.set(detailsFields.getKey(), TypeConverter.getJsonNode(detailsFields.getValue(), details.get(detailsFields.getKey())));
            }
        }

        status.put(Fields.IS_READY, addressSpace.getStatus().isReady());
        if (!addressSpace.getStatus().getMessages().isEmpty()) {
            ArrayNode messages = status.putArray(Fields.MESSAGES);
            for (String message : addressSpace.getStatus().getMessages()) {
                messages.add(message);
            }
        }
    }

    static class TypeConverter {
        private static final Map<Class, Function<Object, JsonNode>> converterMap = new HashMap<>();

        static {
            converterMap.put(String.class, o -> new TextNode((String) o));
            converterMap.put(Integer.class, o -> new IntNode((Integer) o));
            converterMap.put(Long.class, o -> new LongNode((Long) o));
            converterMap.put(Boolean.class, o -> BooleanNode.valueOf((Boolean) o));
        }

        public static JsonNode getJsonNode(Class type, Object value) {
            return converterMap.get(type).apply(value);
        }
    }
}
