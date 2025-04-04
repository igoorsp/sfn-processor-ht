package com.example.camel.routes;

import com.example.camel.service.StepFunctionsService;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.sqs.Sqs2Constants;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class SQSConsumerRouteBuilder extends RouteBuilder {

    private static final String SQS_QUEUE_NAME = "state-machine-queue";
    public static final String IS_VALID = "isValid";
    public static final String TASK_TOKEN = "taskToken";

    private final StepFunctionsService stepFunctionsService;
    private final JacksonDataFormat jsonDataFormat = new JacksonDataFormat(HashMap.class);

    public SQSConsumerRouteBuilder(StepFunctionsService stepFunctionsService) {
        this.stepFunctionsService = stepFunctionsService;
    }

    @Override
    public void configure() throws Exception {
        from("aws2-sqs://" + SQS_QUEUE_NAME +
                "?amazonSQSClient=#amazonSQSClient" +
                "&maxMessagesPerPoll=1" +
                "&waitTimeSeconds=20" +
                "&concurrentConsumers=1" +
                "&visibilityTimeout=30" +
                "&deleteAfterRead=false")
            .unmarshal(jsonDataFormat)
            .doTry()
                .process(this::validateAndProcessMessage)
                .choice()
                    .when(header(IS_VALID).isEqualTo(true))
                        .process(this::handleApproval)
                        .process(this::sendSuccess)
                    .otherwise()
                        .process(this::sendFailure)
                .end()
                .process(this::deleteMessage)
            .endDoTry()
            .doCatch(Exception.class)
                .process(this::handleProcessingError)
            .end();

    }

    // Métodos auxiliares mantidos iguais
    private void validateAndProcessMessage(Exchange exchange) {
        Map<String, Object> message = exchange.getIn().getBody(Map.class);

        String taskToken = (String) message.get(TASK_TOKEN);
        String businessKey = (String) message.get("businessKey");

        exchange.getIn().setHeader(TASK_TOKEN, taskToken);

        if (!businessKey.matches("my-business-key-\\d+")) {
            exchange.getIn().setHeader(IS_VALID, false);
            exchange.getIn().setHeader("errorCause", "Formato inválido do businessKey");
            return;
        }

        String[] parts = businessKey.split("-");
        int number = Integer.parseInt(parts[parts.length - 1]);
        exchange.getIn().setHeader(IS_VALID, true);
        exchange.getIn().setHeader("number", number);
    }

    private void handleApproval(Exchange exchange) {
        int number = exchange.getIn().getHeader("number", Integer.class);
        String status = (number % 2 == 0) ? "APPROVED" : "REJECTED";
        exchange.getIn().setHeader("result", status);
    }

    private void sendSuccess(Exchange exchange) {
        String taskToken = exchange.getIn().getHeader(TASK_TOKEN, String.class);
        String result = exchange.getIn().getHeader("result", String.class);

        stepFunctionsService.sendTaskSuccess(
            taskToken,
            "{\"result\": \"" + result + "\"}"
        );
    }

    private void sendFailure(Exchange exchange) {
        String taskToken = exchange.getIn().getHeader(TASK_TOKEN, String.class);
        String cause = exchange.getIn().getHeader("errorCause", "Validation failed", String.class);

        stepFunctionsService.sendTaskFailure(
            taskToken,
            "ValidationError",
            cause
        );
    }

    private void handleProcessingError(Exchange exchange) {
        Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        String taskToken = exchange.getIn().getHeader(TASK_TOKEN, String.class);

        stepFunctionsService.sendTaskFailure(
            taskToken,
            "ProcessingError",
            "Erro no processamento: " + exception.getMessage()
        );
    }

    private void deleteMessage(Exchange exchange) {
        String receiptHandle = exchange.getIn().getHeader(Sqs2Constants.RECEIPT_HANDLE, String.class);
        if (receiptHandle != null) {
            exchange.getIn().setHeader(Sqs2Constants.RECEIPT_HANDLE, receiptHandle);
            exchange.getIn().setHeader(Sqs2Constants.SQS_OPERATION, "deleteMessage");
        } else {
            log.warn("Receipt Handle não encontrado. A mensagem não será excluída.");
        }
    }
}