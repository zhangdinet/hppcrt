package com.carrotsearch.hppc;

import java.util.Iterator;

import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.predicates.IntPredicate;
import com.carrotsearch.hppc.procedures.IntProcedure;

/**
 * A double-linked set of <code>int</code> values. This data structure is characterized by
 * constant-time lookup, insertions, deletions and removal of all set elements (unlike a 
 * {@link BitSet} which takes time proportional to the maximum element's length). 
 * 
 * <p>The implementation in based on 
 * <a href="http://citeseerx.ist.psu.edu/viewdoc/summary?doi=10.1.1.30.7319">
 * Preston Briggs and Linda Torczon's paper "An Efficient Representation for Sparse Sets"</a></p>
 */
public class IntDoubleLinkedSet implements IntLookupContainer, IntSet
{
    /**
     * Default capacity if no other capacity is given in the constructor.
     */
    public final static int DEFAULT_CAPACITY = 5;

    /**
     * Dense array of set elements. 
     */
    public int [] dense = EmptyArrays.EMPTY_INT_ARRAY;

    /**
     * Sparse, element value-indexed array pointing back at {@link #dense}.
     */
    public int [] sparse = EmptyArrays.EMPTY_INT_ARRAY;

    /**
     * Current number of elements stored in the set ({@link #dense}).
     */
    public int elementsCount;

    /**
     * Buffer resizing strategy.
     */
    protected final ArraySizingStrategy resizer;

    /**
     * Create with default sizing strategy and initial dense capacity of 
     * {@value #DEFAULT_CAPACITY} elements and initial sparse capacity of zero (first insert
     * will cause reallocation).
     * 
     * @see BoundedProportionalArraySizingStrategy
     */
    public IntDoubleLinkedSet()
    {
        this(DEFAULT_CAPACITY, 0);
    }

    /**
     * Create with default sizing strategy and the given initial capacity.
     * 
     * @see BoundedProportionalArraySizingStrategy
     */
    public IntDoubleLinkedSet(int denseCapacity, int sparseCapacity)
    {
        this(denseCapacity, sparseCapacity, new BoundedProportionalArraySizingStrategy());
    }

    /**
     * Create with a custom dense array resizing strategy.
     */
    public IntDoubleLinkedSet(int denseCapacity, int sparseCapacity, ArraySizingStrategy resizer)
    {
        assert denseCapacity >= 0 : "denseCapacity must be >= 0: " + denseCapacity;
        assert sparseCapacity >= 0 : "sparseCapacity must be >= 0: " + sparseCapacity;
        assert resizer != null;

        this.resizer = resizer;
        ensureDenseCapacity(resizer.round(denseCapacity));
        ensureSparseCapacity(sparseCapacity);
    }

    /**
     * Ensures the internal dense buffer has enough free slots to store
     * <code>expectedAdditions</code>.
     */
    protected final void ensureDenseCapacity(int expectedAdditions)
    {
        final int bufferLen = (dense == null ? 0 : dense.length);
        final int elementsCount = size();
        if (elementsCount + expectedAdditions >= bufferLen)
        {
            final int newSize = resizer.grow(bufferLen, elementsCount, expectedAdditions);
            assert newSize >= elementsCount + expectedAdditions : "Resizer failed to" +
                    " return sensible new size: " + newSize + " <= " 
                    + (elementsCount + expectedAdditions);

            final int [] newBuffer = new int [newSize];
            if (bufferLen > 0)
            {
                System.arraycopy(dense, 0, newBuffer, 0, elementsCount);
            }
            this.dense = newBuffer;
        }
    }

    /**
     * Ensures the internal sparse buffer has enough free slots to store
     * index of <code>value</code>.
     */
    protected final void ensureSparseCapacity(int value)
    {
        assert value >= 0 : "value must be >= 0: " + value;

        if (value >= sparse.length)
        {
            final int [] newBuffer = new int [value + 1];
            if (sparse.length > 0)
            {
                System.arraycopy(sparse, 0, newBuffer, 0, sparse.length);
            }
            this.sparse = newBuffer;
        }
    }

    @Override
    public int size()
    {
        return elementsCount;
    }

    @Override
    public int [] toArray()
    {
        int [] result = new int [size()];
        System.arraycopy(dense, 0, result, 0, size());
        return result;
    }

    @Override
    public boolean isEmpty()
    {
        return size() == 0;
    }

    @Override
    public void clear()
    {
        this.elementsCount = 0;
    }

