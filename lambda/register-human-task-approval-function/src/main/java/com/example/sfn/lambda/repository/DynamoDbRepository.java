package com.example.sfn.lambda.repository;

import com.example.sfn.lambda.dto.SqsMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class DynamoDbRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamoDbRepository.class);

    private final DynamoDbClient dynamoDbClient;

    private static final String STATUS = "status";
    private static final String EXECUTION_ID = "executionId";
    private static final String BUSINESS_KEY = "businessKey";
    private static final String EXECUTION_START_TIME = "executionStartTime";
    private static final String TASK_TOKEN = "taskToken";
    private static final String RETRY_COUNT = "retryCount"; // Novo campo

    @Inject
    public DynamoDbRepository(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    public void saveMessage(final SqsMessage message, final String tableName) {
        Map<String, AttributeValue> item = new HashMap<>();

        item.put(BUSINESS_KEY, AttributeValue.builder().s(message.getBusinessKey()).build());
        item.put(EXECUTION_START_TIME, AttributeValue.builder().s(message.getExecutionStartTime()).build());
        item.put(EXECUTION_ID, AttributeValue.builder().s(message.getExecutionId()).build());
        item.put(TASK_TOKEN, AttributeValue.builder().s(message.getTaskToken()).build());
        item.put(STATUS, AttributeValue.builder().s("PENDING").build());

        if (message.getRetryCount() != null) {
            item.put(RETRY_COUNT, AttributeValue.builder().n(String.valueOf(message.getRetryCount())).build());
        } else {
            item.put(RETRY_COUNT, AttributeValue.builder().n("0").build()); // Se não houver, coloca 0
        }

        LOGGER.info("tableName dynamodb: {}", tableName);
        LOGGER.info("item dynamodb: {}", item);

        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build();

        dynamoDbClient.putItem(putItemRequest);
    }
}
