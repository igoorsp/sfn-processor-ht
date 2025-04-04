package com.example.camel.routes;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.sqs.Sqs2Constants;
import org.springframework.stereotype.Component;

@Component
public class SQSConsumerRouteBuilder extends RouteBuilder {

    private static final String SQS_QUEUE_NAME = "state-machine-queue";

    @Override
    public void configure() throws Exception {
        // Configuração principal do consumidor SQS
        from("aws2-sqs://" + SQS_QUEUE_NAME +
                "?amazonSQSClient=#sqsClient" +
                "&maxMessagesPerPoll=5" +
                "&waitTimeSeconds=20" +
                "&concurrentConsumers=3" +
                "&visibilityTimeout=30" +  // Mensagem fica invisível por 30s após erro
                "&deleteAfterRead=false") // Confirmação manual

                // Configuração de retentativas
                .errorHandler(defaultErrorHandler()
                        .maximumRedeliveries(3)    // Máximo de 3 tentativas
                        .redeliveryDelay(10000))  // 10s entre retentativas

                // Processamento
                .log("Nova mensagem recebida - Tentativa ${header.CamelRedeliveryCounter}")
                .process(this::processMessage)

                // Confirmação manual após sucesso
                .log("Processamento concluído com sucesso")
                .removeHeader(Sqs2Constants.RECEIPT_HANDLE) // Não necessário para delete
                .toD("aws2-sqs://" + SQS_QUEUE_NAME + "?amazonSQSClient=#sqsClient&operation=deleteMessage");
    }

    private void processMessage(Exchange exchange) {
        String body = exchange.getIn().getBody(String.class);

        // Simulação de erro transitório
        if (shouldFail(exchange)) {
            throw new RuntimeException("Erro simulado - Tentativa: " +
                    exchange.getIn().getHeader("CamelRedeliveryCounter", 0));
        }

        // Lógica de processamento real
        exchange.getMessage().setBody(transformBody(body));
    }

    private boolean shouldFail(Exchange exchange) {
        int attempt = exchange.getIn().getHeader("CamelRedeliveryCounter", 0, Integer.class);
        return attempt < 2; // Falha nas 2 primeiras tentativas
    }

    private String transformBody(String original) {
        // Sua transformação aqui
        return original.toUpperCase();
    }
}