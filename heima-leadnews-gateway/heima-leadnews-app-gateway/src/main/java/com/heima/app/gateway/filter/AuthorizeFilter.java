package com.heima.app.gateway.filter;

import com.heima.app.gateway.util.AppJwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class AuthorizeFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1.获取请求对象
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        // 2.判断当前请求路径是否为登录路径，如果是，直接放行
        String path = request.getURI().getPath();
        if (path.contains("/login")) {
            return chain.filter(exchange);
        }
        // 3.从请求头中获取token数据
        String token = request.getHeaders().getFirst("token");
        // 4.判断当前请求头中是否有token数据，如果没有，直接响应401
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }
        // 5.如果有token数据，解析token，判断token是否合法
        try {
            Claims claimsBody = AppJwtUtil.getClaimsBody(token);
            int result = AppJwtUtil.verifyToken(claimsBody);
            if (result == 1 || result == 2) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return response.setComplete();
            }
            Object id = claimsBody.get("id");
            ServerHttpRequest request1 = request.mutate().headers(httpHeaders -> {
                httpHeaders.add("userId", id.toString());
            }).build();
            exchange.mutate().request(request1);
        } catch (Exception e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }
        // 6.解析token成功，将用户的信息设置到请求头中，放行
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
