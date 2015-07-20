package com.izapolsky.crawler;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.*;

/**
 * Main entry point for the crawler engine
 */
public class Main {


    public static final int IO_QUEUE_SIZING_FACTOR = 2;

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

        @Parameter(names = {"-o", "--output-dir"}, description = "Output directory", required = true)
        public File outputDir;

        @Parameter(description = "<url to process>+", required = true, converter = URLConverter.class)
        public List<URL> inputUrls;

        @Parameter(names = {"-rs", "--io-pool-size"}, description = "Number of concurrent io threads")
        public int ioPoolSize = 10;

        @Parameter(names = {"-h", "--help"}, help = true, description = "Displays help")
        public boolean showHelp;

        @Parameter(names = {"-cs", "--cpu-pool-size"}, description = "Number of concurrent processing tasks")
        public int cpuPoolSize = 2;
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

        System.exit(0);
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


        System.out.println(String.format("Found %1$s image urls", new UrlDiscovererImpl(ioBoundService).discover(parsedArgs.inputUrls).size()));
    }



}
