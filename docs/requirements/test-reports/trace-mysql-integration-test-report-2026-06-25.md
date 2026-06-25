# PersistentTraceStore 真实 MySQL 集成测试报告

> 测试日期：2026-06-25  
> 测试对象：`PersistentTraceStore`  
> 测试环境：远程 `agent_test` 测试数据库  
> 最终结论：通过

## 1. 测试目标

本次测试用于验证 Trace 持久化在真实 MySQL 环境中的以下可靠性要求：

- Episode 与首条 `EPISODE_CREATED` 事件原子创建；
- 创建请求和事件追加具备代码层内容幂等；
- 相同幂等键不能承载不同业务内容；
- 同一 Episode 的并发追加不会产生重复 sequence number 或 Hash 链分叉；
- 外层业务事务回滚时，使用 `REQUIRES_NEW` 持久化的 Trace 不会丢失；
- Trace 内部持久化步骤失败时，本次 Event、Relation 和 Episode 投影整体回滚；
- 数据库中的完整 Trace 能通过 Hash、HMAC 和事件链完整性校验；
- 测试数据执行结束后能够清理，不污染测试数据库。

## 2. 测试环境

| 项目 | 实际值 |
|---|---|
| MySQL 版本 | `5.7.40-log` |
| 事务隔离级别 | `REPEATABLE-READ` |
| JDBC 时区 | `UTC` |
| Trace 表引擎 | `InnoDB` |
| Spring Boot | `2.7.18` |
| MyBatis-Plus | `3.5.5` |
| Java | `21.0.9` |

参与测试的表：

```text
agent_episodes
trace_events
trace_event_relations
trace_evidence
episode_seals
```

五张表均为 InnoDB，能够支持行锁、事务提交和事务回滚。

## 3. 测试类

测试代码：

```text
fund-agent-repo/src/test/java/com/fundagent/repo/trace/
PersistentTraceStoreMySqlIntegrationTest.java
```

测试仅在以下环境变量显式开启时运行：

```text
TRACE_MYSQL_IT=true
TRACE_IT_DB_URL=<测试数据库JDBC地址>
TRACE_IT_DB_USERNAME=<测试用户名>
TRACE_IT_DB_PASSWORD=<测试密码>
```

测试命令：

```bash
mvn -q -pl fund-agent-repo -am \
  -Dtest=PersistentTraceStoreMySqlIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test
```

报告中不记录数据库密码或 Trace 签名密钥。

## 4. 最终测试结果

Surefire 最终结果：

```text
Tests run: 6
Failures: 0
Errors: 0
Skipped: 0
Time elapsed: 16.338 s
```

| 测试场景 | 结果 | 验证内容 |
|---|---|---|
| Episode 幂等创建 | 通过 | 重复创建只保留一个 Episode 和一条 `EPISODE_CREATED` |
| 创建幂等冲突 | 通过 | 相同幂等键、不同内容被拒绝，已有数据不变 |
| Event 内容级幂等 | 通过 | 相同 `eventCode` 和相同内容返回原事件，不重复插入 |
| Event 幂等冲突 | 通过 | 相同 `eventCode`、不同 Payload 被拒绝 |
| 并发追加 | 通过 | 两线程追加后 sequence 为 `1,2,3,4`，完整性校验通过 |
| 外层事务回滚 | 通过 | 外层业务事务回滚后，`REQUIRES_NEW` Trace 仍然存在 |
| Trace 内部回滚 | 通过 | Relation 故障注入后，Event 未残留，Episode 投影未增加 |
| Hash 链完整性 | 通过 | 持久化后的 Trace 能通过完整性验证 |
| 测试数据清理 | 通过 | 最终 `IT_` Episode 残留数量为 0 |

其中“Event 内容级幂等”和“Event 幂等冲突”由同一个测试方法覆盖，因此最终测试方法数量为 6。

## 5. 测试过程发现的问题

### 5.1 Event Code 长度

