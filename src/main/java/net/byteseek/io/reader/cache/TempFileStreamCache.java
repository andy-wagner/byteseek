/*
 * Copyright Matt Palmer 2018, All rights reserved.
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

import net.byteseek.io.IOUtils;
import net.byteseek.io.TempFileNotDeletedException;
import net.byteseek.io.reader.cache.AbstractFreeNotificationCache;
import net.byteseek.io.reader.cache.WindowCache;
import net.byteseek.io.reader.windows.SoftWindow;
import net.byteseek.io.reader.windows.SoftWindowRecovery;
import net.byteseek.io.reader.windows.Window;
import net.byteseek.io.reader.windows.WindowMissingException;
import net.byteseek.utils.ArgUtils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

/**
 * A {@link WindowCache} which stores {@link Window} objects into a temporary file for later retrieval.
 * It assumes that Windows are added to it sequentially as if they are read from a stream.
 * <p>
 * Windows can be addded from any position in the stream to begin with.  The first window that is added
 * sets the initial position for windows in this cache, but any further windows added must follow on from
 * the intial one sequentially.  Knowing the fixed window size and that windows are added sequentially means
 * that this cache does not have to maintain a map between temporary file position and window position, as this
 * can be calculated as needed.
 * <p>
 * A temporary file is only created if a Window is added to the cache, and it is
 * deleted when the cache is cleared.
 * <p>
 * This cache also implements SoftWindowRecovery, which allows window arrays to be freed in low memory conditions
 * but subsequently re-loaded from the temporary file if a Window array is requested again.
 *
 * @author Matt Palmer
 */
public final class TempFileStreamCache extends AbstractFreeNotificationCache implements SoftWindowRecovery {

    private final int windowSize;
    private final File tempDir;
    private File tempFile;
    private RandomAccessFile file;
    private long startOffset;
    private long length;

    /**
     * Constructs a TempFileCache given a windowSize
     * @param windowSize The size of the windows the cache will use.
     */
    public TempFileStreamCache(final int windowSize) {
        this(null, windowSize);
    }

    /**
     * Constructs a TempFileCache which creates temporary files in the directory specified.
     * If the file is null, then temporary files will be created in the default temp directory.
     *
     * @param tempDir The directory to create temporary files in.
     * @param windowSize The size of the windows the cache will use.
     * @throws IllegalArgumentException if the tempdir supplied is not a directory, or the windowSize is not a postive integer.
     */
    public TempFileStreamCache(final File tempDir, final int windowSize) {
        ArgUtils.checkPositiveInteger(windowSize, "windowSize");
        this.tempDir = tempDir;
        this.windowSize = windowSize;
        if (tempDir != null && !tempDir.isDirectory()) {
            throw new IllegalArgumentException("The temp dir file supplied is not a directory: " + tempDir.getAbsolutePath());
        }
    }

    @Override
    public Window getWindow(final long position) throws IOException {
        // If we're within the file written so far and the position is valid for a fixed window size:
        final long filePos = position - startOffset;
        if (filePos >= 0 && filePos < length && position % windowSize == 0) {

            // Read and return a window for that position:
            final byte[] array = new byte[windowSize];
            final int readLength = IOUtils.readBytes(file, filePos, array);
            //TODO: always softwindows?
            return new SoftWindow(array, position, readLength, this);
        }
        return null;
    }

    @Override
    public void addWindow(final Window window) throws IOException {
        // a newly added window must always be at the end of the previous ones.
        final long winPos = window.getWindowPosition();
        final long filePos = winPos - startOffset;
        if (filePos != length) {
            throw new IOException("Window not sequentially added to the stream cache " + this +
                    ". The valid position for the next window is " + (startOffset + length) +
                    ", but the window " + window + " to be added had position " + winPos + '.');
        }
        // If we don't yet have a temp file, create one and set the start offset.
        if (createFileIfNotExists()) {
            startOffset = winPos;
        }
        // Add the window.
        file.seek(filePos);
        final int windowLength = window.length();
        file.write(window.getArray(), 0, windowLength);
        length += windowLength;
    }

    @Override
    public int read(final long windowPos, final int offset, final byte[] readInto, final int readIntoPos) throws IOException {
        int bytesRead = 0;
        final long filePos = windowPos + offset - startOffset;
        if (file != null && filePos < length) {
            final int bytesToRead = readInto.length - readIntoPos;
            bytesRead = IOUtils.readBytes(file, filePos, readInto, readIntoPos, bytesToRead);
        }
        return bytesRead;
    }

    @Override
    public int read(final long windowPos, final int offset, final ByteBuffer readInto) throws IOException {
        int bytesRead = 0;
        final long filePos = windowPos + offset - startOffset;
        if (file != null && filePos < length) {
            bytesRead = IOUtils.readBytes(file.getChannel(), filePos, readInto);
        }
        return bytesRead;
    }

    /**
     * Clears the map of Window positions to their position and size in the file,
     * and deletes the temporary file if it exists.
     */
    @Override
    public void clear() throws IOException {
        startOffset = 0;
        length      = 0;
        deleteFileIfExists();
    }

    /**
     * Returns the temporary file backing this cache object.
     *
     * @return File The temporary file backing this cache object, or null if it doesn't exist.
     */
    public File getTempFile() {
        return tempFile;
    }


    /**
     * Creates a temporary file if it doesn't already exist.
     * @return Whether a file was created or not.
     * @throws IOException If there was a problem creating the temporary file.
     */
    private boolean createFileIfNotExists() throws IOException {
        if (tempFile == null) {
            tempFile = tempDir == null? IOUtils.createTempFile()
                    : IOUtils.createTempFile(tempDir);
            file = new RandomAccessFile(tempFile, "rw");
            return true;
        }
        return false;
    }

    private void deleteFileIfExists() throws IOException {
        if (tempFile != null) {
            IOException fileCloseException = null;
            String      fileDetails = "";
            boolean tempFileDeleted;
            try {
                file.close();
            } catch (IOException ex) {
                fileCloseException = ex;
            } finally {
                file = null;
                tempFileDeleted = tempFile.delete();
                if (!tempFileDeleted) {
                    fileDetails = tempFile.getAbsolutePath();
                }
                tempFile = null;
            }
            if (fileCloseException != null || !tempFileDeleted) {
                throw tempFileDeleted? fileCloseException
                        : new TempFileNotDeletedException(fileDetails, fileCloseException);
            }
        }
    }

    @Override
    public byte[] reloadWindowBytes(final Window window) throws IOException {
        if (file == null) {
            throw new WindowMissingException("Cache temp file does not exist.");
        }
        final byte[] array = new byte[windowSize];
        IOUtils.readBytes(file, window.getWindowPosition(), array);
        return array;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(temp file: " + tempFile +
                "start offset: " + startOffset + " length: " + length + ')';
    }

}