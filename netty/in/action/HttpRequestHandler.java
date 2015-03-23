package netty.in.action;

import java.io.RandomAccessFile;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedNioFile;


/**
 * @author lihuaqing
 * 功能： WebSocket，处理http请求
 */
public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
	//websocket标识
   private final String wsUri;
   
   public HttpRequestHandler(String wsUri) {
       super();
       this.wsUri = wsUri;
   }
   
   @Override
   protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg)
           throws Exception {
	   
	 //如果是websocket请求，请求地址uri等于wsuri
       if(wsUri.equalsIgnoreCase(msg.getUri())){
		  //将消息转发到下一个ChannelHandler
           ctx.fireChannelRead(msg.retain());
       }else{//如果不是websocket请求
           if(HttpHeaders.is100ContinueExpected(msg)){
			   //如果HTTP请求头部包含Expect: 100-continue
			   //则响应请求
               FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE);
               ctx.writeAndFlush(response);
           }
           
		   //获取index.html的内容响应给客户端 
           RandomAccessFile file = new RandomAccessFile(HttpRequestHandler.class.getResource("/").getPath()+"/index.html", "r");
           HttpResponse response = new DefaultHttpResponse(msg.getProtocolVersion(), HttpResponseStatus.OK);
           response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/html;charset=UTF-8");
            
           boolean isKeepAlive = HttpHeaders.isKeepAlive(msg);
		   //如果http请求保持活跃，设置http请求头部信息
		   //并响应请求
           if(isKeepAlive){
               response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, file.length());
               response.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
           }
            
           ctx.write(response);
		   //如果不是https请求，将index.html内容写入通道
           if(ctx.pipeline().get(SslHandler.class) == null){
               ctx.write(new DefaultFileRegion(file.getChannel(), 0, file.length()));
           }else{
               ctx.write(new ChunkedNioFile(file.getChannel()));
           }
           
		   //标识响应内容结束并刷新通道
           ChannelFuture future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
           if(isKeepAlive == false){
			   //如果http请求不活跃，关闭http连接 
               future.addListener(ChannelFutureListener.CLOSE);
           }
           file.close();
       }
   }
   @Override
   public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
           throws Exception {
       ctx.close();
       cause.printStackTrace(System.err);
   }
}
