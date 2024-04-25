package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


import javax.persistence.*;
import java.time.Instant;
import java.util.List;

@Entity
@Getter
@Setter
@ToString
@Table(name = "site")
public class SiteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED')", name = "status", nullable = false)
    private StatusType status;

    @Column(name = "status_time", nullable = false)
    private Instant statusTime;

    @Column(columnDefinition = "TEXT", name = "last_error")
    private String lastError;

    @Column(columnDefinition = "VARCHAR(255)", name = "url", nullable = false)
    private String url;

    @Column(columnDefinition = "VARCHAR(255)", name = "name", nullable = false)
    private String name;

    @OneToMany(mappedBy = "siteEntity", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PageEntity> pageEntities;

    @OneToMany(mappedBy = "siteEntity", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<LemmaEntity> lemmaEntities;
}
