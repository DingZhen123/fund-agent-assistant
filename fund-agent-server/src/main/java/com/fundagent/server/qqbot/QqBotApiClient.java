package com.fundagent.server.qqbot;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fundagent.server.config.QqBotConfig;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(name = "qq-bot.enabled", havingValue = "true")
public class QqBotApiClient {

    private static final String TOKEN_URL = "https://bots.qq.com/app/getAppAccessToken";
    private static final String MESSAGE_URL = "https://api.sgroup.qq.com/v2/users/%s/messages";
    private static final String REDIS_KEY_PREFIX = "qq:bot:access_token:";
    private static final String LEGACY_REDIS_KEY = "qq:bot:access_token";
    private static final long TOKEN_EXPIRE_SECONDS = 7200;
    private static final long TOKEN_REFRESH_BUFFER = 300;
    private static final int MESSAGE_SEND_MAX_ATTEMPTS = 3;
    private static final MediaType JSON_TYPE = MediaType.parse("application/json");

    @Autowired
    private QqBotConfig config;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .protocols(java.util.List.of(okhttp3.Protocol.HTTP_1_1))
            .build();

    public String getAccessToken() {
        String cached = (String) redisTemplate.opsForValue().get(getTokenRedisKey());
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        return refreshAccessToken();
    }

    public void invalidateAccessToken() {
        redisTemplate.delete(getTokenRedisKey());
        redisTemplate.delete(LEGACY_REDIS_KEY);
        log.info("QQ access_token缓存已清理");
    }

    private String refreshAccessToken() {
        try {
            JSONObject body = new JSONObject();
            body.put("appId", config.getAppId());
            body.put("clientSecret", config.getAppSecret());

            Request request = new Request.Builder()
                    .url(TOKEN_URL)
                    .post(RequestBody.create(body.toJSONString(), JSON_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("获取QQ access_token失败: {} {}", response.code(), response.body().string());
                    return null;
                }
                JSONObject result = JSON.parseObject(response.body().string());
                String token = result.getString("access_token");
                if (token != null) {
                    long ttl = TOKEN_EXPIRE_SECONDS - TOKEN_REFRESH_BUFFER;
                    redisTemplate.opsForValue().set(getTokenRedisKey(), token, ttl, TimeUnit.SECONDS);
                    log.info("QQ access_token刷新成功, TTL={}s", ttl);
                }
                return token;
            }
        } catch (Exception e) {
            log.error("获取QQ access_token异常", e);
            return null;
        }
    }

    public boolean sendPrivateMessage(String openid, String content) {
        return sendPrivateMessage(openid, content, null);
    }

    public boolean sendPrivateMessage(String openid, String content, String replyMsgId) {
        JSONObject body = new JSONObject();
        body.put("content", content);
        body.put("msg_type", 0);
        if (replyMsgId != null) {
            body.put("msg_id", replyMsgId);
        }

        for (int attempt = 1; attempt <= MESSAGE_SEND_MAX_ATTEMPTS; attempt++) {
            boolean forceRefresh = attempt > 1;
            SendMessageResult result = doSendPrivateMessage(openid, body, forceRefresh, attempt);
            if (result.success) {
                return true;
            }
            if (!result.tokenExpired) {
                return false;
            }
            invalidateAccessToken();
            log.warn("QQ access_token已过期，刷新后重试发送私聊消息: openid={}, attempt={}/{}",
                    openid, attempt, MESSAGE_SEND_MAX_ATTEMPTS);
        }
        log.error("发送私聊消息失败，access_token刷新重试仍未成功: openid={}", openid);
        return false;
    }

    private SendMessageResult doSendPrivateMessage(String openid, JSONObject body,
                                                   boolean forceRefreshToken, int attempt) {
        try {
            String token = forceRefreshToken ? refreshAccessToken() : getAccessToken();
            if (token == null) {
                log.error("无access_token，无法发送私聊消息");
                return SendMessageResult.failed(false);
            }

            String url = String.format(MESSAGE_URL, openid);
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "QQBot " + token)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(body.toJSONString(), JSON_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errBody = response.body() != null ? response.body().string() : "";
                    boolean tokenExpired = isTokenExpiredError(response.code(), errBody);
                    log.error("发送私聊消息失败: {} {} openid={}, attempt={}, tokenExpired={}",
                            response.code(), errBody, openid, attempt, tokenExpired);
                    return SendMessageResult.failed(tokenExpired);
                }
                return SendMessageResult.ok();
            }
        } catch (IOException e) {
            log.error("发送私聊消息异常 openid={}", openid, e);
            return SendMessageResult.failed(false);
        }
    }

    private boolean isTokenExpiredError(int httpCode, String errBody) {
        if (errBody == null || errBody.isBlank()) {
            return httpCode == 401;
        }
        try {
            JSONObject error = JSON.parseObject(errBody);
            Integer code = error.getInteger("code");
            Integer errCode = error.getInteger("err_code");
            String message = error.getString("message");
            return httpCode == 401
                    || Integer.valueOf(11244).equals(code)
                    || Integer.valueOf(11244).equals(errCode)
                    || (message != null && message.toLowerCase().contains("token"));
        } catch (Exception ignored) {
            return httpCode == 401 || errBody.toLowerCase().contains("token");
        }
    }

    private String getTokenRedisKey() {
        return REDIS_KEY_PREFIX + config.getAppId();
    }

    private record SendMessageResult(boolean success, boolean tokenExpired) {
        static SendMessageResult ok() {
            return new SendMessageResult(true, false);
        }

        static SendMessageResult failed(boolean tokenExpired) {
            return new SendMessageResult(false, tokenExpired);
        }
    }
}
