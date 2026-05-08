package com.example.photoapp.service.tagging;

import com.example.photoapp.domain.tagging.StudentEvent;
import com.example.photoapp.repository.tagging.StudentEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Maintains the {@code student_event} precompute in the same transaction as
 * the {@link com.example.photoapp.domain.tagging.PhotoStudent} writes that
 * trigger the change. Per ADR 0004 the same-tx invariant is non-negotiable:
 * if {@code photo_student} commits but the precompute doesn't (or vice
 * versa), the home-screen read silently lies. This bean enforces it via
 * {@code @Transactional(propagation = MANDATORY)} — calling outside an
 * already-open transaction throws.
 *
 * <p>Callers compute the delta themselves: +N when adding tags, -N when
 * removing. The refresher upserts the row.
 */
@Service
public class StudentEventRefresher {

    private static final Logger log = LoggerFactory.getLogger(StudentEventRefresher.class);

    private final StudentEventRepository repo;
    private final Clock clock;

    public StudentEventRefresher(StudentEventRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(UUID studentId, UUID eventId, int delta) {
        if (delta == 0) {
            return;
        }
        Instant now = clock.instant();
        Optional<StudentEvent> existing = repo.findByIdStudentIdAndIdEventId(studentId, eventId);
        if (existing.isPresent()) {
            StudentEvent se = existing.get();
            int before = se.getPhotoCount();
            int after = before + delta;
            if (after < 0) {
                log.warn("student_event count would go negative: student={} event={} before={} delta={} — clamping to 0",
                        studentId, eventId, before, delta);
                after = 0;
            }
            se.setPhotoCount(after);
            se.touch(now);
            repo.save(se);
            log.debug("student_event updated student={} event={} {} -> {}", studentId, eventId, before, after);
        } else {
            if (delta < 0) {
                // Decrement on a row that was never created — treat as already-settled.
                return;
            }
            StudentEvent fresh = new StudentEvent(studentId, eventId, delta, now);
            repo.save(fresh);
            log.debug("student_event inserted student={} event={} count={}", studentId, eventId, delta);
        }
    }
}
