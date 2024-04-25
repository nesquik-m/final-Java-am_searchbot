package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import javax.persistence.*;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "lemma")
public class LemmaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity siteEntity;

    @Column(columnDefinition = "VARCHAR(255)", name = "lemma", nullable = false)
    private String lemma;

    @Column(name = "frequency", nullable = false)
    private int frequency;

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "`index`",
            joinColumns = @JoinColumn(name = "lemma_id"),
            inverseJoinColumns = @JoinColumn(name = "page_id"))
    private List<PageEntity> pageEntities;

    @Override
    public String toString() {
        return  "`lemma` -> " +
                "id " + id +
                ", siteEntity " + siteEntity.getId() +
                ", lemma '" + lemma +
                "', frequency " + frequency;
    }
}
