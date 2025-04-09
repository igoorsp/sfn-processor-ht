package com.example.camel.routes;

import com.example.camel.service.StepFunctionsService;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class PostStepFunctionRouteBuilder extends RouteBuilder {

    private final StepFunctionsService stepFunctionsService;
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";

    public PostStepFunctionRouteBuilder(StepFunctionsService stepFunctionsService) {
        this.stepFunctionsService = stepFunctionsService;
    }

    @Override
    public void configure() {

        from("direct:handleCallback")
            .doTry()
                .log("Received callback with body: ${body}")
                .log("Headers: ${headers}")
                .process(exchange -> {
                    Map<String, Object> body = exchange.getIn().getBody(Map.class);
                    final String status = (String) body.get("status");
                    final String taskToken = (String) body.get("taskToken");
                    final String executionId = (String) body.get("executionId");

                    if (status == null || status.trim().isEmpty()) {
                        throw new IllegalArgumentException("Status is missing or empty in the message body");
                    }
                    if (taskToken == null || taskToken.trim().isEmpty()) {
                        throw new IllegalArgumentException("taskToken is missing or empty in the message body");
                    }
                    if (executionId == null || executionId.trim().isEmpty()) {
                        throw new IllegalArgumentException("executionId is missing or empty in the message body");
                    }

                    if (APPROVED.equals(status)) {
                        stepFunctionsService.sendTaskSuccess(taskToken, "{\"retryApproval\": true}");
                        log.info("TaskToken: {} - APPROVED sent", taskToken);
                    } else if (REJECTED.equals(status)) {
                        stepFunctionsService.sendTaskSuccess(taskToken, "{\"retryApproval\": false}");
                        log.info("TaskToken: {} - REJECTED sent", taskToken);
                    } else {
                        stepFunctionsService.sendTaskFailure(taskToken,
                            "Invalid status",
                            "Status not recognized by the system");
                        log.info("TaskToken: {} - FAILURE sent (unknown status: {})", taskToken, status);
                    }

                    // Monta o body para atualizar no DynamoDB
                    Map<String, Object> updateBody = new HashMap<>();
                    updateBody.put("executionId", executionId);
                    updateBody.put("status", status);

                    // Se houver outros atributos que você queira atualizar, insira aqui

                    // Define esse map como body para próxima etapa
                    exchange.getIn().setBody(updateBody);
                })
                // Faz o update no DynamoDB
                .to("direct:updateTask")
                .log("DynamoDB updated with status: ${body}")
            .doCatch(Exception.class)
                .log("Error processing callback: ${exception.message}")
            .end()
            .log("Callback handled successfully");
    }
}