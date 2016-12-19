package com.alibaba.dubbo.remoting.transport.netty4;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.remoting.Channel;
import com.alibaba.dubbo.remoting.ChannelHandler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

@io.netty.channel.ChannelHandler.Sharable
public class NettyHandler extends SimpleChannelInboundHandler {

	private final Map<String, Channel> channels = new ConcurrentHashMap<String, Channel>();

	private final URL url;

	private final ChannelHandler handler;

	public NettyHandler(URL url, ChannelHandler handler) {
		if (url == null) {
			throw new IllegalArgumentException("url == null");
		}
		if (handler == null) {
			throw new IllegalArgumentException("handler == null");
		}
		this.url = url;
		this.handler = handler;
	}

	public Map<String, Channel> getChannels() {
		return channels;
	}

	@Override
	public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
		NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
		try {
			if (channel != null) {
				channels.put(NetUtils.toAddressString((InetSocketAddress) ctx.channel().remoteAddress()), channel);
			}
			handler.connected(channel);
		} finally {
			NettyChannel.removeChannelIfDisconnected(ctx.channel());
		}
	}

	@Override
	public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
		NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
		try {
			channels.remove(NetUtils.toAddressString((InetSocketAddress) ctx.channel().remoteAddress()));
			handler.disconnected(channel);
		} finally {
			NettyChannel.removeChannelIfDisconnected(ctx.channel());
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
		try {
			handler.caught(channel, cause.getCause());
		} finally {
			NettyChannel.removeChannelIfDisconnected(ctx.channel());
		}
	}

	@Override
	protected void channelRead0(ChannelHandlerContext channelHandlerContext, Object o) throws Exception {
		NettyChannel channel = NettyChannel.getOrAddChannel(channelHandlerContext.channel(), url, handler);
		try {
			handler.received(channel, o);
		} finally {
			NettyChannel.removeChannelIfDisconnected(channelHandlerContext.channel());
		}
	}

}
