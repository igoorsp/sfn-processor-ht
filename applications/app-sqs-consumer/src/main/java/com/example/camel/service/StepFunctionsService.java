package com.example.camel.service;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.SendTaskFailureRequest;
import software.amazon.awssdk.services.sfn.model.SendTaskSuccessRequest;

@Service
public class StepFunctionsService {

    private final SfnClient sfnClient;

    public StepFunctionsService(SfnClient sfnClient) {
        this.sfnClient = sfnClient;
    }

    public void sendTaskSuccess(String taskToken, String output) {
        SendTaskSuccessRequest successRequest = SendTaskSuccessRequest.builder()
                .taskToken(taskToken)
                .output(output)
                .build();
        sfnClient.sendTaskSuccess(successRequest);
    }

    public void sendTaskFailure(String taskToken, String error, String cause) {
        SendTaskFailureRequest failureRequest = SendTaskFailureRequest.builder()
                .taskToken(taskToken)
                .error(error)
                .cause(cause)
                .build();
        sfnClient.sendTaskFailure(failureRequest);
    }
}