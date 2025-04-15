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

import java.util.Optional;

@ApplicationScoped
@Named("lambdaSqsHandler")
public class LambdaSqsHandler implements RequestHandler<SQSEvent, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LambdaSqsHandler.class);
    private static final int MAX_RETRY_BEFORE_REJECTION = 2;
    private static final String SUFIX_SEPARATOR = "-";

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
    public Void handleRequest(final SQSEvent event, final Context context) {
        LOGGER.info("Eventos recebidos: {}", event);

        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                final SqsMessage sqsMessage = parseMessage(message.getBody());
                logBasicInfo(sqsMessage);

                final Integer retryCount = Optional.ofNullable(sqsMessage.getRetryCount()).orElse(0);
                final String businessKey = sqsMessage.getBusinessKey();

                logErrorDetailsIfPresent(sqsMessage);

                if (retryCount >= MAX_RETRY_BEFORE_REJECTION && businessKey != null) {
                    LOGGER.warn("TaskToken={} excedeu limite de tentativas ({}), aplicando sufixo.",
                            sqsMessage.getTaskToken(), retryCount);
                    sqsMessage.setBusinessKey(businessKey + SUFIX_SEPARATOR + retryCount);
                }

                dynamoDbRepository.saveMessage(sqsMessage, dynamoDbTable);
                LOGGER.info("Mensagem persistida com sucesso: TaskToken={}", sqsMessage.getTaskToken());

            } catch (InvalidMessageException e) {
                LOGGER.error("Mensagem inválida: {}", message.getBody(), e);
                throw new MessageProcessingException("Falha ao processar mensagem inválida", e);
            } catch (MessageProcessingException e) {
                LOGGER.error("Erro ao processar mensagem: {}", message.getBody(), e);
                throw e;
            }
        }

        return null;
    }

    private SqsMessage parseMessage(String messageBody) {
        try {
            final SqsMessage message = objectMapper.readValue(messageBody, SqsMessage.class);

            if (message.getTaskToken() == null || message.getExecutionId() == null) {
                throw new InvalidMessageException("Campos obrigatórios faltando: taskToken e executionId");
            }

            return message;
        } catch (JsonProcessingException e) {
            throw new InvalidMessageException("JSON inválido: " + messageBody, e);
        }
    }

    private void logBasicInfo(SqsMessage message) {
        LOGGER.info("Processando mensagem: TaskToken={}, Status={}", message.getTaskToken(), message.getStatus());
        LOGGER.info("BusinessKey: {}", message.getBusinessKey());
        LOGGER.info("RetryCount atual: {}", Optional.ofNullable(message.getRetryCount()).orElse(0));
    }

    private void logErrorDetailsIfPresent(SqsMessage message) {
        if (message.getErrorDetail() != null) {
            final String cause = Optional.ofNullable(message.getErrorDetail().getCause()).orElse("(sem causa)");
            final String error = Optional.ofNullable(message.getErrorDetail().getError()).orElse("(sem erro)");

            LOGGER.warn("Mensagem com erro de validação recebida: {}", cause);
            LOGGER.warn("Erro de validação: {}", error);
        }
    }
}
