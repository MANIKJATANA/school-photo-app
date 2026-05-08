# ADR 0009: BlobStore abstraction with config-driven provider selection

- **Status**: Accepted
- **Date**: 2026-05-08
- **Deciders**: Project owner

## Context

Photo bytes are stored in an object store. AWS S3 is the v1 choice (per ADR 0005, "presigned URLs only"), but the project owner wants the option to swap to Azure Blob Storage, Google Cloud Storage, MinIO, or any compatible third party without rewriting service-layer code.

Two questions to settle:

1. **What does the seam look like?** A wide interface that returns vendor-neutral types so service code never sees provider SDK classes.
2. **How is the provider selected at runtime?** Config flag + `@ConditionalOnProperty`-gated configuration classes — no recompile needed.

## Decision

A single `BlobStore` interface with four methods covers every operation the API needs:

```java
URI    presignPut(String key, String contentType, Duration ttl);
URI    presignGet(String key, Duration ttl);
Optional<BlobMetadata> head(String key);
void   delete(String key);
```

Plus one record (`BlobMetadata`) carrying `(sizeBytes, contentType)`.

Every provider has its own configuration class gated by `@ConditionalOnProperty(prefix = "photoapp.storage", name = "provider", havingValue = "<name>")`. The active provider is chosen by setting `photoapp.storage.provider` (default `s3`).

```
photoapp.storage.provider=s3       → S3Config activates, registers S3BlobStore
photoapp.storage.provider=azure    → AzureConfig (future) registers AzureBlobStore
photoapp.storage.provider=gcs      → GcsConfig (future) registers GcsBlobStore
```

Service-layer code (`PhotoUploadService`, `PhotoQueryService`, etc.) injects `BlobStore` and never knows which backend is active.

## Consequences

### Positive

- One interface for every provider — service code is portable.
- Adding a provider is additive: new impl + new config class. Existing code unchanged.
- The active provider is a single config flag — swap by env var, no rebuild.
- Provider-specific properties live under provider-specific prefixes (`photoapp.s3.*`, future `photoapp.azure.*`) so they don't collide.

### Negative

- The interface is the lowest common denominator. Provider-specific features (S3 lifecycle policies, Azure access tiers, GCS object retention) are out of `BlobStore`'s scope and must be configured at the provider's own console / IaC. Acceptable: those are deploy concerns, not application concerns.
- If a future feature genuinely needs provider-specific behaviour (e.g., S3 multipart upload), it widens the interface OR introduces a dedicated abstraction (`MultipartBlobStore`). We accept that cost when it arrives, not preemptively.

### Neutral

- Tests against any provider use Testcontainers (LocalStack for S3, Azurite for Azure, fake-gcs-server for GCS). The S3 integration test in `S3BlobStoreTest` is the template; Docker-skipped where unavailable.

## Procedure to add a new provider (e.g., Azure)

1. Add SDK dependency to `pom.xml` (e.g., `azure-storage-blob`).
2. New file `storage/azure/AzureBlobStore.java implements BlobStore`. The only file that may import the Azure SDK.
3. New file `storage/azure/AzureProperties.java` — record holding the provider's config.
4. New file `config/AzureConfig.java` — `@Configuration @ConditionalOnProperty(prefix = "photoapp.storage", name = "provider", havingValue = "azure")`. Provides `BlobServiceClient`, `AzureProperties`, and the `BlobStore` bean.
5. Add `photoapp.azure.*` properties to `application.properties` with sensible env-driven defaults.
6. Set `photoapp.storage.provider=azure` (env var `STORAGE_PROVIDER=azure`) to activate.
7. Add `AzureBlobStoreTest` mirroring `S3BlobStoreTest` (Testcontainers Azurite, `disabledWithoutDocker = true`).

No changes to `BlobStore`, `BlobMetadata`, `S3KeyStrategy` (the key layout is provider-agnostic), service code, or controllers.

## Alternatives considered

- **Spring Cloud AWS / azure-spring** — more starters and abstraction over a single vendor's services. Rejected: still vendor-coupled at the type level, doesn't help cross-provider swap.
- **Apache Commons VFS / jclouds** — generic file-system abstractions over providers. Rejected: they wrap state-ful object access; we need presigned URLs, which they don't model first-class.
- **No abstraction, just S3** — simpler today, painful migration later. Rejected because the user explicitly asked for swap-readiness.

## References

- ADR 0001 — DB portability (same pattern: interface + provider-specific impl behind a flag).
- ADR 0005 — Presigned URLs only.
- `src/main/java/com/example/photoapp/storage/blob/BlobStore.java`
- `src/main/java/com/example/photoapp/storage/s3/S3BlobStore.java`
- `src/main/java/com/example/photoapp/config/S3Config.java`
