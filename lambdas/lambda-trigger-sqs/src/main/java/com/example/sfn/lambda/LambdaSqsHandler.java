package com.example.sfn.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.example.sfn.lambda.dto.SqsMessage;
import com.example.sfn.lambda.exception.InvalidMessageException;
import com.example.sfn.lambda.exception.MessageProcessingException;
import com.example.sfn.lambda.repository.DynamoDbRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@Named("lambdaSqsHandler")
public class LambdaSqsHandler implements RequestHandler<SQSEvent, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LambdaSqsHandler.class);

    @ConfigProperty(name = "app.dynamodb.table")
    String dynamoDbTable;

    private final DynamoDbRepository dynamoDbRepository;
    private final ObjectMapper objectMapper;

    @Inject
    public LambdaSqsHandler(DynamoDbRepository dynamoDbRepository,
                            ObjectMapper objectMapper) {
        this.dynamoDbRepository = dynamoDbRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage msg : event.getRecords()) {
            try {
                SqsMessage sqsMessage = parseMessage(msg.getBody());
                LOGGER.info("Processando mensagem: TaskToken={}, Status={}", sqsMessage.getTaskToken(), sqsMessage.getStatus());

                dynamoDbRepository.saveMessage(sqsMessage, dynamoDbTable);
                LOGGER.info("Mensagem salva no DynamoDB com sucesso: TaskToken={}", sqsMessage.getTaskToken());

            } catch (InvalidMessageException e) {
                LOGGER.error("Mensagem inválida: {}", msg.getBody(), e);
                throw new MessageProcessingException("Falha ao processar mensagem inválida", e);
            } catch (MessageProcessingException e) {
                LOGGER.error("Erro ao processar mensagem: {}", msg.getBody(), e);
                throw e;
            }
        }
        return null;
    }

    private SqsMessage parseMessage(String messageBody) {
        try {
            SqsMessage message = objectMapper.readValue(messageBody, SqsMessage.class);
            if (message.getTaskToken() == null || message.getExecutionId() == null) {
                throw new InvalidMessageException("Campos obrigatórios faltando: taskToken e executionId");
            }
            return message;
        } catch (JsonProcessingException e) {
            throw new InvalidMessageException("JSON inválido: " + messageBody, e);
        }
    }
}