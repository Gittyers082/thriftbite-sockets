package com.thriftbite.backend.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Before any request that shows or changes stock, gives {@link ExpiryJob} a
 * chance to release missed pickups. Runs before the controller, outside its
 * transaction, so the request then reads the updated stock.
 */
@Configuration
public class LazyExpiryInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private final ExpiryJob expiryJob;

    public LazyExpiryInterceptor(ExpiryJob expiryJob) {
        this.expiryJob = expiryJob;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        expiryJob.runIfDue();
        return true;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns(
                "/api/items", "/api/items/**", "/api/cart/**", "/api/checkout",
                "/api/reservations", "/api/reservations/**", "/api/owner/**");
    }
}
