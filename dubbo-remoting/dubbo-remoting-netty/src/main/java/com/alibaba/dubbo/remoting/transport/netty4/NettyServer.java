package com.alibaba.dubbo.remoting.transport.netty4;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.ExecutorUtil;
import com.alibaba.dubbo.common.utils.NamedThreadFactory;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.remoting.Channel;
import com.alibaba.dubbo.remoting.ChannelHandler;
import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.remoting.transport.AbstractServer;
import com.alibaba.dubbo.remoting.transport.dispatcher.ChannelHandlers;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

public class NettyServer extends AbstractServer {

	private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);

	private Map<String, Channel> channels; // <ip:port, channel>

	private ServerBootstrap bootstrap;

	private io.netty.channel.Channel channel;

	public NettyServer(URL url, ChannelHandler handler) throws RemotingException {
		super(url, ChannelHandlers.wrap(handler, ExecutorUtil.setThreadName(url, SERVER_THREAD_POOL_NAME)));
	}

	@Override
	protected void doOpen() throws Throwable {
		bootstrap = new ServerBootstrap();
		NioEventLoopGroup bossGroup = new NioEventLoopGroup(1, new NamedThreadFactory("NettyServerBoss", true));
		NioEventLoopGroup workerGroup = new NioEventLoopGroup(
				getUrl().getPositiveParameter(Constants.IO_THREADS_KEY, Constants.DEFAULT_IO_THREADS),
				new NamedThreadFactory("NettyServerWorker", true));
		final NettyHandler nettyHandler = new NettyHandler(getUrl(), this);
		channels = nettyHandler.getChannels();
		bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
				.option(ChannelOption.SO_KEEPALIVE, true).option(ChannelOption.TCP_NODELAY, true)
				.childHandler(new ChannelInitializer<NioSocketChannel>() {
					@Override
					protected void initChannel(NioSocketChannel ch) throws Exception {
						NettyCodecAdapter codecAdapter = new NettyCodecAdapter(getCodec(), getUrl(), NettyServer.this);
						ch.pipeline().addLast("decoder", codecAdapter.getDecoder())
								.addLast("encoder", codecAdapter.getEncoder()).addLast("handler", nettyHandler);
					}
				});
		ChannelFuture channelFuture = bootstrap.bind(getBindAddress());
		channel = channelFuture.channel();
	}

	@Override
	protected void doClose() throws Throwable {
		try {
			if (channel != null) {
				// unbind.
				channel.close();
			}
		} catch (Throwable e) {
			logger.warn(e.getMessage(), e);
		}
		try {
			Collection<Channel> channels = getChannels();
			if (channels != null && channels.size() > 0) {
				for (com.alibaba.dubbo.remoting.Channel channel : channels) {
					try {
						channel.close();
					} catch (Throwable e) {
						logger.warn(e.getMessage(), e);
					}
				}
			}
		} catch (Throwable e) {
			logger.warn(e.getMessage(), e);
		}
		try {
			if (channels != null) {
				channels.clear();
			}
		} catch (Throwable e) {
			logger.warn(e.getMessage(), e);
		}
	}

	@Override
	public boolean isBound() {
		return channel.isActive();
	}

	@Override
	public Collection<Channel> getChannels() {
		Collection<Channel> chs = new HashSet<Channel>();
		for (Channel channel : this.channels.values()) {
			if (channel.isConnected()) {
				chs.add(channel);
			} else {
				channels.remove(NetUtils.toAddressString(channel.getRemoteAddress()));
			}
		}
		return chs;
	}

	@Override
	public Channel getChannel(InetSocketAddress remoteAddress) {
		return channels.get(NetUtils.toAddressString(remoteAddress));
	}
}
