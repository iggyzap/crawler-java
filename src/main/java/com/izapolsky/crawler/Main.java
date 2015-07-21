package com.izapolsky.crawler;

import com.beust.jcommander.*;
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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main entry point for the crawler engine
 */
public class Main {


    public static final int IO_QUEUE_SIZING_FACTOR = 2;

    public static class WritableDirValidator implements IValueValidator<File> {
        @Override
        public void validate(String name, File value) throws ParameterException {
            if (!value.isDirectory() || !value.canWrite()) {
                throw new ParameterException(String.format("Parameter %1$s (%2$s) has to be writeable directory", name, value.getAbsolutePath()));
            }
        }
    }


    public static class URLConverter implements IStringConverter<URL> {
        @Override
        public URL convert(String value) {
            try {
                return new URL(value);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(String.format("Failed to parse url: %1$s", value), e);
            }
        }
    }

    public static class Args {
        @Parameter(names = {"-v", "--debug"}, description = "Verbose mode")
        public boolean debug;

        @Parameter(names = {"-o", "--output-dir"}, description = "Output directory", required = true, validateValueWith = WritableDirValidator.class)
        public File outputDir;

        @Parameter(description = "<url to process>+", required = true, converter = URLConverter.class)
        public List<URL> inputUrls;

        @Parameter(names = {"-rs", "--io-pool-size"}, description = "Number of concurrent io threads")
        public int ioPoolSize = 10;

        @Parameter(names = {"-h", "--help"}, help = true, description = "Displays help")
        public boolean showHelp;

        @Parameter(names = {"-cs", "--cpu-pool-size"}, description = "Number of concurrent processing tasks")
        public int cpuPoolSize = 2;

        @Parameter(names = "--keep-going", description = "Do not kill JVM on exit", hidden = true)
        public boolean keepGoing = false;
    }

    public static void main(String... args) {
        Args parsedCmdLine = new Args();
        JCommander jc = new JCommander(parsedCmdLine);
        jc.setProgramName(Main.class.getName());
        jc.parse(args);

        if (parsedCmdLine.showHelp) {
            jc.usage();
            throw new RuntimeException("Check usage... / FIX ME");
        }

        new Main(parsedCmdLine);

        if (!parsedCmdLine.keepGoing) {
            System.exit(0);
        }
    }


    private BlockingQueue<Runnable> ioBoundQueue;
    private ExecutorService ioBoundService;
    private ExecutorService cpuBoundService;
    private BlockingQueue<Runnable> cpuBoundQueue;

    public Main(Args parsedArgs) {
        execute(parsedArgs);
    }

    protected void execute(Args parsedArgs) {
        ioBoundQueue = new LinkedBlockingDeque<>(parsedArgs.ioPoolSize * IO_QUEUE_SIZING_FACTOR);
        ioBoundService = new ThreadPoolExecutor(parsedArgs.ioPoolSize, parsedArgs.ioPoolSize, 0, TimeUnit.SECONDS, ioBoundQueue, new ThreadPoolExecutor.CallerRunsPolicy());

        //we don't put bound for cpu-constrained tasks, for now...
        cpuBoundQueue = new LinkedBlockingQueue<>();
        cpuBoundService = new ThreadPoolExecutor(parsedArgs.cpuPoolSize, parsedArgs.cpuPoolSize, 0, TimeUnit.SECONDS, cpuBoundQueue);

        List<Pair<URL, String>> images = new UrlDiscovererImpl(ioBoundService).discover(parsedArgs.inputUrls);

        //upper bound
        List<Future<String>> results = new ArrayList<>(images.size());

        System.out.println(String.format("Found %1$s image urls, total %2$s", images, images.size()));
        CloseableHttpClient chc = HttpClients.createDefault();
        for (Pair<URL, String> imageInfo : images) {
            results.add(ioBoundService.submit(() -> {
                URL imageUrl = new URL(imageInfo.second);
                String mangledName = mangle(imageUrl);
                File destinationFile = new File(parsedArgs.outputDir, mangledName);
                File propertiesFile = new File(parsedArgs.outputDir, mangledName + ".properties");
                try (RandomAccessFile raf = new RandomAccessFile(propertiesFile, "rw")) {
                    FileLock l = raf.getChannel().tryLock();
                    try {
                        if (l == null) {
                            return "-1";
                        }

                        Pair<Boolean, Properties> imageInfo1 = readOrCreate(propertiesFile, imageUrl);
                        boolean modified = false;
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
                            if (modified) {
                                writeProps(propertiesFile, imageInfo1.second);
                            }
                        }
                    } finally {
                        if (l != null) {
                            l.close();
                        }
                    }

                }
            }));
        }

        Map<String, AtomicInteger> codes = new HashMap<>();

        for (Future<String> future : results) {
            try {
                increment(codes, future.get());
            } catch (Throwable e) {
                e.printStackTrace();
                increment(codes, "-3");
            }
        }
        System.out.println(String.format("Stats of processing : %1$s", codes));

    }

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
            return new Pair<>(new Pair<>(false, "-2"), null);
        } catch (URISyntaxException e) {
            throw new RuntimeException(String.format("Failed to parse URI %1$s", url), e);
        }
    }

    protected boolean isLocal(URL url) {
        return "file".equals(url.getProtocol());
    }

    private void increment(Map<String, AtomicInteger> codes, String s) {
        AtomicInteger value = codes.get(s);
        if (value == null) {
            value = new AtomicInteger(0);
            codes.put(s, value);
        }

        value.incrementAndGet();
    }

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

    protected Pair<Boolean, Properties> readOrCreate(File propertiesFile, URL imageUrl) {
        Properties result = new Properties();
        boolean created = false;
        if (!propertiesFile.isFile()) {
            result.setProperty("image-uri", imageUrl.toString());
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

        return new Pair<>(created, result);
    }

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
