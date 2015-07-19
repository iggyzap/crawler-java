package com.izapolsky.crawler;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import java.io.File;
import java.net.URL;
import java.util.List;

/**
 * Main entry point for the crawler engine
 */
public class Main {


    public static class Args {
        @Parameter(names = {"-v", "--debug"}, description = "Verbose mode")
        public boolean debug;

        @Parameter(names = {"-o", "--output-dir"}, description = "Output directory", required = true)
        public File outputDir;

        @Parameter(description = "<url to process>+", required = true)
        public List<URL> inputUrls;

        @Parameter(names = {"-h", "--help"}, help = true)
        public boolean showHelp;

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

    }

    public Main(Args parsedArgs) {
        execute(parsedArgs);
    }

    protected void execute(Args parsedArgs) {
        //does nothing now
    }
}
