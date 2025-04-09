package com.example.camel.routes;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.ddb.Ddb2Constants;
import org.apache.camel.component.aws2.ddb.Ddb2Operations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.AttributeValueUpdate;

import java.util.HashMap;
import java.util.Map;

@Component
public class PutDynamoDbRouteBuilder extends RouteBuilder {

    private static final String STATUS = "status";
    private static final String EXECUTION_ID = "executionId";

    @Value("${app.dynamodb.table}")
    private String tableName;

    @Override
    public void configure() {

        from("direct:updateTask")
                .log("Updating item in DynamoDB with body: ${body}")
                .process(exchange -> {
                    Map<String, Object> requestBody = exchange.getIn().getBody(Map.class);

                    // Validação dos campos obrigatórios
                    if (!requestBody.containsKey(EXECUTION_ID)) {
                        throw new IllegalArgumentException("executionId is required in the request body");
                    }
                    if (!requestBody.containsKey(STATUS)) {
                        throw new IllegalArgumentException("status is required in the request body");
                    }

                    // Define a chave (partition key) do registro
                    Map<String, AttributeValue> keyMap = new HashMap<>();
                    keyMap.put(EXECUTION_ID, AttributeValue.builder()
                            .s(requestBody.get(EXECUTION_ID).toString())
                            .build());

                    // Define os valores que serão atualizados
                    Map<String, AttributeValueUpdate> updateValues = new HashMap<>();
                    updateValues.put(STATUS, AttributeValueUpdate.builder()
                            .value(AttributeValue.builder()
                                    .s(requestBody.get(STATUS).toString())
                                    .build())
                            .build());

                    // Configura os headers para a operação de atualização no DynamoDB
                    exchange.getIn().setHeader(Ddb2Constants.KEY, keyMap); // Chave do item
                    exchange.getIn().setHeader(Ddb2Constants.OPERATION, Ddb2Operations.UpdateItem); // Operação de atualização
                    exchange.getIn().setHeader(Ddb2Constants.UPDATE_VALUES, updateValues); // Valores a serem atualizados
                })
                .toF("aws2-ddb://%s?amazonDDBClient=#amazonDDBClient", tableName) // Envia para o DynamoDB
                .log("Item updated successfully")
                .setBody(simple("Item updated successfully")); // Resposta de sucesso
    }
}