    @Override
    public boolean contains(int value)
    {
        int index;
        return value >= 0
            && value < sparse.length
            && (index = sparse[value]) < elementsCount
            && dense[index] == value;
    }

    @Override
    public boolean add(int value)
    {
        assert value >= 0 : "Double linked set supports values >= 0 only.";

        final boolean containsAlready = contains(value);  
        if (!containsAlready)
        {
            // TODO: check if a fixed-size set is faster without these checks?
            ensureDenseCapacity(1);
            ensureSparseCapacity(value);
            
            sparse[value] = elementsCount;
            dense[elementsCount++] = value;
        }
        return !containsAlready;
    }

    /**
     * Adds two elements to the set.
     */
    public int add(int e1, int e2)
    {
        int count = 0;
        if (add(e1)) count++;
        if (add(e2)) count++;
        return count;
    }

    /**
     * Vararg-signature method for adding elements to this set.
     * <p><b>This method is handy, but costly if used in tight loops (anonymous 
     * array passing)</b></p>
     * 
     * @return Returns the number of elements that were added to the set
     * (were not present in the set).
     */
    public int add(int... elements)
    {
        int count = 0;
        for (int e : elements)
            if (add(e)) count++;
        return count;
    }

    /**
     * Adds all elements from a given container to this set.
     * 
     * @return Returns the number of elements actually added as a result of this
     * call (not previously present in the set).
     */
    public final int addAll(IntContainer container)
    {
        int count = 0;
        for (IntCursor cursor : container)
        {
            if (add(cursor.value)) count++;
        }

        return count;
    }
    
    /*
     * 
     */
    @Override
    public int removeAllOccurrences(int value)
    {
        if (value >= 0 && value < sparse.length)
        {
            final int slot = sparse[value];
            final int n = elementsCount - 1;
            if (slot <= n && dense[slot] == value)
            {
                // Swap the last value with the removed value.
                final int lastValue = dense[n];
                elementsCount--;
                dense[slot] = lastValue;
                sparse[lastValue] = slot;
                return 1;
            }
        }
        return 0;
    }

    /**
     * An alias for the (preferred) {@link #removeAllOccurrences}.
     */
    public boolean remove(int key)
    {
        return removeAllOccurrences(key) == 1;
    }
    
    @Override
    public Iterator<IntCursor> iterator()
    {
        return new IntArrayList.ValueIterator(dense, 0, size() - 1);
    }

    @Override
    public void forEach(IntProcedure procedure)
    {
        final int max = size();
        final int [] dense = this.dense;
        for (int i = 0; i < max; i++)
        {
            procedure.apply(dense[i]);
        }
    }

    @Override
    public void forEach(IntPredicate predicate)
    {
        final int max = size();
        final int [] dense = this.dense;
        for (int i = 0; i < max; i++)
        {
            if (predicate.apply(dense[i]))
                break;
        }
    }

    @Override
    public int removeAll(IntLookupContainer c)
    {
        int max = size(), removed = 0;
        final int [] dense = this.dense;
        for (int i = 0; i < max;)
        {
            if (c.contains(dense[i])) {
                // Swap the last value with the removed value.
                final int lastValue = dense[--max];
                dense[i] = lastValue;
                sparse[lastValue] = i;
                removed++;
            } else {
                i++;
            }
        }
        this.elementsCount = max;
        return removed;
    }

    @Override
    public int removeAll(IntPredicate predicate)
    {
        int max = size(), removed = 0;
        final int [] dense = this.dense;
        for (int i = 0; i < max;)
        {
            if (predicate.apply(dense[i])) {
                // Swap the last value with the removed value.
                final int lastValue = dense[--max];
                dense[i] = lastValue;
                sparse[lastValue] = i;
                removed++;
            } else {
                i++;
            }
        }
        this.elementsCount = max;
        return removed;
    }

    @Override
    public int retainAll(IntLookupContainer c)
    {
        int max = size(), removed = 0;
        final int [] dense = this.dense;
        for (int i = 0; i < max;)
        {
            if (!c.contains(dense[i])) {
                // Swap the last value with the removed value.
                final int lastValue = dense[--max];
                dense[i] = lastValue;
                sparse[lastValue] = i;
                removed++;
            } else {
                i++;
            }
        }
        this.elementsCount = max;
        return removed;
    }

    @Override
    public int retainAll(final IntPredicate predicate)
    {
        return removeAll(new IntPredicate()
        {
            public boolean apply(int value)
            {
                return !predicate.apply(value);
            };
        });
    }
}
