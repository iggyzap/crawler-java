package com.izapolsky.crawler;

import com.beust.jcommander.ParameterException;
import org.junit.Test;

import java.net.URL;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
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

    @Test(expected = ParameterException.class)
    public void testFailsIfRandomDir() throws Exception {
        Main.main("--keep-going", "-o", UUID.randomUUID().toString());
    }

    @Test
    public void testMangle() throws Exception {
        assertEquals("4ea5b6f4e01c98cd5123a03a51aeacd5cb98ee1344d082f446a9b2c995c05baf", Main.mangle(new URL("http://google.com/foo.png")));
    }

    @Test
    public void testMangleWithPort() throws Exception {
        assertEquals("4ea5b6f4e01c98cd5123a03a51aeacd5cb98ee1344d082f446a9b2c995c05baf", Main.mangle(new URL("http://google.com:80/foo.png")));
    }

    @Test
    public void testMangleWithPortQuery() throws Exception {
        assertEquals("4ea5b6f4e01c98cd5123a03a51aeacd5cb98ee1344d082f446a9b2c995c05baf", Main.mangle(new URL("http://google.com:80/foo.png?foo=bar")));
    }

    @Test
    public void testMangleFile() throws Exception {
        assertEquals("fc97fdccbf364f8fd8a1a6ed440b0adff0412d66c4f6da20688a3e7b7aadb67c", Main.mangle(getClass().getResource("/sample.html")));
    }


}