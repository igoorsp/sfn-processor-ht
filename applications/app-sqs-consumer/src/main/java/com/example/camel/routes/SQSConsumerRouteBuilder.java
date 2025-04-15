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

    // Constants: Queue and Route Parameters
    private static final String SQS_QUEUE_NAME = "send-aut-approval-queue";
    private static final String AMAZON_SQS_CLIENT_REF = "#amazonSQSClient";
    private static final int MAX_MESSAGES_PER_POLL = 1;
    private static final int WAIT_TIME_SECONDS = 20;
    private static final int CONCURRENT_CONSUMERS = 1;
    private static final int VISIBILITY_TIMEOUT = 30;
    private static final boolean DELETE_AFTER_READ = false;

    // Constants: Message Headers
    public static final String IS_VALID = "isValid";
    public static final String TASK_TOKEN = "taskToken";
    private static final String BUSINESS_KEY = "businessKey";
    private static final String RETRY_COUNT = "retryCount";
    private static final String ERROR_CAUSE = "errorCause";
    private static final String NUMBER = "number";
    private static final String RESULT = "result";

    // Constants: Default Values and Patterns
    private static final int DEFAULT_RETRY_COUNT = 0;
    private static final String BUSINESS_KEY_REGEX = "my-business-key-\\d+";
    private static final String DEFAULT_ERROR_CAUSE = "Validation failed";

    // Constructed URI
    private static final String SQS_URI = String.format("aws2-sqs://%s?amazonSQSClient=%s", SQS_QUEUE_NAME, AMAZON_SQS_CLIENT_REF);

    private final StepFunctionsService stepFunctionsService;
    private final JacksonDataFormat jsonDataFormat = new JacksonDataFormat(HashMap.class);

    public SQSConsumerRouteBuilder(StepFunctionsService stepFunctionsService) {
        this.stepFunctionsService = stepFunctionsService;
    }

    @Override
    public void configure() {
        from(SQS_URI +
                "&maxMessagesPerPoll=" + MAX_MESSAGES_PER_POLL +
                "&waitTimeSeconds=" + WAIT_TIME_SECONDS +
                "&concurrentConsumers=" + CONCURRENT_CONSUMERS +
                "&visibilityTimeout=" + VISIBILITY_TIMEOUT +
                "&deleteAfterRead=" + DELETE_AFTER_READ)
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
                .process(this::setDeleteHeaders)
                .to(SQS_URI + "&operation=deleteMessage")
            .endDoTry()
            .doCatch(Exception.class)
                .process(this::handleProcessingError)
            .end();
    }

    private void validateAndProcessMessage(final Exchange exchange) {
        final Map<String, Object> message = exchange.getIn().getBody(Map.class);

        final String taskToken = (String) message.get(TASK_TOKEN);
        final String businessKey = (String) message.get(BUSINESS_KEY);
        final Integer retryCount = (Integer) message.getOrDefault(RETRY_COUNT, DEFAULT_RETRY_COUNT);

        exchange.getIn().setHeader(TASK_TOKEN, taskToken);

        if (businessKey == null || !businessKey.matches(BUSINESS_KEY_REGEX)) {
            exchange.getIn().setHeader(IS_VALID, false);
            exchange.getIn().setHeader(ERROR_CAUSE, "Formato inválido do businessKey");
            return;
        }

        String[] parts = businessKey.split("-");
        String lastPart = parts[parts.length - 1];
        boolean hasNumber = lastPart.matches("\\d+");

        if (!hasNumber) {
            exchange.getIn().setHeader(IS_VALID, false);
            exchange.getIn().setHeader(ERROR_CAUSE, "businessKey sem número. retryCount=" + retryCount);
            return;
        }

        int number = Integer.parseInt(lastPart);
        exchange.getIn().setHeader(NUMBER, number);
        exchange.getIn().setHeader(IS_VALID, true);
    }

    private void handleApproval(Exchange exchange) {
        int number = exchange.getIn().getHeader(NUMBER, Integer.class);
        String status = (number % 2 == 0) ? "APPROVED" : "REJECTED";
        exchange.getIn().setHeader(RESULT, status);
    }

    private void sendSuccess(Exchange exchange) {
        String taskToken = exchange.getIn().getHeader(TASK_TOKEN, String.class);
        String result = exchange.getIn().getHeader(RESULT, String.class);

        stepFunctionsService.sendTaskSuccess(
            taskToken,
            "{\"result\": \"" + result + "\"}"
        );
    }

    private void sendFailure(Exchange exchange) {
        String taskToken = exchange.getIn().getHeader(TASK_TOKEN, String.class);
        String cause = exchange.getIn().getHeader(ERROR_CAUSE, DEFAULT_ERROR_CAUSE, String.class);

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

        setDeleteHeaders(exchange);
        exchange.getContext()
            .createProducerTemplate()
            .send(SQS_URI + "&operation=deleteMessage", exchange);
    }

    private void setDeleteHeaders(Exchange exchange) {
        String receiptHandle = exchange.getIn().getHeader(Sqs2Constants.RECEIPT_HANDLE, String.class);
        if (receiptHandle != null) {
            exchange.getIn().setHeader(Sqs2Constants.RECEIPT_HANDLE, receiptHandle);
        } else {
            log.warn("Receipt Handle não encontrado. A mensagem não será excluída.");
        }
    }
}
