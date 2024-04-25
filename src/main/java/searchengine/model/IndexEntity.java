package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Getter
@Setter
@Table(name = "`index`")
public class IndexEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false)
    private PageEntity pageEntity;

    @ManyToOne
    @JoinColumn(name = "lemma_id", nullable = false)
    private LemmaEntity lemmaEntity;

    @Column(name = "`rank`", nullable = false)
    private float rank;
}
