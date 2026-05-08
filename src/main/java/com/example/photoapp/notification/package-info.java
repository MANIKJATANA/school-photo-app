/**
 * Notification dispatch.
 *
 * <ul>
 *   <li>{@code relay/} — {@code OutboxPoller} (Spring scheduler), claims
 *       outbox rows via the {@code OutboxStore} interface (PG impl uses
 *       {@code FOR UPDATE SKIP LOCKED}) and dispatches to channels.</li>
 *   <li>{@code channel/} — {@code NotificationChannel} strategy. Only
 *       {@code InAppChannel} ships in v1 (writes a {@code notification}
 *       row). Email/SMS/push impls plug in later as new beans (ADR 0007).</li>
 * </ul>
 */
package com.example.photoapp.notification;
