package com.fundagent.server.qqbot;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fundagent.server.config.QqBotConfig;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.annotation.PostConstruct;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@ConditionalOnProperty(name = "qq-bot.enabled", havingValue = "true")
public class QqBotWebSocketClient {

    private static final int C2C_MESSAGE_INTENT = 1 << 25;
    private static final long INITIAL_RECONNECT_DELAY = 3;
    private static final long MAX_RECONNECT_DELAY = 60;
    private static final int MAX_AUTH_RETRIES = 3;

    @Autowired
    private QqBotConfig config;

    @Autowired
    private QqBotApiClient apiClient;

    @Autowired
    private QqBotService qqBotService;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .protocols(java.util.List.of(okhttp3.Protocol.HTTP_1_1))
            .build();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final Object socketLock = new Object();
    private volatile WebSocket webSocket;
    private ScheduledFuture<?> heartbeatTask;
    private final AtomicReference<Integer> sequence = new AtomicReference<>();
    private final AtomicReference<Long> heartbeatInterval = new AtomicReference<>(30000L);
    private final AtomicInteger authRetryCount = new AtomicInteger(0);
    private long reconnectDelay = INITIAL_RECONNECT_DELAY;
    private volatile boolean reconnecting = false;
    private volatile boolean closed = false;
    private volatile boolean currentAuthFailureHandled = false;

    @PostConstruct
    public void connect() {
        scheduler.execute(this::doConnect);
    }

    private void doConnect() {
        if (closed) return;
        currentAuthFailureHandled = false;

        String wsUrl = config.getWebsocketUrl();
        log.info("QQ Bot WebSocket 开始连接: {}", wsUrl);

        Request request = new Request.Builder().url(wsUrl).build();
        WebSocket nextSocket = httpClient.newWebSocket(request, new WebSocketListener() {

            @Override
            public void onOpen(WebSocket ws, Response response) {
                if (!isCurrentSocket(ws)) return;
                log.info("QQ Bot WebSocket 连接成功");
                reconnectDelay = INITIAL_RECONNECT_DELAY;
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                if (!isCurrentSocket(ws)) return;
                try {
                    JSONObject msg = JSON.parseObject(text);
                    int op = msg.getIntValue("op");
                    if (msg.containsKey("s") && msg.get("s") != null) {
                        sequence.set(msg.getIntValue("s"));
                    }

                    if (op == 10) {
                        JSONObject d = msg.getJSONObject("d");
                        long interval = d.getLongValue("heartbeat_interval");
                        heartbeatInterval.set(interval);
                        startHeartbeat(ws);
                        log.info("QQ Bot Hello, heartbeat_interval={}ms", interval);
                        authenticate(ws);
                    } else if (op == 11) {
                        log.debug("QQ Bot Heartbeat ACK");
                    } else if (op == 0) {
                        authRetryCount.set(0);
                        handleDispatch(msg);
                    } else if (op == 7) {
                        log.warn("QQ Bot Gateway 要求重连");
                        reconnect(ws, "server reconnect");
                    } else if (op == 9) {
                        log.warn("QQ Bot Gateway 会话无效，重新鉴权");
                        handleAuthenticationFailure(ws, "invalid session");
                    }
                } catch (Exception e) {
                    log.error("QQ Bot WebSocket 消息处理异常", e);
                }
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                if (!isCurrentSocket(ws)) return;
                log.warn("QQ Bot WebSocket 断开: code={} reason={}", code, reason);
                cancelHeartbeat();
                if (isAuthenticationFailure(code, reason)) {
                    if (!currentAuthFailureHandled) {
                        handleAuthenticationFailure(ws, reason);
                    }
                    return;
                }
                scheduleReconnect();
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                if (!isCurrentSocket(ws)) return;
                log.error("QQ Bot WebSocket 失败", t);
                cancelHeartbeat();
                scheduleReconnect();
            }
        });
        synchronized (socketLock) {
            webSocket = nextSocket;
        }
    }

