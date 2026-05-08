/**
 * Cross-cutting utilities used across modules:
 * <ul>
 *   <li>{@code error/} — GlobalExceptionHandler, ProblemDetail mappers, domain exception base classes.</li>
 *   <li>{@code pagination/} — opaque cursor codec for infinite-scroll endpoints.</li>
 *   <li>{@code school/} — SchoolContext (request-scoped {@code school_id} read from the JWT) and the filter that populates it.</li>
 *   <li>{@code audit/} — {@code Auditable} {@code @MappedSuperclass} providing {@code id}, {@code created_at}, {@code updated_at}, {@code deleted_at}.</li>
 * </ul>
 */
package com.example.photoapp.common;
