# Agent Setup

## Claude Code permissions

When Claude Code runs a cnav command via Bash, it may prompt for approval. You can add a permission rule so all cnav commands are auto-approved.

Take the command shown in the approval prompt, keep everything up to and including `cnav`, and replace the rest with `*`. Add it to `.claude/settings.local.json`:

**Gradle:**

```json
{
  "permissions": {
    "allow": [
      "Bash(./gradlew cnav*)"
    ]
  }
}
```

**Maven:**

```json
{
  "permissions": {
    "allow": [
      "Bash(mvn cnav:*)"
    ]
  }
}
```

If your setup requires a preamble before the build command (e.g. `eval "$(mise activate bash)" && ./gradlew ...`), include the full preamble in the rule:

```json
"Bash(eval \"$(mise activate bash)\" && ./gradlew cnav*)"
```

The `*` is a prefix wildcard — it matches any cnav task and parameters while not allowing arbitrary Bash commands.
