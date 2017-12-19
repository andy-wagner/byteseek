package net.byteseek.io.reader.cache;

import net.byteseek.io.reader.windows.HardWindow;
import net.byteseek.io.reader.windows.Window;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NoCacheTest {

    private Window window;
    private NoCache noCache;

    @Before
    public void setup() {
        window = new HardWindow(new byte[1024], 0, 1024);
        noCache = new NoCache();
    }


    @Test
    public void testGetWindow() throws Exception {
       assertNull(noCache.getWindow(0));
       noCache.addWindow(window);
       assertNull(noCache.getWindow(0));
    }

    @Test
    public void testAddThenGetNullWindow() throws Exception {
        noCache.addWindow(window);
        assertNull(noCache.getWindow(0));
    }

    @Test
    public void testRead() throws Exception {
        assertEquals(0, noCache.read(0, 0, new byte[1024], 0));
        noCache.addWindow(window);
        assertEquals(0, noCache.read(0, 0, new byte[1024], 0));
    }

    @Test
    public void testAddImmediateFreeWindow() throws Exception {
        final Window[] result = new Window[1];
        WindowCache.WindowObserver observer = new WindowCache.WindowObserver() {
            @Override
            public void windowFree(Window window, WindowCache fromCache) throws IOException {
                 result[0] = window;
            }
        };
        noCache.subscribe(observer);
        noCache.addWindow(window);
        assertTrue(window == result[0]);
        assertNull(noCache.getWindow(0));
    }

    @Test
    public void testToString() throws Exception {
        assertTrue(NoCache.NO_CACHE.toString().contains(NoCache.NO_CACHE.getClass().getSimpleName()));
    }
}