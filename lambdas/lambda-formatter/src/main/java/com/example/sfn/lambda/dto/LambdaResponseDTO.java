package com.example.sfn.lambda.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RegisterForReflection
public class LambdaResponseDTO {
    private String businessKey;
}
