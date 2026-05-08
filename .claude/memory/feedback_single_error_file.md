---
name: One file for all errors — common/error/Errors.java
description: All domain exceptions and the global handler live in a single Errors.java file (nested classes), not scattered across one-class-per-exception files
type: feedback
---

All error/exception types and the global handler live in **one Java file**: `src/main/java/com/example/photoapp/common/error/Errors.java`.

Concretely:
- A `final class Errors` is the container; its constructor is private.
- Every domain exception is a `public static class` (or `public sealed` hierarchy) **nested inside** `Errors` — e.g. `Errors.NotFound`, `Errors.Forbidden`, `Errors.Conflict`, `Errors.BadRequest`, `Errors.Unauthorized`.
- The base type `Errors.AppException extends RuntimeException` carries the HTTP status, error code, and message; subclasses just set the right defaults.
- `Errors.GlobalHandler` (`@RestControllerAdvice`) lives in the same file and maps each exception to a `ProblemDetail` response. It also catches `MethodArgumentNotValidException`, `ConstraintViolationException`, and falls through to `Exception` for unmapped errors.
- Domain code throws `throw new Errors.NotFound("student", id)` — no separate `StudentNotFoundException` files anywhere.

**Why:** The user explicitly said: "for error keep one file error.java and write everything there related to errors". Reasons that make this a sensible deviation from the typical Java one-class-per-file convention:
- Errors are configuration-shaped, not behaviour-shaped — most subclasses do nothing but set HTTP status + code.
- One file means one place to read the full error catalogue and one place to add a new error.
- ProblemDetail mapping stays adjacent to the exceptions it maps.

**How to apply:**
- Never create files like `StudentNotFoundException.java` or `error/ResourceNotFoundException.java`. Add a nested class inside `Errors` instead.
- Imports throughout the codebase are `import com.example.photoapp.common.error.Errors;` then references like `Errors.NotFound`.
- The reviewer rejects diffs that introduce new exception files outside `Errors.java`.
- Exception: framework-level types (e.g., a custom `AuthenticationEntryPoint`) that Spring needs as standalone beans go in `security/` as small classes that *delegate* to `Errors` — they don't duplicate the error taxonomy.
