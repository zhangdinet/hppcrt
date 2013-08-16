package com.carrotsearch.hppc;

/**
 * An Object pool of configurable and extensible size, from which 
 * pre-allocated objects can be borrowed, and released after use
 * by returning them to the pool.
 *
 * @param <E>
 */
public class ObjectPool<E> {

    /**
     * The {@link ObjectFactory} used for creating or re-initializing objects
     * of the pool.
     */
    protected ObjectFactory<E> factory;
    
    /**
     * Current number of available (free) objects currently in the pool.
     */
    protected int currentSize;
    
    /**
     * Capacity of the pool, i.e max number of Objects managed by this pool.
     */
    protected int capacity;
    
    
    /**
     * Controls the way more objects are produced when the pool is empty.
     */
    protected ArraySizingStrategy growthPolicy;
    
    /**
     * The low-level array holding the object instances.
     */
    protected E[] arrayPool;
    
    /**
     * Create a new pool of Objects E, constructed with objFactory, of size  initialSize
     * at startup, with growthStrategy as a policy when new objects need to be allocated.
     * @param objFactory
     * @param initialSize
     * @param growthPolicy
     */
    @SuppressWarnings("unchecked")
    public ObjectPool(ObjectFactory<E> objFactory, int initialSize, ArraySizingStrategy growthPolicy) {
          
        //set basic fields
        this.factory = objFactory;
        this.currentSize = initialSize;
        this.growthPolicy = growthPolicy;
        
        assert  this.factory != null;
        assert this.currentSize > 0;
        assert this.growthPolicy != null;
        
        this.capacity = this.currentSize;
        
        //Construct
        this.arrayPool = (E[]) new Object[this.capacity];
        //allocate
        for (int i = 0 ; i < arrayPool.length; i++) {
            
            this.arrayPool[i] = this.factory.create();
        }
    }
    
    
    /**
     * Borrow an Object from  the pool. If there 
     * is no more Objects, more of them are allocated using the {@link #growthPolicy} 
     * policy. The returned object is assured to be initialized with {@link #factory}.initialize(). 
     */
    @SuppressWarnings("unchecked")
    public E borrow() {
        
        E borrowedObject = null;
        
        //A) Check that there are enough objects, else allocate new ones following the policy
        if (this.currentSize <= 0) {
            
            //at least add 1 object...
            int newSize = this.growthPolicy.grow(this.capacity, this.capacity, 1);
            
            //reallocate if policy indeed authorized growth
            if (newSize > this.capacity) {
            
                Object[] newPoolArray = new Object[newSize];
                
                //construct and add the additional objects
                for (int i = 0; i < (newSize - this.capacity); i++) {
                    
                    newPoolArray[i] = this.factory.create();
                }
                
                //update structures
                this.currentSize = newSize - this.capacity;
                this.capacity = newSize;
                this.arrayPool = (E[]) newPoolArray;
            }
        }
        
        //B) extract object. currentSize could still be null if resize was indeed forbidden by policy,
        //in this case the pool returns a null pointer.
        if (this.currentSize > 0) {
            
            borrowedObject = this.arrayPool[this.currentSize - 1];
            
            //null the just extracted place in arrayPool, so there is no dangling reference in the pool
            //while the object is no longer "there". This is useful in case a given object is never actually returned
            //to the pool, so that it can be collected by GC after all. 
            this.arrayPool[this.currentSize - 1] = null;
            //initialize just before use, not before. Needed for closure-like contexts when
            //the context is essentially captured at borrow time.
            this.factory.initialize(borrowedObject);
        
            this.currentSize--;
        }
    
        return borrowedObject;
    }
    
    /**
     * Release this object E by putting it back to the pool.
     * Of course, using releasedObject after it has been released is a logical error,
     * since such object is supposed to be "freed". 
     * @param releasedObject
     */
    public void release(E releasedObject) {
        
        //only authorize if size < capacity.
        //this could happen if by mistake several instances of the same object has been
        //put back to the pool. It is a logical error, but we cannot check that anyway.
        if (releasedObject != null && this.currentSize < this.capacity) {
           
            this.arrayPool[this.currentSize] = releasedObject;
            this.currentSize++;
        }
    }
    
    /**
     * get the current number of available (free) objects in the pool.
     * @return
     */
    public int size() {
        
        return this.currentSize;
    }
    
    /**
     * get the capacity of the pool, i.e max number of Objects managed by this pool.
     */
    public int capacity() {
        
        return this.capacity;
    }

}
