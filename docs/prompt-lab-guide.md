# Prompt Lab 使用指南

## 目标

Prompt Lab 用于独立测试提示词，不经过 DAG、Tool、RAG 和 Memory 主链路。修改提示词后，可以使用固定测试问题重复运行并比较通过率。

## 推荐练习：知识库路由

初始提示词：

```text
你是企业知识检索路由器。

目标：
判断用户问题是否需要查询企业知识库。

需要查询知识库：
- 业务流程、制度、规则和状态含义
- SOP、FAQ、操作说明和异常处理

不需要查询知识库：
- 具体单据的实时状态、金额和付款时间
- 当前时间、计算和普通闲聊

规则：
1. 具体业务数据使用 BUSINESS_QUERY。
2. 规则、原因和处理办法使用 KNOWLEDGE_SEARCH。
3. 同时涉及实时数据和规则时使用 BOTH。
4. 只输出分类名称，不要解释。
```

## 单条测试

```bash
curl -X POST "http://localhost:8080/api/debug/prompt-lab/run" \
  -H "Content-Type: application/json" \
  -d '{
    "systemPrompt": "你是企业知识检索路由器。规则：具体业务数据输出BUSINESS_QUERY；规则、原因和处理办法输出KNOWLEDGE_SEARCH；同时涉及两者输出BOTH。只输出分类名称。",
    "input": "已付款但没有回单是什么原因？",
    "expectedContains": ["KNOWLEDGE_SEARCH"],
    "forbiddenContains": ["BUSINESS_QUERY"]
  }'
```

## 批量测试

```bash
curl -X POST "http://localhost:8080/api/debug/prompt-lab/batch" \
  -H "Content-Type: application/json" \
  -d '{
    "systemPrompt": "你是企业知识检索路由器。规则：具体业务数据输出BUSINESS_QUERY；规则、原因和处理办法输出KNOWLEDGE_SEARCH；同时涉及两者输出BOTH。只输出分类名称。",
    "cases": [
      {
        "name": "实时单据查询",
        "input": "EC2025付款了吗？",
        "expectedContains": ["BUSINESS_QUERY"],
        "forbiddenContains": ["KNOWLEDGE_SEARCH"]
      },
      {
        "name": "规则问题",
        "input": "付款失败应该怎么处理？",
        "expectedContains": ["KNOWLEDGE_SEARCH"],
        "forbiddenContains": ["BUSINESS_QUERY"]
      },
      {
        "name": "组合问题",
        "input": "EC2025为什么付款失败？",
        "expectedContains": ["BOTH"]
      }
    ]
  }'
```

## 练习方法

1. 先运行初始提示词，记录通过率。
2. 增加容易误判的问题，不要只测试正确示例。
3. 根据失败类型修改规则、边界和示例。
4. 每次修改后重新运行全部测试。
5. 提示词稳定后，再迁移到正式 Agent 模块。
