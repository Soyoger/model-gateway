package com.github.soyoger.modelgateway.domain.service;

import com.github.soyoger.modelgateway.client.constant.GatewayErrorCode;
import com.github.soyoger.modelgateway.domain.entity.ApiKeyCredential;
import com.github.soyoger.modelgateway.domain.exception.GatewayException;
import com.github.soyoger.modelgateway.domain.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

/**
 * 认证领域服务。
 */
@RequiredArgsConstructor
public class AuthService {

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyCredential authenticate(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GatewayException(GatewayErrorCode.INVALID_API_KEY);
        }
        Optional<ApiKeyCredential> credential = apiKeyRepository.findByKey(apiKey);
        if (credential.isEmpty() || !credential.get().isEnabled()) {
            throw new GatewayException(GatewayErrorCode.INVALID_API_KEY);
        }
        return credential.get();
    }

    public void authorizeModel(ApiKeyCredential credential, String model) {
        if (!credential.canAccessModel(model)) {
            throw new GatewayException(GatewayErrorCode.UNAUTHORIZED_MODEL,
                "API Key not allowed to access model: " + model);
        }
    }
}
