package com.izapolsky.crawler;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UrlDiscovererImplTest {

    private UrlDiscovererImpl toTest;

    @Before
    public void setUp() {
        toTest = new UrlDiscovererImpl(Executors.newSingleThreadExecutor());
    }

    @Test
    public void testParsingSample() throws Exception {
        assertEquals(1, toTest.discover(Collections.singletonList(getClass().getResource("/sample.html"))).size());
    }

    @Test
    public void testParsingSampleBaseUrl() throws Exception {
        assertEquals(getClass().getResource("/sample.html"), toTest.discover(Collections.singletonList(getClass().getResource("/sample.html"))).get(0).first);
    }

    @Test
    public void testParsingSampleImageSrc() throws Exception {
        assertTrue("Ends with rose.jpg", toTest.discover(Collections.singletonList(getClass().getResource("/sample.html"))).get(0).second.endsWith("/rose.jpg"));
    }


}