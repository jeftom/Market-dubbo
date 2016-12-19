package com.alibaba.dubbo.remoting.transport.netty4;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.remoting.ChannelHandler;
import com.alibaba.dubbo.remoting.Client;
import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.remoting.Server;
import com.alibaba.dubbo.remoting.Transporter;

public class NettyTransporter implements Transporter {

	public static final String NAME = "netty";

	@Override
	public Server bind(URL url, ChannelHandler handler) throws RemotingException {
		return new NettyServer(url, handler);
	}

	@Override
	public Client connect(URL url, ChannelHandler handler) throws RemotingException {
		return new NettyClient(url, handler);
	}
}
