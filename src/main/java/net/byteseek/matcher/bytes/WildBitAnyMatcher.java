/*
 * Copyright Matt Palmer 2017-19, All rights reserved.
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
package net.byteseek.matcher.bytes;

import net.byteseek.io.reader.WindowReader;
import net.byteseek.io.reader.windows.Window;
import net.byteseek.utils.ByteUtils;

import java.io.IOException;

/**
 * A byte matcher which matches any of the ones and zeros in a byte exactly, except for "don't care" bits.
 * A value to match is given, and also a wild mask.
 * Bits which are zero in the wild mask indicate those bits in the value we don't care about - the wild bits.
 * Bits which are one in the wild mask indicate those bits of which at least one must be the same as the value provided.
 * <p>
 * It is an invertible matcher, so you can also specify that it doesn't match the value (aside from the don't care bits).
 *
 * Created by matt on 01/07/17.
 */
public final class WildBitAnyMatcher extends InvertibleMatcher {

    private final byte noMatchValue;
    private final byte wildcardMask;

    /**
     * Constructs a WildBitAnyMatcher from a byte to match, along with a wildMask that specifies which bits
     * we don't care about in the byte to match, and which we do.  A zero bit in the wildMask means we don't
     * care about the value of that bit in the value, a one means we need at least one of the corresponding
     * value bits to match.
     *
     * @param value    The bits to match.
     * @param wildMask The bits we don't care about matching, zero meaning we don't care about that bit in the value.
     */
    public WildBitAnyMatcher(final byte value, final byte wildMask) {
        this(value, wildMask, false);
    }

    /**
     * Constructs a WildBitAnyMatcher from a byte to match, along with a wildMask that specifies which bits
     * we don't care about in the byte to match, and which we do.  A zero bit in the wildMask means we don't
     * care about the value of that bit in the value, a one means we need at least one of the corresponding
     * value bits to match.
     *
     * @param value    The bits to match.
     * @param wildMask The bits we don't care about matching, zero meaning we don't care about that bit in the value.
     * @param inverted Whether the matcher results are inverted or not.
     */
    public WildBitAnyMatcher(final byte value, final byte wildMask, final boolean inverted) {
        super(inverted);
        // The bitwise inverse of the value we specified is the only value we can't match (doesn't have any of the bits of the value).
        this.noMatchValue = (byte) ((~value) & wildMask);
        this.wildcardMask = wildMask;
    }

    @Override
    public boolean matches(final byte theByte) {
        // a wildcard mask of zero means we don't care about any bit values - matches everything.
        return wildcardMask == 0? !inverted : ((theByte & wildcardMask) != noMatchValue) ^ inverted;
    }

    @Override
    public boolean matchesNoBoundsCheck(final byte[] bytes, final int matchPosition) {
        // a wildcard mask of zero means we don't care about any bit values - matches everything.
        return wildcardMask == 0? !inverted : ((bytes[matchPosition] & wildcardMask) != noMatchValue) ^ inverted;
    }

    @Override
    public boolean matches(final WindowReader reader, final long matchPosition) throws IOException {
        final Window window = reader.getWindow(matchPosition);
        return window == null?       false
                : wildcardMask == 0? !inverted  // a wildcard mask of zero means we don't care about any bit values - matches everything.
                : ((window.getByte(reader.getWindowOffset(matchPosition)) & wildcardMask) != noMatchValue) ^ inverted;
    }

    @Override
    public boolean matches(final byte[] bytes, final int matchPosition) {
        return (matchPosition >= 0 && matchPosition < bytes.length) &&
                // a wildcard mask of zero means we don't care about any bit values - matches everything.
                (wildcardMask == 0? !inverted : ((bytes[matchPosition] & wildcardMask) != noMatchValue) ^ inverted);
    }

    @Override
    public byte[] getMatchingBytes() {
        final int numBytes = getNumberOfMatchingBytes();
        final byte[] matchingBytes = new byte[numBytes];
        for (int i = 0, matchPos = 0; matchPos < numBytes && i < 256; i++) {
            final byte possibleByte = (byte) i;
            if (matches(possibleByte)) {
                matchingBytes[matchPos++] = possibleByte;
            }
        }
        return matchingBytes;
    }

    @Override
    public int getNumberOfMatchingBytes() {
        final byte mask = wildcardMask;
        final int numBytesMatchingMask = mask == 0? 256 : 256 - (1 << ByteUtils.countUnsetBits(mask));
        return inverted? 256 - numBytesMatchingMask : numBytesMatchingMask;
        //TODO: check these calculations - not sure they are right.
    }

    @Override
    public String toRegularExpression(final boolean prettyPrint) {
        switch (wildcardMask) {
            case 0: {
                return inverted? "^~__" : "~__"; //TODO: inverted any matching is not legal syntax.  Should ^__ be illegal syntax too?
            }
            case -16: { // 0xF0 - first nibble of a hex byte:
                return inverted? String.format("^~%x_", ~(noMatchValue >>> 4) & 0x0F) :
                                 String.format("~%x_", ~(noMatchValue >>> 4) & 0x0F);
            }
            case 15: { // 0x0F - last nibble of a hex byte:
                return inverted? String.format("^~_%x", (~noMatchValue) & 0x0F) :
                                 String.format("~_%x", (~noMatchValue) & 0x0F);
            }
            default: { // some other bitmask - build a binary string from the value, putting _ where the bitmask is zero.
                final StringBuilder regex = new StringBuilder(12);
                if (inverted) regex.append('^');
                regex.append('~').append('0').append('i');
                final int matchValue = (byte) ~noMatchValue; // the actual value for any matching is the inverse of the no match value.
                for (int bitpos = 7; bitpos >= 0; bitpos--) {
                    final int bitposMask = 1 << bitpos;
                    if ((wildcardMask & bitposMask) == bitposMask) {
                        regex.append((matchValue & bitposMask) == bitposMask? '1' : '0');
                    } else {
                        regex.append('_');
                    }
                }
                return regex.toString();
            }
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + toRegularExpression(true) + ")";
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof WildBitAnyMatcher)) {
            return false;
        }
        final WildBitAnyMatcher other = (WildBitAnyMatcher) obj;
        return wildcardMask == other.wildcardMask &&
                noMatchValue == other.noMatchValue &&
                inverted == other.inverted;
    }

    @Override
    public int hashCode() {
        return ((wildcardMask & 0xFF) + 7) * // Avoid zeros in calculation (and negative numbers):
               ((noMatchValue & 0xFF) + 13) * // Avoid zeros in calculation (and negative numbers)
                (inverted? 43 : 31);
    }
}