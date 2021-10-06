package com.atguigu.gmall.gateway.filter;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.util.IpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class AuthGlobalFilter implements GlobalFilter {

    //  注入操作redis 的客户端
    @Autowired
    private RedisTemplate redisTemplate;

    // 表示获取到配置文件中的黑名单
    // authUrlsUrl=trade.html,myOrder.html,list.html
    @Value("${authUrls.url}")
    private String authUrlsUrl;

    // 匹配路径的工具类
    private AntPathMatcher antPathMatcher=new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //获取到请求对象
        ServerHttpRequest request = exchange.getRequest();
        //获取URL
        String path = request.getURI().getPath();

        //my:浏览器不能直接访问的接口，相当于服务器内部转发一样的道理，所以页需要在网关做拦截
        // 如果是内部接口，则网关拦截不允许外部访问！
        if (antPathMatcher.match("/**/inner/**",path)){
            ServerHttpResponse response = exchange.getResponse();
            return out(response, ResultCodeEnum.PERMISSION);
        }

        //=================================================================================
        /**
         * my:首先判断是不是本地ip
         * 在判断验证访问数据接口中是否带有auth或者访问的控制器是否有黑名单中的控制器且userid为空则需要进行登录
         */
        //  验证访问的内部数据接口中是否带有auth或者访问的控制器是否有黑名单中的控制器

        //  必须先获取到userId,用户Id 存储在缓存，
        //  String userKey = RedisConst.USER_LOGIN_KEY_PREFIX+token;
        String userId = getUserId(request);
        String userTempId = getUserTempId(request);
        //  判断：缓存中存储的数据 ，userId, ip:
        //  防止盗用token
        if ("-1".equals(userId)){//my：ip地址不是本地地址
            //  给一个响应
            ServerHttpResponse response = exchange.getResponse();
            //  编写一个方法：给一个提示信息
            return out(response, ResultCodeEnum.PERMISSION);//返回没有权限
        }
        //控制器是否在黑名单中
        //  authUrlsUrl= trade.html,myOrder.html,list.html
        String[] split = authUrlsUrl.split(",");
        if (null!=split && split.length>0){
            //循环遍历
            for (String url : split) {
                //  url = trade.html
                //  path = http://list.gmall.com/list.html 判断path 中是否包含url 中的数据！
                //  String 字符串中有个方法 path.indexOf(url)!=-1 表示存在 并且 用户是未登录，则表示必须要登录;
                if (path.indexOf(url)!= -1 && StringUtils.isEmpty(userId)){
                    //给提示要用户登录
                    ServerHttpResponse response = exchange.getResponse();
                    //  还有两个方法 303  提示重定向，默认设置
                    response.setStatusCode(HttpStatus.SEE_OTHER);
                    response.setStatusCode(HttpStatus.SEE_OTHER);
                    //  设置一个请求头 :
                    //  第一个参数：headerName, 第二个参数：headerValue
                    response.getHeaders().set(HttpHeaders.LOCATION,"http://www.gmall.com/login.html?originUrl="+request.getURI());
                    //  重定向到登录页面 :  http://www.gmall.com/login.html?originUrl="+request.getURI())
                    return  response.setComplete();
                }
            }
        }

        //  设置  /api/**/auth/**
        if (antPathMatcher.match("/api/**/auth/**",path)){
            //  判断当前用户Id 是否为空！
            if (StringUtils.isEmpty(userId)){
                //  给一个响应
                ServerHttpResponse response = exchange.getResponse();
                //  编写一个方法：给一个提示信息
                return out(response, ResultCodeEnum.LOGIN_AUTH);
            }
        }

        //  用户登录完成之后，我们需要将用户Id 传递到微服务中！
        if (!StringUtils.isEmpty(userId) || !StringUtils.isEmpty(userTempId)) {
            //  将数据存储header 中 ServerHttpRequest
            // 存储登录用户Id
            if (!StringUtils.isEmpty(userId)){
                //  将数据存储header 中 ServerHttpRequest
                request.mutate().header("userId",userId).build();
            }
            //  存储临时用户Id
            if (!StringUtils.isEmpty(userTempId)){
                //  将数据存储header 中 ServerHttpRequest
                request.mutate().header("userTempId",userTempId).build();
            }
            //  将数据传递到后台
            //  Mono<Void> filter(ServerWebExchange);
            //  exchange.mutate().request(request).build();

            return chain.filter(exchange.mutate().request(request).build());
        }

        return chain.filter(exchange);
    }




    // 获取用户数据
    private String getUserId(ServerHttpRequest request) {
        //  用户存储在缓存
        //  获取到token ， token 在cookie 中，同时也有可能在header 中
        //  表示从header 中获取token
        String token = "";
        List<String> list  = request.getHeaders().get("token");
        if(null  != list) {
            // header 中 token 对应只有一个值
            token = list.get(0);
        }
        //  表示从cookie 获取
        MultiValueMap<String, HttpCookie> cookies = request.getCookies();
        if (cookies!=null){
            // 获取key
            HttpCookie httpCookie = cookies.getFirst("token");
            if (httpCookie!=null){
                //  获取token 对应的数据
                //  token = URLDecoder.decode(cookie.getValue());
                //  URLDecoder.decode();
                token = httpCookie.getValue();
            }
        }
        //  缓存的key  String userKey = RedisConst.USER_LOGIN_KEY_PREFIX+token;
        String userKey = "user:login:"+token;
        //  从缓存中获取数据
        //  "{\"ip\":\"192.168.200.1\",\"userId\":2}"
        String strJson = (String)redisTemplate.opsForValue().get(userKey);
        //  这个字符串本质：JSONObject,转换为JSONObject对象
        JSONObject userJson = JSONObject.parseObject(strJson);
        if (null!=userJson){
            //  进一步做判断：防止盗用ip
            //  表示从缓存中获取到ip
            String ip = (String) userJson.get("ip");
            //  判断缓存中的IP 与 当前登录的Ip 是否一致
            if (ip.equals(IpUtil.getGatwayIpAddress(request))){
                //  从缓存中获取数据
                String userId = (String)userJson.get("userId");
                //  返回数据
                return userId;
            }else {
                //  如果没有用户Id ，或者验证Ip地址的时候，发现是异地登录，盗用token
                return "-1";
            }
        }

        return null;
    }

    // 获取用户数据
    private String getUserTempId(ServerHttpRequest request) {
        //  用户存储在缓存
        //  获取到token ， token 在cookie 中，同时也有可能在header 中
        //  表示从header 中获取token
        String userTempId = "";
        List<String> list  = request.getHeaders().get("userTempId");
        if(null  != list) {
            // header 中 token 对应只有一个值
            userTempId = list.get(0);
        }
        //  表示从cookie 获取
        MultiValueMap<String, HttpCookie> cookies = request.getCookies();
        if (cookies!=null){
            // 获取key
            HttpCookie httpCookie = cookies.getFirst("userTempId");
            if (httpCookie!=null){
                //  获取token 对应的数据
                //  token = URLDecoder.decode(cookie.getValue());
                //  URLDecoder.decode();
                userTempId = httpCookie.getValue();
            }
        }
      /*  //  缓存的key  String userKey = RedisConst.USER_LOGIN_KEY_PREFIX+token;
        String userKey = "user:login:"+token;
        //  从缓存中获取数据
        //  "{\"ip\":\"192.168.200.1\",\"userId\":2}"
        String strJson = (String)redisTemplate.opsForValue().get(userKey);
        //  这个字符串本质：JSONObject,转换为JSONObject对象
        JSONObject userJson = JSONObject.parseObject(strJson);
        if (null!=userJson){
            //  进一步做判断：防止盗用ip
            //  表示从缓存中获取到ip
            String ip = (String) userJson.get("ip");
            //  判断缓存中的IP 与 当前登录的Ip 是否一致
            if (ip.equals(IpUtil.getGatwayIpAddress(request))){
                //  从缓存中获取数据
                String userId = (String)userJson.get("userId");
                //  返回数据
                return userId;
            }else {
                //  如果没有用户Id ，或者验证Ip地址的时候，发现是异地登录，盗用token
                return "-1";
            }
        }*/

        return userTempId;
    }
    // 接口鉴权失败返回数据
    private Mono<Void> out(ServerHttpResponse response,ResultCodeEnum resultCodeEnum) {
        // 返回用户没有权限登录
        Result<Object> result = Result.build(null, resultCodeEnum);
        byte[] bits = JSONObject.toJSONString(result).getBytes(StandardCharsets.UTF_8);
        DataBuffer wrap = response.bufferFactory().wrap(bits);
        // 设置请求头：content-type 格式：text/html ,application/json
        response.getHeaders().add("Content-Type","application/json;charset=UTF-8");
        //输入到页面
        return response.writeWith(Mono.just(wrap));
    }
}
