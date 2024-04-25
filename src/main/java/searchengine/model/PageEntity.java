package searchengine.model;
import lombok.Getter;
import lombok.Setter;
import javax.persistence.*;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "page")
public class PageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity siteEntity;

    @Column(columnDefinition = "TEXT", name = "path", nullable = false)
    private String path;

    @Column(name = "code", nullable = false)
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT", name = "content", nullable = false)
    private String content;

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "`index`",
            joinColumns = @JoinColumn(name = "page_id"),
            inverseJoinColumns = @JoinColumn(name = "lemma_id"))
    private List<LemmaEntity> lemmaEntities;

    @Override
    public String toString() {
        return  "`page` -> " +
                "id " + id +
                ", siteEntity " + siteEntity.getId() +
                ", path " + path;
    }
}
