-- Full Execution Trace storage foundation
-- Project: fund-agent-assistant
-- Database: MySQL 8.x
-- Created: 2026-06-21
--
-- This script is intentionally limited to creating the Trace foundation tables.
-- It does not modify or delete existing business tables or data.

CREATE TABLE IF NOT EXISTS agent_episodes (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    episode_code          VARCHAR(64)     NOT NULL COMMENT 'Episode全局唯一标识',
    request_id            VARCHAR(64)     NOT NULL COMMENT '请求唯一标识',
    conversation_id       VARCHAR(64)              COMMENT '会话标识',
    user_id_reference     VARCHAR(128)             COMMENT '脱敏后的用户引用',

    agent_version         VARCHAR(128)    NOT NULL COMMENT 'Agent完整版本',
    original_goal         TEXT                     COMMENT '脱敏后的原始目标',
    risk_level            VARCHAR(32)     NOT NULL COMMENT '风险等级',
    status                VARCHAR(32)     NOT NULL COMMENT 'Episode状态',

    next_sequence_no      BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '下一事件序号',
    last_event_hash       CHAR(64)                 COMMENT '最后一条事件Hash',
    event_count           BIGINT UNSIGNED NOT NULL DEFAULT 0,

    step_count            INT UNSIGNED    NOT NULL DEFAULT 0,
    model_call_count      INT UNSIGNED    NOT NULL DEFAULT 0,
    tool_call_count       INT UNSIGNED    NOT NULL DEFAULT 0,
    token_usage           BIGINT UNSIGNED NOT NULL DEFAULT 0,

    final_error_code      VARCHAR(128),
    final_failure_stage   VARCHAR(64),

    started_at            DATETIME(6),
    finished_at           DATETIME(6),
    elapsed_ms            BIGINT UNSIGNED,

    sealed                TINYINT(1)      NOT NULL DEFAULT 0,
    row_version           BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    created_at            DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                         ON UPDATE CURRENT_TIMESTAMP(6),
    created_by            VARCHAR(128)    NOT NULL DEFAULT 'SYSTEM',
    updated_by            VARCHAR(128)    NOT NULL DEFAULT 'SYSTEM',

    PRIMARY KEY (id),
    UNIQUE KEY uk_episode_code (episode_code),
    UNIQUE KEY uk_episode_request_id (request_id),
    KEY idx_episode_conversation_time (conversation_id, created_at),
    KEY idx_episode_status_time (status, updated_at),
    KEY idx_episode_user_time (user_id_reference, created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent任务执行档案';

CREATE TABLE IF NOT EXISTS trace_events (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_code              VARCHAR(64)     NOT NULL COMMENT '事件全局唯一标识',
    episode_id              BIGINT UNSIGNED NOT NULL COMMENT '关联agent_episodes.id',
    sequence_no             BIGINT UNSIGNED NOT NULL COMMENT 'Episode内严格递增序号',

    parent_event_id         BIGINT UNSIGNED          COMMENT '调用层级父事件',
    causation_event_id      BIGINT UNSIGNED          COMMENT '直接原因事件',
    correlation_id          VARCHAR(64)              COMMENT '同一调用链关联标识',

    event_type              VARCHAR(64)     NOT NULL,
    stage                   VARCHAR(64)     NOT NULL,
    node_id                 VARCHAR(128),
    capability              VARCHAR(128),
    tool_name               VARCHAR(128),
    status                  VARCHAR(32)     NOT NULL,
    reason_code             VARCHAR(128),
    summary                 VARCHAR(1024),

    payload_json            JSON                     COMMENT '脱敏后的事件载荷',
    payload_schema_version  INT UNSIGNED    NOT NULL DEFAULT 1,
    payload_hash            CHAR(64)        NOT NULL,
    producer_id             VARCHAR(128)    NOT NULL,

    occurred_at             DATETIME(6)     NOT NULL COMMENT '事件实际发生时间',
    received_at             DATETIME(6)     NOT NULL COMMENT 'TraceRecorder接收时间',
    persisted_at            DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    previous_hash           CHAR(64)        NOT NULL,
    event_hash              CHAR(64)        NOT NULL,
    signature_algorithm     VARCHAR(32)     NOT NULL,
    signing_key_id          VARCHAR(64)     NOT NULL,
    event_signature         VARCHAR(512)    NOT NULL,

    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                           ON UPDATE CURRENT_TIMESTAMP(6),
    created_by              VARCHAR(128)    NOT NULL DEFAULT 'SYSTEM',
    updated_by              VARCHAR(128)    NOT NULL DEFAULT 'SYSTEM',

    PRIMARY KEY (id),
    UNIQUE KEY uk_event_code (event_code),
    UNIQUE KEY uk_episode_sequence (episode_id, sequence_no),
    KEY idx_event_episode_time (episode_id, persisted_at),
    KEY idx_event_correlation (correlation_id),
    KEY idx_event_node (episode_id, node_id),
    KEY idx_event_type_time (event_type, persisted_at),
    KEY idx_event_reason_time (reason_code, persisted_at),
    KEY idx_event_parent (parent_event_id),
    KEY idx_event_causation (causation_event_id),

    CONSTRAINT fk_trace_event_episode
        FOREIGN KEY (episode_id) REFERENCES agent_episodes (id),
    CONSTRAINT fk_trace_event_parent
        FOREIGN KEY (parent_event_id) REFERENCES trace_events (id),
    CONSTRAINT fk_trace_event_causation
        FOREIGN KEY (causation_event_id) REFERENCES trace_events (id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='只追加的Agent执行事件';

CREATE TABLE IF NOT EXISTS trace_event_relations (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    episode_id        BIGINT UNSIGNED NOT NULL,
    source_event_id   BIGINT UNSIGNED NOT NULL,
    target_event_id   BIGINT UNSIGNED NOT NULL,
    relation_type     VARCHAR(32)     NOT NULL,

    created_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                    ON UPDATE CURRENT_TIMESTAMP(6),
    created_by        VARCHAR(128)    NOT NULL DEFAULT 'SYSTEM',
    updated_by        VARCHAR(128)    NOT NULL DEFAULT 'SYSTEM',

    PRIMARY KEY (id),
    UNIQUE KEY uk_event_relation (
        source_event_id,
        target_event_id,
        relation_type
    ),
    KEY idx_relation_episode (episode_id),
    KEY idx_relation_source (source_event_id),
    KEY idx_relation_target (target_event_id),

    CONSTRAINT fk_relation_episode
        FOREIGN KEY (episode_id) REFERENCES agent_episodes (id),
    CONSTRAINT fk_relation_source
        FOREIGN KEY (source_event_id) REFERENCES trace_events (id),
    CONSTRAINT fk_relation_target
        FOREIGN KEY (target_event_id) REFERENCES trace_events (id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Trace事件因果及业务关系';

CREATE TABLE IF NOT EXISTS trace_evidence (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    evidence_code           VARCHAR(64)     NOT NULL COMMENT '证据全局唯一标识',
    episode_id              BIGINT UNSIGNED NOT NULL,
    event_id                BIGINT UNSIGNED NOT NULL,
    node_id                 VARCHAR(128),

    evidence_type           VARCHAR(64)     NOT NULL,
    source_type             VARCHAR(64)     NOT NULL,
    source_reference        VARCHAR(256),
    claim                   VARCHAR(256)    NOT NULL,

    expected_value          TEXT COMMENT '脱敏后的预期值',
    actual_value            TEXT COMMENT '脱敏后的实际值',
    reliability_level       VARCHAR(32)     NOT NULL,
    verification_status     VARCHAR(32)     NOT NULL,

    payload_json            JSON COMMENT '脱敏后的证据载荷',
    payload_hash            CHAR(64)        NOT NULL,
    collected_at            DATETIME(6)     NOT NULL,

    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                           ON UPDATE CURRENT_TIMESTAMP(6),
    created_by              VARCHAR(128)    NOT NULL DEFAULT 'SYSTEM',
    updated_by              VARCHAR(128)    NOT NULL DEFAULT 'SYSTEM',

    PRIMARY KEY (id),
    UNIQUE KEY uk_evidence_code (evidence_code),
    KEY idx_evidence_episode_time (episode_id, collected_at),
    KEY idx_evidence_event (event_id),
    KEY idx_evidence_node (episode_id, node_id),
    KEY idx_evidence_claim (claim),
    KEY idx_evidence_status (verification_status, created_at),

    CONSTRAINT fk_evidence_episode
        FOREIGN KEY (episode_id) REFERENCES agent_episodes (id),
    CONSTRAINT fk_evidence_event
        FOREIGN KEY (event_id) REFERENCES trace_events (id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='任务完成性验证证据';

CREATE TABLE IF NOT EXISTS episode_seals (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    seal_code           VARCHAR(64)     NOT NULL COMMENT '封印全局唯一标识',
    episode_id          BIGINT UNSIGNED NOT NULL,

    final_sequence_no   BIGINT UNSIGNED NOT NULL,
    final_event_hash    CHAR(64)        NOT NULL,
    event_count         BIGINT UNSIGNED NOT NULL,
    final_status        VARCHAR(32)     NOT NULL,

    sealed_at           DATETIME(6)     NOT NULL,
    signature_algorithm VARCHAR(32)     NOT NULL,
    signing_key_id      VARCHAR(64)     NOT NULL,
    seal_signature      VARCHAR(512)    NOT NULL,

    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                        ON UPDATE CURRENT_TIMESTAMP(6),
    created_by          VARCHAR(128)    NOT NULL DEFAULT 'SYSTEM',
    updated_by          VARCHAR(128)    NOT NULL DEFAULT 'SYSTEM',

    PRIMARY KEY (id),
    UNIQUE KEY uk_seal_code (seal_code),
    UNIQUE KEY uk_seal_episode (episode_id),

    CONSTRAINT fk_seal_episode
        FOREIGN KEY (episode_id) REFERENCES agent_episodes (id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Episode终态完整性封印';
