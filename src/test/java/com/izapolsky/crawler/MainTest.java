package com.izapolsky.crawler;

import org.junit.Test;

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
        Main.main("-o", System.getProperty("java.io.tmpdir"), getClass().getResource("/sample.html").toString());
    }
}