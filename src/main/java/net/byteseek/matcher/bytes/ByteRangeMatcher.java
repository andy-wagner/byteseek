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

package net.byteseek.matcher.bytes;

import java.io.IOException;

import net.byteseek.utils.ByteUtils;
import net.byteseek.io.reader.windows.Window;
import net.byteseek.io.reader.WindowReader;
import net.byteseek.utils.ArgUtils;

/**
 * An immutable {@link ByteMatcher} which matches a range of bytes, 
 * or the bytes outside the range if inverted.
 * 
 * @author Matt Palmer
 *
 */
public final class ByteRangeMatcher extends InvertibleMatcher {

    private final int minByteValue; // use int as a byte is signed, but we need values from 0 to 255
    private final int maxByteValue; // use int as a byte is signed, but we need values from 0 to 255

    /**
     * Constructs an immutable {@link ByteMatcher} which matches a range of bytes.
     * <p>
     * If the minimum value is greater than the maximum value,
     * then the values are reversed, so the same range is matched.
     *
     * @param minValue The minimum value to match, inclusive.
     * @param maxValue The maximum value to match, inclusive.
     */
    public ByteRangeMatcher(final int minValue, final int maxValue) {
        this(minValue, maxValue, InvertibleMatcher.NOT_INVERTED);
    }

    /**
     * Constructs an immutable {@link ByteMatcher} which matches a range of bytes.
     * <p>
     * If the minimum value is greater than the maximum value, 
     * then the values are reversed, so the same range is matched.
     *
     * @param minValue The minimum value to match, inclusive.
     * @param maxValue The maximum value to match, inclusive.
     * @param inverted If true, the matcher matches values outside the range given.
     */
    public ByteRangeMatcher(final int minValue, final int maxValue, final boolean inverted) {
        super(inverted);
        // Preconditions - minValue & maxValue >= 0 and <= 255.  MinValue <= MaxValue
        ArgUtils.checkRangeInclusive(minValue, 0, 255, "minValue");
        ArgUtils.checkRangeInclusive(maxValue, 0, 255, "maxValue");
        if (minValue > maxValue) {
            minByteValue = maxValue;
            maxByteValue = minValue;
        } else {
            minByteValue = minValue;
            maxByteValue = maxValue;
        }
    }

    @Override
    public boolean matches(final WindowReader reader, final long matchPosition) throws IOException{
        final Window window = reader.getWindow(matchPosition);
        if (window == null) {
            return false;
        }

        final int byteValue = window.getByte(reader.getWindowOffset(matchPosition)) & 0xFF;
        final boolean insideRange = byteValue >= minByteValue && byteValue <= maxByteValue;
        return insideRange ^ inverted;
    }

    @Override
    public boolean matches(final byte[] bytes, final int matchPosition) {
        if (matchPosition >= 0 && matchPosition < bytes.length) {
            final int byteValue = bytes[matchPosition] & 0xFF;
            final boolean insideRange = byteValue >= minByteValue && byteValue <= maxByteValue;
            return insideRange ^ inverted;
        }
        return false;
    }    

    @Override
    public boolean matchesNoBoundsCheck(final byte[] bytes, final int matchPosition) {
        final int byteValue = bytes[matchPosition] & 0xFF;
        final boolean insideRange = byteValue >= minByteValue && byteValue <= maxByteValue;
        return insideRange ^ inverted;
    }    

    @Override
    public boolean matches(final byte theByte) {
        final int byteValue = theByte & 0xFF;
        final boolean insideRange = (byteValue >= minByteValue && byteValue <= maxByteValue);
        return insideRange ^ inverted;
    }

    @Override
    public String toRegularExpression(final boolean prettyPrint) {
        final StringBuilder regularExpression = new StringBuilder();
        if (inverted) {
            regularExpression.append('^');
        }
        final String minValue = ByteUtils.byteToString(prettyPrint, minByteValue);
        final String maxValue = ByteUtils.byteToString(prettyPrint, maxByteValue);
        regularExpression.append( String.format("%s-%s", minValue, maxValue ));
        return regularExpression.toString();
    }

    @Override
    public byte[] getMatchingBytes() {
        byte[] values = new byte[getNumberOfMatchingBytes()];
        if (inverted) {
            int byteIndex = 0;
            for (int value = 0; value < minByteValue; value++) {
                values[byteIndex++] = (byte) value;
            }
            for (int value = maxByteValue + 1; value < 256; value++) {
                values[byteIndex++] = (byte) value;
            }
        } else {
            int byteIndex = 0;
            for (int value = minByteValue; value <= maxByteValue; value++) {
                values[byteIndex++] = (byte) value;
            }
        }
        return values;
    }

    @Override
    public int getNumberOfMatchingBytes() {
        return inverted ? 255 - maxByteValue + minByteValue
                        : maxByteValue - minByteValue + 1;
    }

    @Override
    public int hashCode() {
        return minByteValue * maxByteValue * (inverted? 43 : 31);
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof ByteRangeMatcher)) {
            return false;
        }

        final ByteRangeMatcher other = (ByteRangeMatcher) obj;
        return minByteValue == other.minByteValue &&
               maxByteValue == other.maxByteValue &&
               inverted     == other.inverted;
    }
    
    @Override
    public String toString() {
    	return getClass().getSimpleName() + '(' + toRegularExpression(false) + ')';
    }

}
