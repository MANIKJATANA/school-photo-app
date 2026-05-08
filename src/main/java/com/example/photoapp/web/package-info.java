/**
 * HTTP layer.
 *
 * <ul>
 *   <li>{@code controller/} — REST controllers, one per resource. Every
 *       method passes through {@code AccessPolicy} for filtered reads or
 *       has an explicit role guard.</li>
 *   <li>{@code dto/} — Java records for request/response payloads.</li>
 *   <li>{@code mapper/} — MapStruct mappers between DTOs and domain
 *       entities.</li>
 *   <li>{@code auth/} — login, refresh, logout, /auth/me endpoints and the
 *       JWT filter wired in {@code config/SecurityConfig}.</li>
 * </ul>
 */
package com.example.photoapp.web;
