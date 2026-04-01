# Workflow Engine Module

轻量级流程引擎模块，支持：基础审批、会签、或签、移交、终止、撤回。

## API

- `POST /workflow/definition/publish` 发布流程定义（自动版本+1）
- `POST /workflow/instance/start` 发起流程
- `POST /workflow/task/action` 任务动作（APPROVE/REJECT/TRANSFER/WITHDRAW/TERMINATE）
- `GET /workflow/instance/{id}` 查询流程实例

## 节点审批模式

- `BASIC`：单人审批
- `OR_SIGN`：任意一人通过即节点通过
- `COUNTER_SIGN`：全员通过才节点通过

