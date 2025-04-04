package com.example.camel.routes;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MainRestRouteBuilder extends RouteBuilder {

    private static final String STATUS = "status";

    @Override
    public void configure() {

        // Configuração do REST DSL
        restConfiguration()
                .component("servlet")
                .bindingMode(RestBindingMode.json)
                .contextPath("/")
                .enableCORS(false) // Desative o CORS do Camel para evitar conflitos
                .port(8080);

        // AWS Step Functions
        // Endpoint POST
        rest("/callback")
                .post()
                .consumes("application/json")
                .produces("application/json")
                .to("direct:handleCallback");
    }
}
