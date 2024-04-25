package searchengine.services.Impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.StatisticsService;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SitesList sites;

    @Override
    public StatisticsResponse getStatistics() {

        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sitesList = sites.getSites();
        for (Site site : sitesList) {
            DetailedStatisticsItem item = siteRepository.findByUrl(site.getUrl())
                    .map(siteEntity -> {
                        int pages = pageRepository.countBySiteEntity(siteEntity);
                        int lemmas = lemmaRepository.countBySiteEntity(siteEntity);
                        String status = String.valueOf(siteEntity.getStatus());
                        String error = siteEntity.getLastError() == null ? "" : siteEntity.getLastError();
                        long statusTime = siteEntity.getStatusTime().toEpochMilli();

                        return new DetailedStatisticsItem(site.getUrl(), site.getName(), status, statusTime, error, pages, lemmas);
                    })
                    .orElseGet(() -> new DetailedStatisticsItem(site.getName(), site.getUrl(), "NOT INDEXED",
                            0L, "Сайт не проиндексирован", 0, 0));

            total.setPages(total.getPages() + item.getPages());
            total.setLemmas(total.getLemmas() + item.getLemmas());
            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }
}