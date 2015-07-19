package com.izapolsky.crawler;

import java.net.URL;
import java.util.List;

/**
 * Interface for discovering urls from given list of remote documents
 */
public interface UrlDiscoverer {
    /**
     * Discovers pairs of images, each image has a base URL, then an image's source
     * @param documents
     * @return
     */
    List<Pair<URL, String>> discover(List<URL> documents);
}
