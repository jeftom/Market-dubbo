package com.alibaba.dubbo.tracer.client;

import com.alibaba.dubbo.common.extension.SPI;
import com.alibaba.dubbo.tracer.api.TracingCollector;


@SPI(DstConstants.DEFAULT_COLLECTOR_TYPE)
public interface TracingCollectorFactory {

    /**
     * 监控链路的数据同步器
     * @return
     */
    public TracingCollector getTracingCollector();

}
