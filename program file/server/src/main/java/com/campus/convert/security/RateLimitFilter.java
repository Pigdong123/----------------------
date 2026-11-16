package com.campus.convert.security;

import com.campus.convert.common.BaseContext;
import com.campus.convert.common.Result;
import com.campus.convert.common.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 轻量接口限流：登录/注册按 IP，上传/建任务优先按用户，Redis 计数。
 * <p>
 * 设计取舍：Redis 不可用时放行（fail-open），保证核心转换链路不被限流组件拖垮；
 * 下载令牌的一次性校验则采用 fail-closed。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "campus:rate:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${campus.rate-limit.login-per-minute:10}")
    private int loginPerMinute;

    @Value("${campus.rate-limit.register-per-minute:5}")
    private int registerPerMinute;

    @Value("${campus.rate-limit.upload-per-minute:20}")
    private int uploadPerMinute;

    @Value("${campus.rate-limit.task-create-per-minute:30}")
    private int taskCreatePerMinute;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Rule rule = resolveRule(request);
        if (rule != null && !tryAcquire(rule, request)) {
            writeRateLimited(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean tryAcquire(Rule rule, HttpServletRequest request) {
        String key = KEY_PREFIX + rule.name() + ":" + clientKey(request);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofMinutes(1));
            }
            return count == null || count <= rule.limit();
        } catch (Exception e) {
            log.warn("限流检查失败，放行请求: path={}, error={}", request.getRequestURI(), e.getMessage());
            return true;
        }
    }

    private Rule resolveRule(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return switch (path) {
            case "/auth/login" -> new Rule("login", loginPerMinute);
            case "/auth/register" -> new Rule("register", registerPerMinute);
            case "/files" -> new Rule("upload", uploadPerMinute);
            case "/tasks" -> new Rule("task-create", taskCreatePerMinute);
            default -> null;
        };
    }

    private String clientKey(HttpServletRequest request) {
        Long userId = BaseContext.getUserId();
        if (userId != null) {
            return "u:" + userId;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return "ip:" + forwarded.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private void writeRateLimited(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ResultCode.RATE_LIMITED)));
    }

    private record Rule(String name, int limit) {
    }
}
