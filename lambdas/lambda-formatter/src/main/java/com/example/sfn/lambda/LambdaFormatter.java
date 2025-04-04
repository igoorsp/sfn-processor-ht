package com.example.sfn.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.example.sfn.lambda.dto.LambdaResponseDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@ApplicationScoped
@Named("lambdaFormatter")
public class LambdaFormatter implements RequestHandler<Map<String, String>, LambdaResponseDTO> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LambdaFormatter.class);

    @Override
    public LambdaResponseDTO handleRequest(final Map<String, String> event,
                                           final Context context) {

        final String businessKey = event.get("businessKey");
        LOGGER.info("businessKey: {}", businessKey);

        return LambdaResponseDTO.builder()
                .businessKey(businessKey)
                .build();
    }
}