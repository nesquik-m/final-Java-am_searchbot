package searchengine.repositories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;
import java.util.Optional;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {
    @Query("FROM LemmaEntity l WHERE l.siteEntity = ?1 and l.lemma = ?2")
    Optional<LemmaEntity> findBySiteIdAndLemma(SiteEntity siteEntity, String lemma);
    int countBySiteEntity(SiteEntity siteEntity);
}
