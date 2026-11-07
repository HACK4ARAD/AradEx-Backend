package aradexperience.repository.business;

import aradexperience.entity.business.Announcement;
import aradexperience.enums.ContentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    /** Panoul firmei: toate anunțurile ei, cele mai noi primele. */
    List<Announcement> findByBusinessIdOrderByCreatedAtDesc(Long businessId);

    /** Pagina publică: doar cele publicate. */
    List<Announcement> findByBusinessIdAndStatusOrderByCreatedAtDesc(Long businessId, ContentStatus status);
}
