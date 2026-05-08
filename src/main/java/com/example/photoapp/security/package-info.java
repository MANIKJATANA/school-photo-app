/**
 * Authentication and authorisation.
 *
 * <ul>
 *   <li>{@code jwt/} — JwtIssuer, JwtVerifier, signing-key management.</li>
 *   <li>{@code PrincipalResolver} — loads viewer context (school_id, role,
 *       student_ids, class_ids) from the validated JWT.</li>
 *   <li>{@code AccessPolicy} — composes the visibility predicate used by
 *       every filtered read path. The single source of truth for "can this
 *       viewer see this resource?" decisions.</li>
 * </ul>
 */
package com.example.photoapp.security;
