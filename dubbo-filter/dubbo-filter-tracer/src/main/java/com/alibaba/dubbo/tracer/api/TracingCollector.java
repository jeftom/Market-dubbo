package com.alibaba.dubbo.tracer.api;

import java.util.List;

public interface TracingCollector {
	public void push(List<Span> spanList);
}
