package com.izapolsky.crawler;

import java.net.URL;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Service for downloading images
 */
public interface UrlFetcher {

    String SC_SKIPPED_CONCURRENCY = "-1";
    String SC_IO_ERROR = "-2";
    String SC_GENERIC_ERROR = "-3";

    /**
     * Downloads images from given urls to pre-defined location
     * @param imageInfo
     * @return
     */
    List<Future<String>> downloadImages(List<Pair<URL, String>> imageInfo, ImageFetchedCallback callback);
}
