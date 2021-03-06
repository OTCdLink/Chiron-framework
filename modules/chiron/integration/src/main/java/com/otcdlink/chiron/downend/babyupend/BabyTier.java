package com.otcdlink.chiron.downend.babyupend;

import com.otcdlink.chiron.conductor.Responder;
import com.otcdlink.chiron.middle.ChannelTools;
import com.otcdlink.chiron.middle.tier.ConnectionDescriptor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class BabyTier extends SimpleChannelInboundHandler< Object > {

  @SuppressWarnings( "unused" )
  private static final Logger LOGGER = LoggerFactory.getLogger( BabyTier.class ) ;

  private final boolean ssl ;

  private final ConnectionDescriptor connectionDescriptor ;

  private final ChannelGroup channelGroup ;

  private final Responder< TextWebSocketFrame, TextWebSocketFrame > textwebsocketframeGuide ;
  private final Responder< PingWebSocketFrame, PongWebSocketFrame > pingpongGuide ;
  private final Responder< CloseWebSocketFrame, Void > closeFrameResponder ;

  public BabyTier(
      final boolean ssl,
      final ConnectionDescriptor connectionDescriptor,
      final ChannelGroup channelGroup,
      final Responder< TextWebSocketFrame, TextWebSocketFrame > textwebsocketframeGuide,
      final Responder< PingWebSocketFrame, PongWebSocketFrame > pingpongGuide,
      final Responder< CloseWebSocketFrame, Void > closeFrameResponder
  ) {
    super( true ) ;
    this.ssl = ssl ;
    this.connectionDescriptor = checkNotNull( connectionDescriptor ) ;
    this.channelGroup = checkNotNull( channelGroup ) ;
    this.textwebsocketframeGuide = checkNotNull( textwebsocketframeGuide ) ;
    this.pingpongGuide = checkNotNull( pingpongGuide ) ;
    this.closeFrameResponder = checkNotNull( closeFrameResponder ) ;
  }

  @Override
  public void channelActive( final ChannelHandlerContext channelHandlerContext ) {
    channelGroup.add( channelHandlerContext.channel() ) ;
  }

  @Override
  public void channelRead0( final ChannelHandlerContext ctx, final Object msg ) {
    if( msg instanceof FullHttpRequest ) {
      handleHttpRequest( ctx, ( FullHttpRequest ) msg ) ;
    } else if( msg instanceof WebSocketFrame ) {
      handleWebSocketFrame( ctx, ( WebSocketFrame ) msg ) ;
    }
  }

  @Override
  public void channelReadComplete( final ChannelHandlerContext ctx ) {
    ctx.flush();
  }

  private void handleHttpRequest(
      final ChannelHandlerContext context,
      final FullHttpRequest fullHttpRequest
  ) {
    // Handle a bad request.
    if( ! fullHttpRequest.decoderResult().isSuccess() ) {
      sendHttpResponse(
          context,
          fullHttpRequest,
          new DefaultFullHttpResponse( HTTP_1_1, BAD_REQUEST )
      ) ;
      return ;
    }

    // Allow only GET methods.
    if( fullHttpRequest.method() != GET ) {
      sendHttpResponse(
          context,
          fullHttpRequest,
          new DefaultFullHttpResponse( HTTP_1_1, FORBIDDEN )
      ) ;
      return ;
    }

    // Send the demo page and favicon.ico
    if( "/".equals( fullHttpRequest.uri() ) ) {
      final ByteBuf content = BabyUpendHomePage.getContent(
          getWebSocketLocation( fullHttpRequest ) ) ;
      final FullHttpResponse res = new DefaultFullHttpResponse( HTTP_1_1, OK, content ) ;

      res.headers().set( CONTENT_TYPE, "text/html; charset=UTF-8" ) ;
      HttpUtil.setContentLength( res, content.readableBytes() ) ;

      sendHttpResponse( context, fullHttpRequest, res ) ;
      return ;
    }
    if( "/favicon.ico".equals( fullHttpRequest.uri() ) ) {
      final FullHttpResponse res = new DefaultFullHttpResponse( HTTP_1_1, NOT_FOUND ) ;
      sendHttpResponse( context, fullHttpRequest, res ) ;
      return ;
    }

    // TODO: deactivate masking with wss://
    final WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
        getWebSocketLocation( fullHttpRequest ), null, false ) ;
    final WebSocketServerHandshaker handshaker = wsFactory.newHandshaker( fullHttpRequest ) ;
    if( handshaker == null ) {
      WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse( context.channel() ) ;
    } else {
      handshaker.handshake(
          context.channel(),
          fullHttpRequest,
          connectionDescriptor.httpHeaders(),
          context.newPromise()
      ) ;
      ChannelTools.dumpPipeline( context.pipeline(), "after server handshake" ) ;
    }
  }

  private void handleWebSocketFrame(
      final ChannelHandlerContext context,
      final WebSocketFrame frame
  ) {

    if( frame instanceof CloseWebSocketFrame ) {
      final CloseWebSocketFrame closeWebSocketFrame = ( CloseWebSocketFrame ) frame ;
      closeFrameResponder.respond( closeWebSocketFrame ) ;  // Just for recording, sends nothing.
      LOGGER.debug( "Handled " + frame ) ;
      return ;
    }
    if( frame instanceof PingWebSocketFrame ) {
      maybeRespond( context, ( PingWebSocketFrame ) frame, pingpongGuide ) ;
      LOGGER.debug( "Handled " + frame ) ;
      return ;
    }

    if( frame instanceof TextWebSocketFrame ) {
      maybeRespond( context, ( TextWebSocketFrame ) frame, this.textwebsocketframeGuide ) ;
      LOGGER.debug( "Handled " + frame + "[" + ( ( TextWebSocketFrame ) frame ).text() + "]" ) ;
      return ;
    }

    throw new UnsupportedOperationException(
        String.format( "%s frame types not supported", frame.getClass().getName() ) ) ;

  }

  private static< INBOUND, OUTBOUND > void maybeRespond(
      final ChannelHandlerContext context,
      final INBOUND inbound,
      final Responder< INBOUND, OUTBOUND > responder
  ) {
    final OUTBOUND response = responder.respond( inbound ) ;
    if( response != null ) {
      context.writeAndFlush( response ) ;
    }
  }

  private static void sendHttpResponse(
      final ChannelHandlerContext context,
      final FullHttpRequest fullHttpRequest,
      final FullHttpResponse fullHttpResponse
  ) {
    // Generate an error page if response getStatus code is not OK (200).
    if( fullHttpResponse.status().code() != 200 ) {
      final ByteBuf buf = Unpooled.copiedBuffer(
          fullHttpResponse.status().toString(), CharsetUtil.UTF_8 ) ;
      fullHttpResponse.content().writeBytes( buf ) ;
      buf.release() ;
      HttpUtil.setContentLength(
          fullHttpResponse, fullHttpResponse.content().readableBytes() ) ;
    }

    // Send the response and close the connection if necessary.
    final ChannelFuture f = context.channel().writeAndFlush( fullHttpResponse ) ;
    if( ! HttpUtil.isKeepAlive( fullHttpRequest ) ||
        fullHttpResponse.status().code() != 200
        ) {
      f.addListener( ChannelFutureListener.CLOSE ) ;
    }
  }

  @Override
  public void exceptionCaught( final ChannelHandlerContext context, final Throwable cause ) {
    // TODO redirect to the Catcher.
    cause.printStackTrace() ;
    context.close() ;
  }

  private String getWebSocketLocation( final FullHttpRequest req ) {
    final String location = req.headers().get( HOST ) + BabyUpend.WEBSOCKET_PATH ;
    if( ssl ) {
      return "wss://" + location ;
    } else {
      return "ws://" + location ;
    }
  }
}
