package com.alibaba.dubbo.remoting.transport.netty4;

import java.util.concurrent.TimeUnit;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.Version;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.NamedThreadFactory;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.remoting.Channel;
import com.alibaba.dubbo.remoting.ChannelHandler;
import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.remoting.transport.AbstractClient;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

public class NettyClient extends AbstractClient {

	private static final Logger logger = LoggerFactory.getLogger(NettyClient.class);

	private Bootstrap bootstrap;

	private io.netty.channel.Channel channel;

	public NettyClient(URL url, ChannelHandler handler) throws RemotingException {
		super(url, wrapChannelHandler(url, handler));
	}

	@Override
	protected void doOpen() throws Throwable {
		EventLoopGroup bossGroup = new NioEventLoopGroup(Constants.DEFAULT_IO_THREADS,
				new NamedThreadFactory("NettyClientBoss", true));
		final NettyHandler nettyHandler = new NettyHandler(getUrl(), this);
		bootstrap = new Bootstrap();
		bootstrap.group(bossGroup).channel(NioSocketChannel.class).option(ChannelOption.SO_KEEPALIVE, true)
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, getTimeout()).option(ChannelOption.TCP_NODELAY, true)
				.handler(new ChannelInitializer<NioSocketChannel>() {
					@Override
					protected void initChannel(NioSocketChannel ch) throws Exception {
						NettyCodecAdapter adapter = new NettyCodecAdapter(getCodec(), getUrl(), NettyClient.this);
						ch.pipeline().addLast("decoder", adapter.getDecoder()).addLast("encoder", adapter.getEncoder())
								.addLast("handler", nettyHandler);
					}
				});
	}

	@Override
	protected void doClose() throws Throwable {
		// do nothing
	}

	@Override
	protected void doConnect() throws Throwable {
		long start = System.currentTimeMillis();
		ChannelFuture future = bootstrap.connect(getConnectAddress()).sync();
		try {
			boolean ret = future.awaitUninterruptibly(getConnectTimeout(), TimeUnit.MILLISECONDS);
			if (ret && future.isSuccess()) {
				io.netty.channel.Channel newChannel = future.channel();
				try {
					// 关闭旧的连接
					io.netty.channel.Channel oldChannel = NettyClient.this.channel; // copy
																					// reference
					if (oldChannel != null) {
						try {
							if (logger.isInfoEnabled()) {
								logger.info("Close old netty channel " + oldChannel + " on create new netty channel "
										+ newChannel);
							}
							oldChannel.close();
						} finally {
							NettyChannel.removeChannelIfDisconnected(oldChannel);
						}
					}
				} finally {
					if (NettyClient.this.isClosed()) {
						try {
							if (logger.isInfoEnabled()) {
								logger.info("Close new netty channel " + newChannel + ", because the client closed.");
							}
							newChannel.close();
						} finally {
							NettyClient.this.channel = null;
							NettyChannel.removeChannelIfDisconnected(newChannel);
						}
					} else {
						NettyClient.this.channel = newChannel;
					}
				}
			} else if (future.cause() != null) {
				throw new RemotingException(this, "client(url: " + getUrl() + ") failed to connect to server "
						+ getRemoteAddress() + ", error message is:" + future.cause().getMessage(), future.cause());
			} else {
				throw new RemotingException(this,
						"client(url: " + getUrl() + ") failed to connect to server " + getRemoteAddress()
								+ " client-side timeout " + getConnectTimeout() + "ms (elapsed: "
								+ (System.currentTimeMillis() - start) + "ms) from netty client "
								+ NetUtils.getLocalHost() + " using dubbo version " + Version.getVersion());
			}
		} finally {
			if (!isConnected()) {
				future.cancel(true);
			}
		}
	}

	@Override
	protected void doDisConnect() throws Throwable {
		try {
			NettyChannel.removeChannelIfDisconnected(channel);
		} catch (Throwable t) {
			logger.warn(t.getMessage());
		}
	}

	@Override
	protected Channel getChannel() {
		io.netty.channel.Channel c = channel;
		if (c == null || !c.isOpen())
			return null;
		return NettyChannel.getOrAddChannel(c, getUrl(), this);
	}
}
