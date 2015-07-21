package com.izapolsky.crawler;

import org.junit.Test;

import java.net.URL;

import static org.junit.Assert.assertEquals;

public class UrlFetcherImplTest {


    @Test
    public void testMangle() throws Exception {
        assertEquals("4ea5b6f4e01c98cd5123a03a51aeacd5cb98ee1344d082f446a9b2c995c05baf", UrlFetcherImpl.mangle(new URL("http://google.com/foo.png")));
    }

    @Test
    public void testMangleWithPort() throws Exception {
        assertEquals("4ea5b6f4e01c98cd5123a03a51aeacd5cb98ee1344d082f446a9b2c995c05baf", UrlFetcherImpl.mangle(new URL("http://google.com:80/foo.png")));
    }

    @Test
    public void testMangleWithPortQuery() throws Exception {
        assertEquals("4ea5b6f4e01c98cd5123a03a51aeacd5cb98ee1344d082f446a9b2c995c05baf", UrlFetcherImpl.mangle(new URL("http://google.com:80/foo.png?foo=bar")));
    }

    @Test
    public void testMangleFile() throws Exception {
        assertEquals("fc97fdccbf364f8fd8a1a6ed440b0adff0412d66c4f6da20688a3e7b7aadb67c", UrlFetcherImpl.mangle(getClass().getResource("/sample.html")));
    }


}