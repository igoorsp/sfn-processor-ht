package com.example.camel.routes;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GetDynamoDbRouteBuilder extends RouteBuilder {

    private static final String STATUS = "status";
    private static final String EXECUTION_ID = "executionId";
    private static final String BUSINESS_KEY = "businessKey";
    private static final String EXECUTION_START_TIME = "executionStartTime";
    private static final String TASK_TOKEN = "taskToken";

    private final DynamoDbClient dynamoDbClient;

    public GetDynamoDbRouteBuilder(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @Value("${app.dynamodb.table}")
    String tableName;

    @Override
    public void configure() {

        from("direct:getItemsByStatus")
                .log("Fetching tasks with status: ${header.status}")
                .process(exchange -> {

                    final String status = exchange.getIn().getHeader(STATUS, String.class);

                    if (status == null || status.isEmpty()) {
                        throw new IllegalArgumentException("status is required and cannot be null or empty");
                    }

                    // Definindo a chave de partição para a consulta no GSI
                    Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
                    expressionAttributeValues.put(":status", AttributeValue.builder().s(status).build());

                    // Construindo a requisição de consulta no GSI
                    QueryRequest queryRequest = QueryRequest.builder()
                            .tableName(tableName)
                            .indexName("status-index") // Nome do GSI
                            .keyConditionExpression("#status = :status")
                            .expressionAttributeNames(Map.of("#status", STATUS))
                            .expressionAttributeValues(expressionAttributeValues)
                            .build();

                    // Executando a consulta no DynamoDB
                    QueryResponse queryResponse = dynamoDbClient.query(queryRequest);

                    List<Map<String, String>> items = queryResponse.items().stream()
                            .map(item -> {
                                Map<String, String> response = new HashMap<>();
                                response.put(EXECUTION_ID, item.get(EXECUTION_ID).s());
                                response.put(BUSINESS_KEY, item.getOrDefault(BUSINESS_KEY, AttributeValue.builder().s("").build()).s());
                                response.put(EXECUTION_START_TIME, item.getOrDefault(EXECUTION_START_TIME, AttributeValue.builder().s("").build()).s());
                                response.put(STATUS, item.getOrDefault(STATUS, AttributeValue.builder().s("").build()).s());
                                response.put(TASK_TOKEN, item.getOrDefault(TASK_TOKEN, AttributeValue.builder().s("").build()).s());
                                return response;
                            }).toList();
                        exchange.getIn().setBody(items);
                })
                .log("Query result: ${body}");
    }
}