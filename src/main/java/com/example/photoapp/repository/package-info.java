/**
 * Persistence layer.
 *
 * <p>Two flavours:
 * <ul>
 *   <li><strong>Spring Data JPA</strong> interfaces for boring CRUD —
 *       portable across JPA-supported databases.</li>
 *   <li><strong>JdbcClient-backed query repositories</strong> hidden behind
 *       interfaces ({@code PhotoQueryRepository}, {@code OutboxStore}, etc.)
 *       for partition-aware / hot-path queries. PostgreSQL-specific SQL
 *       (e.g. {@code FOR UPDATE SKIP LOCKED}, partition pruning) lives only
 *       inside these PG impls — never leaks to {@code service/} or
 *       {@code domain/}.</li>
 * </ul>
 *
 * <p>This split is the linchpin of the DB-portability rule (ADR 0001).
 */
package com.example.photoapp.repository;
