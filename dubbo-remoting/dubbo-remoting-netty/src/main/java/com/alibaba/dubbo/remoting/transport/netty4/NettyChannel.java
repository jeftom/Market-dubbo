package com.alibaba.dubbo.remoting.transport.netty4;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.remoting.ChannelHandler;
import com.alibaba.dubbo.remoting.Codec;
import com.alibaba.dubbo.remoting.Codec2;
import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.remoting.transport.AbstractChannel;
import com.alibaba.dubbo.remoting.transport.codec.CodecAdapter;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

public class NettyChannel extends AbstractChannel {

	private static final Logger logger = LoggerFactory.getLogger(NettyChannel.class);

	private static final ConcurrentMap<Channel, NettyChannel> channelMap = new ConcurrentHashMap<io.netty.channel.Channel, NettyChannel>();

	private final Codec2 codec;

	// netty channel
	private Channel originChannel;

	private final Map<String, Object> attributes = new ConcurrentHashMap<String, Object>();

	public NettyChannel(Channel channel, URL url, ChannelHandler handler) {
		super(url, handler);
		if (channel == null) {
			throw new IllegalArgumentException("netty channel == null;");
		}
		this.originChannel = channel;
		codec = getChannelCodec(url);
	}

	protected Codec2 getChannelCodec(URL url) {
		String codecName = url.getParameter(Constants.CODEC_KEY, "telnet");
		if (ExtensionLoader.getExtensionLoader(Codec2.class).hasExtension(codecName)) {
			return ExtensionLoader.getExtensionLoader(Codec2.class).getExtension(codecName);
		} else {
			return new CodecAdapter(ExtensionLoader.getExtensionLoader(Codec.class).getExtension(codecName));
		}
	}

	static NettyChannel getOrAddChannel(Channel ch, URL url, ChannelHandler handler) {
		if (ch == null) {
			return null;
		}
		NettyChannel ret = channelMap.get(ch);
		if (ret == null) {
			NettyChannel nc = new NettyChannel(ch, url, handler);
			if (ch.isOpen()) {
				ret = channelMap.putIfAbsent(ch, nc);
			}
			if (ret == null) {
				ret = nc;
			}
		}
		return ret;
	}

	static void removeChannelIfDisconnected(Channel ch) {
		if (ch != null && !ch.isOpen()) {
			channelMap.remove(ch);
		}
	}

	@Override
	public void send(Object message, boolean sent) throws RemotingException {
		super.send(message, sent);
		boolean success = true;
		int timeout = 0;
		try {
			com.alibaba.dubbo.remoting.buffer.ChannelBuffer buffer = com.alibaba.dubbo.remoting.buffer.ChannelBuffers
					.dynamicBuffer(1024);
			codec.encode(this, buffer, message);
			byte[] bytes = new byte[buffer.readableBytes()];
			buffer.readBytes(bytes);
			ChannelFuture future = originChannel.writeAndFlush(bytes);
			if (sent) {
				timeout = getUrl().getPositiveParameter(Constants.TIMEOUT_KEY, Constants.DEFAULT_TIMEOUT);
				success = future.await(timeout);
			}
			Throwable cause = future.cause();
			if (cause != null) {
				throw cause;
			}
		} catch (Throwable e) {
			throw new RemotingException(this,
					"Failed to send message " + message + " to " + getRemoteAddress() + ", cause: " + e.getMessage(),
					e);
		}

		if (!success) {
			throw new RemotingException(this, "Failed to send message " + message + " to " + getRemoteAddress()
					+ "in timeout(" + timeout + "ms) limit");
		}
	}

	@Override
	public InetSocketAddress getRemoteAddress() {
		return (InetSocketAddress) originChannel.remoteAddress();
	}

	@Override
	public boolean isConnected() {
		return originChannel.isOpen();
	}

	@Override
	public boolean hasAttribute(String key) {
		return attributes.containsKey(key);
	}

	@Override
	public Object getAttribute(String key) {
		return attributes.get(key);
	}

	@Override
	public void setAttribute(String key, Object value) {
		if (value == null) {
			attributes.remove(key);
			return;
		}
		attributes.put(key, value);
	}

	@Override
	public void removeAttribute(String key) {
		attributes.remove(key);
	}

	@Override
	public InetSocketAddress getLocalAddress() {
		return (InetSocketAddress) originChannel.localAddress();
	}

	@Override
	public void close() {
		try {
			super.close();
		} catch (Exception e) {
			logger.warn(e.getMessage(), e);
		}
		try {
			removeChannelIfDisconnected(originChannel);
		} catch (Exception e) {
			logger.warn(e.getMessage(), e);
		}
		try {
			attributes.clear();
		} catch (Exception e) {
			logger.warn(e.getMessage(), e);
		}
		try {
			if (logger.isInfoEnabled()) {
				logger.info("Close netty channel " + originChannel);
			}
			originChannel.close();
		} catch (Exception e) {
			logger.warn(e.getMessage(), e);
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((originChannel == null) ? 0 : originChannel.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		NettyChannel other = (NettyChannel) obj;
		if (originChannel == null) {
			if (other.originChannel != null)
				return false;
		} else if (!originChannel.equals(other.originChannel))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "NettyChannel [channel=" + originChannel + "]";
	}
}
