package com.github.soyoger.modelgateway.domain.aggregate;

import com.github.soyoger.modelgateway.domain.entity.ModelEndpoint;
import com.github.soyoger.modelgateway.domain.enums.RouteReason;
import lombok.Builder;
import lombok.Data;

/**
 * 路由结果聚合对象。
 */
@Data
@Builder
public class RouteResult {

    private ModelEndpoint endpoint;
    private RouteReason reason;
    private boolean canaryHit;
    private String stainHit;
    private String message;
}
