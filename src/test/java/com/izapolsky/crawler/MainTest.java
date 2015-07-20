package com.izapolsky.crawler;

import com.beust.jcommander.ParameterException;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertNull;

public class MainTest {

    private Main.Args argsFromExecute;

    class Tmp extends Main {
        public Tmp(Args parsedArgs) {
            super(parsedArgs);
        }

        @Override
        protected void execute(Args parsedArgs) {
            argsFromExecute = parsedArgs;
        }
    }

    @Test
    public void testMainInvocationUsage() {
        try {
            Tmp.main("-h");
        } catch (RuntimeException e) {
            assertNull("execute should not be called", argsFromExecute);
        }
    }

    @Test
    public void testFindsRose() throws Exception {
        Main.main("--keep-going", "-o", System.getProperty("java.io.tmpdir"), getClass().getResource("/sample.html").toString());
    }

    @Test (expected = ParameterException.class)
    public void testFailsIfRandomDir() throws Exception {
       Main.main("--keep-going", "-o", UUID.randomUUID().toString());
    }
}