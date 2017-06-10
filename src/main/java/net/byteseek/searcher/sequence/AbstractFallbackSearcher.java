/*
 * Copyright Matt Palmer 2017, All rights reserved.
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
package net.byteseek.searcher.sequence;

import net.byteseek.io.reader.WindowReader;
import net.byteseek.matcher.sequence.SequenceMatcher;
import net.byteseek.utils.factory.ObjectFactory;
import net.byteseek.utils.lazy.DoubleCheckImmutableLazyObject;
import net.byteseek.utils.lazy.LazyObject;

import java.io.IOException;

/**
 * An abstract searcher which allows for a fallback searcher to be used in place of the selected searcher.
 * It also allows for the search index size of the algorithm and the method to select that size to be chosen.
 *
 * <p>
 * Some algorithms cannot process certain types of pattern (e.g. very short ones when q-grams are used), so
 * these algorithms must fallback to another algorithm in these cases.  Since these algorithms are (so far)
 * also tunable in their memory usage, the fallback algorithm may also be used if there is insufficient memory available
 * which would lead to unacceptable performance.
 *
 * Created by matt on 03/06/17.
 */
public abstract class AbstractFallbackSearcher extends AbstractWindowSearcher<SequenceMatcher> {

     /**
     * A replacement searcher for sequences whose length is less than the qgram length, which this searcher cannot search for.
     * Also used as a fallback in case it is not possible to create a hash table which would give reasonable performance
     * (e.g. if the maximum table size isn't sufficient, or the pattern is pathological in some way).
     */
    protected final LazyObject<SequenceSearcher> fallbackSearcher;

    public AbstractFallbackSearcher(final SequenceMatcher sequence) {
        super(sequence);
        fallbackSearcher = new DoubleCheckImmutableLazyObject<SequenceSearcher>(new FallbackSearcherFactory());
    }

    @Override
    public void prepareForwards() {
        if (fallbackForwards()) {
            fallbackSearcher.get().prepareForwards();
        }
    }

    @Override
    public void prepareBackwards() {
        if (fallbackBackwards()) {
            fallbackSearcher.get().prepareBackwards();
        }
    }

    protected String getForwardSearchDescription(LazyObject<?> searchInfo) {
        return (searchInfo.created()? fallbackForwards()? fallbackSearcher.get() : searchInfo.get() : searchInfo).toString();
    }

    protected String getBackwardSearchDescription(LazyObject<?> searchInfo) {
        return (searchInfo.created()? fallbackBackwards()? fallbackSearcher.get() : searchInfo.get() : searchInfo).toString();
    }

    /**
     * Returns true if the fallback searcher should be used instead for forwards searches.
     * @return true if the fallback searcher should be used instead for forwards searches.
     */
    protected abstract boolean fallbackForwards();

    /**
     * Returns true if the fallback searcher should be used instead for backwards searches.
     * @return true if the fallback searcher should be used instead for backwards searches.
     */
    protected abstract boolean fallbackBackwards();

    /**
     * Implementation of the forwards sequence search algorithm in a byte array.
     *
     * @param bytes         The bytes to search.
     * @param fromPosition  The position to start searching from.
     * @param toPosition    The position to stop searching for a match.
     * @return              The position a match was found at, or a negative number if no match was found.
     */
    protected abstract int doSearchSequenceForwards(byte[] bytes, int fromPosition, int toPosition);

    /**
     * Implementation of the backwards sequence search algorithm in a byte array.
     *
     * @param bytes         The bytes to search.
     * @param fromPosition  The position to start searching from.
     * @param toPosition    The position to stop searching for a match.
     * @return              The position a match was found at, or a negative number if no match was found.
     */
    protected abstract int doSearchSequenceBackwards(byte[] bytes, int fromPosition, int toPosition);


    @Override
    public int searchSequenceForwards(byte[] bytes, int fromPosition, int toPosition) {
        return fallbackForwards()? fallbackSearcher.get().searchSequenceForwards(bytes, fromPosition, toPosition)
                                 :                      doSearchSequenceForwards(bytes, fromPosition, toPosition);
    }

    @Override
    public long searchSequenceForwards(final WindowReader reader, final long fromPosition, final long toPosition) throws IOException {
        return fallbackForwards()? fallbackSearcher.get().searchSequenceForwards(reader, fromPosition, toPosition)
                                 :                  super.searchSequenceForwards(reader, fromPosition, toPosition);
    }

    @Override
    public int searchSequenceBackwards(byte[] bytes, int fromPosition, int toPosition) {
        return fallbackBackwards() ? fallbackSearcher.get().searchSequenceBackwards(bytes, fromPosition, toPosition)
                                   :                      doSearchSequenceBackwards(bytes, fromPosition, toPosition);
    }

    @Override
    public long searchSequenceBackwards(final WindowReader reader, final long fromPosition, final long toPosition) throws IOException {
        return fallbackBackwards()? fallbackSearcher.get().searchSequenceBackwards(reader, fromPosition, toPosition)
                                  :                  super.searchSequenceBackwards(reader, fromPosition, toPosition);
    }

    @Override
    protected int getSequenceLength() {
        return sequence.length();
    }

    /*******************
     * Private classes *
     *******************/

    /**
     * A factory for a short sequence matcher searcher, to fill in for sequences with a length less than the Qgram length.
     * <p>
     * <b>Design Note</b>
     * <p>
     * This allows a developer to pass any valid pattern into this search algorithm without error.  The alternative is
     * to throw an IllegalArgumentException in the constructor if the pattern is too short, but this could easily
     * lead to errors in user applications, since patterns are commonly supplied by the user, not the programmer.
     * <p>
     * While this decision violates the principle that these search algorithms are primitives and should not make high
     * level decisions on behalf of the programmer, it seems the lesser of two evils to make it safe to use any search
     * algorithm with any valid pattern, even if occasionally you don't quite get the algorithm you thought you specified.
     * <p>
     * Given this, we choose to supply the fastest known algorithm for short patterns (ShiftOr),
     * rather than one which is more spiritually similar to this algorithm (e.g. the SignedHorspoolSearcher), or the
     * simplest possible algorithm (e.g. the SequenceMatcherSearcher).
     */
    private final class FallbackSearcherFactory implements ObjectFactory<SequenceSearcher> {

        @Override
        public SequenceSearcher create() {
            return new ShiftOrSearcher(sequence);  // the fastest searcher for short patterns, and largley ignores pattern complexity.
        }
    }

}