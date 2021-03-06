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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import io.enmasse.address.model.*;
import io.enmasse.address.model.types.AddressSpaceType;
import io.enmasse.address.model.types.Plan;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

/**
 * Deserializer for AddressSpace V1 format
 */
class AddressSpaceV1Deserializer extends JsonDeserializer<AddressSpace> {

    private static final ObjectMapper mapper = new ObjectMapper();
    private final DecodeContext context;

    AddressSpaceV1Deserializer(DecodeContext context) {
        this.context = context;
    }

    @Override
    public AddressSpace deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        ObjectNode root = mapper.readValue(jsonParser, ObjectNode.class);
        return deserialize(root);
    }

    AddressSpace deserialize(ObjectNode root) {

        ObjectNode metadata = (ObjectNode) root.get(Fields.METADATA);
        ObjectNode spec = (ObjectNode) root.get(Fields.SPEC);

        AddressSpaceType type = context.getAddressSpaceType(spec.get(Fields.TYPE).asText());

        AddressSpace.Builder builder = new AddressSpace.Builder()
                .setName(metadata.get(Fields.NAME).asText())
                .setType(type);

        if (spec.hasNonNull(Fields.PLAN)) {
            builder.setPlan(findPlan(type, spec.get(Fields.PLAN).asText()));
        }

        if (metadata.hasNonNull(Fields.NAMESPACE)) {
            builder.setNamespace(metadata.get(Fields.NAMESPACE).asText());
        }

        if (spec.hasNonNull(Fields.ENDPOINTS)) {
            ArrayNode endpoints = (ArrayNode) spec.get(Fields.ENDPOINTS);
            for (int i = 0; i < endpoints.size(); i++) {
                ObjectNode endpoint = (ObjectNode) endpoints.get(i);
                io.enmasse.address.model.Endpoint.Builder b = new io.enmasse.address.model.Endpoint.Builder()
                        .setName(endpoint.get(Fields.NAME).asText())
                        .setService(endpoint.get(Fields.SERVICE).asText());

                if (endpoint.hasNonNull(Fields.HOST)) {
                    b.setHost(endpoint.get(Fields.HOST).asText());
                }

                if (endpoint.hasNonNull(Fields.CERT_PROVIDER)) {
                    ObjectNode certProvider = (ObjectNode) endpoint.get(Fields.CERT_PROVIDER);
                    b.setCertProvider(new CertProvider(
                            certProvider.get(Fields.NAME).asText(),
                            certProvider.get(Fields.SECRET_NAME).asText()));
                }
                builder.appendEndpoint(b.build());
            }
        }

        if (spec.hasNonNull(Fields.AUTHENTICATION_SERVICE)) {
            AuthenticationService.Builder authService = new AuthenticationService.Builder();
            ObjectNode authenticationService = (ObjectNode) spec.get(Fields.AUTHENTICATION_SERVICE);
            AuthenticationServiceType authType = AuthenticationServiceType.create(authenticationService.get(Fields.TYPE).asText());
            authService.setType(authType);

            Map<String, Object> detailsMap = new HashMap<>();
            if (authenticationService.hasNonNull(Fields.DETAILS)) {
                ObjectNode details = (ObjectNode) authenticationService.get(Fields.DETAILS);
                Iterator<Map.Entry<String, JsonNode>> it = details.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> entry = it.next();
                    JsonNode node = entry.getValue();
                    if (!authType.getDetailsFields().containsKey(entry.getKey())) {
                        throw new RuntimeException("Unknown details field " + entry.getKey() + " encountered");
                    }
                    detailsMap.put(entry.getKey(), TypeConverter.getValue(authType.getDetailsFields().get(entry.getKey()), node));
                }
            }
            authService.setDetails(detailsMap);
            if (!detailsMap.keySet().containsAll(authType.getMandatoryFields())) {
                Set<String> missingDetails = new HashSet<>(authType.getMandatoryFields());
                missingDetails.removeAll(detailsMap.keySet());
                throw new RuntimeException("Missing details " + missingDetails + " for type " + authType.getName());
            }

            if (!authType.getDetailsFields().keySet().containsAll(detailsMap.keySet())) {
                Set<String> extraDetails = new HashSet<>(detailsMap.keySet());
                extraDetails.removeAll(authType.getDetailsFields().keySet());
                throw new RuntimeException("Unknown details " + extraDetails + " specified for type " + authType.getName());
            }
            builder.setAuthenticationService(authService.build());
        } else {
            builder.setAuthenticationService(new AuthenticationService.Builder()
                    .setType(context.getDefaultAuthenticationServiceType())
                    .build());
        }

        ObjectNode status = (ObjectNode) root.get(Fields.STATUS);
        if (status != null) {
            boolean isReady = status.get(Fields.IS_READY).asBoolean();
            Status s = new Status(isReady);
            if (status.hasNonNull(Fields.MESSAGES)) {
                ArrayNode messages = (ArrayNode) status.get(Fields.MESSAGES);
                for (int i = 0; i < messages.size(); i++) {
                    s.appendMessage(messages.get(i).asText());
                }
            }
            builder.setStatus(s);
        }
        return builder.build();
    }

    private static Plan findPlan(AddressSpaceType type, String planName) {
        for (Plan plan : type.getPlans()) {
            if (plan.getName().equals(planName)) {
                return plan;
            }
        }

        throw new RuntimeException("Unknown plan " + planName + " for type " + type.getName());
    }

    static class TypeConverter {
        private static final Map<Class, Function<JsonNode, Object>> converterMap = new HashMap<>();

        static {
            converterMap.put(TextNode.class, JsonNode::textValue);
            converterMap.put(IntNode.class, JsonNode::intValue);
            converterMap.put(LongNode.class, JsonNode::longValue);
            converterMap.put(BooleanNode.class, JsonNode::booleanValue);
        }

        public static Object getValue(Class type, JsonNode node) {
            Object value = converterMap.get(node.getClass()).apply(node);
            if (!type.equals(value.getClass())) {
                throw new RuntimeException("Expected value of type " + type + ", but was " + value.getClass());
            }
            return value;
        }
    }
}
