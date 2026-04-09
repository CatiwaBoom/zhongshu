# Workflow Designer Module

This module provides a drag-and-drop workflow designer backend for the Vue3 front end.

## Backend Endpoints

- `GET /workflow/definitions?keyword=`: list definitions
- `GET /workflow/definition/next-code`: generate a code
- `POST /workflow/definition/publish`: create definition
- `PUT /workflow/definition/{id}`: update metadata
- `DELETE /workflow/definition/{id}`: delete definition and design
- `GET /workflow/definition/{id}/design`: load designer JSON
- `PUT /workflow/definition/{id}/design`: save designer JSON
- `DELETE /workflow/definition/{id}/node/{nodeId}`: remove a node and related edges

## Frontend Pages

- `ui/src/views/workflow-definition/WorkflowDefinitionManagement.vue`
- `ui/src/views/workflow-design/WorkflowDesigner.vue`

## Designer JSON Shape

```json
{
  "nodes": [],
  "edges": [],
  "viewport": { "x": 0, "y": 0, "zoom": 1 }
}
```

