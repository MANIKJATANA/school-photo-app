/**
 * Domain entities and value objects. <strong>No Spring imports here.</strong>
 *
 * <p>Plain JPA entities live under sub-packages by aggregate:
 * {@code school}, {@code user}, {@code student}, {@code teacher},
 * {@code klass}, {@code event}, {@code photo}, {@code tagging}
 * (PhotoStudent, StudentEvent), {@code ml}, {@code share},
 * {@code notification}.
 *
 * <p>UUIDv7 PKs (generated app-side via {@code IdGenerator}), soft delete via
 * {@code deleted_at}, and {@code school_id} on every top-level entity are the
 * cross-aggregate invariants enforced here.
 */
package com.example.photoapp.domain;
