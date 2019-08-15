/*
 * Copyright Matt Palmer 2009-2017, All rights reserved.
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

package net.byteseek.matcher.sequence;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import net.byteseek.utils.ByteUtils;
import net.byteseek.io.reader.windows.Window;
import net.byteseek.io.reader.WindowReader;
import net.byteseek.matcher.bytes.ByteMatcher;
import net.byteseek.matcher.bytes.OneByteMatcher;
import net.byteseek.utils.ArgUtils;

/**
 * An immutable class which matches a sequence of bytes backed by a byte array.
 * <p>
 * The internal byte array is always constructed to be immutable.
 * It can be shared with other immutable SequenceMatchers, constructed from an existing ByteSequenceMatcher.
 * Different views over the original byte array can be quickly constructed, such 
 * as subsequences, or the reverse order of the bytes. 
 *
 * @author Matt Palmer
 */
public final class ByteSequenceMatcher extends AbstractSequenceMatcher {

    private final int hashCode;
    private final byte[] byteArray;
    private final int startArrayIndex; // the position to start at (an inclusive value)
    private final int endArrayIndex;   // one past the actual end position (an exclusive value)

    
    /****************
     * Constructors *
     ***************/

    /**
     * Constructs an immutable ByteSequenceMatcher from an array of bytes, which 
     * can be passed in directly as an array of bytes, or specified as a comma-separated list of bytes.
     * The array of bytes passed in is cloned to avoid mutability and concurrency issues.
     * 
     * @param bytes The array of bytes to match.
     * @throws IllegalArgumentException if the array of bytes passed in is null or empty.
     */
    public ByteSequenceMatcher(final byte...bytes) {
        ArgUtils.checkNullOrEmptyByteArray(bytes);
        this.byteArray = bytes.clone(); // avoid mutability issues - clone byte array.
        this.startArrayIndex = 0;
        this.endArrayIndex = byteArray.length;
        this.hashCode = calculateHash();
    }

    
    /**
     * Constructor creating an immutable ByteSequenceMatcher from a new byte array created
     * from the source to fit the start and end indexes supplied.  
     * A new array will be created even if the new array would be identical to the array passed in.
     * <p>
     * If you want to construct a ByteSequenceMatcher which shares the underlying byte
     * arrays, then use the copy constructor passing in an existing ByteSequenceMatcher: 
     * {@link #ByteSequenceMatcher(ByteSequenceMatcher, int, int)}. 
     *  
     * @param source A byte array containing a subsequence of bytes to use.
     * @param startIndex The start position of the source to begin from, inclusive.
     * @param endIndex The end position of the source, exclusive.
     * @throws IllegalArgumentException If the source is null or empty or the number of repeats is less than one.
     * @throws IndexOutOfBoundsException if the the start or end index are out of bounds, 
     */
    public ByteSequenceMatcher(final byte[] source, final int startIndex, final int endIndex) {
    	this(1, source, startIndex, endIndex);
    }
    

    /**
     * Constructor creating an immutable ByteSequenceMatcher from a new byte array created
     * to fit the start and end indexes supplied, repeated a number of times.
     * A new array will be created even if the new array would be identical to the array passed in.
     * 
     * @param numberOfRepeats The number of times to repeat the subsequence.
     * @param source A byte array containing a subsequence of bytes to repeat a number of times.
     * @param startIndex The start position of the source to begin from, inclusive.
     * @param endIndex The end position of the source, exclusive.
     * 
     * @throws IllegalArgumentException If the source is null or empty or the number of repeats is less than one.
     * @throws IndexOutOfBoundsException if the start or end index are out of bounds.
     */
    public ByteSequenceMatcher(final int numberOfRepeats, 
                               final byte[] source, final int startIndex,
                               final int endIndex) {
        ArgUtils.checkNullOrEmptyByteArray(source);
        ArgUtils.checkIndexOutOfBounds(source.length, startIndex, endIndex);
        ArgUtils.checkPositiveInteger(numberOfRepeats, "numberOfRepeats");
        this.byteArray = ByteUtils.repeat(numberOfRepeats, source, startIndex, endIndex);
        this.startArrayIndex = 0;
        this.endArrayIndex = this.byteArray.length;
        this.hashCode = calculateHash();
    }    
                    

