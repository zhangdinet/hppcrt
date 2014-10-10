package com.carrotsearch.hppcrt.maps;

import java.util.*;

import com.carrotsearch.hppcrt.*;
import com.carrotsearch.hppcrt.cursors.*;
import com.carrotsearch.hppcrt.predicates.*;
import com.carrotsearch.hppcrt.procedures.*;

/*! ${TemplateOptions.doNotGenerateKType("BOOLEAN")} !*/
/*! #set( $ROBIN_HOOD_FOR_GENERICS = true) !*/
/*! #set( $SINGLE_ARRAY_FOR_PRIMITIVES = true) !*/
/*! #set( $DEBUG = false) !*/
// If RH is defined, RobinHood Hashing is in effect
/*! #set( $RH = ($TemplateOptions.KTypeGeneric && $ROBIN_HOOD_FOR_GENERICS) ) !*/
//If SA is defined, no allocated array is used but instead default sentinel values
/*! #set( $SA = ($TemplateOptions.KTypePrimitive && $SINGLE_ARRAY_FOR_PRIMITIVES) ) !*/
/**
 * A hash map of <code>KType</code> to <code>VType</code>, implemented using open
 * addressing with linear probing for collision resolution.
 * 
 * <p>
 * The internal buffers of this implementation ({@link #keys}, {@link #values},
 * {@link #allocated}) are always allocated to the nearest size that is a power of two. When
 * the capacity exceeds the given load factor, the buffer size is doubled.
 * </p>
 *
#if ($TemplateOptions.AllGeneric)
 * <p>
 * A brief comparison of the API against the Java Collections framework:
 * </p>
 * <table class="nice" summary="Java Collections HashMap and HPPC ObjectObjectOpenHashMap, related methods.">
 * <caption>Java Collections HashMap and HPPC {@link ObjectObjectOpenHashMap}, related methods.</caption>
 * <thead>
 *     <tr class="odd">
 *         <th scope="col">{@linkplain HashMap java.util.HashMap}</th>
 *         <th scope="col">{@link ObjectObjectOpenHashMap}</th>
 *     </tr>
 * </thead>
 * <tbody>
 * <tr            ><td>V put(K)       </td><td>V put(K)      </td></tr>
 * <tr class="odd"><td>V get(K)       </td><td>V get(K)      </td></tr>
 * <tr            ><td>V remove(K)    </td><td>V remove(K)   </td></tr>
 * <tr class="odd"><td>size, clear,
 *                     isEmpty</td><td>size, clear, isEmpty</td></tr>
 * <tr            ><td>containsKey(K) </td><td>containsKey(K), lget()</td></tr>
 * <tr class="odd"><td>containsValue(K) </td><td>(no efficient equivalent)</td></tr>
 * <tr            ><td>keySet, entrySet </td><td>{@linkplain #iterator() iterator} over map entries,
 *                                               keySet, pseudo-closures</td></tr>
#else
 * <p>See {@link ObjectObjectOpenHashMap} class for API similarities and differences against Java
 * Collections.
#end
 * 
#if ($TemplateOptions.KTypeGeneric)
 * <p>This implementation supports <code>null</code> keys.</p>
 * 
 * </tbody>
 * </table>
 * <p><b>Important note.</b> The implementation uses power-of-two tables and linear
 * probing, which may cause poor performance (many collisions) if hash values are
 * not properly distributed.
 * This implementation uses rehashing
 * using {@link MurmurHash3}.</p>
#end
#if ($TemplateOptions.VTypeGeneric)
 * <p>This implementation supports <code>null</code> values.</p>
#end
 * 
 * @author This code is inspired by the collaboration and implementation in the <a
 *         href="http://fastutil.dsi.unimi.it/">fastutil</a> project.
 * 
#if ($RH)
 *   <p> Robin-Hood hashing algorithm is also used to minimize variance
 *  in insertion and search-related operations, for an all-around smother operation at the cost
 *  of smaller peak performance:</p>
 *  <p> - Pedro Celis (1986) for the original Robin-Hood hashing paper, </p>
 *  <p> - <a href="cliff@leaninto.it">MoonPolySoft/Cliff Moon</a> for the initial Robin-hood on HPPC implementation,</p>
 *  <p> - <a href="vsonnier@gmail.com" >Vincent Sonnier</a> for the present implementation using cached hashes.</p>
#end
 */
