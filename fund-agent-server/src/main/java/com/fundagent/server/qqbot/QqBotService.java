package com.fundagent.server.qqbot;

import com.fundagent.server.config.QqBotConfig;
import com.fundagent.server.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@ConditionalOnProperty(name = "qq-bot.enabled", havingValue = "true")
public class QqBotService {

    private static final String USER_ID_PREFIX = "qq_";

    @Autowired
    private QqBotConfig config;

    @Autowired
    private QqBotApiClient apiClient;

    @Autowired
    private ChatService chatService;

    private ThreadPoolExecutor executor;

    @PostConstruct
    public void init() {
        QqBotConfig.ThreadPool pool = config.getThreadPool();
        this.executor = new ThreadPoolExecutor(
                pool.getCoreSize(),
                pool.getMaxSize(),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(pool.getQueueCapacity()),
                (r, exe) -> {
                    if (r instanceof QqTask) {
                        QqTask t = (QqTask) r;
                        log.warn("QQ消息被拒绝(队列满): openid={}", t.openid);
                        apiClient.sendPrivateMessage(t.openid, "系统繁忙，请稍后重试");
                    }
                }
        );
        this.executor.allowCoreThreadTimeOut(true);
        log.info("QQ Bot 线程池初始化: core={}, max={}, queue={}",
                pool.getCoreSize(), pool.getMaxSize(), pool.getQueueCapacity());
    }

    public void handleC2cMessage(String userOpenid, String content, String msgId) {
        executor.execute(new QqTask(userOpenid, () -> {
            try {
                String userId = USER_ID_PREFIX + userOpenid;
                apiClient.sendPrivateMessage(userOpenid, "正在查询...", msgId);
                String answer = chatService.sendSync(userId, content);
                apiClient.sendPrivateMessage(userOpenid, answer);
                log.info("QQ私聊处理完成: userId={}", userId);
            } catch (Exception e) {
                log.error("QQ私聊处理异常 openid={}", userOpenid, e);
                apiClient.sendPrivateMessage(userOpenid, "系统繁忙，请稍后重试");
            }
        }));
    }

    private static class QqTask implements Runnable {
        final String openid;
        final Runnable task;

        QqTask(String openid, Runnable task) {
            this.openid = openid;
            this.task = task;
        }

        @Override
        public void run() {
            task.run();
        }
    }
}
