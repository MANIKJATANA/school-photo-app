package com.example.photoapp.repository.photo;

import com.example.photoapp.TestcontainersConfiguration;
import com.example.photoapp.domain.photo.MlStatus;
import com.example.photoapp.domain.photo.Photo;
import com.example.photoapp.domain.photo.UploadStatus;
import com.example.photoapp.domain.school.School;
import com.example.photoapp.domain.user.AppUser;
import com.example.photoapp.domain.user.Role;
import com.example.photoapp.domain.event.Event;
import com.example.photoapp.repository.event.EventRepository;
import com.example.photoapp.repository.school.SchoolRepository;
import com.example.photoapp.repository.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class PhotoRepositoryTest {

    @Autowired PhotoRepository photos;
    @Autowired SchoolRepository schools;
    @Autowired AppUserRepository users;
    @Autowired EventRepository events;

    @Test
    void persist_and_find_active_resolves_via_composite_pk() {
        Fixture f = newFixture();
        Photo p = newPhoto(f, "schools/.../1.jpg");
        photos.saveAndFlush(p);

        var loaded = photos.findActive(p.getEventId(), p.getId());

        assertThat(loaded).isPresent();
        assertThat(loaded.get().getBlobKey()).isEqualTo("schools/.../1.jpg");
    }

    @Test
    void default_statuses_are_PENDING() {
        Fixture f = newFixture();
        Photo p = newPhoto(f, "k1");
        photos.saveAndFlush(p);

        Photo loaded = photos.findActive(p.getEventId(), p.getId()).orElseThrow();
        assertThat(loaded.getUploadStatus()).isEqualTo(UploadStatus.PENDING);
        assertThat(loaded.getMlStatus()).isEqualTo(MlStatus.PENDING);
    }

    @Test
    void find_by_event_returns_only_that_events_photos_in_DESC_order() throws InterruptedException {
        Fixture f = newFixture();
        Photo p1 = newPhoto(f, "k1");
        photos.saveAndFlush(p1);
        Thread.sleep(5); // ensure created_at differs deterministically
        Photo p2 = newPhoto(f, "k2");
        photos.saveAndFlush(p2);

        // Different event → must not appear.
        Event otherEvent = persistedEvent(f.school.getId(), f.admin.getId(), "Other");
        Photo other = new Photo(otherEvent.getId(), f.school.getId(), "k-other",
                "test-bucket", "image/jpeg", 100, f.admin.getId());
        photos.saveAndFlush(other);

        List<Photo> page = photos.findActiveByEvent(f.event.getId());

        assertThat(page).hasSize(2);
        assertThat(page.get(0).getId()).isEqualTo(p2.getId()); // newer first
        assertThat(page.get(1).getId()).isEqualTo(p1.getId());
    }

    @Test
    void soft_deleted_photo_excluded_from_active_finders() {
        Fixture f = newFixture();
        Photo p = newPhoto(f, "k");
        photos.saveAndFlush(p);
        p.softDelete(Instant.now());
        photos.saveAndFlush(p);

        assertThat(photos.findActive(p.getEventId(), p.getId())).isEmpty();
        assertThat(photos.findActiveByIdAlone(p.getId())).isEmpty();
        assertThat(photos.findActiveByEvent(p.getEventId())).isEmpty();
    }

    @Test
    void find_by_id_alone_resolves_across_partitions() {
        Fixture f = newFixture();
        Photo p = newPhoto(f, "k");
        photos.saveAndFlush(p);

        var loaded = photos.findActiveByIdAlone(p.getId());

        assertThat(loaded).isPresent();
        assertThat(loaded.get().getEventId()).isEqualTo(p.getEventId());
    }

    @Test
    void find_by_upload_status_older_than_returns_only_matching_rows() throws InterruptedException {
        Fixture f = newFixture();
        Photo old = newPhoto(f, "old");
        photos.saveAndFlush(old);
        Thread.sleep(20);
        Instant cutoff = Instant.now();
        Thread.sleep(20);
        Photo fresh = newPhoto(f, "fresh");
        photos.saveAndFlush(fresh);

        List<Photo> stale = photos.findByUploadStatusOlderThan(UploadStatus.PENDING, cutoff);

        assertThat(stale).extracting(Photo::getId).contains(old.getId()).doesNotContain(fresh.getId());
    }

    // ============ fixture helpers ============

    private record Fixture(School school, AppUser admin, Event event) {}

    private Fixture newFixture() {
        School s = schools.saveAndFlush(new School("Test " + UUID.randomUUID(), null, null));
        AppUser a = new AppUser(s.getId(), "admin-" + UUID.randomUUID() + "@x.test", "h", Role.ADMIN);
        users.saveAndFlush(a);
        Event e = persistedEvent(s.getId(), a.getId(), "Default");
        return new Fixture(s, a, e);
    }

    private Event persistedEvent(UUID schoolId, UUID createdBy, String name) {
        Event e = new Event(schoolId, name, createdBy);
        return events.saveAndFlush(e);
    }

    private static Photo newPhoto(Fixture f, String key) {
        return new Photo(f.event.getId(), f.school.getId(), key,
                "test-bucket", "image/jpeg", 1024, f.admin.getId());
    }
}
