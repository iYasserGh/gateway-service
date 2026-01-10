package com.kaustack.gateway.config;

import com.kaustack.jwt.JwtUtils;
import com.kaustack.jwt.TokenType;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Collections;

@Component
public class JwtAuthenticationFilter implements WebFilter {

    private final JwtUtils jwtUtils;

    public JwtAuthenticationFilter(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Extract access_token from httpOnly cookie
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst("access_token");
        
        if (cookie == null || cookie.getValue().isEmpty()) {
            return chain.filter(exchange);
        }

        String token = cookie.getValue();

        try {
            if (!jwtUtils.validateToken(token, TokenType.ACCESS)) {
                return chain.filter(exchange);
            }

            String userId = jwtUtils.extractUserId(token).toString();

            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());

            // Forward JWT as Bearer token to downstream microservices
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("Authorization", "Bearer " + token)
                    .build();

            ServerWebExchange mutatedExchange = exchange.mutate()
                    .request(mutatedRequest)
                    .build();

            return chain.filter(mutatedExchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));

        } catch (Exception e) {
            return chain.filter(exchange);
        }
    }
}