    /**
     * Copy constructor creating an immutable sub-sequence of a ByteSequenceMatcher passed in, 
     * and backed by the original byte array of the ByteSequenceMatcher passed in.
     * 
     * @param source The ByteSequenceMatcher to create a subsequence from.
     * @param startIndex The start position of the source to begin from.
     * @param endIndex The end position of the source, which is one greater than
     *                 the last position to match in the source array.
     * @throws IllegalArgumentException if the source is null
     * @throws IndexOutOfBoundsException if  the start index is
     *         greater or equal to the end index, the start index is greater or
     *         equal to the length of the source, or the end index is greater than
     *         the length of the source.
     */
    public ByteSequenceMatcher(final ByteSequenceMatcher source, 
                               final int startIndex, final int endIndex) {
        ArgUtils.checkNullObject(source);
        ArgUtils.checkIndexOutOfBounds(source.length(), startIndex, endIndex);
        this.byteArray = source.byteArray;
        this.startArrayIndex = source.startArrayIndex + startIndex;
        this.endArrayIndex = source.startArrayIndex + endIndex;
        this.hashCode = calculateHash();
    }
    
    
    /**
     * A constructor which creates a ByteSequenceMatcher matching the 
     * array contained by a ReverseByteArrayMatcher, but now matching forwards.
     * 
     * @param toReverse The ReverseByteArrayMatcher to construct this ByteSequenceMatcher from.
     * @throws IllegalArgumentException if a null ReverseByteArrayMatcher is passed in.
     */
    public ByteSequenceMatcher(final ReverseByteArrayMatcher toReverse) {
        ArgUtils.checkNullObject(toReverse);
        this.byteArray= toReverse.byteArray;
        this.startArrayIndex = toReverse.startArrayIndex;
        this.endArrayIndex = toReverse.endArrayIndex;
        this.hashCode = calculateHash();
    }
    
    
    /**
     * Constructs an immutable byte sequence matcher from a list of other
     * ByteArrayMatchers.  The final sequence to match is the sequence of
     * bytes defined by joining all the bytes in the other ByteSequenceMatcher's
     * together in the order they appear in the list.<p>
     * A new byte array will be created to hold this sequence of bytes.  
     * 
     *
     * @param matchers The list of ByteArrayMatchers to join.
     * @throws IllegalArgumentException if the matcher list is null or empty, or
     *         any of the ByteArrayMatchers in the list are null.
     */
    public ByteSequenceMatcher(final List<ByteSequenceMatcher> matchers) {
    	ArgUtils.checkNullOrEmptyCollectionNoNullElements(matchers);
        int totalLength = 0;
        for (final ByteSequenceMatcher matcher : matchers) {
            totalLength += matcher.endArrayIndex;
        }
        this.byteArray = new byte[totalLength];
        int position = 0;
        for (final ByteSequenceMatcher matcher : matchers) {
            System.arraycopy(matcher.byteArray, 0, this.byteArray, position, matcher.endArrayIndex);
            position += matcher.endArrayIndex;
        }
        this.startArrayIndex = 0;
        this.endArrayIndex = totalLength;
        this.hashCode = calculateHash();
    }

    /**
     * Constructs an immutable ByteSequenceMatcher from another SequenceMatcher.
     * <p>
     * All the positions in the sequence matcher passed in must match only a single byte,
     * or it is impossible to create a ByteSequenceMatcher from it.
     *
     * @param matcher The matcher to construct the ByteSequenceMatcher from.
     * @throws IllegalArgumentException if the matcher is null, or it contains a ByteMatcher which matches
     *         more than one byte value.
     */
    public ByteSequenceMatcher(final SequenceMatcher matcher) {
        ArgUtils.checkNullObject(matcher);
        final int finalLength = matcher.length();
        this.byteArray = new byte[finalLength];
        for (int matcherIndex = 0; matcherIndex < finalLength; matcherIndex++) {
            final ByteMatcher byteMatcher = matcher.getMatcherForPosition(matcherIndex);
            final int numberOfMatcherBytes = byteMatcher.getNumberOfMatchingBytes();
            if (numberOfMatcherBytes != 1) {
                throw new IllegalArgumentException("The matcher passed in contains a matcher at position " + matcherIndex +
                                                   " which matches more than one byte value: " + byteMatcher);
            }
            this.byteArray[matcherIndex] = byteMatcher.getMatchingBytes()[0];
        }
        this.startArrayIndex = 0;
        this.endArrayIndex = finalLength;
        this.hashCode = calculateHash();
    }

