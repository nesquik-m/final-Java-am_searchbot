package searchengine.utils;

import lombok.SneakyThrows;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.ConnectionProperties;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.StatusType;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CreatingSiteMap extends RecursiveAction {

    private final SiteEntity siteEntity;
    private final String url;
    private final String root;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final ConnectionProperties connectionProperties;
    private final List<CreatingSiteMap> taskList = new ArrayList<>();
    private AtomicBoolean indexingProcessing;

    public CreatingSiteMap(SiteEntity siteEntity,
                           String url, String root,
                           SiteRepository siteRepository,
                           PageRepository pageRepository,
                           LemmaRepository lemmaRepository,
                           IndexRepository indexRepository,
                           AtomicBoolean indexingProcessing,
                           ConnectionProperties connectionProperties) {
        this.siteEntity = siteEntity;
        this.url = url;
        this.root = root;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.indexingProcessing = indexingProcessing;
        this.connectionProperties = connectionProperties;
    }

    @SneakyThrows
    @Override
    protected void compute() {

        int statusCode = getResponse(url);
        if (!isFirstDigitNotFourOrFive(statusCode)) {
            return;
        }
        Document doc = getDocument(url);
        Elements links = doc.select("a");
        for (Element element : links) {
            String absHref = element.attr("abs:href");
            String href = getHref(absHref);

            boolean linkIsValid = absHref.contains(root) &&
                        !absHref.contains("#") &&
                        !absHref.contains("%") &&
                        !absHref.contains(".jpg") &&
                        !absHref.contains(".zip") &&
                        !absHref.contains(".sql") &&
                        !absHref.contains(".yaml") &&
                        !absHref.endsWith("null") &&
                        !href.isEmpty() &&
                        pageRepository.findByPath(href).isEmpty();
            if (linkIsValid) {
                String content = getDocument(absHref).html();
                PageEntity pageEntity = mapToNewPageEntity(siteEntity, href, statusCode, content);
                pageRepository.save(pageEntity);

                CreatingSiteMap task = new CreatingSiteMap(siteEntity, absHref, root, siteRepository, pageRepository,
                        lemmaRepository, indexRepository, indexingProcessing, connectionProperties);
                taskList.add(task);

                LemmasAndIndexes lemmasAndIndexes = new LemmasAndIndexes(siteRepository, lemmaRepository, indexRepository);
                lemmasAndIndexes.creatingLemmasAndIndexes(siteEntity, pageEntity);

                if (!indexingProcessing.get()) {
                    updateStatusFailed(siteEntity);
                    return;
                }
            }
        }
        taskList.forEach(CreatingSiteMap::fork);
        taskList.parallelStream().forEach(CreatingSiteMap::join);
    }

    private String getHref(String absHref) {
        String regex = "(?<=(.ru|.com))([^;]*)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(absHref);
        String href = "";

        while (matcher.find()) {
            href = matcher.group();
            href = !href.endsWith("/") ? href += "/" : href;
        }

        return href;
    }

    public boolean isFirstDigitNotFourOrFive(int statusCode) {
        String status = String.valueOf(statusCode);
        char firstDigit = status.charAt(0);
        return !(firstDigit == '4' || firstDigit == '5');
    }

    private int getResponse(String url) throws IOException {
        return Jsoup.connect(url).followRedirects(false).execute().statusCode();
    }

    private Document getDocument(String url) throws InterruptedException, IOException {
        Thread.sleep(150);
        return Jsoup.connect(url)
                .timeout(90000)
                .userAgent(connectionProperties.getUserAgent())
                .referrer(connectionProperties.getReferer())
                .get();
    }

    private PageEntity mapToNewPageEntity(SiteEntity siteEntity, String href, int statusCode, String content) {
        PageEntity pageEntity = new PageEntity();
        pageEntity.setSiteEntity(siteEntity);
        pageEntity.setPath(href);
        pageEntity.setCode(statusCode);
        pageEntity.setContent(content);
        return pageEntity;
    }

    public void updateStatusFailed(SiteEntity siteEntity) {
        siteEntity.setStatus(StatusType.FAILED);
        siteEntity.setStatusTime(Instant.now());
        siteEntity.setLastError("Индексация прервана пользователем");
        siteRepository.save(siteEntity);
    }
}