    private void authenticate(WebSocket ws) {
        String token = apiClient.getAccessToken();
        if (token == null) {
            log.error("无 access_token，无法认证");
            handleAuthenticationFailure(ws, "no token");
            return;
        }

        JSONObject payload = new JSONObject();
        payload.put("op", 2);
        JSONObject data = new JSONObject();
        data.put("token", "QQBot " + token);
        data.put("intents", C2C_MESSAGE_INTENT);
        data.put("shard", java.util.List.of(0, 1));
        payload.put("d", data);

        if (!ws.send(payload.toJSONString())) {
            log.warn("QQ Bot 认证发送失败，准备重连");
            handleAuthenticationFailure(ws, "identify send failed");
            return;
        }
        log.info("QQ Bot 认证已发送");
    }

    private void startHeartbeat(WebSocket ws) {
        cancelHeartbeat();
        long interval = heartbeatInterval.get();
        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            if (isCurrentSocket(ws)) {
                JSONObject hb = new JSONObject();
                hb.put("op", 1);
                hb.put("d", sequence.get());
                if (!ws.send(hb.toJSONString())) {
                    log.warn("QQ Bot 心跳发送失败，准备重连");
                    reconnect(ws, "heartbeat send failed");
                    return;
                }
                log.debug("QQ Bot 发送心跳 seq={}", sequence.get());
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
    }

    private void cancelHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
    }

    private void handleDispatch(JSONObject msg) {
        String type = msg.getString("t");
        if (!"C2C_MESSAGE_CREATE".equals(type)) {
            return;
        }

        JSONObject d = msg.getJSONObject("d");
        String userOpenid = d.getJSONObject("author").getString("user_openid");
        String content = d.getString("content");
        String msgId = d.getString("id");

        log.info("QQ私聊消息: openid={}, content={}", userOpenid, content);

        qqBotService.handleC2cMessage(userOpenid, content, msgId);
    }

    private void scheduleReconnect() {
        if (reconnecting) return;
        reconnecting = true;
        log.info("QQ Bot WebSocket 将在 {}秒后重连", reconnectDelay);
        scheduler.schedule(() -> {
            reconnecting = false;
            doConnect();
            reconnectDelay = Math.min(reconnectDelay * 2, MAX_RECONNECT_DELAY);
        }, reconnectDelay, TimeUnit.SECONDS);
    }

    private boolean isCurrentSocket(WebSocket ws) {
        return ws != null && ws == webSocket && !closed;
    }

    private void reconnect(WebSocket ws, String reason) {
        if (!isCurrentSocket(ws)) return;
        log.info("QQ Bot WebSocket 关闭当前连接: {}", reason);
        cancelHeartbeat();
        ws.close(1000, reason);
        scheduleReconnect();
    }

    private void handleAuthenticationFailure(WebSocket ws, String reason) {
        if (!isCurrentSocket(ws)) return;
        currentAuthFailureHandled = true;
        cancelHeartbeat();
        apiClient.invalidateAccessToken();

        int retry = authRetryCount.incrementAndGet();
        if (retry > MAX_AUTH_RETRIES) {
            log.error("QQ Bot Gateway 认证连续失败{}次，停止自动重连: {}", MAX_AUTH_RETRIES, reason);
            closed = true;
            ws.close(1000, "authentication retry limit exceeded");
            return;
        }

        log.warn("QQ Bot Gateway 认证失败，清理token后准备第{}/{}次重试: {}",
                retry, MAX_AUTH_RETRIES, reason);
        ws.close(1000, reason);
        scheduleReconnect();
    }

    private boolean isAuthenticationFailure(int code, String reason) {
        return code == 4004 || (reason != null && reason.toLowerCase().contains("authentication fail"));
    }

    @PreDestroy
    public void shutdown() {
        closed = true;
        cancelHeartbeat();
        WebSocket current = webSocket;
        if (current != null) {
            current.close(1000, "application shutdown");
        }
        scheduler.shutdownNow();
    }
}
