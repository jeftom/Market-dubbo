package com.alibaba.dubbo.tracer.client;


import com.alibaba.dubbo.common.extension.SPI;
import com.alibaba.dubbo.tracer.api.Span;

@SPI("default")
public interface SyncTransfer {
	
    public void start();
    
    public void cancel();
    
    public void syncSend(Span span);

}
