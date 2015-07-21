package com.izapolsky.crawler;

import java.io.File;
import java.net.URL;

/**
 * Callback interface to notify that image from given url has been downloaded
 */
public interface ImageFetchedCallback {
    /**
     * Called from producer when image was downloaded to file system
     * @param url
     * @param imageFile
     */
    void notifyImageDownloaded(URL url, File imageFile);
}