第一轮测试使用 `episodeCode + 场景名` 生成 Event Code，超过数据库字段：

```text
trace_events.event_code VARCHAR(64)
```

处理：

- 测试改为使用确定性 UUID 生成 Event Code；
- 生成结果长度为 36；
- 数据库表结构无需修改。

该问题属于测试数据生成问题，不是生产持久化逻辑缺陷。

### 5.2 MySQL JSON 表示与幂等比较

第一轮测试发现 MySQL `JSON` 列读取后可能重新排列键顺序或调整空格。直接比较原始 JSON 字符串会把语义相同的 Payload 误判为幂等冲突。

修复：

```text
数据库已有 Payload → Canonical JSON
本次请求 Payload   → Canonical JSON
→ 比较规范化结果
```

修复位置：

```text
PersistentTraceStore.assertSameEventRequest
PersistentTraceStore.assertSameEvidenceRequest
```

该修复属于生产代码可靠性修复，确保内容级幂等使用 JSON 语义，而不是字符串排版。

### 5.3 自关联事件表清理顺序

第二轮测试的业务断言全部通过，但测试清理阶段失败。原因是：

```text
trace_events.parent_event_id
trace_events.causation_event_id
```

均为指向 `trace_events.id` 的自关联外键，不能无序批量删除父事件。

修复：

- 先删除 Seal、Evidence 和 Relation；
- 按 `sequence_no DESC` 查询 Event；
- 从子事件到父事件逐条删除；
- 最后删除 Episode。

第三轮测试执行后自动清理成功。

## 6. 事务与行锁验证结论

### 6.1 行锁

两个线程同时对同一个 Episode 追加事件时：

```text
SELECT agent_episodes ... FOR UPDATE
```

会串行化 Episode 投影读取和更新。

最终验证结果：

```text
sequence_no = 1, 2, 3, 4
```

没有重复 sequence，没有 previousHash 分叉，完整性校验通过。

### 6.2 外层事务回滚

测试在一个普通业务事务中调用：

```text
TraceStore.append()
→ Trace 独立事务提交
→ 外层业务代码抛出异常
→ 外层事务回滚
```

最终 Trace Event 仍然存在，证明 `REQUIRES_NEW` 达到了“业务失败仍然留痕”的设计目标。

### 6.3 Trace 内部事务回滚

测试在 Trace Event 插入后、Relation 插入时注入异常。

最终结果：

- 新 Event 数量为 0；
- Episode `event_count` 未增加；
- Episode `row_version` 未增加；
- 原事件链完整性校验通过。

说明 Event、Relation 和 Episode 投影处于同一个原子事务内。

## 7. 数据清理结果

测试数据统一使用：

```text
episode_code LIKE 'IT\_%'
```

前两轮因旧清理逻辑失败产生的 7 条 Episode 已单独清除。

最终核验：

```text
remaining_it_episodes = 0
```

未修改或删除任何非 `IT_` 前缀数据。

## 8. 最终结论

`PersistentTraceStore` 在当前真实 MySQL 环境中满足本阶段要求：

- 代码层与数据库层双重幂等；
- 同一 Episode 并发追加安全；
- sequence number 严格连续；
- Hash 链不分叉；
- 外层业务事务回滚不影响已提交 Trace；
- Trace 内部异常能够整体回滚；
- 持久化后可重建并验证完整 Trace；
- 测试不会留下 Trace 测试数据。

当前可以进入下一阶段：通过 `TraceRecorder` 将持久化 Trace 接入 Chat、DAG、LLM、Tool、Verifier 和 Recovery 主执行链路。

## 9. 剩余限制

- 本次只验证了单实例、单 MySQL 主库场景；
- 未验证数据库故障切换和网络闪断；
- 未进行高并发压力测试；
- 未验证独立低权限 Trace 数据库账号；
- 未验证生产级 KMS 或密钥轮换；
- 未执行真实资金副作用操作。

