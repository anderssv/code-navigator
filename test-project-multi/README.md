# Multi-module analysis fixture

Standalone Gradle build that includes code-navigator directly from the parent source tree.

Modules:

- `:service` depends on `:shared` and references `OrderId` across the module boundary.
- `:unrelated` is a sibling with no dependency edge and must be excluded when analysis is invoked on `:service`.

Manual verification from this directory:

```bash
../gradlew build
../gradlew :service:cnavDsm --format=llm
../gradlew :service:cnavCycles --format=llm
../gradlew :service:cnavRings --scope=prod --format=llm
```

No module flag is required. Module discovery is the shared input phase for Gradle analysis tasks.
