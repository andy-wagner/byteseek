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

package net.byteseek.io;

import java.io.File;
import java.io.IOException;

import net.byteseek.io.reader.FileReader;
import net.byteseek.io.reader.windows.Window;
import net.byteseek.io.reader.WindowReader;

/**
 * Simple utilities to faciliate testing IO.
 *
 * @author matt
 */
public class Utilities {

    /**
     * Gets a file as a byte array given a path string.
     *
     * @param path The path and filename of the file
     * @return A byte array containing the file contents.
     * @throws IOException If something goes wrong
     */
    public static byte[] getByteArray(final String path) throws IOException {
        return getByteArray(new File(path));
    }
                
    
    /**
     * Returns a file as a byte array given a file object.
     *
     * @param file A file object which we want to get as a byte array.
     * @return A byte array containing the file contents.
     * @throws IOException If something goes wrong.
     */
    public static byte[] getByteArray(final File file) throws IOException {
        final WindowReader reader = new FileReader(file);
        final Window window = reader.getWindow(0);
        reader.close();
        return window.getArray();
    }
    
    
}
