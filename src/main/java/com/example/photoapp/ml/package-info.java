/**
 * Java&#x2194;Python ML integration.
 *
 * <ul>
 *   <li>{@code client/} — {@code MlClient} interface and the HTTP impl that
 *       calls the separate Python ML service (ADR 0002).</li>
 *   <li>{@code webhook/} — {@code MlCallbackController}: HMAC-validated
 *       endpoint that ingests match results and triggers the
 *       {@code photo_student}+{@code student_event}+outbox transactional
 *       write.</li>
 *   <li>{@code dto/} — request/response shapes for both directions of the
 *       contract. Versioned alongside the Python repo.</li>
 * </ul>
 */
package com.example.photoapp.ml;