/*! ${TemplateOptions.generatedAnnotation} !*/
public class KTypeVTypeOpenHashMap<KType, VType>
        implements KTypeVTypeMap<KType, VType>, Cloneable
{
    /**
     * Minimum capacity for the map.
     */
    public final static int MIN_CAPACITY = HashContainerUtils.MIN_CAPACITY;

    /**
     * Default capacity.
     */
    public final static int DEFAULT_CAPACITY = HashContainerUtils.DEFAULT_CAPACITY;

    /**
     * Default load factor.
     */
    public final static float DEFAULT_LOAD_FACTOR = HashContainerUtils.DEFAULT_LOAD_FACTOR;

    protected VType defaultValue = Intrinsics.<VType> defaultVTypeValue();

    /**
     * Hash-indexed array holding all keys.
    #if ($TemplateOptions.KTypeGeneric)
     * <p><strong>Important!</strong>
     * The actual value in this field is always an instance of <code>Object[]</code>.
     * Be warned that <code>javac</code> emits additional casts when <code>keys</code>
     * are directly accessed; <strong>these casts
     * may result in exceptions at runtime</strong>. A workaround is to cast directly to
     * <code>Object[]</code> before accessing the buffer's elements (although it is highly
     * recommended to use a {@link #iterator()} instead.
     * </pre>
    #end
     * <p>
     * Direct map iteration: iterate  {keys[i], values[i]} for i in [0; keys.length[ where this.allocated[i] is true.
     * </p>
     * 
     * <p><b>Direct iteration warning: </b>
     * If the iteration goal is to fill another hash container, please iterate {@link #keys} in reverse to prevent performance losses.
     * @see #values
     * @see #allocated
     */
    public KType[] keys;

    /**
     * Hash-indexed array holding all values associated to the keys
     * stored in {@link #keys}.
    #if ($TemplateOptions.KTypeGeneric)
     * <p><strong>Important!</strong>
     * The actual value in this field is always an instance of <code>Object[]</code>.
     * Be warned that <code>javac</code> emits additional casts when <code>values</code>
     * are directly accessed; <strong>these casts
     * may result in exceptions at runtime</strong>. A workaround is to cast directly to
     * <code>Object[]</code> before accessing the buffer's elements (although it is highly
     * recommended to use a {@link #iterator()} instead.
     * </pre>
    #end
     * 
     * @see #keys
     */
    public VType[] values;

    /**
     * Information if an entry (slot) in the {@link #values} table is allocated
     * or empty.
     * #if ($RH)
     * In addition it caches hash value :  If = -1, it means not allocated, else = HASH(keys[i]) & mask
     * for every index i.
     * #end
     * @see #assigned
     */
    /*! #if ($RH) !*/
    public int[] allocated;
    /*! #elseif ($SA)
     public boolean allocatedDefaultKey = false;
     public VType defaultKeyValue;
    #else
    public boolean[] allocated;
    #end !*/

    /**
     * Cached number of assigned slots in {@link #allocated}.
     */
    protected int assigned;

    /**
     * The load factor for this map (fraction of allocated slots
     * before the buffers must be rehashed or reallocated).
     */
    protected final float loadFactor;

    /**
     * Resize buffers when {@link #allocated} hits this value.
     */
    protected int resizeAt;

    /**
     * The most recent slot accessed in {@link #containsKey} (required for
     * {@link #lget}).
     * 
     * @see #containsKey
     * @see #lget
     */
    protected int lastSlot;

    /**
     * Creates a hash map with the default capacity of {@value #DEFAULT_CAPACITY},
     * load factor of {@value #DEFAULT_LOAD_FACTOR}.
     * 
     * <p>See class notes about hash distribution importance.</p>
     */
    public KTypeVTypeOpenHashMap()
    {
        this(KTypeVTypeOpenHashMap.DEFAULT_CAPACITY);
    }

    /**
     * Creates a hash map with the given initial capacity, default load factor of
     * {@value #DEFAULT_LOAD_FACTOR}.
     * 
     * <p>See class notes about hash distribution importance.</p>
     * 
     * @param initialCapacity Initial capacity (greater than zero and automatically
     *            rounded to the next power of two).
     */
    public KTypeVTypeOpenHashMap(final int initialCapacity)
    {
        this(initialCapacity, KTypeVTypeOpenHashMap.DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a hash map with the given initial capacity,
     * load factor.
     * 
     * <p>See class notes about hash distribution importance.</p>
     * 
     * @param initialCapacity Initial capacity (greater than zero and automatically
     *            rounded to the next power of two).
     *
     * @param loadFactor The load factor (greater than zero and smaller than 1).
     */
    public KTypeVTypeOpenHashMap(final int initialCapacity, final float loadFactor)
    {
        assert loadFactor > 0 && loadFactor <= 1 : "Load factor must be between (0, 1].";

        this.loadFactor = loadFactor;

        //take into account of the load factor to garantee no reallocations before reaching  initialCapacity.
        int internalCapacity = (int) (initialCapacity / loadFactor) + KTypeVTypeOpenHashMap.MIN_CAPACITY;

        //align on next power of two
        internalCapacity = HashContainerUtils.roundCapacity(internalCapacity);

        this.keys = Intrinsics.newKTypeArray(internalCapacity);
        this.values = Intrinsics.newVTypeArray(internalCapacity);

        //fill with "not allocated" value
        /*! #if ($RH) !*/
        this.allocated = new int[internalCapacity];
        Internals.blankIntArrayMinusOne(this.allocated, 0, this.allocated.length);
        /*! #elseif (!$SA)
        this.allocated = new boolean[internalCapacity];
        #end !*/

        //Take advantage of the rounding so that the resize occur a bit later than expected.
        //allocate so that there is at least one slot that remains allocated = false
        //this is compulsory to guarantee proper stop in searching loops
        this.resizeAt = Math.max(3, (int) (internalCapacity * loadFactor)) - 2;
    }

    /**
     * Create a hash map from all key-value pairs of another container.
     */
    public KTypeVTypeOpenHashMap(final KTypeVTypeAssociativeContainer<KType, VType> container)
    {
        this(container.size());
        putAll(container);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VType put(KType key, VType value)
    {
/*! #if ($SA)

        if (Intrinsics.equalsKTypeDefault(key)) {

            if (this.allocatedDefaultKey) {

                VType previousValue = this.defaultKeyValue;
                this.defaultKeyValue = value;

                return previousValue;
            }

            this.defaultKeyValue = value;
            this.assigned++;
            this.allocatedDefaultKey = true;

            return this.defaultValue;
        }
#end !*/

        final int mask = this.keys.length - 1;

        int slot = Internals.rehash(key) & mask;

        final KType[] keys = this.keys;
        final VType[] values = this.values;

        /*! #if ($RH) !*/
        final int[] allocated = this.allocated;
        /*! #elseif (!$SA)
        final boolean[] allocated = this.allocated;
        #end !*/

        /*! #if ($RH) !*/
        KType tmpKey;
        VType tmpValue;
        int tmpAllocated;
        int initial_slot = slot;
        int dist = 0;
        int existing_distance = 0;
        /*! #end !*/

        while (is_allocated(allocated, slot, keys))
        {
            if (Intrinsics.equalsKType(key, keys[slot]))
            {
                final VType oldValue = values[slot];
                values[slot] = value;

                return oldValue;
            }

            /*! #if ($RH) !*/
            //re-shuffle keys to minimize variance
            existing_distance = probe_distance(slot, allocated);

            if (dist > existing_distance)
            {
                //swap current (key, value, initial_slot) with slot places
                tmpKey = keys[slot];
                keys[slot] = key;
                key = tmpKey;

                tmpAllocated = allocated[slot];
                allocated[slot] = initial_slot;
                initial_slot = tmpAllocated;

                tmpValue = values[slot];
                values[slot] = value;
                value = tmpValue;

                /*! #if($DEBUG) !*/
                //Check invariants

                assert allocated[slot] == (Internals.rehash(keys[slot]) & mask);
                assert initial_slot == (Internals.rehash(key) & mask);
                /*! #end !*/

                dist = existing_distance;
            }
            /*! #end !*/

            slot = (slot + 1) & mask;

            /*! #if ($RH) !*/
            dist++;
            /*! #end !*/
        }

        // Check if we need to grow. If so, reallocate new data, fill in the last element
        // and rehash.
        if (this.assigned == this.resizeAt)
        {
            expandAndPut(key, value, slot);
        }
        else
        {
            this.assigned++;
            /*! #if ($RH) !*/
            allocated[slot] = initial_slot;
            /*! #elseif (!$SA)
            allocated[slot] = true;
            #end !*/

            keys[slot] = key;
            values[slot] = value;

            /*! #if ($RH) !*/
            /*! #if($DEBUG) !*/
            //Check invariants
            assert allocated[slot] == (Internals.rehash(keys[slot]) & mask);

            /*! #end !*/
            /*! #end !*/

        }
        return this.defaultValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int putAll(final KTypeVTypeAssociativeContainer<? extends KType, ? extends VType> container)
    {
        return putAll((Iterable<? extends KTypeVTypeCursor<? extends KType, ? extends VType>>) container);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int putAll(final Iterable<? extends KTypeVTypeCursor<? extends KType, ? extends VType>> iterable)
    {
        final int count = this.assigned;
        for (final KTypeVTypeCursor<? extends KType, ? extends VType> c : iterable)
        {
            put(c.key, c.value);
        }
        return this.assigned - count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean putIfAbsent(final KType key, final VType value)
    {
        if (!containsKey(key))
        {
            put(key, value);
            return true;
        }
        return false;
    }

    /*! #if ($TemplateOptions.VTypeNumeric) !*/
    /**
     * <a href="http://trove4j.sourceforge.net">Trove</a>-inspired API method. An equivalent
     * of the following code:
     * <pre>
     *  if (containsKey(key))
     *  {
     *      VType v = (VType) (lget() + additionValue);
     *      lset(v);
     *      return v;
     *  }
     *  else
     *  {
     *     put(key, putValue);
     *     return putValue;
     *  }
     * </pre>
     * 
     * @param key The key of the value to adjust.
     * @param putValue The value to put if <code>key</code> does not exist.
     * @param additionValue The value to add to the existing value if <code>key</code> exists.
     * @return Returns the current value associated with <code>key</code> (after changes).
     */
    /*! #end !*/
    /*! #if ($TemplateOptions.VTypeNumeric)
    @Override
    public VType putOrAdd(KType key, VType putValue, VType additionValue)
    {
        #if (!$SA)
        assert this.assigned < this.keys.length ;
        #end

     #if ($SA)

        assert this.assigned <= this.keys.length ;

        if (Intrinsics.equalsKTypeDefault(key)) {

            if (this.allocatedDefaultKey) {

                VType previousValue = this.defaultKeyValue;
                this.defaultKeyValue = putValue;

                return previousValue;
            }

            this.defaultKeyValue = putValue;
            this.assigned++;
            this.allocatedDefaultKey = true;

            return this.defaultValue;
        }
    #end

        final int mask = keys.length - 1;

        int slot = Internals.rehash(key) & mask;

        #if ($RH)
        final int[] allocated = this.allocated;
        #elseif (!$SA)
        final boolean[] allocated = this.allocated;
        #end


        final KType[] keys = this.keys;

        final VType[] values = this.values;
        VType value  = this.defaultValue;

    	#if ($RH)
        KType tmpKey;
        VType tmpValue;
        int tmpAllocated;
        int initial_slot = slot;
        int dist = 0;
        int existing_distance = 0;
        #end

        while (is_allocated(allocated, slot, keys))
        {
            #if ($RH)
            existing_distance = probe_distance(slot, allocated);
            #end

            if (Intrinsics.equalsKType(key, keys[slot]))
            {
                values[slot] += additionValue;
                return values[slot];
            }
            #if ($RH)
    		else if (dist > existing_distance)
            {
    			//swap current (key, value, initial_slot) with slot places
    			tmpKey = keys[slot];
    			keys[slot] = key;
    			key =  tmpKey;

    			tmpValue = values[slot];
    			values[slot] = value;
    			value =  tmpValue;

    			tmpAllocated = allocated[slot];
    			allocated[slot] = initial_slot;
    			initial_slot = tmpAllocated;

                dist = existing_distance;
            }
            #end

            slot = (slot + 1) & mask;

    		#if ($RH)
            dist++;
            #end
        }

        if (assigned == resizeAt) {
            expandAndPut(key, putValue, slot);
        } else {

            assigned++;
            #if ($RH)
            allocated[slot] = initial_slot;
            #elseif (!$SA)
            allocated[slot] = true;
            #end

            keys[slot] = key;
            values[slot] = putValue;
        }
        return putValue;
    }
    #end !*/

    /*! #if ($TemplateOptions.VTypeNumeric) !*/
    /**
     * An equivalent of calling
     * <pre>
     *  if (containsKey(key))
     *  {
     *      VType v = (VType) (lget() + additionValue);
     *      lset(v);
     *      return v;
     *  }
     *  else
     *  {
     *     put(key, additionValue);
     *     return additionValue;
     *  }
     * </pre>
     * 
     * @param key The key of the value to adjust.
     * @param additionValue The value to put or add to the existing value if <code>key</code> exists.
     * @return Returns the current value associated with <code>key</code> (after changes).
     */
    /*! #end !*/
    /*! #if ($TemplateOptions.VTypeNumeric)
    @Override
    public VType addTo(KType key, VType additionValue)
    {
        return putOrAdd(key, additionValue, additionValue);
    }
    #end !*/

    /**
     * Expand the internal storage buffers (capacity) and rehash.
     */
    private void expandAndPut(final KType pendingKey, final VType pendingValue, final int freeSlot)
    {
        assert this.assigned == this.resizeAt;

        /*! #if ($RH) !*/
        assert this.allocated[freeSlot] == -1;
        /*! #elseif ($SA)
        //default sentinel value is never in the keys[] array, so never trigger reallocs
        assert !Intrinsics.equalsKTypeDefault(pendingKey);
        #else
        assert !allocated[freeSlot];
        #end !*/

        // Try to allocate new buffers first. If we OOM, it'll be now without
        // leaving the data structure in an inconsistent state.
        final KType[] oldKeys = this.keys;
        final VType[] oldValues = this.values;

        /*! #if ($RH) !*/
        final int[] oldAllocated = this.allocated;
        /*! #elseif (!$SA)
        final boolean[] oldAllocated = this.allocated;
        #end !*/

        allocateBuffers(HashContainerUtils.nextCapacity(this.keys.length));

        // We have succeeded at allocating new data so insert the pending key/value at
        // the free slot in the old arrays before rehashing.
        this.lastSlot = -1;
        this.assigned++;
        //We don't care of the oldAllocated value, so long it means "allocated = true", since the whole set is rebuilt from scratch.
        /*! #if ($RH) !*/
        oldAllocated[freeSlot] = 1;
        /*!#elseif (!$SA)
        oldAllocated[freeSlot] = true;
        #end !*/

        oldKeys[freeSlot] = pendingKey;
        oldValues[freeSlot] = pendingValue;

        //for inserts
        final int mask = this.keys.length - 1;

        KType key = Intrinsics.<KType> defaultKTypeValue();
        VType value = Intrinsics.<VType> defaultVTypeValue();

        int slot = -1;

        final KType[] keys = this.keys;
        final VType[] values = this.values;

        /*! #if ($RH) !*/
        final int[] allocated = this.allocated;
        /*! #elseif (!$SA)
        final boolean[] allocated = this.allocated;
        #end !*/

        /*! #if ($RH) !*/
        KType tmpKey = Intrinsics.<KType> defaultKTypeValue();
        VType tmpValue = Intrinsics.<VType> defaultVTypeValue();
        int tmpAllocated = -1;
        int initial_slot = -1;
        int dist = -1;
        int existing_distance = -1;
        /*! #end !*/

        //iterate all the old arrays to add in the newly allocated buffers
        //It is important to iterate backwards to minimize the conflict chain length !
        for (int i = oldKeys.length; --i >= 0;)
        {
            if (is_allocated(oldAllocated, i, oldKeys))
            {
                key = oldKeys[i];
                value = oldValues[i];

                slot = Internals.rehash(key) & mask;

                /*! #if ($RH) !*/
                initial_slot = slot;
                dist = 0;
                /*! #end !*/

                while (is_allocated(allocated, slot, keys))
                {
                    /*! #if ($RH) !*/
                    //re-shuffle keys to minimize variance
                    existing_distance = probe_distance(slot, allocated);

                    if (dist > existing_distance)
                    {
                        //swap current (key, value, initial_slot) with slot places
                        tmpKey = keys[slot];
                        keys[slot] = key;
                        key = tmpKey;

                        tmpAllocated = allocated[slot];
                        allocated[slot] = initial_slot;
                        initial_slot = tmpAllocated;

                        tmpValue = values[slot];
                        values[slot] = value;
                        value = tmpValue;

                        /*! #if($DEBUG) !*/
                        //Check invariants
                        assert allocated[slot] == (Internals.rehash(keys[slot]) & mask);
                        assert initial_slot == (Internals.rehash(key) & mask);
                        /*! #end !*/

                        dist = existing_distance;
                    }
                    /*! #end !*/

                    slot = (slot + 1) & mask;

                    /*! #if ($RH) !*/
                    dist++;
                    /*! #end !*/
                } //end while

                /*! #if ($RH) !*/
                allocated[slot] = initial_slot;
                /*! #elseif (!$SA)
                allocated[slot] = true;
                #end !*/

                keys[slot] = key;
                values[slot] = value;

                /*! #if ($RH) !*/
                /*! #if($DEBUG) !*/
                //Check invariants
                assert allocated[slot] == (Internals.rehash(keys[slot]) & mask);
                /*! #end !*/
                /*! #end !*/
            }
        }
    }

    /**
     * Allocate internal buffers for a given capacity.
     * 
     * @param capacity New capacity (must be a power of two).
     */
    private void allocateBuffers(final int capacity)
    {
        final KType[] keys = Intrinsics.newKTypeArray(capacity);
        final VType[] values = Intrinsics.newVTypeArray(capacity);

        /*! #if ($RH) !*/
        final int[] allocated = new int[capacity];
        Internals.blankIntArrayMinusOne(allocated, 0, allocated.length);
        /*! #elseif (!$SA)
         final boolean[] allocated = new boolean[capacity];
        #end !*/

        this.keys = keys;
        this.values = values;

        /*! #if (!$SA) !*/
        this.allocated = allocated;
        /*! #end !*/

        //allocate so that there is at least one slot that remains allocated = false
        //this is compulsory to guarantee proper stop in searching loops
        this.resizeAt = Math.max(3, (int) (capacity * this.loadFactor)) - 2;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VType remove(final KType key)
    {
        final int mask = this.keys.length - 1;

/*! #if ($SA)
        if (Intrinsics.equalsKTypeDefault(key)) {

            if (this.allocatedDefaultKey) {

                VType previousValue = this.defaultKeyValue;

                this.assigned--;
                this.allocatedDefaultKey = false;
                return previousValue;
            }

            return this.defaultValue;
        }
#end !*/

        int slot = Internals.rehash(key) & mask;

        /*! #if ($RH) !*/
        int dist = 0;
        /*! #end !*/

        final KType[] keys = this.keys;

        /*! #if ($RH) !*/
        final int[] states = this.allocated;
        /*! #elseif (!$SA)
        final boolean[] states = this.allocated;
        #end !*/

        while (is_allocated(states, slot, keys)
                /*! #if ($RH) !*/&& dist <= probe_distance(slot, states) /*! #end !*/)
        {
            if (Intrinsics.equalsKType(key, keys[slot]))
            {
                final VType value = this.values[slot];

                this.assigned--;
                shiftConflictingKeys(slot);

                return value;
            }
            slot = (slot + 1) & mask;

            /*! #if ($RH) !*/
            dist++;
            /*! #end !*/
        } //end while true

        return this.defaultValue;
    }

    /**
     * Shift all the slot-conflicting keys allocated to (and including) <code>slot</code>.
     */
    protected void shiftConflictingKeys(int slotCurr)
    {
        // Copied nearly verbatim from fastutil's impl.
        final int mask = this.keys.length - 1;
        int slotPrev, slotOther;

        final KType[] keys = this.keys;
        final VType[] values = this.values;

        /*! #if ($RH) !*/
        final int[] allocated = this.allocated;
        /*! #elseif (!$SA)
         final boolean[] allocated = this.allocated;
        #end !*/

        while (true)
        {
            slotCurr = ((slotPrev = slotCurr) + 1) & mask;

            while (is_allocated(allocated, slotCurr, keys))
            {
                /*! #if ($RH) !*/
                //use the cached value, no need to recompute
                slotOther = allocated[slotCurr];
                /*! #if($DEBUG) !*/
                //Check invariants
                assert slotOther == (Internals.rehash(keys[slotCurr]) & mask);
                /*! #end !*/
                /*! #else
                 slotOther = Internals.rehash(keys[slotCurr]) & mask;
                #end !*/

                if (slotPrev <= slotCurr)
                {
                    // we're on the right of the original slot.
                    if (slotPrev >= slotOther || slotOther > slotCurr)
                    {
                        break;
                    }
                }
                else
                {
                    // we've wrapped around.
                    if (slotPrev >= slotOther && slotOther > slotCurr)
                    {
                        break;
                    }
                }
                slotCurr = (slotCurr + 1) & mask;
            }

            if (!is_allocated(allocated, slotCurr, keys))
            {
                break;
            }

            /*! #if ($RH) !*/
            /*! #if($DEBUG) !*/
            //Check invariants
            assert allocated[slotCurr] == (Internals.rehash(keys[slotCurr]) & mask);
            assert allocated[slotPrev] == (Internals.rehash(keys[slotPrev]) & mask);
            /*! #end !*/
            /*! #end !*/

            // Shift key/value/allocated triplet.
            keys[slotPrev] = keys[slotCurr];
            values[slotPrev] = values[slotCurr];

            /*! #if ($RH) !*/
            allocated[slotPrev] = allocated[slotCurr];
            /*! #end !*/
        }

        //means not allocated
        /*! #if ($RH) !*/
        allocated[slotPrev] = -1;
        /*! #elseif (!$SA)
         allocated[slotPrev] = false;
        #end !*/

        /* #if (($TemplateOptions.KTypeGeneric) || $SA) */
        keys[slotPrev] = Intrinsics.<KType> defaultKTypeValue();
        /* #end */
        /* #if ($TemplateOptions.VTypeGeneric) */
        values[slotPrev] = Intrinsics.<VType> defaultVTypeValue();
        /* #end */
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int removeAll(final KTypeContainer<? extends KType> container)
    {
        final int before = this.assigned;

        for (final KTypeCursor<? extends KType> cursor : container)
        {
            remove(cursor.value);
        }

        return before - this.assigned;
    }

    /**
     * {@inheritDoc}
     * <p><strong>Important!</strong>
     * If the predicate actually injects the removed keys in another hash container, you may experience performance losses.
     */
    @Override
    public int removeAll(final KTypePredicate<? super KType> predicate)
    {
        final int before = this.assigned;

/*! #if ($SA)
        if (this.allocatedDefaultKey) {

            if (predicate.apply(Intrinsics.defaultKTypeValue()))
            {
                 this.allocatedDefaultKey = false;
                 this.assigned--;
            }
        }
#end !*/

        final KType[] keys = this.keys;

        /*! #if ($RH) !*/
        final int[] states = this.allocated;
        /*! #elseif (!$SA)
        final boolean[] states = this.allocated;
        #end !*/

        for (int i = 0; i < keys.length;)
        {
            if (is_allocated(states, i, keys))
            {
                if (predicate.apply(keys[i]))
                {
                    this.assigned--;
                    shiftConflictingKeys(i);
                    // Repeat the check for the same i.
                    continue;
                }
            }
            i++;
        }
        return before - this.assigned;
    }

    /**
     * {@inheritDoc}
     * 
     * <p> Use the following snippet of code to check for key existence
     * first and then retrieve the value if it exists.</p>
     * <pre>
     * if (map.containsKey(key))
     *   value = map.lget();
     * </pre>
     */
    @Override
    public VType get(final KType key)
    {
        final int mask = this.keys.length - 1;

/*! #if ($SA)
        if (Intrinsics.equalsKTypeDefault(key)) {

            if (this.allocatedDefaultKey) {

                return this.defaultKeyValue;
            }

            return this.defaultValue;
        }
#end !*/

        int slot = Internals.rehash(key) & mask;

        /*! #if ($RH) !*/
        int dist = 0;
        /*! #end !*/

        final KType[] keys = this.keys;

        /*! #if ($RH) !*/
        final int[] states = this.allocated;
        /*! #elseif(!$SA)
        final boolean[] states = this.allocated;
        #end !*/

        while (is_allocated(states, slot, keys)
                /*! #if ($RH) !*/&& dist <= probe_distance(slot, states) /*! #end !*/)
        {
            if (Intrinsics.equalsKType(key, keys[slot]))
            {
                return this.values[slot];
            }
            slot = (slot + 1) & mask;

            /*! #if ($RH) !*/
            dist++;
            /*! #end !*/
        } //end while true

        return this.defaultValue;
    }

    /**
     * Returns the last key stored in this has map for the corresponding
     * most recent call to {@link #containsKey}.
     * Precondition : {@link #containsKey} must have been called previously !
     * <p>Use the following snippet of code to check for key existence
     * first and then retrieve the key value if it exists.</p>
     * <pre>
     * if (map.containsKey(key))
     *   value = map.lkey();
     * </pre>
     * 
     * <p>This is equivalent to calling:</p>
     * <pre>
     * if (map.containsKey(key))
     *   key = map.keys[map.lslot()];
     * </pre>
     */
    public KType lkey()
    {
/*! #if ($SA)

     if (this.lastSlot == -2) {

         return Intrinsics.defaultKTypeValue();
     }

#end !*/
        assert this.lastSlot >= 0 : "Call containsKey() first.";
        /*! #if ($RH) !*/
        assert this.allocated[this.lastSlot] != -1 : "Last call to exists did not have any associated value.";
        /*! #elseif ($SA)
         assert ! Intrinsics.equalsKTypeDefault(this.keys[lastSlot]) : "Last call to exists did not have any associated value.";
         #else
         assert allocated[lastSlot] : "Last call to exists did not have any associated value.";
        #end !*/

        return this.keys[this.lastSlot];
    }

    /**
     * Returns the last value saved in a call to {@link #containsKey}.
     * Precondition : {@link #containsKey} must have been called previously !
     * @see #containsKey
     */
    public VType lget()
    {
/*! #if ($SA)
        if (this.lastSlot == -2) {

            return this.defaultKeyValue;
        }
#end !*/
        assert this.lastSlot >= 0 : "Call containsKey() first.";
        /*! #if ($RH) !*/
        assert this.allocated[this.lastSlot] != -1 : "Last call to exists did not have any associated value.";
        /*! #elseif ($SA)
         assert !Intrinsics.equalsKTypeDefault(this.keys[lastSlot]) : "Last call to exists did not have any associated value.";
         #else
         assert allocated[lastSlot] : "Last call to exists did not have any associated value.";
        #end !*/

        return this.values[this.lastSlot];
    }

    /**
     * Sets the value corresponding to the key saved in the last
     * call to {@link #containsKey}, if and only if the key exists
     * in the map already.
     * Precondition : {@link #containsKey} must have been called previously !
     * @see #containsKey
     * @return Returns the previous value stored under the given key.
     */
    public VType lset(final VType value)
    {
/*! #if ($SA)
        if (this.lastSlot == -2) {

            VType previous = this.defaultKeyValue;
            this.defaultKeyValue = value;
            return previous;
        }
#end !*/
        assert this.lastSlot >= 0 : "Call containsKey() first.";
        /*! #if ($RH) !*/
        assert this.allocated[this.lastSlot] != -1 : "Last call to exists did not have any associated value.";
        /*! #elseif ($SA)
         assert ! Intrinsics.equalsKTypeDefault(this.keys[lastSlot]) : "Last call to exists did not have any associated value.";
         #else
         assert allocated[lastSlot] : "Last call to exists did not have any associated value.";
        #end !*/

        final VType previous = this.values[this.lastSlot];
        this.values[this.lastSlot] = value;
        return previous;
    }

    /**
     * @return Returns the slot of the last key looked up in a call to {@link #containsKey} if
     * it returned <code>true</code>.
     * 
     * @see #containsKey
     */
    public int lslot()
    {
        assert this.lastSlot >= 0 || this.lastSlot == -2 : "Call containsKey() first.";
        return this.lastSlot;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Saves the associated value for fast access using {@link #lget}
     * or {@link #lset}.</p>
     * <pre>
     * if (map.containsKey(key))
     *   value = map.lget();
     * </pre>
     * or, to modify the value at the given key without looking up
     * its slot twice:
     * <pre>
     * if (map.containsKey(key))
     *   map.lset(map.lget() + 1);
     * </pre>
     * #if ($TemplateOptions.KTypeGeneric) or, to retrieve the key-equivalent object from the map:
     * <pre>
     * if (map.containsKey(key))
     *   map.lkey();
     * </pre>#end
     */
    @Override
    public boolean containsKey(final KType key)
    {
        final int mask = this.keys.length - 1;

/*! #if ($SA)
        if (Intrinsics.equalsKTypeDefault(key)) {

            if (this.allocatedDefaultKey) {
                this.lastSlot = -2;
            } else {
                this.lastSlot = -1;
            }

            return this.allocatedDefaultKey;
        }
#end !*/

        int slot = Internals.rehash(key) & mask;

        /*! #if ($RH) !*/
        int dist = 0;
        /*! #end !*/

        final KType[] keys = this.keys;

        /*! #if ($RH) !*/
        final int[] states = this.allocated;
        /*! #elseif (!$SA)
        final boolean[] states = this.allocated;
        #end !*/

        while (is_allocated(states, slot, keys)
                /*! #if ($RH) !*/&& dist <= probe_distance(slot, states) /*! #end !*/)
        {
            if (Intrinsics.equalsKType(key, keys[slot]))
            {
                this.lastSlot = slot;
                return true;
            }
            slot = (slot + 1) & mask;

            /*! #if ($RH) !*/
            dist++;
            /*! #end !*/
        } //end while true

        //unsuccessful search
        this.lastSlot = -1;

        return false;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Does not release internal buffers.</p>
     */
    @Override
    public void clear()
    {
        this.assigned = 0;
        this.lastSlot = -1;

        // States are always cleared.
        /*! #if ($RH) !*/
        Internals.blankIntArrayMinusOne(this.allocated, 0, this.allocated.length);
        /*! #elseif (!$SA)
         Internals.blankBooleanArray(allocated, 0, allocated.length);
        #end !*/

        /*! #if ($SA)
        this.allocatedDefaultKey = false;
        #end !*/

        /*! #if (($TemplateOptions.KTypeGeneric) || $SA) !*/
        //Faster than Arrays.fill(keys, null); // Help the GC.
        KTypeArrays.blankArray(this.keys, 0, this.keys.length);
        /*! #end !*/

        /*! #if ($TemplateOptions.VTypeGeneric) !*/
        //Faster than Arrays.fill(values, null); // Help the GC.
        Internals.blankObjectArray(this.values, 0, this.values.length);
        /*! #end !*/
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size()
    {
        return this.assigned;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int capacity() {

        return this.resizeAt - 1;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Note that an empty container may still contain many deleted keys (that occupy buffer
     * space). Adding even a single element to such a container may cause rehashing.</p>
     */
    @Override
    public boolean isEmpty()
    {
        return size() == 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {
        int h = 0;

        /*! #if ($SA)
        if (this.allocatedDefaultKey) {
            h +=  Internals.rehash(Intrinsics.defaultKTypeValue()) + Internals.rehash(this.defaultKeyValue);
        }
        #end !*/

        final KType[] keys = this.keys;
        final VType[] values = this.values;

        /*! #if ($RH) !*/
        final int[] states = this.allocated;
        /*! #elseif (!$SA)
        final boolean[] states = this.allocated;
        #end !*/

        for (int i = keys.length; --i >= 0;)
        {
            if (is_allocated(states, i, keys))
            {
                //This hash is an intrinsic property of the container contents,
                //consequently is independent from the HashStrategy, so do not use it !
                h += Internals.rehash(keys[i]) + Internals.rehash(values[i]);
            }
        }

        return h;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj)
    {
        if (obj != null)
        {
            if (obj == this)
            {
                return true;
            }

            if (!(obj instanceof KTypeVTypeOpenHashMap))
            {
                return false;
            }

            /* #if ($TemplateOptions.AnyGeneric) */
            @SuppressWarnings("unchecked")
            final/* #end */
            KTypeVTypeMap<KType, VType> other = (KTypeVTypeMap<KType, VType>) obj;
            if (other.size() == this.size())
            {
                final EntryIterator it = this.iterator();

                while (it.hasNext())
                {
                    final KTypeVTypeCursor<KType, VType> c = it.next();

                    if (other.containsKey(c.key))
                    {
                        final VType v = other.get(c.key);
                        if (Intrinsics.equalsVType(c.value, v))
                        {
                            continue;
                        }
                    }
                    //recycle
                    it.release();
                    return false;
                }
                return true;
            }

        }
        return false;
    }

    /**
     * An iterator implementation for {@link #iterator}.
     */
    public final class EntryIterator extends AbstractIterator<KTypeVTypeCursor<KType, VType>>
    {
        public final KTypeVTypeCursor<KType, VType> cursor;

        public EntryIterator()
        {
            this.cursor = new KTypeVTypeCursor<KType, VType>();
            this.cursor.index = -2;
        }

        /**
         * Iterate backwards w.r.t the buffer, to
         * minimize collision chains when filling another hash container (ex. with putAll())
         */
        @Override
        protected KTypeVTypeCursor<KType, VType> fetch()
        {
            /*! #if ($SA)
               if (this.cursor.index == KTypeVTypeOpenHashMap.this.keys.length + 1) {

                   if (KTypeVTypeOpenHashMap.this.allocatedDefaultKey) {

                       this.cursor.index = KTypeVTypeOpenHashMap.this.keys.length;
                       this.cursor.key = Intrinsics.defaultKTypeValue();
                       this.cursor.value = KTypeVTypeOpenHashMap.this.defaultKeyValue;

                       return this.cursor;

                   } else {
                       //no value associated with the default key, continue iteration...
                   }

                   this.cursor.index = KTypeVTypeOpenHashMap.this.keys.length;
               }

            #end !*/

            int i = this.cursor.index - 1;

            while (i >= 0 &&
                    !is_allocated(KTypeVTypeOpenHashMap.this.allocated, i, KTypeVTypeOpenHashMap.this.keys))
            {
                i--;
            }

            if (i == -1)
            {
                return done();
            }

            this.cursor.index = i;
            this.cursor.key = KTypeVTypeOpenHashMap.this.keys[i];
            this.cursor.value = KTypeVTypeOpenHashMap.this.values[i];

            return this.cursor;
        }
    }

    /**
     * internal pool of EntryIterator
     */
    protected final IteratorPool<KTypeVTypeCursor<KType, VType>, EntryIterator> entryIteratorPool = new IteratorPool<KTypeVTypeCursor<KType, VType>, EntryIterator>(
            new ObjectFactory<EntryIterator>() {

                @Override
                public EntryIterator create()
                {
                    return new EntryIterator();
                }

                @Override
                public void initialize(final EntryIterator obj)
                {
                    obj.cursor.index = KTypeVTypeOpenHashMap.this.keys.length /*! #if ($SA) +1 #end !*/;
                }

                @Override
                public void reset(final EntryIterator obj) {
                    // nothing
                }
            });

    /**
     * {@inheritDoc}
     * @return
     */
    @Override
    public EntryIterator iterator()
    {
        //return new EntryIterator();
        return this.entryIteratorPool.borrow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends KTypeVTypeProcedure<? super KType, ? super VType>> T forEach(final T procedure)
    {

/*! #if ($SA)
        if (this.allocatedDefaultKey) {

            procedure.apply(Intrinsics.defaultKTypeValue(), this.defaultKeyValue);
        }
#end !*/
        final KType[] keys = this.keys;
        final VType[] values = this.values;

        /*! #if ($RH) !*/
        final int[] states = this.allocated;
        /*! #elseif (!$SA)
        final boolean[] states = this.allocated;
        #end !*/

        //Iterate in reverse for side-stepping the longest conflict chain
        //in another hash, in case apply() is actually used to fill another hash container.
        for (int i = keys.length - 1; i >= 0; i--)
        {
            if (is_allocated(states, i, keys))
            {
                procedure.apply(keys[i], values[i]);
            }
        }

        return procedure;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends KTypeVTypePredicate<? super KType, ? super VType>> T forEach(final T predicate)
    {
/*! #if ($SA)
        if (this.allocatedDefaultKey) {

            if(! predicate.apply(Intrinsics.defaultKTypeValue(), this.defaultKeyValue)) {

                return predicate;
            }
        }
#end !*/
        final KType[] keys = this.keys;
        final VType[] values = this.values;

        /*! #if ($RH) !*/
        final int[] states = this.allocated;
        /*! #elseif (!$SA)
        final boolean[] states = this.allocated;
        #end !*/

        //Iterate in reverse for side-stepping the longest conflict chain
        //in another hash, in case apply() is actually used to fill another hash container.
        for (int i = keys.length - 1; i >= 0; i--)
        {
            if (is_allocated(states, i, keys))
            {
                if (!predicate.apply(keys[i], values[i]))
                {
                    break;
                }
            }
        } //end for

        return predicate;
    }

    /**
     *  @return a new KeysContainer view of the keys of this associated container.
     * This view then reflects all changes from the map.
     */
    @Override
    public KeysContainer keys()
    {
        return new KeysContainer();
    }

    /**
     * A view of the keys inside this hash map.
     */
    public final class KeysContainer
            extends AbstractKTypeCollection<KType> implements KTypeLookupContainer<KType>
    {
        private final KTypeVTypeOpenHashMap<KType, VType> owner =
                KTypeVTypeOpenHashMap.this;

        @Override
        public boolean contains(final KType e)
        {
            return containsKey(e);
        }

        @Override
        public <T extends KTypeProcedure<? super KType>> T forEach(final T procedure)
        {
            /*! #if ($SA)
                    if (this.owner.allocatedDefaultKey) {

                        procedure.apply(Intrinsics.defaultKTypeValue());
                    }
            #end !*/
            final KType[] keys = this.owner.keys;

            /*! #if ($RH) !*/
            final int[] states = this.owner.allocated;
            /*! #elseif (!$SA)
            final boolean[] states = owner.allocated;
            #end !*/

            //Iterate in reverse for side-stepping the longest conflict chain
            //in another hash, in case apply() is actually used to fill another hash container.
            for (int i = keys.length - 1; i >= 0; i--)
            {
                if (is_allocated(states, i, keys))
                {
                    procedure.apply(keys[i]);
                }
            }

            return procedure;
        }

        @Override
        public <T extends KTypePredicate<? super KType>> T forEach(final T predicate)
        {
            /*! #if ($SA)
                   if (this.owner.allocatedDefaultKey) {

                       if(! predicate.apply(Intrinsics.defaultKTypeValue())) {

                           return predicate;
                       }
                   }
            #end !*/
            final KType[] keys = this.owner.keys;

            /*! #if ($RH) !*/
            final int[] states = this.owner.allocated;
            /*! #elseif (!$SA)
            final boolean[] states = owner.allocated;
            #end !*/

            //Iterate in reverse for side-stepping the longest conflict chain
            //in another hash, in case apply() is actually used to fill another hash container.
            for (int i = keys.length - 1; i >= 0; i--)
            {
                if (is_allocated(states, i, keys))
                {
                    if (!predicate.apply(keys[i]))
                    {
                        break;
                    }
                }
            }

            return predicate;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public KeysIterator iterator()
        {
            //return new KeysIterator();
            return this.keyIteratorPool.borrow();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int size()
        {
            return this.owner.size();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int capacity() {

            return this.owner.capacity();
        }

        @Override
        public void clear()
        {
            this.owner.clear();
        }

        @Override
        public int removeAll(final KTypePredicate<? super KType> predicate)
        {
            return this.owner.removeAll(predicate);
        }

        @Override
        public int removeAllOccurrences(final KType e)
        {
            final boolean hasKey = this.owner.containsKey(e);
            int result = 0;
            if (hasKey)
            {
                this.owner.remove(e);
                result = 1;
            }
            return result;
        }

        /**
         * internal pool of KeysIterator
         */
        protected final IteratorPool<KTypeCursor<KType>, KeysIterator> keyIteratorPool = new IteratorPool<KTypeCursor<KType>, KeysIterator>(
                new ObjectFactory<KeysIterator>() {

                    @Override
                    public KeysIterator create()
                    {
                        return new KeysIterator();
                    }

                    @Override
                    public void initialize(final KeysIterator obj)
                    {
                        obj.cursor.index = KTypeVTypeOpenHashMap.this.keys.length /*! #if ($SA) +1 #end !*/;
                    }

                    @Override
                    public void reset(final KeysIterator obj) {
                        // nothing

                    }
                });

        @Override
        public KType[] toArray(final KType[] target)
        {
            int count = 0;

            /*! #if ($SA)
                    if (this.owner.allocatedDefaultKey) {

                        target[count++] = Intrinsics.defaultKTypeValue();
                    }
            #end !*/
            final KType[] keys = this.owner.keys;

            /*! #if ($RH) !*/
            final int[] states = this.owner.allocated;
            /*! #elseif (!$SA)
            final boolean[] states = owner.allocated;
            #end !*/

            for (int i = 0; i < keys.length; i++)
            {
                if (is_allocated(states, i, keys))
                {
                    target[count++] = keys[i];
                }
            }

            assert count == this.owner.assigned;
            return target;
        }
    };

    /**
     * An iterator over the set of assigned keys.
     */
    public final class KeysIterator extends AbstractIterator<KTypeCursor<KType>>
    {
        public final KTypeCursor<KType> cursor;

        public KeysIterator()
        {
            this.cursor = new KTypeCursor<KType>();
            this.cursor.index = -2;
        }

        /**
         * Iterate backwards w.r.t the buffer, to
         * minimize collision chains when filling another hash container (ex. with putAll())
         */
        @Override
        protected KTypeCursor<KType> fetch()
        {
            /*! #if ($SA)
            if (this.cursor.index == KTypeVTypeOpenHashMap.this.keys.length + 1) {

                if (KTypeVTypeOpenHashMap.this.allocatedDefaultKey) {

                    this.cursor.index = KTypeVTypeOpenHashMap.this.keys.length;
                    this.cursor.value = Intrinsics.defaultKTypeValue();

                    return this.cursor;

                } else {
                    //no value associated with the default key, continue iteration...
                }

                this.cursor.index = KTypeVTypeOpenHashMap.this.keys.length;
            }

            #end !*/
            int i = this.cursor.index - 1;

            while (i >= 0 && !is_allocated(KTypeVTypeOpenHashMap.this.allocated, i, KTypeVTypeOpenHashMap.this.keys))
            {
                i--;
            }

            if (i == -1)
            {
                return done();
            }

            this.cursor.index = i;
            this.cursor.value = KTypeVTypeOpenHashMap.this.keys[i];

            return this.cursor;
        }
    }

    /**
     *  @return a new ValuesContainer, view of the values of this map.
     * This view then reflects all changes from the map.
     */
    @Override
    public ValuesContainer values()
    {
        return new ValuesContainer();
    }

    /**
     * A view over the set of values of this map.
     */
    public final class ValuesContainer extends AbstractKTypeCollection<VType>
    {
        private final KTypeVTypeOpenHashMap<KType, VType> owner =
                KTypeVTypeOpenHashMap.this;

        /**
         * {@inheritDoc}
         */
        @Override
        public int size()
        {
            return this.owner.size();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int capacity() {

            return this.owner.capacity();
        }

        @Override
        public boolean contains(final VType value)
        {
            /*! #if ($SA)
                    if (this.owner.allocatedDefaultKey && Intrinsics.equalsVType(value, this.owner.defaultKeyValue)) {

                        return true;
                    }
            #end !*/

            // This is a linear scan over the values, but it's in the contract, so be it.
            /*! #if ($RH) !*/
            final int[] states = this.owner.allocated;
            /*! #elseif (!$SA)
            final boolean[] states = owner.allocated;
            #end !*/

            final KType[] keys = this.owner.keys;
            final VType[] values = this.owner.values;

            for (int slot = 0; slot < keys.length; slot++)
            {
                if (is_allocated(states, slot, keys)
                        && Intrinsics.equalsVType(value, values[slot]))
                {
                    return true;
                }
            }
            return false;
        }

        @Override
        public <T extends KTypeProcedure<? super VType>> T forEach(final T procedure)
        {
            /*! #if ($SA)
                    if (this.owner.allocatedDefaultKey) {

                        procedure.apply(this.owner.defaultKeyValue);
                    }
            #end !*/

            /*! #if ($RH) !*/
            final int[] states = this.owner.allocated;
            /*! #elseif (!$SA)
            final boolean[] states = owner.allocated;
            #end !*/

            final KType[] keys = this.owner.keys;
            final VType[] values = this.owner.values;

            for (int slot = 0; slot < keys.length; slot++)
            {
                if (is_allocated(states, slot, keys)) {

                    procedure.apply(values[slot]);
                }
            }

            return procedure;
        }

        @Override
        public <T extends KTypePredicate<? super VType>> T forEach(final T predicate)
        {
            /*! #if ($SA)
            if (this.owner.allocatedDefaultKey) {

               if (!predicate.apply(this.owner.defaultKeyValue))
               {
                   return predicate;
               }
            }
            #end !*/

            /*! #if ($RH) !*/
            final int[] states = this.owner.allocated;
            /*! #elseif (!$SA)
            final boolean[] states = owner.allocated;
            #end !*/

            final KType[] keys = this.owner.keys;
            final VType[] values = this.owner.values;

            for (int slot = 0; slot < keys.length; slot++)
            {
                if (is_allocated(states, slot, keys))
                {
                    if (!predicate.apply(values[slot]))
                    {
                        break;
                    }
                }
            }

            return predicate;
        }

        @Override
        public ValuesIterator iterator()
        {
            // return new ValuesIterator();
            return this.valuesIteratorPool.borrow();
        }

        /**
         * {@inheritDoc}
         * Indeed removes all the (key,value) pairs matching
         * (key ? ,  e) with the  same  e,  from  the map.
         */
        @Override
        public int removeAllOccurrences(final VType e)
        {
            final int before = this.owner.assigned;

            /*! #if ($SA)
            if (this.owner.allocatedDefaultKey) {

                if(Intrinsics.equalsVType(e, this.owner.defaultKeyValue)) {

                    this.owner.assigned--;
                    this.owner.allocatedDefaultKey = false;
                }
            }
            #end !*/

            final KType[] keys = this.owner.keys;
            final VType[] values = this.owner.values;

            /*! #if ($RH) !*/
            final int[] states = this.owner.allocated;
            /*! #elseif (!$SA)
            final boolean[] states = owner.allocated;
            #end !*/

            for (int slot = 0; slot < keys.length;)
            {
                if (is_allocated(states, slot, keys))
                {
                    if (Intrinsics.equalsVType(e, values[slot]))
                    {
                        this.owner.assigned--;
                        shiftConflictingKeys(slot);
                        // Repeat the check for the same i.
                        continue;
                    }
                }
                slot++;
            }
            return before - this.owner.assigned;
        }

        /**
         * {@inheritDoc}
         * Indeed removes all the (key,value) pairs matching
         * the predicate for the values, from  the map.
         */
        @Override
        public int removeAll(final KTypePredicate<? super VType> predicate)
        {
            final int before = this.owner.assigned;

            /*! #if ($SA)
            if (this.owner.allocatedDefaultKey) {

                if(predicate.apply(this.owner.defaultKeyValue)) {

                    this.owner.assigned--;
                    this.owner.allocatedDefaultKey = false;
                }
            }
            #end !*/

            final KType[] keys = this.owner.keys;
            final VType[] values = this.owner.values;

            /*! #if ($RH) !*/
            final int[] states = this.owner.allocated;
            /*! #elseif (!$SA)
            final boolean[] states = owner.allocated;
            #end !*/

            for (int slot = 0; slot < keys.length;)
            {
                if (is_allocated(states, slot, keys))
                {
                    if (predicate.apply(values[slot]))
                    {
                        this.owner.assigned--;
                        shiftConflictingKeys(slot);
                        // Repeat the check for the same i.
                        continue;
                    }
                }
                slot++;
            }
            return before - this.owner.assigned;
        }

        /**
         * {@inheritDoc}
         *  Alias for clear() the whole map.
         */
        @Override
        public void clear()
        {
            this.owner.clear();
        }

        /**
         * internal pool of ValuesIterator
         */
        protected final IteratorPool<KTypeCursor<VType>, ValuesIterator> valuesIteratorPool = new IteratorPool<KTypeCursor<VType>, ValuesIterator>(
                new ObjectFactory<ValuesIterator>() {

                    @Override
                    public ValuesIterator create()
                    {
                        return new ValuesIterator();
                    }

                    @Override
                    public void initialize(final ValuesIterator obj)
                    {
                        obj.cursor.index = KTypeVTypeOpenHashMap.this.keys.length /*! #if ($SA) +1 #end !*/;
                    }

                    @Override
                    public void reset(final ValuesIterator obj) {
                        // nothing
                    }
                });

        @Override
        public VType[] toArray(final VType[] target)
        {
            int count = 0;

            /*! #if ($SA)
            if (this.owner.allocatedDefaultKey) {

                target[count++] = this.owner.defaultKeyValue;
            }
            #end !*/

            /*! #if ($RH) !*/
            final int[] states = this.owner.allocated;
            /*! #elseif (!$SA)
            final boolean[] states = owner.allocated;
            #end !*/

            final KType[] keys = this.owner.keys;
            final VType[] values = this.owner.values;

            for (int i = 0; i < values.length; i++)
            {
                if (is_allocated(states, i, keys))
                {
                    target[count++] = values[i];
                }
            }

            assert count == this.owner.assigned;
            return target;
        }
    }

    /**
     * An iterator over the set of assigned values.
     */
    public final class ValuesIterator extends AbstractIterator<KTypeCursor<VType>>
    {
        public final KTypeCursor<VType> cursor;

        public ValuesIterator()
        {
            this.cursor = new KTypeCursor<VType>();
            this.cursor.index = -2;
        }

        /**
         * Iterate backwards w.r.t the buffer, to
         * minimize collision chains when filling another hash container (ex. with putAll())
         */
        @Override
        protected KTypeCursor<VType> fetch()
        {
            /*! #if ($SA)
            if (this.cursor.index == KTypeVTypeOpenHashMap.this.values.length + 1) {

                if (KTypeVTypeOpenHashMap.this.allocatedDefaultKey) {

                    this.cursor.index = KTypeVTypeOpenHashMap.this.values.length;
                    this.cursor.value = KTypeVTypeOpenHashMap.this.defaultKeyValue;

                    return this.cursor;

                } else {
                    //no value associated with the default key, continue iteration...
                }

                this.cursor.index = KTypeVTypeOpenHashMap.this.keys.length;
            }

            #end !*/

            int i = this.cursor.index - 1;

            while (i >= 0 &&
                    !is_allocated(KTypeVTypeOpenHashMap.this.allocated, i, KTypeVTypeOpenHashMap.this.keys))
            {
                i--;
            }

            if (i == -1)
            {
                return done();
            }

            this.cursor.index = i;
            this.cursor.value = KTypeVTypeOpenHashMap.this.values[i];

            return this.cursor;
        }
    }

    /**
     * Clone this object.
     * #if ($TemplateOptions.AnyGeneric)
     * The returned clone will use the same HashingStrategy strategy.
     * It also realizes a trim-to- this.size() in the process.
     * #end
     */
    @Override
    public KTypeVTypeOpenHashMap<KType, VType> clone()
    {
        final KTypeVTypeOpenHashMap<KType, VType> cloned = new KTypeVTypeOpenHashMap<KType, VType>(this.size(), this.loadFactor);

        cloned.putAll(this);

        cloned.defaultValue = this.defaultValue;

        return cloned;
    }

    /**
     * Convert the contents of this map to a human-friendly string.
     */
    @Override
    public String toString()
    {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("[");

        boolean first = true;
        for (final KTypeVTypeCursor<KType, VType> cursor : this)
        {
            if (!first)
            {
                buffer.append(", ");
            }
            buffer.append(cursor.key);
            buffer.append("=>");
            buffer.append(cursor.value);
            first = false;
        }
        buffer.append("]");
        return buffer.toString();
    }

    /**
     * Creates a hash map from two index-aligned arrays of key-value pairs.
     */
    public static <KType, VType> KTypeVTypeOpenHashMap<KType, VType> from(final KType[] keys, final VType[] values)
    {
        if (keys.length != values.length)
        {
            throw new IllegalArgumentException("Arrays of keys and values must have an identical length.");
        }

        final KTypeVTypeOpenHashMap<KType, VType> map = new KTypeVTypeOpenHashMap<KType, VType>(keys.length);

        for (int i = 0; i < keys.length; i++)
        {
            map.put(keys[i], values[i]);
        }
        return map;
    }

    /**
     * Create a hash map from another associative container.
     */
    public static <KType, VType> KTypeVTypeOpenHashMap<KType, VType> from(final KTypeVTypeAssociativeContainer<KType, VType> container)
    {
        return new KTypeVTypeOpenHashMap<KType, VType>(container);
    }

    /**
     * Create a new hash map without providing the full generic signature (constructor
     * shortcut).
     */
    public static <KType, VType> KTypeVTypeOpenHashMap<KType, VType> newInstance()
    {
        return new KTypeVTypeOpenHashMap<KType, VType>();
    }

    /**
     * Create a new hash map with initial capacity and load factor control. (constructor
     * shortcut).
     */
    public static <KType, VType> KTypeVTypeOpenHashMap<KType, VType> newInstance(final int initialCapacity, final float loadFactor)
    {
        return new KTypeVTypeOpenHashMap<KType, VType>(initialCapacity, loadFactor);
    }

    /**
     * Returns the "default value" value used
     * in containers methods returning "default value"
     * @return
     */
    public VType getDefaultValue()
    {
        return this.defaultValue;
    }

    /**
     * Set the "default value" value to be used
     * in containers methods returning "default value"
     * @return
     */
    public void setDefaultValue(final VType defaultValue)
    {
        this.defaultValue = defaultValue;
    }

/*! #if ($RH) !*/
    //Test for existence in RH or template

    /*! #if ($TemplateOptions.inline("is_allocated",
    "(alloc, slot, keys)",
    "alloc[slot] != -1")) !*/
    /**
     * Robin-Hood / template version
     * (actual method is inlined in generated code)
     */
    private boolean is_allocated(final int[] alloc, final int slot, final KType[] keys) {

        return alloc[slot] != -1;
    }

    /*! #end !*/

/*! #elseif ($SA)
  //Test for existence with default value sentinels

     #if ($TemplateOptions.inline("is_allocated",
    "(alloc, slot, keys)",
    "! Intrinsics.equalsKTypeDefault(keys[slot])"))
    //nothing !
    #end
#else
  //Test for existence with boolean array
    #if ($TemplateOptions.inline("is_allocated",
    "(alloc, slot, keys)",
    "alloc[slot]"))
    //nothing !
    #end
#end !*/

    /*! #if ($TemplateOptions.inline("probe_distance",
    "(slot, alloc)",
    "slot < alloc[slot] ? slot + alloc.length - alloc[slot] : slot - alloc[slot]")) !*/
    /**
     * (actual method is inlined in generated code)
     */
    private int probe_distance(final int slot, final int[] alloc) {

        final int rh = alloc[slot];

        /*! #if($DEBUG) !*/
        //Check : cached hashed slot is == computed value
        final int mask = alloc.length - 1;
        assert rh == (Internals.rehash(this.keys[slot]) & mask);
        /*! #end !*/

        if (slot < rh) {
            //wrap around
            return slot + alloc.length - rh;
        }

        return slot - rh;
    }
    /*! #end !*/
}