    /**
     * Constructs an immutable byte sequence matcher from a repeated byte.
     *
     * @param byteValue The byte value to repeat.
     * @param numberOfBytes The number of bytes to repeat.
     * @throws IllegalArgumentException If the number of bytes is less than one.
     */
    public ByteSequenceMatcher(final byte byteValue, final int numberOfBytes) {
        ArgUtils.checkPositiveInteger(numberOfBytes);
        this.byteArray = new byte[numberOfBytes];
        Arrays.fill(this.byteArray, byteValue);
        this.startArrayIndex = 0;
        this.endArrayIndex = numberOfBytes;
        this.hashCode = calculateHash();
    }


    /**
     * Constructs an immutable byte sequence matcher from a single byte.
     *
     * @param byteValue The byte to match.
     */
    public ByteSequenceMatcher(final byte byteValue) {
        this(byteValue, 1);
    }

    
    
    /**
     * Constructs an immutable ByteSequenceMatcher from a string, encoding the
     * bytes of the string using the system default Charset.
     * 
     * @param string The string whose bytes will be matched.
     * @throws IllegalArgumentException if the string is null or empty.
     */
    public ByteSequenceMatcher(final String string) {
        this(string, Charset.defaultCharset());
    }
    

    /**
     * Constructs a ByteSequenceMatcher from a string and a Charset to use
     * to encode the bytes in the string.
     * 
     * @param string The string whose bytes will be matched
     * @param charset The Charset to encode the strings bytes in.
     * @throws IllegalArgumentException if the string is null or empty, or the
     *         Charset is null.
     */
    public ByteSequenceMatcher(final String string, final Charset charset) {
        ArgUtils.checkNullOrEmptyString(string, "string");
        ArgUtils.checkNullObject(charset, "charset");
        this.byteArray = string.getBytes(charset);
        this.startArrayIndex = 0;
        this.endArrayIndex = byteArray.length;
        this.hashCode = calculateHash();
    }
    
    
    /******************
     * Public methods *
     ******************/
    
    /**
     * {@inheritDoc}
     * 
     * @throws NullPointerException if the WindowReader is null.
     */
    @Override 
    public boolean matches(final WindowReader reader, final long matchPosition)
            throws IOException {
        final byte[] matchArray = byteArray;          
        final int matchStart = startArrayIndex;
        final int matchEnd = endArrayIndex;
        final int matchLength = matchEnd - matchStart;
        Window window = reader.getWindow(matchPosition);
        int matchPos = matchStart;
        int bytesMatchedSoFar = 0;
        while (window != null) {
            final byte[] source = window.getArray();    
            final int offset = reader.getWindowOffset(matchPosition + bytesMatchedSoFar);
            final int finalWindowIndex = window.length();
            final int finalMatchIndex = offset + matchLength - bytesMatchedSoFar;
            final int sourceEnd = finalWindowIndex < finalMatchIndex?
                                  finalWindowIndex : finalMatchIndex;
            for (int sourcePos = offset; sourcePos < sourceEnd; sourcePos++) {
                if (source[sourcePos] != matchArray[matchPos++]) {
                    return false;
                }
            }
            if (matchPos >= matchEnd) {
                return true;
            }
            bytesMatchedSoFar = matchPos - matchStart;
            window = reader.getWindow(matchPosition + bytesMatchedSoFar);
        }
        return false;
    }


