package com.github.soyoger.modelgateway.domain.repository;

import com.github.soyoger.modelgateway.domain.entity.ApiKeyCredential;

import java.util.Optional;

/**
 * API Key 仓储接口。
 */
public interface ApiKeyRepository {

    Optional<ApiKeyCredential> findByKey(String apiKey);
}
