/*
 * Copyright Matt Palmer 2013-19. All rights reserved.
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

package net.byteseek.utils.lazy;


import net.byteseek.utils.factory.ObjectFactory;

/**
 * This class creates objects using the dangerous double-check lazy initialisation pattern,
 * with synchronization on the second check, and no volatile references.
 * <p>
 * The object being created *must* be immutable, meaning it only has final fields.  In this case
 * only will the Java Memory Model ensure that all the fields of the object are published safely
 * to all threads that read it.
 * @see <a href="http://www.cs.umd.edu/~pugh/java/memoryModel/DoubleCheckedLocking.html">The "Double-Checked Locking is Broken" Declaration</a>
 * <p>
 * @param <T> The type of immutable object to instantiate lazily.
 * 
 * @author Matt Palmer
 */
public final class DoubleCheckImmutableLazyObject<T> implements LazyObject<T> {

    private final ObjectFactory<T> factory;
    private T object; // since the object is immutable, this field does not have to be volatile.

    /**
     * Constructs a DoubleCheckLazyObject with an object factory to create the 
     * immutable object lazily.
     * 
     * @param factory A factory which can create an instance of type T.
     */
    public DoubleCheckImmutableLazyObject(ObjectFactory<T> factory) {
    	this.factory = factory;
    }
    
   
    /**
     * Uses Double-Check lazy initialisation.  Only one instance will be created, no matter
     * how many threads call this method.  The object created must be immutable.
     * 
     * @return An object of type T.
     */
    @Override
    public final T get() {
        if (object == null) {
        	synchronized(this) {
        		if (object == null) {
        			object = factory.create();
        		}
        	}
        }
        return object;
    }

    @Override
    public boolean created() {
        return object != null;
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + (object == null? "{not yet created}" : object.toString()) + ")";
    }

}