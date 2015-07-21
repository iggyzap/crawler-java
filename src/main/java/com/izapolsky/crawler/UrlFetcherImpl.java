package com.izapolsky.crawler;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Fetcher implementation
 */
public class UrlFetcherImpl implements UrlFetcher {

    private final CloseableHttpClient chc = HttpClients.createDefault();
    private final ExecutorService ioBoundService;
    private final File outputDir;

    public UrlFetcherImpl(ExecutorService ioBoundService, File outputDir) {
        this.ioBoundService = ioBoundService;
        this.outputDir = outputDir;
    }

    @Override
    public List<Future<String>> downloadImages(List<Pair<URL, String>> images, ImageFetchedCallback callback) {

        List<Future<String>> results = new ArrayList<>(images.size());

        for (Pair<URL, String> imageInfo : images) {
            results.add(ioBoundService.submit(() -> {
                URL imageUrl = new URL(imageInfo.second);
                String mangledName = mangle(imageUrl);
                boolean modified = false;
                File destinationFile = new File(outputDir, mangledName);
                File propertiesFile = new File(outputDir, mangledName + ".properties");
                try (RandomAccessFile raf = new RandomAccessFile(propertiesFile, "rw")) {
                    FileLock l = null;
                    try {
                        l = raf.getChannel().tryLock();
                        if (l == null) {
                            return SC_SKIPPED_CONCURRENCY;
                        }

                        Pair<Boolean, Properties> imageInfo1 = readOrCreate(propertiesFile, imageUrl);
                        InputStream toClose = null;
                        try {
                            Pair<Pair<Boolean, String>, InputStream> fetchResult = fetch(chc, imageInfo1.second, imageUrl);
                            if (fetchResult.first.first) {
                                try (InputStream is = fetchResult.second) {
                                    FileUtils.copyInputStreamToFile(is, destinationFile);
                                    modified = true;
                                }
                            }

                            return fetchResult.first.second;
                        } finally {
                            IOUtils.closeQuietly(toClose);
                            if (modified) {
                                writeProps(propertiesFile, imageInfo1.second);
                            }
                        }
                    } catch (OverlappingFileLockException e) {
                        new RuntimeException(String.format("URL %1$s already being processed", imageUrl), e).printStackTrace();
                        return SC_SKIPPED_CONCURRENCY;
                    } finally {
                        if (l != null) {
                            l.close();
                        }
                        try {
                            if (modified && callback != null) {
                                callback.notifyImageDownloaded(imageUrl, destinationFile);
                            }
                        } catch (Throwable  e) {
                            //we'll swallow exception
                            e.printStackTrace();
                        }
                    }
                }
            }));
        }

        return results;
    }

    /**
     * Process of actual fetching - have to distinguish between local and remote files
     *
     * @param chc
     * @param props
     * @param url
     * @return
     */
    protected Pair<Pair<Boolean, String>, InputStream> fetch(CloseableHttpClient chc, Properties props, URL url) {
        try {
            if (isLocal(url)) {
                return new Pair<>(new Pair<>(true, String.valueOf(HttpStatus.SC_OK)), url.openStream());
            }

            HttpGet imageGet = new HttpGet(url.toURI());
            imageGet.setConfig(RequestConfig.DEFAULT);
            if (props.containsKey(HttpHeaders.ETAG)) {
                imageGet.addHeader(HttpHeaders.IF_NONE_MATCH, props.getProperty(HttpHeaders.ETAG));
            }

            //todo can be a problem when we close response before write image from stream
            //unfortunately there is mixed responsibility in when we should close requests due to commons-http API
            CloseableHttpResponse response = chc.execute(imageGet);
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_MODIFIED) {
                return new Pair<>(new Pair<>(false, String.valueOf(HttpStatus.SC_NOT_MODIFIED)), null);
            }

            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                HttpEntity entity = response.getEntity();
                if (response.containsHeader(HttpHeaders.ETAG)) {
                    //todo might have multiple etag headers
                    props.setProperty(HttpHeaders.ETAG, response.getFirstHeader(HttpHeaders.ETAG).getValue());
                }
                return new Pair<>(new Pair<>(true, String.valueOf(HttpStatus.SC_OK)), entity.getContent());
            }

            return new Pair<>(new Pair<>(false, String.valueOf(response.getStatusLine().getStatusCode())), null);

        } catch (IOException e) {
            e.printStackTrace();
            return new Pair<>(new Pair<>(false, SC_IO_ERROR), null);
        } catch (URISyntaxException e) {
            throw new RuntimeException(String.format("Failed to parse URI %1$s", url), e);
        }
    }

    /**
     * Checks if given url is from "local" filesystem
     *
     * @param url
     * @return
     */
    protected boolean isLocal(URL url) {
        return "file".equals(url.getProtocol());
    }

    /**
     * Writes property data to file
     *
     * @param propertiesFIle
     * @param what
     */
    protected void writeProps(File propertiesFIle, Properties what) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(propertiesFIle);
            what.store(fos, String.format("Change on %1$s", new Date()));
        } catch (Throwable e) {
            throw new RuntimeException(String.format("Failed writing to %1$s", propertiesFIle.getAbsolutePath()), e);
        } finally {
            IOUtils.closeQuietly(fos);
        }
    }

    /**
     * Obtains or creates properties
     *
     * @param propertiesFile
     * @param imageUrl
     * @return
     */
    protected Pair<Boolean, Properties> readOrCreate(File propertiesFile, URL imageUrl) {
        Properties result = new Properties();
        boolean created = false;
        if (!propertiesFile.isFile()) {
            writeProps(propertiesFile, result);
            created = true;
        } else {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(propertiesFile);
                result.load(fis);
            } catch (Throwable e) {
                throw new RuntimeException(String.format("Failed to read %1$s", propertiesFile), e);
            } finally {
                IOUtils.closeQuietly(fis);
            }
        }

        if (!result.containsKey("image-uri")) {
            result.setProperty("image-uri", imageUrl.toString());
        }

        return new Pair<>(created, result);
    }

    /**
     * Transforms file url into sha-256 hex hash
     *
     * @param imageUrl
     * @return
     */
    protected static String mangle(URL imageUrl) {
        //we exclude query part from image uri
        try {
            int port = imageUrl.getPort() == -1 ? detectPort(imageUrl.getProtocol()) : imageUrl.getPort();
            return DigestUtils.sha256Hex(new URL(imageUrl.getProtocol(), imageUrl.getHost(), port, imageUrl.getPath(), null).toString());
        } catch (MalformedURLException e) {
            throw new RuntimeException(String.format("Failed to construct url %1$s", imageUrl), e);
        }
    }

    /**
     * Detects port for few known protocols
     *
     * @param protocol
     * @return
     */
    private static int detectPort(String protocol) {
        if ("http".equals(protocol)) {
            return 80;
        }
        if ("https".equals(protocol)) {
            return 443;
        }
        if ("file".equals(protocol)) {
            return 0;
        }
        throw new IllegalArgumentException(String.format("I don't know about protocol %1$s", protocol));
    }

}
