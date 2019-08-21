# Gentics Mesh Netlify Integration Plugin

This plugin provides a Gentics Mesh Netlify integration. Gentics Mesh will trigger a configurable set of Netlify Build Hooks when contents get published, deleted or unpublished.

## Web Hook Handling

The plugin will register to `publish`, `delete` and `unpublish` events of Gentics Mesh contents. When a content gets deleted a event is dispatched. The plugin will in turn trigger the configured webhooks.

## Configuration

The `plugins/netlify/config.yml` file will be created during startup of the plugin. It contains information about netlify hooks.

```
---
hooks:
- id: "5d544baaf196f9c20c979fba"
  projects:
  - "test0"
```

Each hook can also contain a list of projects for which it is active. The hook will only be triggered for projects which match up with the project of the content that triggered the event.

Omitting the projects list will create a global hook which will be triggered regardless of the project origin of the event.

## API

Hooks can also be added / listed / updated / removed during runtime via the REST endpoints. Changing the hooks will automatically update the configuration file.

The API can only accessed with admin role permissions.

### Return list of all hooks

**GET /api/v2/plugins/netlify/hooks**

### Load a single hook

**GET /api/v2/plugins/netlify/hooks/:hookId**

### Register new hook

**POST /api/v2/plugins/netlify/hooks**
```
{
    "id": "5d544baaf196f9c20c979fba",
    "triggerBranch": null,
    "projects": [
        "test0"
    ]
}
```

### Delete registered hook

**DELETE /api/v2/plugins/netlify/hooks/:hookId**
