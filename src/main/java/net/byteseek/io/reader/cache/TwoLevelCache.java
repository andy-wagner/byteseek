/*
 * Copyright Matt Palmer 2011-2017, All rights reserved.
 *
 * This code is licensed under a standard 3-clause BSD license:
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, 
 *    this list of conditions and the following disclaimer.
 * 
 *  * Redistributions in binary form must reproduce the above copyright notice, 
 *    this list of conditions and the following disclaimer in the documentation 
 *    and/or other materials provided with the distribution.
 * 
 *  * The names of its contributors may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 * POSSIBILITY OF SUCH DAMAGE.
 */

package net.byteseek.io.reader.cache;

import net.byteseek.io.reader.windows.Window;
import net.byteseek.io.reader.cache.WindowCache.WindowObserver;
import net.byteseek.utils.ArgUtils;

import java.io.IOException;

/**
 * A {@link WindowCache} which wraps two further WindowCaches.  When a {@link net.byteseek.io.reader.windows.Window}
 * leaves the primary cache, it is automatically added to the secondary cache.
 * Observers of this cache are notified when a Window leaves the secondary cache, 
 * but not when it leaves the primary one (as it is added immediately to the secondary).
 * Adding a Window to this cache adds it to the primary cache.
 * <p>
 * This class can only be constructed using a static method, as it subscribes as an
 * observer to the primary secondary cache's passed in.  We do not want to have
 * subscription happening in its constructor, as this may allow an invalid "this" reference
 * to escape if an error occurs during construction.
 * <p>
 * It implements {@link WindowObserver} in order to receive notifications from the 
 * primary and secondary caches about Windows leaving them.
 * 
 * @author Matt Palmer
 */
 public final class TwoLevelCache extends AbstractFreeNotificationCache implements WindowObserver {

    /**
     * A static constructor for TwoLevelCache, which avoids passing "this" in the
     * cache constructor so it can subscribe to window free notifications from the
     * primary cache.
     *
     * @param primaryCache The primary cache.
     * @param secondaryCache The secondary cache.
     * @return A TwoLevelCache using the primary and secondary caches.
     * @throws IllegalArgumentException if either of the caches are null.
     */
    public static TwoLevelCache create(final WindowCache primaryCache, final WindowCache secondaryCache) {
        ArgUtils.checkNullObject(primaryCache, "primaryCache");
        ArgUtils.checkNullObject(secondaryCache, "secondaryCache");
        final TwoLevelCache twoLevels = new TwoLevelCache(primaryCache, secondaryCache);
        primaryCache.subscribe(twoLevels);
        secondaryCache.subscribe(twoLevels);
        return twoLevels;
    }
    
    private final WindowCache primaryCache;
    private final WindowCache secondaryCache;

    private TwoLevelCache(final WindowCache primaryCache, final WindowCache secondaryCache) {
        this.primaryCache = primaryCache;
        this.secondaryCache = secondaryCache;
    }

    @Override
    public Window getWindow(final long position) throws IOException {
        Window window = primaryCache.getWindow(position);
        if (window == null) {
            window = secondaryCache.getWindow(position);
            if (window != null) {
                addWindow(window);
            }
        }
        return window;
    }

    @Override
    public void addWindow(final Window window) throws IOException {
        primaryCache.addWindow(window);
    }

    @Override
    public int read(final long windowPos, final int offset,
                    final byte[] readInto, final int readIntoPos) throws IOException {
        int bytesRead = primaryCache.read(windowPos, offset, readInto, readIntoPos);
        if (bytesRead == 0) {
            bytesRead = secondaryCache.read(windowPos, offset, readInto, readIntoPos);
        }
        return bytesRead;
    }

    /**
     * Clears both the primary and secondary caches, using whatever 
     * mechanisms they use to clear themselves.
     */
    @Override
    public void clear() throws IOException {
        try {
            primaryCache.clear();
        } finally {
            secondaryCache.clear();
        }
    }

    /**
     * Implementation of the {@link WindowObserver} method to receive 
     * notification that a Window is freed from a cache.
     * <p>
     * If a {@link net.byteseek.io.reader.windows.Window} leaves the primary
     * cache, then it is automatically added to the secondary cache.
     * If it leaves the secondary cache, then any observer of this cache
     * is notified that the Window is no longer cached at all by this cache.
     * 
     * @param window The Window which is leaving either the primary or secondary cache.
     * @param fromCache The WindowCache from which the Window is leaving.
     */
    @SuppressWarnings("ObjectEquality")
    @Override
    public void windowFree(final Window window, final WindowCache fromCache) throws IOException {
        if (fromCache == primaryCache) {
            secondaryCache.addWindow(window);
        } else if (fromCache == secondaryCache) {
            notifyWindowFree(window, fromCache);
        }
    }
    
    /**
     * Returns the primary cache used by this TwoLevelCache.
     * 
     * @return WindowCache The primary cache used by this TwoLevelCache.
     */
    public WindowCache getPrimaryCache() {
        return primaryCache;
    }

    /**
     * Returns the secondary cache used by this TwoLevelCache.
     * 
     * @return WindowCache the secondary cache used by this TwoLevelCache.
     */
    public WindowCache getSecondaryCache() {
        return secondaryCache;
    }
    
	@Override
	public String toString() {
		return getClass().getSimpleName() + "(primary cache: " + primaryCache +
											", secondary cache: " + secondaryCache + ')';
	}
    
}