    /**
     * {@inheritDoc}
     * 
     * @throws NullPointerException if the byte array passed in is null.
     */
    @Override
    public boolean matches(final byte[] bytes, final int matchPosition) {
        if (matchPosition + endArrayIndex - startArrayIndex <= bytes.length && matchPosition >= 0) {
            final byte[] matchArray = byteArray;
            final int endingIndex = endArrayIndex;
            int position = matchPosition;            
            for (int matchIndex = startArrayIndex; matchIndex < endingIndex; matchIndex++) {
                if (matchArray[matchIndex] != bytes[position++]) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }    

    
    /**
     * {@inheritDoc}
     * 
     * @throws NullPointerException if the byte array passed in is null.
     */
    @Override
    public boolean matchesNoBoundsCheck(final byte[] bytes, final int matchPosition) {
        int position = matchPosition;
        final byte[] matchArray = byteArray;   
        final int endingIndex = endArrayIndex;
        for (int matchIndex = startArrayIndex; matchIndex < endingIndex; matchIndex++) {
            if (matchArray[matchIndex] != bytes[position++]) {
                return false;
            }
        }
        return true;
    }
    
    
    /**
     * {@inheritDoc}
     */
    @Override
    public int length() {
        return endArrayIndex - startArrayIndex;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof ByteSequenceMatcher) {
            final ByteSequenceMatcher other = (ByteSequenceMatcher) obj;
            if (hashCode == other.hashCode && length() == other.length()) {
                int otherIndex = other.startArrayIndex;
                for (int thisIndex = startArrayIndex; thisIndex < endArrayIndex; thisIndex++) {
                    if (byteArray[thisIndex] != other.byteArray[otherIndex++]) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }


    /**
     * Returns a string representation of this matcher.  The format is subject
     * to change, but it will generally return the name of the matching class
     * and a regular expression defining the bytes matched by the matcher.
     * 
     * @return A string representing this matcher.
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + toRegularExpression(true) + ')';
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public String toRegularExpression(final boolean prettyPrint) {
        return ByteUtils.bytesToString(prettyPrint, byteArray, startArrayIndex, endArrayIndex);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public ByteMatcher getMatcherForPosition(final int position) {
    	ArgUtils.checkIndexOutOfBounds(length(), position);
        return OneByteMatcher.valueOf(byteArray[position + startArrayIndex]);
    }

    @Override
    public int getNumBytesAtPosition(int position) {
        ArgUtils.checkIndexOutOfBounds(length(), position);
        return 1;
    }


    /**
     * {@inheritDoc}
     */
    @Override    
    public SequenceMatcher reverse() {
        return new ReverseByteArrayMatcher(this);
    }

    
    /**
     * {@inheritDoc}
     */    
    @Override
    public SequenceMatcher subsequence(final int beginIndex, final int endIndex) {
        ArgUtils.checkIndexOutOfBounds(length(), beginIndex, endIndex);
        final int subsequenceLength = endIndex - beginIndex;
        if (subsequenceLength == 1) {
            return OneByteMatcher.valueOf(byteArray[startArrayIndex + beginIndex]);
        }
        if (subsequenceLength == length()) {
            return this;
        }
        return new ByteSequenceMatcher(this, beginIndex, endIndex);
    }
    
    
    /**
     * {@inheritDoc}
     */  
    @Override
    public SequenceMatcher subsequence(final int beginIndex) {
        return subsequence(beginIndex, length());
    }    

    
    /**
     * {@inheritDoc}
     */ 
    @Override
    public SequenceMatcher repeat(final int numberOfRepeats) {
        ArgUtils.checkPositiveInteger(numberOfRepeats);
        if (numberOfRepeats == 1) {
            return this;
        }
        return new ByteSequenceMatcher(numberOfRepeats, byteArray, startArrayIndex, endArrayIndex);
    }
    

	@Override
	public Iterator<ByteMatcher> iterator() {
		return new ByteMatcherIterator();
	}
	
	
	private final class ByteMatcherIterator implements Iterator<ByteMatcher> {

		int position = startArrayIndex;
		
		@Override
		public boolean hasNext() {
			return position < endArrayIndex;
		}

		@Override
		public ByteMatcher next() {
			if (hasNext()) {
				return OneByteMatcher.valueOf(byteArray[position++]);
			}
			throw new NoSuchElementException();
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException("Byte matchers cannot be removed from a ByteSequenceMatcher");
		}
		
	}

    private int calculateHash() {
        long hash = 31;
        for (int i = startArrayIndex; i < endArrayIndex; i++) {
            hash = hash * byteArray[i];
        }
        return (int) hash;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //                                ReverseByteArrayMatcher                 //        
    ////////////////////////////////////////////////////////////////////////////
    
    
    /**
     * A ReverseByteArrayMatcher is a matcher which matches the reverse order of bytes in a byte array.
     * 
     */
    public static final class ReverseByteArrayMatcher extends AbstractSequenceMatcher {

         private final int hashCode;
         private final byte[] byteArray;
         private final int startArrayIndex; // the position to start in the array (inclusive)
         private final int endArrayIndex;   // one past the last position in the array (exclusive)
         
         
         /**
          * Constructs a ReverseByteArrayMatcher from an original ByteSequenceMatcher.
          * 
          * @param toReverse The ByteSequenceMatcher to construct a ReverseByteArrayMatcher from.
          * @throws IllegalArgumentException if the ByteSequenceMatcher is null.
          */
         public ReverseByteArrayMatcher(final ByteSequenceMatcher toReverse) {
             ArgUtils.checkNullObject(toReverse);
             this.byteArray = toReverse.byteArray;
             this.startArrayIndex = toReverse.startArrayIndex;
             this.endArrayIndex   = toReverse.endArrayIndex;
             this.hashCode        = toReverse.hashCode();
         }
         
         /**
          * Constructs a ReverseByteArrayMatcher directly from a byte array.  The byte array
          * is cloned, making the ReverseByteArrayMatcher immutable.
          * 
          * @param bytes The array to clone and construct a ReverseByteArrayMatcher from.
          * @throws IllegalArgumentException if the byte array is null or empty.
          */
         public ReverseByteArrayMatcher(final byte... bytes) {
        	 ArgUtils.checkNullOrEmptyByteArray(bytes);
             this.byteArray = bytes.clone();
             this.startArrayIndex = 0;
             this.endArrayIndex = bytes.length;
             this.hashCode = calculateHash();
         }

        /**
         * Copy constructor creating an immutable sub-sequence of another ReverseByteArrayMatcher, 
         * backed by the original byte array, but otherwise behaving as if the array
         * had been reversed.  In particular, start indexes and end indexes should be
         * interpreted with that in mind - they reference the byte array as if it
         * was actually reversed.  Translation to the underlying byte array indexes
         * is done automatically and transparently.
         * 
         * @param source The ByteSequenceMatcher to create a subsequence from.
         * @param startIndex The start position of the source to begin from, inclusive.
         * @param endIndex The end position of the source, exclusive.
         * @throws IllegalArgumentException if the source is null,
         * @throws IndexOutOfBoundsException if the start or end index are out of bounds.
         */
        public ReverseByteArrayMatcher(final ReverseByteArrayMatcher source, 
                                       final int startIndex, final int endIndex) {
            ArgUtils.checkNullObject(source);
            final int sourceLength = source.length();
            ArgUtils.checkIndexOutOfBounds(sourceLength, startIndex, endIndex);
            this.byteArray       = source.byteArray;
            this.startArrayIndex = source.startArrayIndex + sourceLength - endIndex;
            this.endArrayIndex   = source.endArrayIndex - startIndex;
            this.hashCode = calculateHash();
        }
               
        
        /**
         * Constructs a ReverseByteArrayMatcher from a source byte array, a start index
         * and an end index, repeated a number of times.
         * <p>
         * Note that the start and end indexes are specified on the source array positions
         * as they actually exist; not the reversed bytes which the matcher will eventually match.
         * 
         * @param numberOfRepeats The number of times to repeat the source array bytes.
         * @param source The source array to construct a ReverseByteArrayMatcher from.
         * @param startIndex The first position in the source array to repeat from, inclusive.
         * @param endIndex The endIndex in the source array to repeat up to, exclusive.
         * 
         * @throws IllegalArgumentException if the source is null or empty, or the number of repeats is less than one.
         * @throws IndexOutOfBoundsException if the startIndex or endIndex are out of bounds.
         */ 
        public ReverseByteArrayMatcher(final int numberOfRepeats,
                              final byte[] source, final int startIndex,
                              final int endIndex) {
            ArgUtils.checkNullOrEmptyByteArray(source);
            ArgUtils.checkIndexOutOfBounds(source.length, startIndex, endIndex);
            ArgUtils.checkPositiveInteger(numberOfRepeats, "numberOfRepeats");
            this.byteArray = ByteUtils.repeat(numberOfRepeats, source, startIndex, endIndex);
            this.startArrayIndex = 0;
            this.endArrayIndex = this.byteArray.length;
            this.hashCode = calculateHash();
        }

        @Override
        public boolean matches(final WindowReader reader, final long matchPosition)
                throws IOException {
            final int matchStart = startArrayIndex;
            final int matchLength = endArrayIndex - startArrayIndex;
            final int matchEnd = endArrayIndex - 1;
            final byte[] matchArray = byteArray;          
            Window window = reader.getWindow(matchPosition);
            int matchPos = matchEnd;
            int bytesMatchedSoFar = 0;
            while (window != null) {
                final byte[] source = window.getArray();            
                final int offset = reader.getWindowOffset(matchPosition + bytesMatchedSoFar);
                final int finalWindowIndex = window.length();
                final int finalMatchIndex = offset + matchLength - bytesMatchedSoFar;
                final int sourceEnd = finalWindowIndex < finalMatchIndex?
                                      finalWindowIndex : finalMatchIndex;
                for (int sourcePos = offset; sourcePos < sourceEnd; sourcePos++) {
                    if (source[sourcePos] != matchArray[matchPos--]) {
                        return false;
                    }
                }
                if (matchPos < matchStart) {
                    return true;
                }
                bytesMatchedSoFar = matchEnd - matchPos;
                window = reader.getWindow(matchPosition + bytesMatchedSoFar);
            }
            return false;
        }

        @Override
        public boolean matches(final byte[] bytes, final int matchPosition) {
            if (matchPosition + length() <= bytes.length && matchPosition >= 0) {
                final byte[] matchArray = byteArray;
                final int endingIndex = startArrayIndex;
                int position = matchPosition;            
                for (int matchIndex = endArrayIndex - 1; matchIndex >= endingIndex; matchIndex--) {
                    if (matchArray[matchIndex] != bytes[position++]) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }    

        @Override
        public boolean matchesNoBoundsCheck(final byte[] bytes, final int matchPosition) {
            int position = matchPosition;
            final byte[] matchArray = byteArray;   
            final int endingIndex = startArrayIndex;
            for (int matchIndex = endArrayIndex - 1; matchIndex >= endingIndex; matchIndex--) {
                if (matchArray[matchIndex] != bytes[position++]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int length() {
            return endArrayIndex - startArrayIndex;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj instanceof ReverseByteArrayMatcher) {
                final ReverseByteArrayMatcher other = (ReverseByteArrayMatcher) obj;
                if (hashCode == other.hashCode && length() == other.length()) {
                    int otherIndex = other.startArrayIndex;
                    for (int thisIndex = startArrayIndex; thisIndex < endArrayIndex; thisIndex++) {
                        if (byteArray[thisIndex] != other.byteArray[otherIndex++]) {
                            return false;
                        }
                    }
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toRegularExpression(final boolean prettyPrint) {
            //TODO: can we have a reverseBytesToString method instead...?
            //       current method creates a new byte array just to print the bytes out.
            return ByteUtils.bytesToString(prettyPrint, 
            							   ByteUtils.reverseArraySubsequence(byteArray, startArrayIndex, endArrayIndex));
        }

        @Override
        public ByteMatcher getMatcherForPosition(final int position) {
            ArgUtils.checkIndexOutOfBounds(length(), position);
            return OneByteMatcher.valueOf(byteArray[endArrayIndex - 1 - position]);
        }

        @Override
        public int getNumBytesAtPosition(final int position) {
            ArgUtils.checkIndexOutOfBounds(length(), position);
            return 1;
        }

        @Override    
        public SequenceMatcher reverse() {
            return new ByteSequenceMatcher(this);
        }

        @Override
        public SequenceMatcher subsequence(final int beginIndex, final int endIndex) {
            final int length = length();
            ArgUtils.checkIndexOutOfBounds(length, beginIndex, endIndex);
            final int subsequenceLength = endIndex - beginIndex;
            if (subsequenceLength == length) {
                return this;
            }
            if (subsequenceLength == 1) {
                return OneByteMatcher.valueOf(byteArray[this.endArrayIndex - beginIndex - 1]);
            }
            return new ReverseByteArrayMatcher(this, beginIndex, endIndex);
        }

        @Override
        public SequenceMatcher subsequence(final int beginIndex) {
            return subsequence(beginIndex, length());
        }

        @Override
        public SequenceMatcher repeat(final int numberOfRepeats) {
            ArgUtils.checkPositiveInteger(numberOfRepeats);
            if (numberOfRepeats == 1) {
                return this;
            }
            return new ReverseByteArrayMatcher(numberOfRepeats, byteArray, startArrayIndex, endArrayIndex);
        }

    	@Override
    	public Iterator<ByteMatcher> iterator() {
    		return new ReverseByteMatcherIterator();
    	}

        private int calculateHash() {
            long hash = 31;
            for (int i = startArrayIndex; i < endArrayIndex; i++) {
                hash = hash * byteArray[i];
            }
            return (int) hash;
        }
    	
    	private final class ReverseByteMatcherIterator implements Iterator<ByteMatcher> {

    		int position = endArrayIndex;
    		
    		@Override
    		public boolean hasNext() {
    			return position > startArrayIndex;
    		}

    		@Override
    		public ByteMatcher next() {
    			if (hasNext()) {
    				return OneByteMatcher.valueOf(byteArray[--position]);
    			}
    			throw new NoSuchElementException();
    		}

    		@Override
    		public void remove() {
    			throw new UnsupportedOperationException("Byte matchers cannot be removed from a ReverseByteSequenceMatcher");
    		}
    		
    	}
         
    }

    
}
