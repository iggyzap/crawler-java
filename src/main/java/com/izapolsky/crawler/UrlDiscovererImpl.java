package com.izapolsky.crawler;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Implementation for url discovery service.
 */
public class UrlDiscovererImpl implements UrlDiscoverer {

    private final ExecutorService ioBoundService;

    public UrlDiscovererImpl(ExecutorService ioBoundService) {
        this.ioBoundService = ioBoundService;
    }

    @Override
    public List<Pair<URL, String>> discover(List<URL> documents) {
        List<Pair<URL, String>> images = new ArrayList<>();

        try {
            List<Future<List<Pair<URL, String>>>> imagePromises = ioBoundService.invokeAll(Collections2.transform(documents, url -> () -> {
                List<Pair<URL, String>> result;

                try (InputStream is = url.openStream()){
                    //TODO - encoding detection...
                    Document doc = Jsoup.parse(is, "utf-8", url.toString());
                    Elements els = doc.select("img[src]");
                    result = Lists.transform(els, elements -> new Pair<>(url, elements.attr("abs:src")));
                } catch (Throwable e) {
                    throw new RuntimeException(String.format("Failed processing url %1$s", url), e);
                }
                return result;
            }));
            for (Future<List<Pair<URL, String>>> future : imagePromises) {
                images.addAll(extract(future));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while extracting images urls...", e);
        }

        return images;
    }

    protected List<Pair<URL, String>> extract(Future<List<Pair<URL, String>>> futures) {
        try {
            return futures.get();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
