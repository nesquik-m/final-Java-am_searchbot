package searchengine.services.Impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.config.ConnectionProperties;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.SiteEntity;
import searchengine.model.StatusType;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.IndexingService;
import searchengine.utils.CreatingSiteMap;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sites;
    private final ConnectionProperties connectionSettings;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    @Override
    public void startIndexing(AtomicBoolean indexingProcessing) {
        indexingProcessing.set(true);
        for (Site site : sites.getSites()) {
            generalMethod(site, site.getUrl(), indexingProcessing);
        }
    }

    @Override
    public void indexPage(Site site, String link, AtomicBoolean indexingProcessing) {
        indexingProcessing.set(true);
        generalMethod(site, link, indexingProcessing);
    }

    private void generalMethod(Site site, String link, AtomicBoolean indexingProcessing) {
        int cores = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(cores);
        executor.execute(() -> {
            Optional<SiteEntity> optionalSiteEntity = siteRepository.findByUrl(site.getUrl());
            optionalSiteEntity.ifPresent(s -> siteRepository.deleteById(s.getId()));
            SiteEntity siteEntity = mapAndSaveSiteEntityWithIndexingStatus(site);
            CreatingSiteMap creatingSiteMap = new CreatingSiteMap(siteEntity, link, link, siteRepository, pageRepository,
                    lemmaRepository, indexRepository, indexingProcessing, connectionSettings);
            new ForkJoinPool().invoke(creatingSiteMap);

            if (indexingProcessing.get()) {
                updateStatusIndexed(siteEntity);
            }
        });
        executor.shutdown();
    }

    public SiteEntity mapAndSaveSiteEntityWithIndexingStatus(Site site) {
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setUrl(site.getUrl());
        siteEntity.setName(site.getName());
        siteEntity.setStatus(StatusType.INDEXING);
        siteEntity.setStatusTime(Instant.now());
        siteRepository.save(siteEntity);
        return siteEntity;
    }

    public void updateStatusIndexed(SiteEntity siteEntity) {
        siteEntity.setStatus(StatusType.INDEXED);
        siteEntity.setStatusTime(Instant.now());
        siteRepository.save(siteEntity);
    }
}
