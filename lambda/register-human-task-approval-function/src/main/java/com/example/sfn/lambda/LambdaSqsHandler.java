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
    public LambdaSqsHandler(final DynamoDbRepository dynamoDbRepository,
                            final ObjectMapper objectMapper) {
        this.dynamoDbRepository = dynamoDbRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        LOGGER.info("Events: {}", event);

        for (SQSEvent.SQSMessage msg : event.getRecords()) {
            try {
                SqsMessage sqsMessage = parseMessage(msg.getBody());

                // 🔽 NOVO: log e controle do retryCount
                Integer retryCount = sqsMessage.getRetryCount() != null ? sqsMessage.getRetryCount() : 0;
                LOGGER.info("RetryCount atual: {}", retryCount);

                if (sqsMessage.getErrorDetail() != null) {
                    String cause = sqsMessage.getErrorDetail().getCause();
                    String error = sqsMessage.getErrorDetail().getError();

                    LOGGER.warn("Mensagem com erro de validação recebida: {}", cause != null ? cause : "(sem causa)");
                    LOGGER.warn("Erro de validação: {}", error != null ? error : "(sem erro)");
                }

                    LOGGER.info("Processando mensagem: TaskToken={}, Status={}", sqsMessage.getTaskToken(), sqsMessage.getStatus());

                // 🔽 NOVO: se quiser forçar rejeição após X tentativas
                if (retryCount >= 3) {
                    LOGGER.warn("TaskToken={} excedeu o limite de tentativas ({}), forçando status REJECTED.",
                            sqsMessage.getTaskToken(), retryCount);
                    sqsMessage.setStatus("REJECTED");
                }

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
