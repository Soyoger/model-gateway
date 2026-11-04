package com.github.soyoger.modelgateway.domain.repository;

import com.github.soyoger.modelgateway.domain.entity.ModelEndpoint;

import java.util.List;
import java.util.Optional;

/**
 * 后端节点仓储接口。
 */
public interface EndpointRepository {

    List<ModelEndpoint> findByModel(String model);

    List<ModelEndpoint> findAll();

    Optional<ModelEndpoint> findById(String endpointId);

    void save(ModelEndpoint endpoint);
}
