/*
 *  CollectionUtils, Currently only consists of the ListMap.class.
 *  Copyright (C) 2016 N247S
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.n247s.util.collection;

import java.io.IOException;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;

import com.n247s.util.collection.ListMap.SubMap;


/**
 * This Map acts like an regular {@link ArrayList} type, but with the difference
 * that it holds key and values instead of single entries. This Map is not about
 * the best performance, but about the best control over its (duplicated)
 * entries.<br>
 * <br>
 * Basically this Map a ListWrapper adapting to the Maps functionality. Meaning
 * it is not as efficient as other Map implementations might be. But it offers
 * advanced key/value control in return. Since Keys aren't the most important
 * object of the Map it is possible to replace keys just like values. Although
 * this means the behavior of this Map does differ the intended behavior of the
 * Map<br>
 * <br>
 * This Map supports {@link SubMap SubMaps} (including tailMaps and headMaps).
 * These SubMaps are backed up by the main ListMap, meaning they will be updated
 * once the ListMap changes and visa versa.<br>
 * <br>
 * Since the keys are not that important for identifying (because of the
 * indexing), the (key/value/entry)Views of this map have more operation support
 * as regular Views, Such as <tt>add</tt>, <tt>addAll</tt>, <tt>put</tt>,
 * <tt>putAll</tt>
 * 
 * @param <K>
 *            the type of keys maintained by this map
 * @param <V>
 *            the type of mapped values
 * 
 * @author N247S
 * @version 1.2
 */
public class ListMap<K, V> implements Map<K, V>, Serializable
{

	private static final long								serialVersionUID	= 6227762186134413555L;

	protected transient ListMapEntrySet						entrySet;
	protected transient ListMapKeySet						keySet;
	protected transient ListMapValueSet						valueSet;
	protected transient List<WeakReference<SubMap<K, V>>>	_subMapInstances;

	protected transient int									_modCount;

	/** Constructs a ListMap using the given List as backingList */
	public ListMap(List<ListMapEntry<K, V>> backingList)
	{
		if(backingList != null)
			this.entrySet = new ListMapEntrySet(backingList);
		else this.entrySet = new ListMapEntrySet();
		this.keySet = new ListMapKeySet();
		this.valueSet = new ListMapValueSet();
		this._subMapInstances = new ArrayList<WeakReference<SubMap<K, V>>>();
		this._modCount = 0;
	}

	/** Constructs a ListMap using an ArrayList as backingList */
	public ListMap()
	{
		this((List<ListMapEntry<K, V>>)null);
	}

	/**
	 * Constructs a ListMap using the given List as backingList and puts the
	 * Entries of the given map into the backingList in the order which the
	 * iterator presents them.
	 */
	public ListMap(Map<? extends K, ? extends V> map, List<ListMapEntry<K, V>> backingList)
	{
		this(backingList);
		if(map != null)
			map.entrySet().forEach(E -> this.add(E.getKey(), E.getValue()));
	}

	/**
	 * Constructs a ListMap and puts the Entries of the given map into the
	 * backingList in the order which the iterator presents them.
	 */
	public ListMap(Map<? extends K, ? extends V> map)
	{
		this(map, null);
	}

	/**
	 * Adds a new entry at the end of this Map.
	 * 
	 * @param key
	 *            The key of the new entry.
	 * @param value
	 *            The value of the new entry.
	 */
	public void add(K key, V value)
	{
		this.put(key, value);
	}

	/**
	 * Adds the entries from the given Map at the end of this Map in the order
	 * the Iterator is presenting the map entries.
	 * 
	 * @param map
	 */
	public void addAll(Map<? extends K, ? extends V> m)
	{
		this.putAll(m);
	}

	/**
	 * Puts a new Entry at the end of this Map. In contrast to the 'usual'
	 * behavior of a Map, this method won't replace any values.
	 * 
	 * @param key
	 *            The key of the new entry.
	 * @param value
	 *            The value of the new entry.
	 * @return Always {@code null}
	 */
	@Override
	public V put(K key, V value)
	{
		this.put(this.size(), key, value);
		return null;
	}

	/**
	 * Puts a new Entry at the given index in this Map and shifts the remaining
	 * entries in the Map by one.
	 * 
	 * @param index
	 *            The position where this entry should be put.
	 * @param key
	 *            The key of the new entry.
	 * @param value
	 *            The value of the new entry.
	 * @throws IndexOutOfBoundsException
	 *             if index < 0 || index > {@link #size()}
	 */
	public void put(int index, K key, V value)
	{
		if(index < 0 || index > this.entrySet.size())
			throw new IndexOutOfBoundsException(String.format("Index: %s, Size: %s", index, this.entrySet.size()));

		this.entrySet.entryList.add(index, new ListMapEntry<K, V>(key, value));
		this._commitEdit("Put", index);
		this._modCount++;
	}

	/**
	 * Puts in all the given entries from the given Map in the order the
	 * Iterator is presenting the Map's entries.
	 * 
	 * @param map
	 *            The map of entries that should be placed into this Map.
	 */
	@Override
	public void putAll(Map<? extends K, ? extends V> m)
	{
		m.entrySet().forEach(E -> this.add(E.getKey(), E.getValue()));
	}

	/**
	 * Puts in all the given entries from the given map in the order the
	 * Iterator is presenting the Map's entries, starting from the given index.
	 * The remaining entries that were already in the list will shift the amount
	 * of entries that are present in the given Map.
	 * 
	 * @param index
	 *            The index where to start implementing the given map
	 * @param map
	 *            The map of entries that should be placed into this Map.
	 * @throws IndexOutOfBoundsException
	 *             if index < 0 || index > {@link #size()}
	 */
	public void putAll(int index, Map<? extends K, ? extends V> map)
	{
		if(index < 0 || index > this.entrySet.size())
			throw new IndexOutOfBoundsException(String.format("Index: %s, Size: %s", index, this.entrySet.size()));

		ArrayList<Entry<? extends K, ? extends V>> l = new ArrayList<Entry<? extends K, ? extends V>>(map.entrySet());
		Collections.reverse(l);

		for(Entry<? extends K, ? extends V> entry : l)
			this.put(index, entry.getKey(), entry.getValue());
	}

	/**
	 * Replaces the value of the first entry with the same value and key as the
	 * given key and Value.
	 * 
	 * @param key
	 *            The key of the entry that the value should be set of.
	 * @param oldValue
	 *            The value the entry should have.
	 * @param newValue
	 *            The new value that should be bound to the specific entry.
	 * @return {@code true} if the specified entry was present in this Map.
	 */
	@Override
	public boolean replace(K key, V oldValue, V newValue)
	{
		for(ListMapEntry<K, V> entry : this.entrySet.entryList)
		{
			if(entry.compareKey(key))
				if(entry.compareValue(oldValue))
				{
					entry.setValue(newValue);
					this._modCount++;
					return true;
				}
		}
		return false;
	}

	/**
	 * Replaces the value of the first Entry with the given key.
	 * 
	 * @param key
	 *            The key which value should be bound to.
	 * @param value
	 *            The value to be bound.
	 * @return The value that was previously bound to the given key, or
	 *         {@code null} if there was no mapping for the given key. Note that
	 *         a returned {@code null} value might also mean that the previous
	 *         value was {@code null}!
	 */
	@Override
	public V replace(K key, V value)
	{
		for(ListMapEntry<K, V> entry : this.entrySet.entryList)
			if(entry.compareKey(key))
			{
				V oldValue = entry.setValue(value);
				this._modCount++;
				return oldValue;
			}
		return null;
	}

	/**
	 * Sets the value at the given index.
	 * 
	 * @param index
	 *            The index of the entry which value should be set.
	 * @param value
	 *            The value to be set.
	 * @return The value that was previous at the given index.
	 * @throws IndexOutOfBoundsException
	 *             if index < 0 || index > {@link #size()}
	 */
	public V setValue(int index, V value)
	{
		if(index < 0 || index > this.entrySet.size())
			throw new IndexOutOfBoundsException(String.format("Index: %s, Size: %s", index, this.entrySet.size()));

		V oldValue = this.entrySet.entryList.get(index).setValue(value);
		this._modCount++;
		return oldValue;
	}

	/**
	 * Sets the value of the first key that is equal to the given key.
	 * 
	 * @param key
	 *            The key which value should be bound to.
	 * @param value
	 *            The value to be bound.
	 * @return The value that was previously bound to the given key, or
	 *         {@code null} if there was no mapping for the given key. Note that
	 *         a returned {@code null} value might also mean that the previous
	 *         value was {@code null}!
	 */
	public V setValue(K key, V value)
	{
		for(ListMapEntry<K, V> entry : this.entrySet.entryList)
			if(entry.compareKey(key))
			{
				V oldValue = entry.setValue(value);
				this._modCount++;
				return oldValue;
			}
		return null;
	}

	/**
	 * Sets the value of the key at the index of the duplicated keys that occurs
	 * in the map. If there is a Map containing 4 times Object
	 * {@code exampleKey}, than {@code listMap.setValue(2, exampleKey, value);}
	 * will set the value of the third key that is equal to {@code exampleKey}.
	 * If the given index is bigger than the amount of duplicated keys, an
	 * exception is thrown.
	 * 
	 * @param index
	 *            The index of this key of the duplicated keys this Map
	 *            contains.
	 * @param key
	 *            The key the value should be bound to.
	 * @param value
	 *            The value that should be bound to the key.
	 * @return The value that was previous bound to the specified key.
	 * @throws IndexOutOfBoundsException
	 *             if index < 0 || index > amount of keys that are equal to the
	 *             given key.
	 */
	public V setValue(int index, K key, V value)
	{
		int frequency = this.keyfrequency(key);
		if(index < 0 || index >= frequency)
			throw new IndexOutOfBoundsException(String.format("Index: %s, frequency: %s", index, frequency));

		for(ListMapEntry<K, V> entry : this.entrySet.entryList)
		{
			if(entry.compareKey(key))
				--index;
			if(index < 0)
			{
				V oldValue = entry.setValue(value);
				this._modCount++;
				return oldValue;
			}
		}
		// something has changed between the frequency check and the iteration.
		throw new ConcurrentModificationException();
	}

	/**
	 * Sets the value of the first entry that has the same key and value that is
	 * given key and value.
	 * 
	 * @param key
	 *            The key of the entry that the value should be set of.
	 * @param oldValue
	 *            The value the entry should have.
	 * @param newValue
	 *            The new value that should be bound to the specific entry.
	 * @return {@code true} if the specified entry was present in this Map.
	 */
	public boolean setValue(K key, V oldValue, V newValue)
	{
		for(ListMapEntry<K, V> entry : this.entrySet.entryList)
		{
			if(entry.compareKey(key))
				if(entry.compareValue(oldValue))
				{
					entry.setValue(newValue);
					this._modCount++;
					return true;
				}
		}
		return false;
	}

	/**
	 * Sets the value of the entry at the index of the duplicated entries that
	 * occurs in the Map. If there is a Map containing 4 times exact the same
	 * entries , than
	 * {@code listMap.setValue(2, dupeEntryKey, dupeEntryValue, newEntryValue);}
	 * will set the value of the third entry that is equal to the specified
	 * entry. If the given index is bigger than the amount of the specified
	 * duplicated entries, an exception will be thrown.
	 * 
	 * @param index
	 *            The index of the entry of the duplicated entries.
	 * @param key
	 *            The key of the entry that the value should be set of.
	 * @param oldValue
	 *            The value that should be set to the entry.
	 * @param newValue
	 *            The new value that should be bound to the specific entry.
	 * @return {@code true} if the specified entry was present in this Map.
	 * @throws IndexOutOfBoundsException
	 *             if index < 0 || index > the amount of the specified
	 *             duplicated entries.
	 */
	public boolean setValue(int index, K key, V oldValue, V newValue)
	{
		int frequency = this.entryfrequency(key, oldValue);
		if(index < 0 || index >= frequency)
			throw new IndexOutOfBoundsException(String.format("Index: %s, frequency: %s", index, frequency));

		for(ListMapEntry<K, V> entry : this.entrySet.entryList)
		{
			if(entry.compareKey(key))
			{
				if(entry.compareValue(oldValue))
					--index;
				if(index < 0)
				{
					entry.setValue(newValue);
					this._modCount++;
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Replaces the key at the given index in this Map.
	 * 
	 * @param index
	 *            The index of the key that should be replaced.
	 * @param newKey
	 *            The Key which the old key should be replaced with.
	 * @return The key that is replaced.
	 * @throws IndexOutOfBoundsException
	 *             if index < 0 || index > {@link #size()}
	 */
	public K replaceKey(int index, K newKey)
	{
		if(index < 0 || index > this.entrySet.size())
			throw new IndexOutOfBoundsException(String.format("Index: %s, Size: %s", index, this.entrySet.size()));

		K oldKey = this.entrySet.entryList.get(index).setKey(newKey);
		this._modCount++;
		return oldKey;
	}

	/**
	 * Replaces the first key in this Map that is equal to the given key
	 * 
	 * @param key
	 * @param newKey
	 *            The Key which the old key should be replaced with.
	 * @return {@code true} if the specified entry was present in this Map, and
	 *         its key was replaced.
	 */
	public boolean replaceKey(K key, K newKey)
	{
		for(ListMapEntry<K, V> entry : this.entrySet.entryList)
			if(entry.compareKey(key))
			{
				entry.setKey(newKey);
				this._modCount++;
				return true;
			}
		return false;
	}

	/**
	 * Replaces the key at the index of the duplicated keys that occurs in the
	 * map. If there is a Map containing 4 times Object {@code exampleKey}, than
	 * {@code listMap.replaceKey(2, exampleKey, value);} will replace the third
	 * key that is equal to {@code exampleKey}. If the given index is bigger
	 * than the amount of duplicated keys, an exception is thrown.
	 * 
	 * @param index
	 *            The index of this key of the duplicated keys this Map
	 *            contains.
	 * @param key
	 *            The key the value should be replaced.
	 * @param newKey
	 *            The new key.
	 * @return {@code true} if the specified entry was present in this Map, and
	 *         its key was replaced.
	 * @throws IndexOutOfBoundsException
	 *             if index < 0 || index > amount of keys that are equal to the
	 *             given key.
	 */
	public boolean replaceKey(int index, K key, K newKey)
	{
		int frequency = this.keyfrequency(key);
		if(index < 0 || index >= frequency)
			throw new IndexOutOfBoundsException(String.format("Index: %s, frequency: %s", index, frequency));

		for(ListMapEntry<K, V> entry : this.entrySet.entryList)
		{
			if(entry.compareKey(key))
				--index;
			if(index < 0)
			{
				entry.setKey(newKey);
				this._modCount++;
				return true;
			}
		}
		return false;
	}

	/**
	 * Sets the Key of the first entry that has the same key and value that is
	 * given key and value.
	 * 
	 * @param oldKey
	 *            The key of the entry that the value should be set of.
	 * @param value
	 *            The value the entry should have.
	 * @param newKey
	 *            The new Key that should be set to the specific entry.
	 * @return {@code true} if the specified entry was present in this Map, and
	 *         its key was replaced.
	 */
	public boolean replaceKey(K oldKey, V value, K newKey)
	{
		for(ListMapEntry<K, V> entry : this.entrySet.entryList)
		{
			if(entry.compareKey(oldKey))
				if(entry.compareValue(value))
				{
					entry.setKey(newKey);
					this._modCount++;
					return true;
				}
		}
		return false;
	}

	/**
	 * Sets the Key of the entry at the index of the duplicated entries that
	 * occurs in the Map. If there is a Map containing 4 times exact the same
	 * entries , than
	 * {@code listMap.replaceKey(2, dupeEntryKey, dupeEntryValue, newEntryKey);}
	 * will set the key of the third entry that is equal to the specified entry.
	 * If the given index is bigger than the amount of the specified duplicated
	 * entries, an exception will be thrown.
	 * 
	 * @param index
	 *            The index of the entry of the duplicated entries.
	 * @param oldKey
	 *            The key of the entry that the key should be set from.
	 * @param value
	 *            The value of the entry that the should be set from.
	 * @param newKey
	 *            The new key that should be set to the specific entry.
	 * @return {@code true} if the specified entry was present in this Map, and
	 *         its key was replaced.
	 * @throws IndexOutOfBoundsException
	 *             if index < 0 || index > the amount of the specified
	 *             duplicated entries.
	 */
	public boolean replaceKey(int index, K oldKey, V value, K newKey)
	{
		int frequency = this.entryfrequency(oldKey, value);
		if(index < 0 || index >= frequency)
			throw new IndexOutOfBoundsException(String.format("Index: %s, frequency: %s", index, frequency));

		for(ListMapEntry<K, V> entry : this.entrySet.entryList)
		{
			if(entry.compareKey(oldKey))
			{
				if(entry.compareValue(value))
					--index;
				if(index < 0)
				{
					entry.setKey(newKey);
					this._modCount++;
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * @param index
	 *            The index of the entry that should be removed.
	 * @throws IndexOutOfBoundsException
	 *             if index < 0 || index > {@link #size()}
	 */
	public void remove(int index)
	{
		if(index < 0 || index > this.entrySet.size())
			throw new IndexOutOfBoundsException(String.format("Index: %s, Size: %s", index, this.entrySet.size()));

		this.entrySet.entryList.remove(index);
		this._commitEdit("Remove", index);
		this._modCount++;
	}

	/**
	 * This will remove the first entry with a key that is equal to the given
	 * key.
	 * 
	 * @param key
	 *            The key of the entry that should be removed.
	 * @return The value of the removed entry, or {@code null} if the given key
	 *         could not be found. Note, that a returned {@code null} value may
	 *         also mean that the value of the removed Entry was {@code null}!
	 */
	@Override
	public V remove(Object key)
	{
		ListMapEntry<K, V> entry;
		for(int i = 0; i < this.size(); i++)
		{
			entry = this.entrySet.entryList.get(i);
			if(entry.compareKey(key))
			{
				V value = entry.getValue();
				this.remove(i);
				return value;
			}
		}
		return null;
	}

	/**
	 * Removes the Entry with the key at the index of the duplicated keys that
	 * occurs in the map. If there is a Map containing 4 times Object
	 * {@code exampleKey}, than {@code listMap.remove(2, exampleKey);} will
	 * remove the third entry with the key that is equal to {@code exampleKey}.
	 * If the given index is bigger than the amount of duplicated keys, an
	 * exception is thrown.
	 * 
	 * @param index
	 *            The index of this key of the duplicated keys this Map
	 *            contains.
	 * @param key
	 *            The key the value should be replaced.
	 * @return The value of the removed entry, or {@code null} if the given key
	 *         could not be found. Note, that a returned {@code null} value may
	 *         also mean that the value of the removed Entry was {@code null}!
	 * @throws IndexOutOfBoundsException
	 *             if index < 0 || index > amount of keys that are equal to the
	 *             given key.
	 */
	public V remove(int index, K key)
	{
		int frequency = this.keyfrequency(key);
		if(index < 0 || index >= frequency)
			throw new IndexOutOfBoundsException(String.format("Index: %s, frequency: %s", index, frequency));

		ListMapEntry<K, V> entry;
		for(int i = 0; i < this.size(); i++)
		{
			entry = this.entrySet.entryList.get(i);
			if(entry.compareKey(key))
				--index;
			if(index < 0)
			{
				V value = entry.getValue();
				this.remove(i);
				return value;
			}
		}
		// Something has changed between the frequency check and the iteration.
		throw new ConcurrentModificationException();
	}

	@Override
	public boolean remove(Object key, Object value)
	{
		ListMapEntry<K, V> entry;

		for(int i = 0; i < this.size(); i++)
		{
			entry = this.entrySet.entryList.get(i);
			if(entry.compareKey(key))
				if(entry.compareValue(value))
				{
					this.remove(i);
					return true;
				}
		}
		return false;
	}

	/**
	 * Removes the entry at the index of the duplicated entries that occurs in
	 * the Map. If there is a Map containing 4 times exact the same entries ,
	 * than {@code listMap.remove(2, dupeEntryKey, dupeEntryValue);} will remove
	 * the third entry that is equal to the specified entry.
	 * 
	 * @param index
	 *            The index of the entry of the duplicated entries.
	 * @param key
	 *            The key of the entry that should be removed.
	 * @param value
	 *            The value of the entry that should be removed.
	 * @return {@code true} if the specified entry at the index of the specified
	 *         duplicated entries was present in this Map, {@code false}
	 *         otherwise.
	 * @throws IndexOutOfBoundsException
	 *             if index < 0 || index > the amount of the specified
	 *             duplicated entries.
	 */
	public boolean remove(int index, K key, V value)
	{
		int frequency = this.entryfrequency(key, value);
		if(index < 0 || index >= frequency)
			throw new IndexOutOfBoundsException(String.format("Index: %s, frequency: %s", index, frequency));

		ListMapEntry<K, V> entry;

		for(int i = 0; i < this.size(); i++)
		{
			entry = this.entrySet.entryList.get(i);

			if(entry.compareKey(key))
				if(entry.compareValue(value))
					--index;
			if(index < 0)
			{
				this.remove(i);
				return true;
			}
		}
		return false;
	}

	/**
	 * Removes all the entries with this specific key.
	 * 
	 * @param key
	 * @return The amount of entries that have been removed.
	 */
	public int removeAll(Object key)
	{
		int removedAmount = 0;
		ListMapEntryIterator iterator = this.entrySet.iterator();

		while(iterator.hasNext())
		{
			if(iterator.next().compareKey(key))
			{
				iterator.remove();
				++removedAmount;
			}
		}
		return removedAmount;
	}

	/**
	 * Removes all the entries with the specific key and value
	 * 
	 * @param key
	 * @param value
	 * @return The amount of entries that have been removed
	 */
	public int removeAll(Object key, Object value)
	{
		int removedAmount = 0;
		ListMapEntryIterator iterator = this.entrySet.iterator();

		while(iterator.hasNext())
		{
			if(iterator.next().compareKey(key))
				if(iterator.current().compareValue(value))
				{
					iterator.remove();
					++removedAmount;
				}
		}
		return removedAmount;
	}

	@Override
	public void clear()
	{
		this.entrySet.entryList.clear();
		this._commitEdit("Clear", -1);
		this._modCount++;
	}

	/**
	 * Returns the value of the entry at the given index.
	 * 
	 * @param index
	 * @return The value of the entry at the given index.
	 * @throws IndexOutOfBoundsException
	 *             if index < 0 || index > {@link #size()}
	 */
	public V get(int index)
	{
		if(index < 0 || index > this.entrySet.size())
			throw new IndexOutOfBoundsException(String.format("Index: %s, Size: %s", index, this.entrySet.size()));

		return this.entrySet.entryList.get(index).getValue();
	}

	/**
	 * Returns the value of the first entry with a key thats equal to the given
	 * key.
	 * 
	 * @param key
	 * @return The value bound to the given key, or null if the Map does not
	 *         contain the given key
	 */
	@Override
	public V get(Object key)
	{
		return getOrDefault(key, null);
	}

	/**
	 * This method returns the value of the key at the index of duplicated keys.
	 * If there is a Map containing 4 times Object {@code example}, than
	 * {@code listMap.get(Object, 2);} will return the value of the third key
	 * that is equal to {@code example}.If the given index is bigger than the
	 * amount of duplicated keys, an exception is thrown.
	 * 
	 * @param index
	 *            The index of this key of the duplicated keys this Map
	 *            contains.
	 * @param key
	 *            The key the value is bound to.
	 * @return The value that is bound to the given key, at the index of the
	 *         duplicated keys in this Map.
	 * @throws IndexOutOfBoundsException
	 *             if index < 0 || index > amount of keys that are equal to the
	 *             given key.
	 */
	public V get(int index, K key)
	{
		return getOrDefault(index, key, null);
	}

	/**
	 * Returns the value of the first entry with a key thats equal to the given
	 * key, or the default value if the Map does not contain the given key.
	 * 
	 * @param key
	 * @param defaultValue
	 * @return The value bound to the given key, or the default value if the Map
	 *         does not contain the given key.
	 */
	@Override
	public V getOrDefault(Object key, V defaultValue)
	{
		for(ListMapEntry<K, V> entry : this.entrySet.entryList)
			if(entry.compareKey(key))
				return entry.getValue();
		return defaultValue;
	}

	/**
	 * This method returns the value of the key at the index of duplicated keys.
	 * Or the default value if the Map does not contain the given key. If there
	 * is a Map containing 4 times Object {@code example}, than
	 * {@code listMap.get(Object, 2);} will return the value of the third key
	 * that is equal to {@code example}.If the given index is bigger than the
	 * amount of duplicated keys, an exception is thrown.
	 * 
	 * @param index
	 *            The index of this key of the duplicated keys this Map
	 *            contains.
	 * @param key
	 *            The key the value is bound to.
	 * @return The value that is bound to the given key, at the index of the
	 *         duplicated keys in this Map. or the default value if the Map does
	 *         not contain the given key.
	 * @throws IndexOutOfBoundsException
	 *             if index < 0 || index > amount of keys that are equal to the
	 *             given key.
	 */
	public V getOrDefault(int index, K key, V defaultValue)
	{
		int frequency = this.keyfrequency(key);
		if(index < 0 || index >= frequency)
			throw new IndexOutOfBoundsException(String.format("Index: %s, frequency: %s", index, frequency));

		if(frequency == 0)
			return defaultValue;

		for(ListMapEntry<K, V> entry : this.entrySet.entryList)
		{
			if(entry.compareKey(key))
				--index;
			if(index < 0)
				return entry.getValue();
		}
		// something has changed between the frequency check and the iteration.
		throw new ConcurrentModificationException();
	}

	/**
	 * Returns the key of the entry at the given index.
	 * 
	 * @param index
	 * @return The key of the entry at the given index.
	 * @throws IndexOutOfBoundsException
	 *             if index < 0 || index > {@link #size()}
	 */
	public K getKey(int index)
	{
		if(index < 0 || index > this.entrySet.size())
			throw new IndexOutOfBoundsException(String.format("Index: %s, Size: %s", index, this.entrySet.size()));

		return this.entrySet.entryList.get(index).getKey();
	}

	/**
	 * Checks if this Map contains the given key.
	 * 
	 * @param key
	 * @return {@code true} if this Map contains the given key.
	 */
	@Override
	public boolean containsKey(Object key)
	{
		return this.entrySet.containsKey(key);
	}

	/**
	 * Checks if this Map contains the given value.
	 * 
	 * @param key
	 * @return {@code true} if this Map contains the given value.
	 */
	@Override
	public boolean containsValue(Object value)
	{
		return this.entrySet.containsValue(value);
	}

	/** @return The amount entries containing the given key. */
	public int keyfrequency(Object key)
	{
		int frequency = 0;
		for(ListMapEntry<K, V> entry : this.entrySet.entryList)
			if(entry.compareKey(key))
				frequency++;
		return frequency;
	}

	/** @return The amount of entries containing the given value. */
	public int valuefrequency(Object value)
	{
		int frequency = 0;
		for(ListMapEntry<K, V> entry : this.entrySet.entryList)
			if(entry.compareValue(value))
				frequency++;
		return frequency;
	}

	/**
	 * @return The amount of entries in this Map that contains the given key and
	 *         value
	 */
	public int entryfrequency(K key, V value)
	{
		int frequency = 0;
		for(ListMapEntry<K, V> entry : this.entrySet.entryList)
			if(entry.compareKey(key))
				if(entry.compareValue(value))
					frequency++;
		return frequency;
	}

	@Override
	public int size()
	{
		return this.entrySet.size();
	}

	@Override
	public boolean isEmpty()
	{
		return this.entrySet.isEmpty();
	}

	/**
	 * Returns a {@link Set} view of the mappings contained in this map. The set
	 * is backed by the map, so changes to the map are reflected in the set, and
	 * vice-versa. If the map is modified while an iteration over the set is in
	 * progress (except through the iterator's own <tt>remove</tt> operation, or
	 * through the <tt>setValue</tt> operation on a map entry returned by the
	 * iterator) the results of the iteration are undefined.
	 *
	 * @return a set view of the mappings contained in this map
	 */
	@Override
	public Set<K> keySet()
	{
		return this.keySet;
	}

	/**
	 * Returns a {@link Set} view of the mappings contained in this map. The set
	 * is backed by the map, so changes to the map are reflected in the set, and
	 * vice-versa. If the map is modified while an iteration over the set is in
	 * progress (except through the iterator's own <tt>remove</tt> operation, or
	 * through the <tt>setValue</tt> operation on a map entry returned by the
	 * iterator) the results of the iteration are undefined.
	 *
	 * @return a set view of the mappings contained in this map
	 */
	@Override
	public Collection<V> values()
	{
		return this.valueSet;
	}

	/**
	 * Returns a {@link Set} view of the mappings contained in this map. The set
	 * is backed by the map, so changes to the map are reflected in the set, and
	 * vice-versa. If the map is modified while an iteration over the set is in
	 * progress (except through the iterator's own <tt>remove</tt> operation, or
	 * through the <tt>setValue</tt> operation on a map entry returned by the
	 * iterator) the results of the iteration are undefined.
	 *
	 * @return a set view of the mappings contained in this map
	 */
	public Set<ListMapEntry<K, V>> getEntrySet()
	{
		return this.entrySet;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet()
	{
		// This weird casting is needed since the Compiler doesn't allow direct
		// casting of the EntrySet, while it is valid for the JVM.
		Object set = this.entrySet;
		return (Set<java.util.Map.Entry<K, V>>)set;
	}

	/**
	 * This will return the backing list of this ListMap. Although that changes
	 * to the list are reflected in this map and visa versa, though its highly
	 * recommended not to modify the list since it can break the map!
	 * 
	 * @return The backing list of this map
	 */
	public List<ListMapEntry<K, V>> getBackingList()
	{
		return this.entrySet.entryList;
	}

	/**
	 * Returns a SubMap that is backed up by this Map. Every modification that
	 * is made to the SubMap is represented in the Map and visa versa.<br>
	 * <br>
	 * This is equivalent to
	 * <code>new SubMap(fromIndex, true, toIndex, false)</code>
	 * 
	 * @param fromIndex
	 *            The index from where the SubMap should start.
	 * @param toIndex
	 *            The index where the SubMap should end.
	 * @return
	 */
	public SubMap<K, V> subMap(int fromIndex, int toIndex)
	{
		return new SubMap<K, V>(this, fromIndex, toIndex);
	}

	/**
	 * Returns a SubMap that is backed up by this Map. Every modification that
	 * is made to the SubMap is represented in the Map and visa versa.
	 * 
	 * @param fromIndex
	 *            The index from where the SubMap should start.
	 * @param inclusiveLowKey
	 *            Whether the key at the given index should be included to the
	 *            SubMap.
	 * @param toIndex
	 *            The index where the SubMap should end.
	 * @param inclusiveHighKey
	 *            Whether the key at the given index should be included to the
	 *            SubMap.
	 * @return
	 */
	public SubMap<K, V> subMap(int fromIndex, boolean inclusiveLowKey, int toIndex, boolean inclusiveHighKey)
	{
		return new SubMap<K, V>(this, fromIndex, inclusiveLowKey, toIndex, inclusiveHighKey);
	}

	/**
	 * Returns a SubMap that is backed up by this Map. Every modification that
	 * is made to the SubMap is represented in the Map and visa versa.<br>
	 * <br>
	 * This is equivalent to
	 * <code>new SubMap(fromKey, true, toKey, false)</code>
	 * 
	 * @param fromKey
	 *            The key from where the SubMap should start. Note that it will
	 *            pick the first appearance of the given key!
	 * @param toKey
	 *            The key where the SubMap should end. Note that it will pick
	 *            the first appearance of the given key!
	 * @return
	 * @throws NullPointerException
	 *             If one of the given keys are not present in this Map
	 */
	public SubMap<K, V> subMap(K fromKey, K toKey)
	{
		if(!this.containsKey(fromKey) || !this.containsKey(toKey))
			throw new NullPointerException("Couldn't find the given keys!");

		return this.subMap(fromKey, true, toKey, false);
	}

	/**
	 * Returns a SubMap that is backed up by this Map. Every modification that
	 * is made to the SubMap is represented in the Map and visa versa.
	 * 
	 * @param fromKey
	 *            The key from where the SubMap should start. Note that it will
	 *            pick the first appearance of the given key!
	 * @param toKey
	 *            The key where the SubMap should end. Note that it will pick
	 *            the first appearance of the given key!
	 * @return
	 * @throws NullPointerException
	 *             If one of the given keys are not present in this Map
	 */
	public SubMap<K, V> subMap(K fromKey, boolean inclusiveLowKey, K toKey, boolean inclusiveHighKey)
	{
		if(!this.containsKey(fromKey) || !this.containsKey(toKey))
			throw new NullPointerException("Couldn't find the given keys!");

		int from = -1;
		int to = -1;
		ListMapEntry<K, V> entry;

		for(int i = 0; i < this.size(); i++)
		{
			entry = this.entrySet.entryList.get(i);
			if(entry.compareKey(fromKey) && from > -1)
				from = i;
			if(entry.compareKey(toKey) && to > -1)
				to = i;
			if(from > 0 && to > 0)
				break;
		}
		return new SubMap<K, V>(this, from, inclusiveLowKey, to, inclusiveHighKey);
	}

	/**
	 * Returns a SubMap that is backed up by this Map. Every modification that
	 * is made to the SubMap is represented in the Map and visa versa.<br>
	 * <br>
	 * This is equivalent to
	 * <code>new SubMap(fromIndex, true, toIndex, false)</code>
	 * 
	 * @param fromKeyIndex
	 *            The given key's index of the duplicated keys in this Map.
	 * @param fromKey
	 *            The key from where the SubMap should start.
	 * @param toKeyIndex
	 *            The given key's index of the duplicated keys in this Map.
	 * @param toKey
	 *            The key where the SubMap should end.
	 * @return
	 * @throws NullPointerException
	 *             If one of the given keys are not present in this Map
	 */
	public SubMap<K, V> subMap(int fromKeyIndex, K fromKey, int toKeyIndex, K toKey)
	{
		return this.subMap(fromKeyIndex, fromKey, true, toKeyIndex, toKey, true);
	}

	/**
	 * Returns a SubMap that is backed up by this Map. Every modification that
	 * is made to the SubMap is represented in the Map and visa versa.
	 * 
	 * @param fromKeyIndex
	 *            The given key's index of the duplicated keys in this Map.
	 * @param fromKey
	 *            The key from where the SubMap should start.
	 * @param inclusiveLowKey
	 *            Whether the key at the given index should be included to the
	 *            SubMap.
	 * @param toKeyIndex
	 *            The given key's index of the duplicated keys in this Map.
	 * @param toKey
	 *            The key where the SubMap should end.
	 * @param inclusiveHighKey
	 *            Whether the key at the given index should be included to the
	 *            SubMap.
	 * @return
	 * @throws IndexOutOfBoundsException
	 *             If the given index of duplicated keys are lower than 0, or
	 *             higher than the frequency they appears in the Map .
	 */
	public SubMap<K, V> subMap(int fromKeyIndex, K fromKey, boolean inclusiveLowKey, int toKeyIndex, K toKey, boolean inclusiveHighKey)
	{
		int fromfrequency = this.keyfrequency(fromKey);
		if(fromKeyIndex < 0 || fromKeyIndex > fromfrequency)
			throw new IndexOutOfBoundsException(String.format("frequency %s, Size: %s", fromfrequency, this.entrySet.size()));
		int tofrequency = this.keyfrequency(toKey);
		if(fromKeyIndex < 0 || fromKeyIndex > tofrequency)
			throw new IndexOutOfBoundsException(String.format("frequency %s, Size: %s", tofrequency, this.entrySet.size()));

		int from = -1;
		int to = -1;
		ListMapEntry<K, V> entry;

		for(int i = 0; i < this.size(); i++)
		{
			entry = this.entrySet.entryList.get(i);
			if(entry.compareKey(fromKey) && fromKeyIndex > -1)
				--fromKeyIndex;
			if(entry.compareKey(toKey) && toKeyIndex > -1)
				--toKeyIndex;
			if(fromKeyIndex < 0 && from > -1)
				from = i;
			if(toKeyIndex < 0 && to > -1)
				to = i;
			if(from > 0 && to > 0)
				break;
		}
		return new SubMap<K, V>(this, from, inclusiveLowKey, to, inclusiveHighKey);
	}

	/**
	 * Returns a {@link SubMap tailMap} that is backed up by this Map. Every
	 * modification that is made to the SubMap is represented in the Map and
	 * visa versa.<br>
	 * <br>
	 * This is equivalent to <code>new tailMap(fromIndex, true)</code>
	 * 
	 * @param fromIndex
	 *            The index from where the SubMap should start.
	 * @return
	 */
	public SubMap<K, V> tailMap(int fromIndex)
	{
		return this.tailMap(fromIndex, true);
	}

	/**
	 * Returns a {@link SubMap tailMap} that is backed up by this Map. Every
	 * modification that is made to the SubMap is represented in the Map and
	 * visa versa.<br>
	 * <br>
	 * This is equivalent to <code>new tailMap(fromKey, true)</code>
	 * 
	 * @param fromKey
	 *            The key from where the SubMap should start. Note that it will
	 *            pick the first appearance of the given key!
	 * @return
	 * @throws NullPointerException
	 *             If one of the given key is not present in this Map
	 */
	public SubMap<K, V> tailMap(K fromKey)
	{
		return this.tailMap(fromKey, true);
	}

	/**
	 * Returns a {@link SubMap tailMap} that is backed up by this Map. Every
	 * modification that is made to the SubMap is represented in the Map and
	 * visa versa.
	 * 
	 * @param fromIndex
	 *            The index from where the SubMap should start.
	 * @param inclusiveKey
	 *            Whether the key at the given index should be included to the
	 *            SubMap.
	 * @return
	 */
	public SubMap<K, V> tailMap(int fromIndex, boolean inclusiveKey)
	{
		return new SubMap<K, V>(this, false, fromIndex, inclusiveKey, true, -1, false);
	}

	/**
	 * Returns a {@link SubMap tailMap} that is backed up by this Map. Every
	 * modification that is made to the SubMap is represented in the Map and
	 * visa versa.
	 * 
	 * @param key
	 *            The key from where the SubMap should start. Note that it will
	 *            pick the first appearance of the given key!
	 * @param inclusiveKey
	 *            Whether the key at the given index should be included to the
	 *            SubMap.
	 * @return
	 * @throws NullPointerException
	 *             If one of the given key is not present in this Map
	 */
	public SubMap<K, V> tailMap(K key, boolean inclusiveKey)
	{
		for(int i = 0; i < this.size(); i++)
			if(this.entrySet.entryList.get(i).compareKey(key))
				return this.tailMap(i, inclusiveKey);
		throw new NullPointerException("Couldn't find the given keys!");
	}

	/**
	 * Returns a {@link SubMap TailMap} that is backed up by this Map. Every
	 * modification that is made to the SubMap is represented in the Map and
	 * visa versa.
	 * 
	 * @param keyIndex
	 *            The given key's index of the duplicated keys in this Map.
	 * @param key
	 *            The key from where the SubMap should start.
	 * @param inclusiveKey
	 *            Whether the key at the given index should be included to the
	 *            SubMap.
	 * @return
	 * @throws IndexOutOfBoundsException
	 *             if index < 0 || index > amount of keys that are equal to the
	 *             given key.
	 */
	public SubMap<K, V> tailMap(int keyIndex, K key, boolean inclusiveKey)
	{
		int frequency = this.keyfrequency(key);
		if(keyIndex < 0 || keyIndex > frequency)
			throw new IndexOutOfBoundsException(String.format("frequency %s, Size: %s", frequency, this.entrySet.size()));

		for(int i = 0; i < this.size(); i++)
		{
			if(this.entrySet.entryList.get(i).compareKey(key))
				--keyIndex;
			if(keyIndex < 0)
				return this.tailMap(i, inclusiveKey);
		}
		throw new ConcurrentModificationException();
	}

	/**
	 * Returns a {@link SubMap headMap} that is backed up by this Map. Every
	 * modification that is made to the SubMap is represented in the Map and
	 * visa versa.<br>
	 * <br>
	 * This is equivalent to <code>new headMap(toIndex, false)</code>
	 * 
	 * @param toIndex
	 *            The index where the SubMap should end.
	 * @return
	 */
	public SubMap<K, V> headMap(int toIndex)
	{
		return this.headMap(toIndex, false);
	}

	/**
	 * Returns a {@link SubMap headMap} that is backed up by this Map. Every
	 * modification that is made to the SubMap is represented in the Map and
	 * visa versa.<br>
	 * <br>
	 * This is equivalent to <code>new headMap(toKey, false)</code>
	 * 
	 * @param toIndex
	 *            The key where the SubMap should end. Note that it will pick
	 *            the first appearance of the given key!
	 * @return
	 */
	public SubMap<K, V> headMap(K toKey)
	{
		return this.headMap(toKey, false);
	}

	/**
	 * Returns a {@link SubMap headMap} that is backed up by this Map. Every
	 * modification that is made to the SubMap is represented in the Map and
	 * visa versa.
	 * 
	 * @param toIndex
	 *            The index where the SubMap should end.
	 * @param inclusiveKey
	 *            Whether the key at the given index should be included to the
	 *            SubMap.
	 * @return
	 */
	public SubMap<K, V> headMap(int toIndex, boolean inclusiveKey)
	{
		return new SubMap<K, V>(this, true, -1, true, false, toIndex, inclusiveKey);
	}

	/**
	 * Returns a {@link SubMap headMap} that is backed up by this Map. Every
	 * modification that is made to the SubMap is represented in the Map and
	 * visa versa.
	 * 
	 * @param key
	 *            The key where the SubMap should end. Note that it will pick
	 *            the first appearance of the given key!
	 * @param inclusiveKey
	 *            Whether the key at the given index should be included to the
	 *            SubMap.
	 * @return
	 * @throws NullPointerException
	 *             If one of the given key is not present in this Map
	 */
	public SubMap<K, V> headMap(K key, boolean inclusiveKey)
	{
		for(int i = 0; i < this.size(); i++)
			if(this.entrySet.entryList.get(i).compareKey(key))
				return this.headMap(i, inclusiveKey);
		throw new NullPointerException("Couldn't find the given keys!");
	}

	/**
	 * Returns a {@link SubMap headMap} that is backed up by this Map. Every
	 * modification that is made to the SubMap is represented in the Map and
	 * visa versa.
	 * 
	 * @param keyIndex
	 *            The given key's index of the duplicated keys in this Map.
	 * @param key
	 *            The key where the SubMap should end.
	 * @param inclusiveKey
	 *            Whether the key at the given index should be included to the
	 *            SubMap.
	 * @return
	 * @throws IndexOutOfBoundsException
	 *             if index < 0 || index > amount of keys that are equal to the
	 *             given key.
	 */
	public SubMap<K, V> headMap(int keyIndex, K key, boolean inclusiveKey)
	{
		int frequency = this.keyfrequency(key);
		if(keyIndex < 0 || keyIndex > frequency)
			throw new IndexOutOfBoundsException(String.format("frequency %s, Size: %s", frequency, this.entrySet.size()));

		for(int i = 0; i < this.size(); i++)
		{
			if(this.entrySet.entryList.get(i).compareKey(key))
				--keyIndex;
			if(keyIndex < 0)
				return this.headMap(i, inclusiveKey);
		}
		throw new ConcurrentModificationException();
	}

	/**
	 * This method is copied from AbstractMap#eq(o1, o2). <br>
	 * Utility method for SimpleEntry and SimpleImmutableEntry. Test for
	 * equality, checking for nulls.
	 *
	 * NB: Do not replace with Object.equals until JDK-8015417 is resolved. <br>
	 */
	protected static boolean compareObjects(Object o1, Object o2)
	{
		return o1 == null ? o2 == null : o1.equals(o2);
	}

	/** Used for internally for synchronizing subMaps */
	protected void _commitEdit(String editType, int index)
	{
		Iterator<WeakReference<SubMap<K, V>>> itt = this._subMapInstances.iterator();
		WeakReference<SubMap<K, V>> subMapRef;
		SubMap<K, V> subMap;

		if("Put".equals(editType))
		{
			while(itt.hasNext())
			{
				subMapRef = itt.next();

				if(subMapRef.get() == null)
				{
					itt.remove();
					continue;
				}

				subMap = subMapRef.get();

				if(subMap.isIndexInBounderies(index) && !subMap.toEnd)
					++subMap.toEntry;
			}
		}
		else if("Remove".equals(editType))
		{
			while(itt.hasNext())
			{
				subMapRef = itt.next();

				if(subMapRef.get() == null)
				{
					itt.remove();
					continue;
				}

				subMap = subMapRef.get();
				if(subMap.isIndexInBounderies(index) && !subMap.toEnd)
					--subMap.toEntry;
			}
		}
		else if("clear".equals(editType))
		{
			while(itt.hasNext())
			{
				subMapRef = itt.next();

				if(subMapRef.get() == null)
				{
					itt.remove();
					continue;
				}
				subMap = subMapRef.get();

				subMap.fromEntry = -1;
				subMap.toEntry = -1;
			}
		}
	}

	private void writeObject(java.io.ObjectOutputStream s) throws IOException
	{
		// If there is any hidden stuff
		s.defaultWriteObject();
		this.entrySet.writeObject(s);
	}

	private void readObject(java.io.ObjectInputStream s) throws IOException, ClassNotFoundException
	{
		// If there is any hidden stuff
		s.defaultReadObject();
		this.entrySet.readObject(s);
	}

	public static class ListMapEntry<K, V> implements Entry<K, V>
	{

		K	key;
		V	value;

		public ListMapEntry(K key, V value)
		{
			this.key = key;
			this.value = value;
		}

		@Override
		public K getKey()
		{
			return this.key;
		}

		/**
		 * Replaces the key corresponding to this entry with the specified key
		 * (optional operation). (Writes through to the map.) The behavior of
		 * this call is undefined if the mapping has already been removed from
		 * the map (by the iterator's <tt>remove</tt> operation).
		 *
		 * @param key
		 *            new key to be stored in this entry
		 * @return old key corresponding to the entry
		 */
		public K setKey(K key)
		{
			K oldKey = this.key;
			this.key = key;
			return oldKey;
		}

		@Override
		public V getValue()
		{
			return this.value;
		}

		@Override
		public V setValue(V value)
		{
			V oldValue = this.value;
			this.value = value;
			return oldValue;
		}

		/**
		 * Compares the specified object with this entry for equality. Returns
		 * {@code true} if the given object is also a map entry and the two
		 * entries represent the same mapping. More formally, two entries
		 * {@code e1} and {@code e2} represent the same mapping if
		 * 
		 * <pre>
		 * (e1.getKey() == null ?
		 * 		e2.getKey() == null :
		 * 		e1.getKey().equals(e2.getKey()))
		 * 		&amp;&amp;
		 * 		(e1.getValue() == null ?
		 * 				e2.getValue() == null :
		 * 				e1.getValue().equals(e2.getValue()))
		 * </pre>
		 * 
		 * This ensures that the {@code equals} method works properly across
		 * different implementations of the {@code Map.Entry} interface.
		 *
		 * @param o
		 *            object to be compared for equality with this map entry
		 * @return {@code true} if the specified object is equal to this map
		 *         entry
		 * @see #hashCode
		 */
		@Override
		public boolean equals(Object o)
		{
			if(!(o instanceof Map.Entry<?, ?>))
				return false;

			Map.Entry<?, ?> e = (Map.Entry<?, ?>)o;
			return compareObjects(e.getKey(), this.key) && compareObjects(e.getValue(), this.value);
		}

		/**
		 * Compares the given key with the key in this Entry, using
		 * {@link ListMap#compareObjects(Object, Object)}.
		 * 
		 * @param key
		 *            The key to be compared
		 * @return Whether the key of this entry is equal to the given key
		 */
		public boolean compareKey(Object key)
		{
			return compareObjects(this.key, key);
		}

		/**
		 * Compares the given value with the value in this Entry, using
		 * {@link ListMap#compareObjects(Object, Object)}.
		 * 
		 * @param value
		 *            The value to be compared
		 * @return Whether the value of this entry is equal to the given value
		 */
		public boolean compareValue(Object value)
		{
			return compareObjects(this.value, value);
		}

		/**
		 * Returns the hash code value for this map entry. The hash code of a
		 * map entry {@code e} is defined to be:
		 * 
		 * <pre>
		 * (e.getKey() == null ? 0 : e.getKey().hashCode()) &circ;
		 * 		(e.getValue() == null ? 0 : e.getValue().hashCode())
		 * </pre>
		 * 
		 * This ensures that {@code e1.equals(e2)} implies that
		 * {@code e1.hashCode()==e2.hashCode()} for any two Entries {@code e1}
		 * and {@code e2}, as required by the general contract of
		 * {@link Object#hashCode}.
		 *
		 * @return the hash code value for this map entry
		 * @see #equals
		 */
		@Override
		public int hashCode()
		{
			return (key == null ? 0 : key.hashCode()) ^
					(value == null ? 0 : value.hashCode());
		}

		/**
		 * Returns a String representation of this map entry. This
		 * implementation returns the string representation of this entry's key
		 * followed by the equals character ("<tt>=</tt>") followed by the
		 * string representation of this entry's value.
		 *
		 * @return a String representation of this map entry
		 */
		@Override
		public String toString()
		{
			return key + "=" + value;
		}
	}

	public final class ListMapKeySet extends AbstractSet<K>
	{

		/**
		 * Adds a key to the end of this {@link ListMapKeySet}. The value to
		 * this key will be set to null.
		 * 
		 * @param key
		 * @return Always {@code true}
		 * @see ListMap#add(Object, Object)
		 */
		@Override
		public boolean add(K key)
		{
			ListMap.this.add(key, null);
			return true;
		}

		/**
		 * Adds the keys inside the Collection at the end of this Map. The
		 * values will be set to null
		 * 
		 * @param key
		 * @return Always {@code true}
		 * @see ListMap#addAll(Map)
		 */
		@Override
		public boolean addAll(Collection<? extends K> collection)
		{
			return super.addAll(collection);
		}

		/**
		 * Puts an Entry with the given key at the given index in of this Map,
		 * and shifts the remaining entries by one. The value will be null.
		 * 
		 * @param index
		 * @param key
		 * @see ListMap#put(int, Object, Object)
		 */
		public void put(int index, K key)
		{
			ListMap.this.put(index, key, null);
		}

		/**
		 * Puts All the given keys in this Map starting at the beginIndex, and
		 * shifts the remaining entries by the amount of added entries.
		 * 
		 * @param beginIndex
		 * @param c
		 * @see ListMap#putAll(int, Map)
		 */
		public void putAll(int beginIndex, Collection<? extends K> c)
		{
			ArrayList<K> l = new ArrayList<K>(c);
			Collections.reverse(l);
			for(K k : l)
				this.put(beginIndex, k);
		}

		/**
		 * Replaces the key at the given index in this Map.
		 * 
		 * @param index
		 *            The index of the key that should be replaced.
		 * @param newKey
		 *            The new key.
		 * @return The key that was previously in this place.
		 * @throws IndexOutOfBoundsException
		 *             If index < 0 || index > {@link #size()}
		 */
		public K replace(int index, K newKey)
		{
			return ListMap.this.replaceKey(index, newKey);
		}

		/**
		 * Replaces the first appearance of the given key in this Map.
		 * 
		 * @param oldKey
		 *            The key that should be replaced.
		 * @param newKey
		 *            The new key.
		 * @return {@code true} if the specified entry was present in this Map,
		 *         and its key was replaced.
		 */
		public boolean replace(K oldKey, K newKey)
		{
			return ListMap.this.replaceKey(oldKey, newKey);
		}

		/**
		 * This method replaces the key at the index of duplicated keys. If
		 * there is a Map containing 4 times Object {@code example}, than
		 * {@code listMapKeySet.replace(2, oldKey, newKey);} will replace the
		 * the third key that is equal to {@code example}.If the given index is
		 * bigger than the amount of duplicated keys, an exception is thrown.
		 * 
		 * @param index
		 * @param oldKey
		 * @param newKey
		 * @throws IndexOutOfBoundsException
		 *             If index < 0 || index > amount of keys that are equal to
		 *             the given key.
		 * @return {@code true} if the specified entry was present in this Map,
		 *         and its key was replaced.
		 */
		public boolean replace(int index, K oldKey, K newKey)
		{
			return ListMap.this.replaceKey(index, oldKey, newKey);
		}

		/**
		 * @param index
		 *            The index of the entry that should be removed
		 * @throws IndexOutOfBoundsException
		 *             If index < 0 || index > {@link #size()}
		 */
		public void remove(int index)
		{
			ListMap.this.remove(index);
		}

		/**
		 * Removes the first entry with a key thats equal to the given key.
		 * 
		 * @param key
		 *            The key of the entry that should be removed
		 * @return {@code true} if this Map contained the given key.
		 */
		@Override
		public boolean remove(Object key)
		{
			// Seperate check because of possible null Value's.
			if(this.contains(key))
			{
				ListMap.this.remove(key);
				return true;
			}
			return false;
		}

		/**
		 * This method removes the entry with the given key at the index of
		 * duplicated keys. If there is a Map containing 4 times Object
		 * {@code example}, than {@code listMapKeySet.remove(2, example);} will
		 * remove the third entry with the key that is equal to {@code example}
		 * .If the given index is bigger than the amount of duplicated keys, an
		 * exception is thrown.
		 * 
		 * @param index
		 *            The index of the duplicated keys that should be removed
		 * @param key
		 *            The key of the entry that should be removed
		 * @return {@code true} if this Map contained the given key.
		 * @throws IndexOutOfBoundsException
		 *             If index < 0 || index > amount of keys that are equal to
		 *             the given key.
		 */
		public boolean remove(int index, Object key)
		{
			int frequency = keyfrequency(key);
			if(index < 0 || index > frequency)
				throw new IndexOutOfBoundsException(String.format("Index: %s, frequency: %s", index, frequency));

			return ListMap.this.remove(index, key);
		}

		/**
		 * Removes all the entries in the Map with the given key.
		 * 
		 * @param key
		 *            The key to check for if an entry should be deleted.
		 * @return The amount of deleted entries.
		 */
		public int removeAll(Object key)
		{
			return ListMap.this.removeAll(key);
		}

		/**
		 * This method clears all the entries from this Map.
		 */
		@Override
		public void clear()
		{
			ListMap.this.clear();
		}

		/**
		 * Returns a {@link ListMapKeyIterator} that starts at the begin of the
		 * Map.
		 * 
		 * @return A new {@link ListMapKeyIterator}.
		 */
		@Override
		public Iterator<K> iterator()
		{
			return new ListMapKeyIterator();
		}

		/**
		 * Returns a {@link ListMapKeyIterator} that starts at the given
		 * position in the Map.
		 * 
		 * @param startIndex
		 *            The index where this Iterator should start iterating.
		 * @return A new {@link ListMapKeyIterator}.
		 */
		public Iterator<K> iterator(int startIndex)
		{
			return new ListMapKeyIterator(startIndex);
		}

		@Override
		public int size()
		{
			return ListMap.this.entrySet.size();
		}

		@Override
		public Spliterator<K> spliterator()
		{
			return new ListMapKeySpliterator<K>(ListMap.this, 0, -1, -1);
		}
	}

	public final class ListMapValueSet extends AbstractSet<V>
	{

		/**
		 * Adds a value to the end of this {@link ListMapValueSet}. The key of
		 * this value will be set to null.
		 * 
		 * @param key
		 * @return Always {@code true}
		 * @see ListMap#add(Object, Object)
		 */
		@Override
		public boolean add(V value)
		{
			ListMap.this.add(null, value);
			return true;
		}

		/**
		 * Adds the values inside the Collection at the end of this Map. The
		 * keys will be set to null
		 * 
		 * @param key
		 * @return Always {@code true}
		 * @see ListMap#addAll(Map)
		 */
		@Override
		public boolean addAll(Collection<? extends V> collection)
		{
			return super.addAll(collection);
		}

		/**
		 * Puts an Entry with the given value at the given index in of this Map,
		 * and shifts the remaining entries by one. The key will be null.
		 * 
		 * @param index
		 * @param key
		 * @see ListMap#put(int, Object, Object)
		 */
		public void put(int index, V value)
		{
			ListMap.this.put(index, null, value);
		}

		/**
		 * Puts All the given values in this Map starting at the beginIndex, and
		 * shifts the remaining entries by the amount of added entries.
		 * 
		 * @param beginIndex
		 * @param c
		 * @see ListMap#putAll(int, Map)
		 */
		public void putAll(int beginIndex, Collection<? extends V> c)
		{
			ArrayList<V> l = new ArrayList<V>(c);
			Collections.reverse(l);
			for(V v : l)
				this.put(beginIndex, v);
		}

		/**
		 * Sets the value at the given index in this Map.
		 * 
		 * @param index
		 *            The index of the value that should be replaced.
		 * @param newValue
		 *            The new value.
		 * @return The value that was previously in this place.
		 * @throws IndexOutOfBoundsException
		 *             If index < 0 || index > {@link #size()}
		 */
		public V set(int index, V newValue)
		{
			return ListMap.this.setValue(index, newValue);
		}

		/**
		 * @param index
		 *            The index of the entry that should be removed
		 * @throws IndexOutOfBoundsException
		 *             If index < 0 || index > {@link #size()}
		 */
		public void remove(int index)
		{
			ListMap.this.remove(index);
		}

		/**
		 * Removes all the entries in the Map with the given value.
		 * 
		 * @param value
		 *            The value to check for if an entry should be deleted.
		 * @return The amount of deleted entries.
		 */
		public int removeAll(V value)
		{
			int removedAmount = 0;

			ListMapValueIterator iterator = (ListMap<K, V>.ListMapValueIterator)this.iterator();

			while(iterator.hasNext())
				if(compareObjects(iterator.next(), value))
				{
					iterator.remove();
					removedAmount++;
				}

			return removedAmount;
		}

		/**
		 * This method clears all the entries from this Map.
		 */
		@Override
		public void clear()
		{
			ListMap.this.clear();
		}

		/**
		 * Returns a {@link ListMapValueIterator} that starts at the begin of
		 * the Map.
		 * 
		 * @return A new {@link ListMapValueIterator}.
		 */
		@Override
		public Iterator<V> iterator()
		{
			return new ListMapValueIterator();
		}

		/**
		 * Returns a {@link ListMapValueIterator} that starts at the given
		 * position in the Map.
		 * 
		 * @param startIndex
		 *            The index where this Iterator should start iterating.
		 * @return A new {@link ListMapValueIterator}.
		 */
		public Iterator<V> iterator(int startIndex)
		{
			return new ListMapValueIterator(startIndex);
		}

		@Override
		public int size()
		{
			return ListMap.this.entrySet.size();
		}

		@Override
		public Spliterator<V> spliterator()
		{
			return new ListMapValueSpliterator<V>(ListMap.this, 0, -1, -1);
		}
	}

	public final class ListMapEntrySet extends AbstractSet<ListMapEntry<K, V>>
	{

		protected List<ListMapEntry<K, V>>	entryList;

		/** Default constructor */
		public ListMapEntrySet()
		{
			this.entryList = new ArrayList<ListMapEntry<K, V>>();
		}

		/**
		 * This constructor is added to support custom implementation of a
		 * backing list. An example use of this is the javafx' ObservableList
		 * implementation. This enables the use of this map in combination with
		 * TreeViews and TableViews.
		 * 
		 * @param entryList
		 *            The backing Entry list that should be used for this
		 *            ListMap.
		 */
		public ListMapEntrySet(List<ListMapEntry<K, V>> entryList)
		{
			this.entryList = entryList;
		}

		/**
		 * @param entry
		 * @return Always {@code true}
		 */
		@Override
		public boolean add(ListMapEntry<K, V> entry)
		{
			if(entry == null)
				throw new NullPointerException();

			ListMap.this.add(entry.getKey(), entry.getValue());
			return true;
		}

		/** Adds a new entry at the end of this Map */
		public void add(K key, V value)
		{
			ListMap.this.add(key, value);
		}

		/**
		 * @param collection
		 * @return Always {@code true}
		 */
		@Override
		public boolean addAll(Collection<? extends ListMapEntry<K, V>> collection)
		{
			if(collection.contains(null))
				throw new NullPointerException();

			collection.forEach(E -> this.add(E));
			return true;
		}

		/**
		 * Adds the entries from the given Map at the end of this Map in the
		 * order the Iterator is presenting the map entries
		 */
		public void addAll(Map<? extends K, ? extends V> map)
		{
			map.entrySet().forEach(E -> this.add(E.getKey(), E.getValue()));
		}

		/**
		 * Puts a new Entry at the given index in this Map. and shifts the
		 * remaining entries in the Map by one.
		 * 
		 * @param index
		 *            The position where this entry should be put.
		 * @param key
		 *            The key which the new entry.
		 * @param value
		 *            The value which the new entry.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > {@link #size()}
		 */
		public void put(int index, K key, V value)
		{
			ListMap.this.put(index, key, value);
		}

		/**
		 * Puts all the given entries from the given map in the order the
		 * Iterator is presenting the Map entries, starting from the given
		 * index. The remaining entries that were already in the list will shift
		 * the amount of entries that are present in the given Map.
		 * 
		 * @param index
		 *            The index where to start implementing the given map
		 * @param map
		 *            The map of entries that should be placed into this Map.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > {@link #size()}
		 */
		public void putAll(int index, Map<? extends K, ? extends V> map)
		{
			ListMap.this.putAll(index, map);
		}

		/**
		 * Sets the value at the given index.
		 * 
		 * @param index
		 *            The index of the entry which value should be set.
		 * @param value
		 *            The value to be set.
		 * @return The value that was previous at the given index.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > {@link #size()}
		 */
		public V setValue(int index, V value)
		{
			return ListMap.this.setValue(index, value);
		}

		/**
		 * Sets the value of the first key that is equal to the given key.
		 * 
		 * @param key
		 *            The key which value should be bound to.
		 * @param value
		 *            The value to be bound.
		 * @return The value that was previously bound to the given key.
		 * @throws NullPointerException
		 *             If this Map does not contain the given key.
		 */
		public V setValue(K key, V value)
		{
			return ListMap.this.setValue(key, value);
		}

		/**
		 * Sets the value of the key at the index of the duplicated keys that
		 * occurs in the map. If there is a Map containing 4 times Object
		 * {@code exampleKey}, than
		 * {@code listMapEntrySet.setValue(2, exampleKey, value);} will set the
		 * value of the third key that is equal to {@code exampleKey}. If the
		 * given index is bigger than the amount of duplicated keys, an
		 * exception is thrown.
		 * 
		 * @param index
		 *            The index of this key of the duplicated keys this Map
		 *            contains.
		 * @param key
		 *            The key the value should be bound to.
		 * @param value
		 *            The value that should be bound to the key.
		 * @return The value that was previous bound to the specified key.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > amount of keys that are equal to
		 *             the given key.
		 */
		public V setValue(int index, K key, V value)
		{
			return ListMap.this.setValue(index, key, value);
		}

		/**
		 * Sets the value of the first entry that has the same key and value
		 * that is given key and value.
		 * 
		 * @param key
		 *            The key of the entry that the value should be set of.
		 * @param oldValue
		 *            The value the entry should have.
		 * @param newValue
		 *            The new value that should be bound to the specific entry.
		 * @return {@code true} if the specified entry was present in this Map,
		 *         and its value was replaced.
		 */
		public boolean setValue(K key, V oldValue, V newValue)
		{
			return ListMap.this.setValue(key, oldValue, newValue);
		}

		/**
		 * Sets the value of the entry at the index of the duplicated entries
		 * that occurs in the Map. If there is a Map containing 4 times exact
		 * the same entries , than
		 * {@code listMapEntrySet.setValue(2, dupeEntryKey, dupeEntryValue, newEntryValue);}
		 * will set the value of the third entry that is equal to the specified
		 * entry. If the given index is bigger than the amount of the specified
		 * duplicated entries, an exception will be thrown.
		 * 
		 * @param index
		 *            The index of the entry of the duplicated entries.
		 * @param key
		 *            The key of the entry that the value should be set of.
		 * @param oldValue
		 *            The value that should be set to the entry.
		 * @param newValue
		 *            The new value that should be bound to the specific entry.
		 * @return {@code true} if the specified entry was present in this Map,
		 *         and its value was replaced.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > the amount of the specified
		 *             duplicated entries.
		 */
		public boolean setValue(int index, K key, V oldValue, V newValue)
		{
			return ListMap.this.setValue(index, key, oldValue, newValue);
		}

		/**
		 * Replaces the key at the given index in this Map.
		 * 
		 * @param index
		 *            The index of the key that should be replaced.
		 * @param newKey
		 *            The Key which the old key should be replaced with.
		 * @return The key that is replaced.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > {@link #size()}
		 */
		public K replaceKey(int index, K newKey)
		{
			return ListMap.this.replaceKey(index, newKey);
		}

		/**
		 * Replaces the first key in this Map that is equal to the given key
		 * 
		 * @param key
		 * @param newKey
		 *            The Key which the old key should be replaced with.
		 * @return {@code true} if the specified entry was present in this Map.
		 * @throws NullPointerException
		 *             If this Map does not contain the given key, and its key
		 *             was replaced.
		 */
		public boolean replaceKey(K key, K newKey)
		{
			return ListMap.this.replaceKey(key, newKey);
		}

		/**
		 * Replaces the key at the index of the duplicated keys that occurs in
		 * the map. If there is a Map containing 4 times Object
		 * {@code exampleKey}, than
		 * {@code listMapEntrySet.replaceKey(2, exampleKey, value);} will
		 * replace the third key that is equal to {@code exampleKey}. If the
		 * given index is bigger than the amount of duplicated keys, an
		 * exception is thrown.
		 * 
		 * @param index
		 *            The index of this key of the duplicated keys this Map
		 *            contains.
		 * @param key
		 *            The key the value should be replaced.
		 * @param newKey
		 *            The new key.
		 * @return {@code true} if the specified entry was present in this Map,
		 *         and its key was replaced.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > amount of keys that are equal to
		 *             the given key.
		 */
		public boolean replaceKey(int index, K key, K newKey)
		{
			return ListMap.this.replaceKey(index, key, newKey);
		}

		/**
		 * Sets the Key of the first entry that has the same key and value that
		 * is given key and value.
		 * 
		 * @param oldKey
		 *            The key of the entry that the value should be set of.
		 * @param value
		 *            The value the entry should have.
		 * @param newKey
		 *            The new Key that should be set to the specific entry.
		 * @return {@code true} if the specified entry was present in this Map,
		 *         and its key was replaced.
		 */
		public boolean replaceKey(K oldKey, V value, K newKey)
		{
			return ListMap.this.replaceKey(oldKey, value, newKey);
		}

		/**
		 * Sets the Key of the entry at the index of the duplicated entries that
		 * occurs in the Map. If there is a Map containing 4 times exact the
		 * same entries , than
		 * {@code listMapEntrySet.replaceKey(2, dupeEntryKey, dupeEntryValue, newEntryKey);}
		 * will set the key of the third entry that is equal to the specified
		 * entry. If the given index is bigger than the amount of the specified
		 * duplicated entries, an exception will be thrown.
		 * 
		 * @param index
		 *            The index of the entry of the duplicated entries.
		 * @param oldKey
		 *            The key of the entry that the key should be set from.
		 * @param value
		 *            The value of the entry that the should be set from.
		 * @param newKey
		 *            The new key that should be set to the specific entry.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > the amount of the specified
		 *             duplicated entries.
		 */
		public void replaceKey(int index, K oldKey, V value, K newKey)
		{
			ListMap.this.replaceKey(index, oldKey, value, newKey);
		}

		/**
		 * @param index
		 *            The index of the entry that should be removed.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > {@link #size()}
		 */
		public void remove(int index)
		{
			ListMap.this.remove(index);
		}

		/**
		 * Removes the Entry with the key at the index of the duplicated keys
		 * that occurs in the map. If there is a Map containing 4 times Object
		 * {@code exampleKey}, than
		 * {@code listMapEntrySet.remove(2, exampleKey);} will remove the third
		 * entry with the key that is equal to {@code exampleKey}. If the given
		 * index is bigger than the amount of duplicated keys, an exception is
		 * thrown.
		 * 
		 * @param index
		 *            The index of this key of the duplicated keys this Map
		 *            contains.
		 * @param key
		 *            The key the value should be replaced.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > amount of keys that are equal to
		 *             the given key.
		 */
		public V remove(int index, K key)
		{
			return ListMap.this.remove(index, key);
		}

		/**
		 * Removes the first Entry with the same key and value.
		 * 
		 * @param key
		 * @param value
		 * @return {@code true} if this Map contained the specified entry.
		 */
		public boolean remove(Object key, Object value)
		{
			return ListMap.this.remove(key, value);
		}

		/**
		 * Removes the entry at the index of the duplicated entries that occurs
		 * in the Map. If there is a Map containing 4 times exact the same
		 * entries , than
		 * {@code listMapEntrySet.remove(2, dupeEntryKey, dupeEntryValue);} will
		 * remove the third entry that is equal to the specified entry.
		 * 
		 * @param index
		 *            The index of the entry of the duplicated entries.
		 * @param key
		 *            The key of the entry that should be removed.
		 * @param value
		 *            The value of the entry that should be removed.
		 * @return {@code true} if the specified entry at the index of the
		 *         specified duplicated entries was present in this Map. So if
		 *         index < 0 or index > the amount of specified duplicated
		 *         entries, than False is returned.
		 */
		public boolean remove(int index, K key, V value)
		{
			return ListMap.this.remove(index, key, value);
		}

		/**
		 * Removes all the entries with this specific key.
		 * 
		 * @param key
		 * @return The amount of entries that have been removed.
		 */
		public int removeAll(Object key)
		{
			return ListMap.this.removeAll(key);
		}

		/**
		 * Removes all the entries with the specific key and value
		 * 
		 * @param key
		 * @param value
		 * @return The amount of entries that have been removed
		 */
		public int removeAll(K key, V value)
		{
			return ListMap.this.removeAll(key, value);
		}

		@Override
		public void clear()
		{
			ListMap.this.clear();
		}

		/**
		 * Returns a {@link ListMapEntryIterator} that starts at the begin of
		 * the Map.
		 * 
		 * @return A new {@link ListMapEntryIterator}.
		 */
		@Override
		public ListMapEntryIterator iterator()
		{
			return new ListMapEntryIterator();
		}

		/**
		 * Returns a {@link ListMapEntryIterator} that starts at the given
		 * position in the Map.
		 * 
		 * @param startIndex
		 *            The index where this Iterator should start iterating.
		 * @return A new {@link ListMapEntryIterator}.
		 */
		public ListMapEntryIterator iterator(int startIndex)
		{
			return new ListMapEntryIterator(startIndex);
		}

		@Override
		public int size()
		{
			return this.entryList.size();
		}

		/**
		 * Checks if this EntrySet contains the given key.
		 * 
		 * @param key
		 * @return {@code true} if this Map contains the given key.
		 */
		public boolean containsKey(Object key)
		{
			for(ListMapEntry<K, V> entry : this.entryList)
				if(entry.compareKey(key))
					return true;
			return false;
		}

		/**
		 * Checks if this Map contains the given value.
		 * 
		 * @param key
		 * @return {@code true} if this Map contains the given value.
		 */
		public boolean containsValue(Object value)
		{
			for(ListMapEntry<K, V> entry : this.entryList)
				if(entry.compareValue(value))
					return true;
			return false;
		}

		@Override
		public Spliterator<ListMapEntry<K, V>> spliterator()
		{
			return new ListMapEntrySpliterator<ListMapEntry<K, V>>(ListMap.this, 0, -1, -1);
		}

		private void writeObject(java.io.ObjectOutputStream s) throws IOException
		{
			s.writeInt(this.size());
			for(Entry<K, V> currentEntry : this.entryList)
			{
				s.writeObject(currentEntry.getKey());
				s.writeObject(currentEntry.getValue());
			}
		}

		@SuppressWarnings("unchecked")
		private void readObject(java.io.ObjectInputStream s) throws IOException, ClassNotFoundException
		{
			int length = s.readInt();
			for(int i = 0; i < length; i++)
			{
				K key = (K)s.readObject();
				V value = (V)s.readObject();
				this.add(key, value);
			}
		}
	}

	public abstract class ListMapIterator
	{

		protected ListMapEntry<K, V>	current;
		protected int					index;
		protected int					_expectedModCount;

		/**
		 * @param startIndex
		 *            The index where this iterator should start in this Map.
		 */
		public ListMapIterator(int startIndex)
		{
			if(startIndex < 0 || startIndex > ListMap.this.entrySet.size())
				throw new IndexOutOfBoundsException(String.format("StartIndex: %s, Size: %s", startIndex, entrySet.size()));

			this._expectedModCount = ListMap.this._modCount;
			this.index = startIndex;
			this.current = null;
		}

		public ListMapIterator()
		{
			this(0);
		}
		
		/** @return {@code true} if there is a previous entry, {@code false} otherwise. */
		public boolean hasPrevious()
		{
			return this.index > 0;
		}
		
		/** @return The previous index. */
		public int previousIndex()
		{
			return this.index - 1;
		}
		
		/** @return {@code true} if the current element is not removed. */
		public boolean hasCurrent()
		{
			return this.current != null;
		}
		
		/** @return The current index (cursor position). */
		public int currentIndex()
		{
			return this.index;
		}

		/** @return {@code true} if there is a next Entry, {@code false} otherwise. */
		public boolean hasNext()
		{
			return this.index < entrySet.size() - 1;
		}
		
		/** @return The next index. */
		public int nextIndex()
		{
			return this.index + 1;
		}

		protected ListMapEntry<K, V> getNextEntry()
		{
			if(this.index >= ListMap.this.entrySet.size() - 1)
				throw new NoSuchElementException();
			this.checkCoModifications();
			
			if(this.current != null)
				this.current = ListMap.this.entrySet.entryList.get(++this.index);
			else this.current = ListMap.this.entrySet.entryList.get(this.index);
			return this.current;
		}
		
		protected ListMapEntry<K, V> peekForeward()
		{
			if(this.index >= ListMap.this.entrySet.size() - 1)
				throw new NoSuchElementException();
			this.checkCoModifications();
			return ListMap.this.entrySet.entryList.get(this.index + 1);
		}
		
		protected ListMapEntry<K, V> getCurrent()
		{
			if(current == null)
				throw new IllegalStateException();
			this.checkCoModifications();
			return this.current;
		}
		
		protected ListMapEntry<K, V> getPreviousEntry()
		{
			if(this.index <= 0)
				throw new NoSuchElementException();
			this.checkCoModifications();
			
			if(current != null)
				this.current = ListMap.this.entrySet.entryList.get(--this.index);
			else this.current = ListMap.this.entrySet.entryList.get(this.index);
			return this.current;
		}
		
		protected ListMapEntry<K, V> peekBackwards()
		{
			if(this.index <= 0)
				throw new NoSuchElementException();
			this.checkCoModifications();
			return ListMap.this.entrySet.entryList.get(this.index - 1);
		}
		
		protected ListMapEntry<K, V> set(K key, V value)
		{
			this.checkCoModifications();
			if(current == null)
				throw new IllegalStateException();
			return new ListMapEntry<K, V>(this.current.setKey(key), this.current.setValue(value));
		}
		
		protected void add(K key, V value)
		{
			this.checkCoModifications();
			ListMap.this.put(this.index, key, value);
			this.index++;
			this._expectedModCount = ListMap.this._modCount;
		}

		/** Removes the Entry at the current index/cursor. */
		public void remove()
		{
			if(this.current == null)
				throw new IllegalStateException();
			this.checkCoModifications();
			
			current = null;
			ListMap.this.remove(this.index);
			_expectedModCount = ListMap.this._modCount;
		}

		protected void checkCoModifications()
		{
			if(this._expectedModCount != ListMap.this._modCount
					|| this.index < 0 || this.index >= ListMap.this.size())
				throw new ConcurrentModificationException();
		}
	}

	public final class ListMapKeyIterator extends ListMapIterator implements Iterator<K>
	{
		/** @param startIndex The index where this iterator should start in this Map. */
		public ListMapKeyIterator(int startIndex)	{super(startIndex);}
		public ListMapKeyIterator()					{super();}
		public K previous()		{return this.getPreviousEntry().getKey();}
		public K peekPrevious()	{return this.peekBackwards().getKey();}
		public K current()		{return this.getCurrent().getKey();}
		@Override
		public K next()			{return this.getNextEntry().getKey();}
		public K peekNext()		{return this.peekForeward().getKey();}
		public K set(K key)		{return this.set(key, this.getCurrent().getValue()).getKey();}
		public void add(K key)	{this.add(key, null);}
	}

	public final class ListMapValueIterator extends ListMapIterator implements Iterator<V>
	{
		/** @param startIndex The index where this iterator should start in this Map. */
		public ListMapValueIterator(int startIndex)	{super(startIndex);}
		public ListMapValueIterator()				{super();}
		public V previous()		{return this.getPreviousEntry().getValue();}
		public V peekPrevious()	{return this.peekBackwards().getValue();}
		public V current()		{return this.getCurrent().getValue();}
		@Override
		public V next()			{return this.getNextEntry().getValue();}
		public V peekNext()		{return this.peekForeward().getValue();}
		public V set(V value)	{return this.set(this.getCurrent().getKey(), value).getValue();}
		public void add(V value){this.add(null, value);}
	}

	public final class ListMapEntryIterator extends ListMapIterator implements Iterator<ListMapEntry<K, V>>
	{
		/** @param startIndex The index where this iterator should start in this Map. */
		public ListMapEntryIterator(int startIndex)	{super(startIndex);}
		public ListMapEntryIterator()				{super();}
		public ListMapEntry<K, V> previous()			{return this.getPreviousEntry();}
		public ListMapEntry<K, V> peekPrevious()		{return this.peekBackwards();}
		public ListMapEntry<K, V> current()				{return this.getCurrent();}
		@Override
		public ListMapEntry<K, V> next()				{return this.getNextEntry();}
		public ListMapEntry<K, V> peekNext()			{return this.peekForeward();}
		@Override
		public ListMapEntry<K, V> set(K key, V value)	{return super.set(key, value);}
		@Override
		public void add(K key, V value)					{super.add(key, value);}
	}

	public static abstract class ListMapSpliterator<E>
	{

		protected final ListMap<?, ?>	listMap;
		// current index, modified on advance/split
		protected int					index;
		// -1 if not used, then one past last index
		protected int					lowBoundery;
		// -1 if not used; then first index inclusive
		protected int					highBoundery;
		// initialized when fence set
		protected int					_expectedModCount;

		/** Create new spliterator covering the given range */
		public ListMapSpliterator(ListMap<?, ?> listMap, int startIndex, int lowBoundery, int highBoundery)
		{
			if(listMap != null && (highBoundery < 0 ? listMap.size() : highBoundery) < (lowBoundery < 0 ? 0 : lowBoundery))
				throw new IllegalArgumentException("HigherBoundery < lowerBoundery!");
			this.listMap = listMap;
			this.index = startIndex;
			this.highBoundery = highBoundery;
			this.lowBoundery = lowBoundery;
			this._expectedModCount = listMap != null ? listMap._modCount : -1;
		}

		protected abstract Spliterator<E> getNewSpilterator(ListMap<?, ?> listMap, int startIndex, int lowBoundery, int highBoundery);

		protected abstract E get(int index);

		protected void _initBounds()
		{
			if(this.highBoundery < 0)
			{
				if(this.listMap == null)
					highBoundery = 0;
				else
				{
					_expectedModCount = this.listMap._modCount;
					highBoundery = this.listMap.size();
				}
			}

			if(this.lowBoundery < 0)
				if(this.listMap == null)
					this.lowBoundery = -1;
				else this.lowBoundery = 0;
		}

		public Spliterator<E> trySplit()
		{
			this._initBounds();
			int mid = (this.lowBoundery + this.highBoundery) >>> 1;
			if(this.lowBoundery >= mid)
				return null;
			Spliterator<E> split = getNewSpilterator(this.listMap, this.index < mid ? this.index : mid, this.lowBoundery, mid);
			if(this.index < mid)
				this.index = mid;
			this.lowBoundery = mid;
			return split;
		}

		public boolean tryAdvance(Consumer<? super E> action)
		{
			if(action == null)
				throw new NullPointerException();
			this._initBounds();
			if(this.highBoundery < 0 ? this.index < this.listMap.size() : this.index < this.highBoundery)
			{
				E object = this.get(this.index++);
				action.accept(object);
				if(this.listMap._modCount != this._expectedModCount)
					throw new ConcurrentModificationException();
				return true;
			}
			return false;
		}

		public boolean tryWithdraw(Consumer<? super E> action)
		{
			if(action == null)
				throw new NullPointerException();
			this._initBounds();
			if(this.lowBoundery < 0 ? this.index > -1 : this.index >= this.lowBoundery)
			{
				E object = this.get(this.index--);
				action.accept(object);
				if(this.listMap._modCount != this._expectedModCount)
					throw new ConcurrentModificationException();
				return true;
			}
			return false;
		}

		public void forEachRemaining(Consumer<? super E> action)
		{
			if(action == null)
				throw new NullPointerException();
			this._initBounds();
			int expModCount = this._expectedModCount;
			if(this.listMap != null)
				if(this.index >= (this.lowBoundery < 0 ? 0 : this.lowBoundery) && index < (this.highBoundery < 0 ? this.listMap.size() : this.highBoundery))
				{
					for(int i = this.index; i < (this.highBoundery < 0 ? this.listMap.size() : this.highBoundery); ++i)
						action.accept(this.get(i));
					if(this.listMap._modCount == expModCount)
						return;
				}
			throw new ConcurrentModificationException();
		}

		public void forEachPassed(Consumer<? super Object> action)
		{
			if(action == null)
				throw new NullPointerException();
			this._initBounds();
			int expModCount = this._expectedModCount;
			if(this.listMap != null)
				if(this.index >= (this.lowBoundery < 0 ? 0 : this.lowBoundery) && index < (this.highBoundery < 0 ? this.listMap.size() : this.highBoundery))
				{
					for(int i = this.index; i < (this.highBoundery < 0 ? this.listMap.size() : this.highBoundery); ++i)
						action.accept(this.get(i));
					if(this.listMap._modCount == expModCount)
						return;
				}
			throw new ConcurrentModificationException();
		}

		public long estimateSize()
		{
			return (long)(this.highBoundery < 0 ? this.listMap.size() : this.highBoundery - this.index);
		}

		public int characteristics()
		{
			return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
		}
	}

	public static final class ListMapKeySpliterator<K> extends ListMapSpliterator<K> implements Spliterator<K>
	{

		public ListMapKeySpliterator(ListMap<?, ?> listMap, int startIndex, int lowBoundery, int highBoundery)
		{
			super(listMap, startIndex, lowBoundery, highBoundery);
		}

		@Override
		protected Spliterator<K> getNewSpilterator(ListMap<?, ?> listMap, int startIndex, int lowBoundery, int highBoundery)
		{
			return new ListMapKeySpliterator<K>(listMap, startIndex, lowBoundery, highBoundery);
		}

		@SuppressWarnings("unchecked")
		@Override
		protected K get(int index)
		{
			return (K)this.listMap.entrySet.entryList.get(index).getKey();
		}
	}

	public static final class ListMapValueSpliterator<V> extends ListMapSpliterator<V> implements Spliterator<V>
	{

		public ListMapValueSpliterator(ListMap<?, ?> listMap, int startIndex, int lowBoundery, int highBoundery)
		{
			super(listMap, startIndex, lowBoundery, highBoundery);
		}

		@Override
		protected Spliterator<V> getNewSpilterator(ListMap<?, ?> listMap, int startIndex, int lowBoundery, int highBoundery)
		{
			return new ListMapValueSpliterator<V>(listMap, startIndex, lowBoundery, highBoundery);
		}

		@SuppressWarnings("unchecked")
		@Override
		protected V get(int index)
		{
			return (V)this.listMap.entrySet.entryList.get(index).getValue();
		}
	}

	public static final class ListMapEntrySpliterator<E extends ListMapEntry<?, ?>> extends ListMapSpliterator<E> implements Spliterator<E>
	{

		public ListMapEntrySpliterator(ListMap<?, ?> listMap, int startIndex, int lowBoundery, int highBoundery)
		{
			super(listMap, startIndex, lowBoundery, highBoundery);
		}

		@Override
		protected Spliterator<E> getNewSpilterator(ListMap<?, ?> listMap, int startIndex, int lowBoundery, int highBoundery)
		{
			return new ListMapEntrySpliterator<E>(listMap, startIndex, lowBoundery, highBoundery);
		}

		@SuppressWarnings("unchecked")
		@Override
		protected E get(int index)
		{
			return (E)this.listMap.entrySet.entryList.get(index);
		}
	}

	public static class SubMap<K, V> implements Map<K, V>, Serializable
	{

		private static final long	serialVersionUID	= 2220692614896016222L;

		protected ListMap<K, V>		listMap;

		protected int				fromEntry, toEntry;
		protected boolean			fromStart, toEnd;

		/**
		 * @param listMap
		 *            The Map that is backing up this SubMap .
		 * @param fromStart
		 *            Whether this SubMap should start from the beginning of the
		 *            backing Map.
		 * @param fromKey
		 *            The index from where the SubMap should start.
		 * @param inclLowKey
		 *            Whether the key at the given index should be included to
		 *            this SubMap.
		 * @param toEnd
		 *            Whether this SubMap should end at the end of the backing
		 *            Map.
		 * @param toKey
		 *            The index where the SubMap should end.
		 * @param inclHighKey
		 *            Whether the key at the given index should be included to
		 *            this SubMap.
		 */
		public SubMap(ListMap<K, V> listMap, boolean fromStart, int fromKey, boolean inclLowKey, boolean toEnd, int toKey, boolean inclHighKey)
		{
			if(!inclHighKey)
				--toKey;
			if(!inclLowKey)
				++fromKey;

			if((fromKey < 0 && !fromStart) || fromKey >= listMap.size())
				throw new IndexOutOfBoundsException(String.format("LowerKeyIndex: %s. Size: %s", fromKey, listMap.size()));
			if((toKey < 0 && !toEnd) || toKey >= listMap.size())
				throw new IndexOutOfBoundsException(String.format("HigherKeyIndex: %s. Size: %s", toKey, listMap.size()));
			if(toKey < fromKey && !fromStart && !toEnd)
				throw new IllegalArgumentException("HigherKeyIndex < LowerKeyIndex");

			this.listMap = listMap;
			this.fromStart = fromStart;
			this.fromEntry = fromStart ? -1 : fromKey;
			this.toEnd = toEnd;
			this.toEntry = toEnd ? -1 : toKey;
			listMap._subMapInstances.add(new WeakReference<ListMap.SubMap<K, V>>(this));
		}

		/**
		 * @param listMap
		 *            The Map that is backing up this SubMap .
		 * @param fromKey
		 *            The index from where the SubMap should start.
		 * @param inclLowKey
		 *            Whether the key at the given index should be included to
		 *            this SubMap.
		 * @param toKey
		 *            The index where the SubMap should end.
		 * @param inclHighKey
		 *            Whether the key at the given index should be included to
		 *            this SubMap.
		 */
		public SubMap(ListMap<K, V> listMap, int fromKey, boolean inclLowKey, int toKey, boolean inclHighKey)
		{
			this(listMap, false, fromKey, inclLowKey, false, toKey, inclHighKey);
		}

		/**
		 * @param listMap
		 *            The Map that is backing up this SubMap .
		 * @param fromKey
		 *            The index from where the SubMap should start.
		 * @param toKey
		 *            The index where the SubMap should end.
		 */
		public SubMap(ListMap<K, V> listMap, int fromKey, int toKey)
		{
			this(listMap, false, fromKey, true, false, toKey, false);
		}

		/**
		 * Adds an Entry at the end of this SubMap with the given key and value.
		 * The SubMaps boundaries ({@link #toEntry}) will be adjusted to keep
		 * the keys inside the range of this SubMap.
		 * 
		 * @param key
		 * @param value
		 */
		public void add(K key, V value)
		{
			if(this.toEnd)
				this.listMap.add(key, value);
			else this.listMap.put(this.toEntry + 1, key, value);
			// toEntry++ because it falls outside the rangeCheck.
			this.toEntry++;
		}

		/**
		 * Adds the entries from the given Map at the end of this SubMap in the
		 * order the Iterator is presenting the map entries The SubMaps
		 * boundaries ({@link #toEntry}) will be adjusted to keep the keys
		 * inside the range of this SubMap.
		 */
		public void addAll(Map<? extends K, ? extends V> map)
		{
			map.entrySet().forEach(E -> this.add(E.getKey(), E.getValue()));
		}

		/**
		 * Puts a new Entry at the end of this SubMap. In contrast to the
		 * 'usual' behavior of a Map, this method won't replace any values.
		 * 
		 * @param key
		 *            The key of the new entry.
		 * @param value
		 *            The value of the new entry.
		 * @return Always {@code null}
		 */
		@Override
		public V put(K key, V value)
		{
			this.put(this.size(), key, value);
			return null;
		}

		/**
		 * Puts a new Entry at the given index in this SubMap. and shifts the
		 * remaining entries in the Map by one. The {@link SubMap SubMaps}
		 * boundaries ({@link #toEntry}) will be adjusted to keep the keys
		 * inside the range of this SubMap.
		 * 
		 * @param index
		 *            The position where this entry should be put in this
		 *            {@link SubMap SUBMAP}.
		 * @param key
		 *            The key which the new entry.
		 * @param value
		 *            The value which the new entry.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > {@link #size()}
		 */
		public void put(int index, K key, V value)
		{
			if(index < 0 || index > this.size())
				throw new IndexOutOfBoundsException(String.format("Index: %s, Size: %s", index, this.size()));

			this.listMap.put((this.fromStart ? 0 : this.fromEntry) + index, key, value);
		}

		/**
		 * Puts in all the given entries from the given Map in the order the
		 * Iterator is presenting the Map's entries.
		 * 
		 * @param map
		 *            The map of entries that should be placed into this Map.
		 */
		@Override
		public void putAll(Map<? extends K, ? extends V> m)
		{
			m.entrySet().forEach(E -> this.add(E.getKey(), E.getValue()));
		}

		/**
		 * Puts in all the given entries from the given map in the order the
		 * Iterator is presenting the Map entries, starting from the given
		 * index. The remaining entries that were already in the list will shift
		 * the amount of entries that are present in the given Map. The SubMaps
		 * boundaries ({@link #toEntry}) will be adjusted to keep the keys
		 * inside the range of this SubMap.
		 * 
		 * @param index
		 *            The index where to start in this {@link SubMap SUBMAP},
		 *            implementing the given map entries.
		 * @param map
		 *            The map of entries that should be placed into this Map.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > {@link #size()}
		 */
		public void putAll(int index, Map<? extends K, ? extends V> map)
		{
			if(index < 0 || index > this.size())
				throw new IndexOutOfBoundsException(String.format("Index: %s, Size: %s", index, this.size()));

			ArrayList<Entry<? extends K, ? extends V>> l = new ArrayList<Entry<? extends K, ? extends V>>(map.entrySet());
			Collections.reverse(l);

			for(Entry<? extends K, ? extends V> entry : l)
				this.put(index, entry.getKey(), entry.getValue());
		}

		/**
		 * Replaces the value of the first entry with the same value and key as
		 * the given key and Value.
		 * 
		 * @param key
		 *            The key of the entry that the value should be set of.
		 * @param oldValue
		 *            The value the entry should have.
		 * @param newValue
		 *            The new value that should be bound to the specific entry.
		 * @return {@code true} if the specified entry was present in this Map,
		 *         and its value was replaced.
		 */
		@Override
		public boolean replace(K key, V oldValue, V newValue)
		{
			ListMapEntry<K, V> entry;

			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
			{
				entry = this.listMap.entrySet.entryList.get(i);

				if(entry.compareKey(key))
					if(entry.compareValue(oldValue))
					{
						this.listMap.setValue(i, newValue);
						return true;
					}
			}
			return false;
		}

		/**
		 * Replaces the value of the first Entry with the given key.
		 * 
		 * @param key
		 *            The key which value should be bound to.
		 * @param value
		 *            The value to be bound.
		 * @return The value that was previously bound to the given key, or
		 *         {@code null} if there was no mapping for the given key. Note
		 *         that a returned {@code null} value might also mean that the
		 *         previous value was {@code null}!
		 */
		@Override
		public V replace(K key, V value)
		{
			if(!this.containsKey(key))
				throw new NullPointerException("Couldn't find the given key!");

			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
			{
				if(this.listMap.entrySet.entryList.get(i).compareKey(key))
					return this.listMap.setValue(i, value);
			}
			throw new NullPointerException("Couldn't find the given key!");
		}

		/**
		 * Sets the value at the given index.
		 * 
		 * @param index
		 *            The index of the entry in this {@link SubMap SUBMAP} which
		 *            value should be set.
		 * @param value
		 *            The value to be set.
		 * @return The value that was previous at the given index.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > {@link #size()}
		 */
		public V setValue(int index, V value)
		{
			if(index < 0 || index > this.size())
				throw new IndexOutOfBoundsException(String.format("Index: %s, Size: %s", index, this.size()));

			return this.listMap.setValue((this.fromStart ? 0 : this.fromEntry) + index, value);
		}

		/**
		 * Sets the value of the first key in this {@link SubMap SUBMAP} that is
		 * equal to the given key.
		 * 
		 * @param key
		 *            The key which value should be bound to.
		 * @param value
		 *            The value to be bound.
		 * @return The value that was previously bound to the given key, or
		 *         {@code null} if there was no mapping in this SubMap for the
		 *         given key. Note that a returned {@code null} value might also
		 *         mean that the previous value was {@code null}!
		 */
		public V setValue(K key, V value)
		{
			if(!this.containsKey(key))
				throw new NullPointerException("Couldn't find the given key!");

			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
			{
				if(this.listMap.entrySet.entryList.get(i).compareKey(key))
					return this.listMap.setValue(i, value);
			}
			return null;
		}

		/**
		 * Sets the value of the key at the index of the duplicated keys that
		 * occurs in the {@link SubMap SUBMAP}. If there is a SubMap containing
		 * 4 times Object {@code exampleKey}, than
		 * {@code subMap.setValue(2, exampleKey, value);} will set the value of
		 * the third key that is equal to {@code exampleKey}. If the given index
		 * is bigger than the amount of duplicated keys, an exception is thrown.
		 * 
		 * @param index
		 *            The index of this key of the duplicated keys this SubMap
		 *            contains.
		 * @param key
		 *            The key the value should be bound to.
		 * @param value
		 *            The value that should be bound to the key.
		 * @return The value that was previous bound to the specified key.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > amount of keys that are equal to
		 *             the given key.
		 */
		public V setValue(int index, K key, V value)
		{
			int frequency = this.keyfrequency(key);
			if(index < 0 || index > frequency)
				throw new IndexOutOfBoundsException(String.format("Index: %s, frequency: %s", index, frequency));

			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
			{
				if(this.listMap.entrySet.entryList.get(i).compareKey(key))
					--index;
				if(index < 0)
					return this.listMap.setValue(i, value);
			}
			throw new ConcurrentModificationException();
		}

		/**
		 * Sets the value of the first entry in this {@link SubMap SUBMAP} that
		 * has the same key and value that is given key and value.
		 * 
		 * @param key
		 *            The key of the entry that the value should be set of.
		 * @param oldValue
		 *            The value the entry should have.
		 * @param newValue
		 *            The new value that should be bound to the specific entry.
		 * @return {@code true} if the specified entry was present in this
		 *         SubMap, and its value was replaced.
		 */
		public boolean setValue(K key, V oldValue, V newValue)
		{
			ListMapEntry<K, V> entry;

			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
			{
				entry = this.listMap.entrySet.entryList.get(i);

				if(entry.compareKey(key))
					if(entry.compareValue(oldValue))
					{
						this.listMap.setValue(i, newValue);
						return true;
					}
			}
			return false;
		}

		/**
		 * Sets the value of the entry at the index of the duplicated entries
		 * that occurs in the {@link SubMap SUBMAP}. If there is a SubMap
		 * containing 4 times exact the same entries , than
		 * {@code subMap.setValue(2, dupeEntryKey, dupeEntryValue, newEntryValue);}
		 * will set the value of the third entry that is equal to the specified
		 * entry. If the given index is bigger than the amount of the specified
		 * duplicated entries, an exception will be thrown.
		 * 
		 * @param index
		 *            The index of the entry in the {@link SubMap SUBMAP} of the
		 *            duplicated entries.
		 * @param key
		 *            The key of the entry that the value should be set of.
		 * @param oldValue
		 *            The value that should be set to the entry.
		 * @param newValue
		 *            The new value that should be bound to the specific entry.
		 * @return {@code true} if the specified entry was present in this
		 *         SubMap, and its value was replaced.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > the amount of the specified
		 *             duplicated entries.
		 */
		public boolean setValue(int index, K key, V oldValue, V newValue)
		{
			int frequency = this.entryfrequency(key, oldValue);
			if(index < 0 || index > frequency)
				throw new IndexOutOfBoundsException(String.format("Index: %s, frequency: %s", index, frequency));

			ListMapEntry<K, V> entry;

			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
			{
				entry = this.listMap.entrySet.entryList.get(i);
				if(entry.compareKey(key))
				{
					if(entry.compareValue(oldValue))
						--index;
					if(index < 0)
					{
						this.listMap.setValue(i, newValue);
						return true;
					}
				}
			}
			return false;
		}

		/**
		 * Replaces the key at the given index in the {@link SubMap SUBMAP}.
		 * 
		 * @param index
		 *            The index of the key in the {@link SubMap SUBMAP} that
		 *            should be replaced.
		 * @param newKey
		 *            The Key which the old key should be replaced with.
		 * @return The key that is replaced.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > {@link #size()}
		 */
		public K replaceKey(int index, K newKey)
		{
			if(index < 0 || index > this.size())
				throw new IndexOutOfBoundsException(String.format("Index: %s, Size: %s", index, this.listMap.entrySet.size()));

			return this.listMap.replaceKey((this.fromStart ? 0 : this.fromEntry) + index, newKey);
		}

		/**
		 * Replaces the first key in the {@link SubMap SUBMAP} that is equal to
		 * the given key
		 * 
		 * @param key
		 * @param newKey
		 *            The Key which the old key should be replaced with.
		 * @return {@code true} if the specified entry was present in this
		 *         SubMap, and its key was replaced.
		 * @return {@code true} if the specified entry was present in this
		 *         SubMap, and its key was replaced.
		 */
		public boolean replaceKey(K key, K newKey)
		{
			if(!this.listMap.keySet.contains(key))
				throw new NullPointerException("Couldn't find the given key!");

			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
				if(this.listMap.entrySet.entryList.get(i).compareKey(key))
				{
					this.listMap.replaceKey(i, newKey);
					return true;
				}
			return false;
		}

		/**
		 * Replaces the key at the index of the duplicated keys that occurs in
		 * the {@link SubMap SUBMAP}. If there is a SubMap containing 4 times
		 * Object {@code exampleKey}, than
		 * {@code subMap.replaceKey(2, exampleKey, value);} will replace the
		 * third key that is equal to {@code exampleKey}. If the given index is
		 * bigger than the amount of duplicated keys, an exception is thrown.
		 * 
		 * @param index
		 *            The index of this key of the duplicated keys this SubMap
		 *            contains.
		 * @param key
		 *            The key the value should be replaced.
		 * @param newKey
		 *            The new key.
		 * @return {@code true} if the specified entry was present in this
		 *         SubMap, and its key was replaced.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > amount of keys that are equal to
		 *             the given key.
		 */
		public boolean replaceKey(int index, K key, K newKey)
		{
			int frequency = this.keyfrequency(key);
			if(index < 0 || index > frequency)
				throw new IndexOutOfBoundsException(String.format("Index: %s, frequency: %s", index, frequency));

			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
			{
				if(this.listMap.entrySet.entryList.get(i).compareKey(key))
					--index;
				if(index < 0)
				{
					this.listMap.replaceKey(i, newKey);
					return true;
				}
			}
			return false;
		}

		/**
		 * Sets the Key of the first entry that has the same key and value that
		 * is given key and value.
		 * 
		 * @param oldKey
		 *            The key of the entry that the value should be set of.
		 * @param value
		 *            The value the entry should have.
		 * @param newKey
		 *            The new Key that should be set to the specific entry.
		 * @return {@code true} if the specified entry was present in this
		 *         SubMap, and its key was replaced.
		 */
		public boolean replaceKey(K oldKey, V value, K newKey)
		{
			ListMapEntry<K, V> entry;
			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
			{
				entry = this.listMap.entrySet.entryList.get(i);
				if(entry.compareKey(oldKey))
					if(entry.compareValue(value))
					{
						this.listMap.replaceKey(i, newKey);
						return true;
					}
			}
			return false;
		}

		/**
		 * Sets the Key of the entry at the index of the duplicated entries that
		 * occurs in the SubMap. If there is a SubMap containing 4 times exact
		 * the same entries , than
		 * {@code subMap.replaceKey(2, dupeEntryKey, dupeEntryValue, newEntryKey);}
		 * will set the key of the third entry that is equal to the specified
		 * entry. If the given index is bigger than the amount of the specified
		 * duplicated entries, an exception will be thrown.
		 * 
		 * @param index
		 *            The index of the entry of the duplicated entries.
		 * @param oldKey
		 *            The key of the entry that the key should be set from.
		 * @param value
		 *            The value of the entry that the should be set from.
		 * @param newKey
		 *            The new key that should be set to the specific entry.
		 * @return {@code true} if the specified entry was present in this
		 *         SubMap, and its key was replaced.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > the amount of the specified
		 *             duplicated entries.
		 */
		public boolean replaceKey(int index, K oldKey, V value, K newKey)
		{
			int frequency = this.entryfrequency(oldKey, value);
			if(index < 0 || index > frequency)
				throw new IndexOutOfBoundsException(String.format("Index: %s, frequency: %s", index, frequency));

			ListMapEntry<K, V> entry;
			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
			{
				entry = this.listMap.entrySet.entryList.get(i);
				if(entry.compareKey(oldKey))
				{
					if(entry.compareValue(value))
						--index;
					if(index < 0)
					{
						this.listMap.replaceKey(i, newKey);
						return true;
					}
				}
			}
			return false;
		}

		/**
		 * The SubMaps boundaries ({@link #toEntry}) will be adjusted to keep
		 * the keys inside the range of this SubMap.
		 * 
		 * @param index
		 *            The index of the entry that should be removed.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > {@link #size()}
		 */
		public void remove(int index)
		{
			if(index < 0 || index > this.size())
				throw new IndexOutOfBoundsException(String.format("Index: %s, Size: %s", index, this.size()));

			this.listMap.remove((this.fromStart ? 0 : this.fromEntry) + index);
		}

		/**
		 * This will remove the first entry with a key that is equal to the
		 * given key. The SubMaps boundaries ({@link #toEntry}) will be adjusted
		 * to keep the keys inside the range of this SubMap.
		 * 
		 * @param key
		 *            The key of the entry that should be removed.
		 * @return The value of the removed entry. Warning, this might return
		 *         the wrong value, or null if the map was modified before the
		 *         specified entry could be removed properly.
		 * @throws NullPointerException
		 *             If this SubMap does not contain the given key.
		 */
		@Override
		public V remove(Object key)
		{
			if(!this.containsKey(key))
				throw new NullPointerException("Couldn't find the given key!");

			ListMapEntry<K, V> entry;
			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
			{
				entry = this.listMap.entrySet.entryList.get(i);
				if(entry.compareKey(key))
				{
					V value = entry.getValue();
					this.listMap.remove(i);
					return value;
				}
			}
			return null;
		}

		/**
		 * Removes the Entry with the key at the index of the duplicated keys
		 * that occurs in the map. If there is a SubMap containing 4 times
		 * Object {@code exampleKey}, than {@code subMap.remove(2, exampleKey);}
		 * will remove the third entry with the key that is equal to
		 * {@code exampleKey}. If the given index is bigger than the amount of
		 * duplicated keys, an exception is thrown.
		 * 
		 * The SubMaps boundaries ({@link #toEntry}) will be adjusted to keep
		 * the keys inside the range of this SubMap.
		 * 
		 * @param index
		 *            The index of this key of the duplicated keys this SubMap
		 *            contains.
		 * @param key
		 *            The key the value should be replaced.
		 * @return The value of the removed entry. Warning, this might return
		 *         the wrong value, or null if the map was modified before the
		 *         specified entry could be removed properly.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > amount of keys that are equal to
		 *             the given key.
		 */
		public V remove(int index, K key)
		{
			int frequency = this.keyfrequency(key);
			if(index < 0 || index > frequency)
				throw new IndexOutOfBoundsException(String.format("Index: %s, frequency: %s", index, frequency));

			V returnValue = null;
			ListMapEntry<K, V> entry;

			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
			{
				entry = this.listMap.entrySet.entryList.get(i);

				if(entry.compareKey(key))
					--index;
				if(index < 0)
				{
					V value = entry.getValue();
					this.listMap.remove(i);
					return value;
				}
			}
			return returnValue;
		}

		/**
		 * {@inheritDoc} The SubMaps boundaries ({@link #toEntry} ) will be
		 * adjusted to keep the keys inside the range of this SubMap.
		 */
		@Override
		public boolean remove(Object key, Object value)
		{
			ListMapEntry<K, V> entry;
			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
			{
				entry = this.listMap.entrySet.entryList.get(i);
				if(entry.compareKey(key))
					if(entry.compareValue(value))
					{
						this.listMap.remove(i);
						return true;
					}
			}
			return false;
		}

		/**
		 * Removes the entry at the index of the duplicated entries that occurs
		 * in the SubMap. If there is a SubMap containing 4 times exact the same
		 * entries , than
		 * {@code subMap.remove(2, dupeEntryKey, dupeEntryValue);} will remove
		 * the second entry that is equal to the specified entry.
		 * 
		 * The SubMaps boundaries ({@link #toEntry}) will be adjusted to keep
		 * the keys inside the range of this SubMap.
		 * 
		 * @param index
		 *            The index of the entry of the duplicated entries.
		 * @param key
		 *            The key of the entry that should be removed.
		 * @param value
		 *            The value of the entry that should be removed.
		 * @return {@code true} if the specified entry at the index of the
		 *         specified duplicated entries was present in this SubMap. So
		 *         if index < 0 or index > the amount of specified duplicated
		 *         entries, than False is returned.
		 */
		@SuppressWarnings("unchecked")
		// because of frequency check
		public boolean remove(int index, Object key, Object value)
		{
			if(index < 0 || index > this.entryfrequency((K)key, (V)value))
				return false;

			ListMapEntry<K, V> entry;

			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
			{
				entry = this.listMap.entrySet.entryList.get(i);
				if(entry.compareKey(key))
					if(entry.compareValue(value))
					{
						this.listMap.remove(i);
						return true;
					}
			}
			return false;
		}

		/**
		 * Removes all the entries with this specific key. The {@link SubMap
		 * SubMaps} boundaries ({@link #toEntry}) will be adjusted to keep the
		 * keys inside the range of this SubMap.
		 * 
		 * @param key
		 * @return The amount of entries that have been removed.
		 */
		public int removeAll(Object key)
		{
			int removedAmount = 0;

			SubMapKeyIterator iterator = (SubMap<K, V>.SubMapKeyIterator)this.keySet().iterator();

			while(iterator.hasNext())
			{
				if(compareObjects(iterator.next(), key))
				{
					iterator.remove();
					++removedAmount;
				}
			}
			return removedAmount;
		}

		/**
		 * Removes all the entries with the specific key and value. The SubMaps
		 * boundaries ({@link #toEntry}) will be adjusted to keep the keys
		 * inside the range of this SubMap.
		 * 
		 * @param key
		 * @param value
		 * @return The amount of entries that have been removed
		 */
		public int removeAll(K key, V value)
		{
			int removedAmount = 0;

			SubMapEntryIterator iterator = (SubMap<K, V>.SubMapEntryIterator)this.getEntrySet().iterator();

			while(iterator.hasNext())
			{
				if(compareObjects(iterator.next().getKey(), key))
					if(compareObjects(iterator.current().getValue(), value))
					{
						iterator.remove();
						++removedAmount;
					}
			}
			return removedAmount;
		}

		@Override
		public void clear()
		{
			SubMapEntryIterator iterator = (SubMapEntryIterator)this.getEntrySet().iterator();

			while(iterator.hasNext())
			{
				iterator.next();
				iterator.remove();
			}
		}

		/**
		 * Returns the value of the entry at the given index.
		 * 
		 * @param index
		 * @return The value of the entry at the given index.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > {@link #size()}
		 */
		public V get(int index)
		{
			if(index < 0 || index > this.size())
				throw new IndexOutOfBoundsException(String.format("Index: %s, Size: %s", index, this.size()));

			return this.listMap.entrySet.entryList.get((this.fromStart ? 0 : this.fromEntry) + index).getValue();
		}

		/**
		 * Returns the value of the first entry with a key thats equal to the
		 * given key.
		 * 
		 * @param key
		 * @return The value bound to the given key, or null if the SubMap does
		 *         not contain the given key
		 */
		@Override
		public V get(Object key)
		{
			return getOrDefault(key, null);
		}

		/**
		 * This method returns the value of the key at the index of duplicated
		 * keys. If there is a {@link SubMap SUBMAP} containing 4 times Object
		 * {@code example}, than {@code subMap.get(Object, 2);} will return the
		 * value of the third key that is equal to {@code example}.If the given
		 * index is bigger than the amount of duplicated keys, an exception is
		 * thrown.
		 * 
		 * @param index
		 *            The index of this key of the duplicated keys this SubMap
		 *            contains.
		 * @param key
		 *            The key the value is bound to.
		 * @return The value that is bound to the given key, at the index of the
		 *         duplicated keys in this SubMap.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > amount of keys that are equal to
		 *             the given key.
		 */
		public V get(int index, K key)
		{
			return getOrDefault(index, key, null);
		}

		/**
		 * Returns the value of the first entry with a key thats equal to the
		 * given key, or the default value if the SubMap does not contain the
		 * given key.
		 * 
		 * @param key
		 * @param defaultValue
		 * @return The value bound to the given key, or the default value if the
		 *         SubMap does not contain the given key.
		 */
		@Override
		public V getOrDefault(Object key, V defaultValue)
		{
			ListMapEntry<K, V> entry;
			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
			{
				entry = this.listMap.entrySet.entryList.get(i);
				if(entry.compareKey(key))
					return entry.getValue();
			}
			return defaultValue;
		}

		/**
		 * This method returns the value of the key at the index of duplicated
		 * keys. Or the default value if the {@link SubMap SUBMAP} does not
		 * contain the given key. If there is a SubMap containing 4 times Object
		 * {@code example}, than {@code subMap.get(Object, 2);} will return the
		 * value of the third key that is equal to {@code example}.If the given
		 * index is bigger than the amount of duplicated keys, an exception is
		 * thrown.
		 * 
		 * @param index
		 *            The index of this key of the duplicated keys this SubMap
		 *            contains.
		 * @param key
		 *            The key the value is bound to.
		 * @return The value that is bound to the given key, at the index of the
		 *         duplicated keys in this SubMap. or the default value if the
		 *         SubMap does not contain the given key.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > amount of keys that are equal to
		 *             the given key.
		 */
		public V getOrDefault(int index, K key, V defaultValue)
		{
			int frequency = this.keyfrequency(key);
			if(index < 0 || index > frequency)
				throw new IndexOutOfBoundsException(String.format("Index: %s, frequency: %s", index, frequency));

			if(frequency == 0)
				return defaultValue;

			ListMapEntry<K, V> entry;

			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
			{
				entry = this.listMap.entrySet.entryList.get(i);
				if(entry.compareKey(key))
					--index;
				if(index < 0)
					return entry.getValue();
			}
			throw new ConcurrentModificationException();
		}

		/**
		 * Returns the key of the entry at the given index.
		 * 
		 * @param index
		 * @return The key of the entry at the given index.
		 * @throws IndexOutOfBoundsException
		 *             if index < 0 || index > {@link #size()}
		 */
		public K getKey(int index)
		{
			if(index < 0 || index > this.size())
				throw new IndexOutOfBoundsException(String.format("Index: %s, Size: %s", index, this.size()));

			return this.listMap.entrySet.entryList.get((this.fromStart ? 0 : this.fromEntry) + index).getKey();
		}

		@Override
		public int size()
		{
			return (this.toEnd ? this.listMap.size() : this.toEntry + 1) - (this.fromStart ? 0 : this.fromEntry);
		}

		@Override
		public boolean isEmpty()
		{
			return this.fromEntry > this.toEntry;
		}

		@Override
		public boolean containsKey(Object key)
		{
			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
				if(this.listMap.entrySet.entryList.get(i).compareKey(key))
					return true;
			return false;
		}

		@Override
		public boolean containsValue(Object value)
		{
			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
				if(this.listMap.entrySet.entryList.get(i).compareValue(value))
					return true;
			return false;
		}

		/**
		 * Returns a {@link Set} view of the mappings contained in this map. The
		 * set is backed by the map, so changes to the map are reflected in the
		 * set, and vice-versa. If the map is modified while an iteration over
		 * the set is in progress (except through the iterator's own
		 * <tt>remove</tt> operation, or through the <tt>setValue</tt> operation
		 * on a map entry returned by the iterator) the results of the iteration
		 * are undefined.
		 *
		 * @return a set view of the mappings contained in this map
		 */
		@Override
		public Set<K> keySet()
		{
			return new SubMapKeySet(this);
		}

		/**
		 * Returns a {@link Set} view of the mappings contained in this map. The
		 * set is backed by the map, so changes to the map are reflected in the
		 * set, and vice-versa. If the map is modified while an iteration over
		 * the set is in progress (except through the iterator's own
		 * <tt>remove</tt> operation, or through the <tt>setValue</tt> operation
		 * on a map entry returned by the iterator) the results of the iteration
		 * are undefined.
		 *
		 * @return a set view of the mappings contained in this map
		 */
		@Override
		public Collection<V> values()
		{
			return new SubMapValueSet(this);
		}

		/**
		 * Returns a {@link Set} view of the mappings contained in this map. The
		 * set is backed by the map, so changes to the map are reflected in the
		 * set, and vice-versa. If the map is modified while an iteration over
		 * the set is in progress (except through the iterator's own
		 * <tt>remove</tt> operation, or through the <tt>setValue</tt> operation
		 * on a map entry returned by the iterator) the results of the iteration
		 * are undefined.
		 *
		 * @return a set view of the mappings contained in this map
		 */
		public Set<ListMapEntry<K, V>> getEntrySet()
		{
			return new SubMapEntrySet(this);
		}

		@SuppressWarnings("unchecked")
		@Override
		public Set<java.util.Map.Entry<K, V>> entrySet()
		{
			// This weird casting is needed since the Compiler doesn't allow
			// direct
			// casting of the SubMapEntrySet, while it is valid for the JVM.
			Object set = new SubMapEntrySet(this);
			return (Set<java.util.Map.Entry<K, V>>)set;
		}

		public boolean isIndexInBounderies(int index)
		{
			return this.fromStart ? index >= 0 : index >= this.fromEntry &&
					this.toEnd ? index <= this.size() : index <= this.toEntry;
		}

		public int entryfrequency(K key, V value)
		{
			int frequency = 0;
			ListMapEntry<K, V> entry;
			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
			{
				entry = this.listMap.entrySet.entryList.get(i);
				if(entry.compareKey(key))
					if(entry.compareValue(value))
						++frequency;
			}
			return frequency;
		}

		public int keyfrequency(K key)
		{
			int frequency = 0;
			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
				if(this.listMap.entrySet.entryList.get(i).compareKey(key))
					++frequency;
			return frequency;
		}

		public int valuefrequency(V value)
		{
			int frequency = 0;
			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
				if(this.listMap.entrySet.entryList.get(i).compareValue(value))
					++frequency;
			return frequency;
		}

		/**
		 * Returns a SubMap that is backed up by the Map that is backing this
		 * SubMap. Every modification that is made to the SubMap is represented
		 * in the Map and visa versa.<br>
		 * <br>
		 * This is equivalent to
		 * <code>new SubMap(fromIndex, true, toIndex, false)</code>
		 * 
		 * @param fromIndex
		 *            The index from where the SubMap should start.
		 * @param toIndex
		 *            The index where the SubMap should end.
		 * @return
		 */
		public SubMap<K, V> subMap(int fromIndex, int toIndex)
		{
			return new SubMap<K, V>(this.listMap, this.fromEntry + fromIndex, this.fromEntry + toIndex);
		}

		/**
		 * Returns a SubMap that is backed up by the Map that is backing this
		 * SubMap. Every modification that is made to the SubMap is represented
		 * in the Map and visa versa.
		 * 
		 * @param fromIndex
		 *            The index from where the SubMap should start.
		 * @param inclusiveLowKey
		 *            Whether the key at the given index should be included to
		 *            the SubMap.
		 * @param toIndex
		 *            The index where the SubMap should end.
		 * @param inclusiveHighKey
		 *            Whether the key at the given index should be included to
		 *            the SubMap.
		 * @return
		 */
		public SubMap<K, V> subMap(int fromIndex, boolean inclusiveLowKey, int toIndex, boolean inclusiveHighKey)
		{
			return new SubMap<K, V>(this.listMap, this.fromEntry + fromIndex, inclusiveLowKey, this.fromEntry + toIndex, inclusiveHighKey);
		}

		/**
		 * Returns a SubMap that is backed up by the Map that is backing this
		 * SubMap. Every modification that is made to the SubMap is represented
		 * in the Map and visa versa.<br>
		 * <br>
		 * This is equivalent to
		 * <code>new SubMap(fromKey, true, toKey, false)</code>
		 * 
		 * @param fromKey
		 *            The key from where the SubMap should start. Note that it
		 *            will pick the first appearance of the given key!
		 * @param toKey
		 *            The key where the SubMap should end. Note that it will
		 *            pick the first appearance of the given key!
		 * @return
		 */
		public SubMap<K, V> subMap(K fromKey, K toKey)
		{
			if(!this.containsKey(fromKey) || !this.containsKey(toKey))
				throw new NullPointerException("Couldn't find the given keys!");

			return this.subMap(fromKey, true, toKey, true);
		}

		/**
		 * Returns a SubMap that is backed up by the Map that is backing this
		 * SubMap. Every modification that is made to the SubMap is represented
		 * in the Map and visa versa.
		 * 
		 * @param fromKey
		 *            The key from where the SubMap should start. Note that it
		 *            will pick the first appearance of the given key!
		 * @param toKey
		 *            The key where the SubMap should end. Note that it will
		 *            pick the first appearance of the given key!
		 * @return
		 */
		public SubMap<K, V> subMap(K fromKey, boolean inclusiveLowKey, K toKey, boolean inclusiveHighKey)
		{
			if(!this.containsKey(fromKey) || !this.containsKey(toKey))
				throw new NullPointerException("Couldn't find the given keys!");

			int from = -1;
			int to = -1;
			ListMapEntry<K, V> entry;

			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
			{
				entry = this.listMap.entrySet.entryList.get(i);
				if(entry.compareKey(fromKey) && from > -1)
					from = i;
				if(entry.compareKey(toKey) && to > -1)
					to = i;
				if(from > 0 && to > 0)
					break;
			}
			return new SubMap<K, V>(this.listMap, from, inclusiveLowKey, to, inclusiveHighKey);
		}

		/**
		 * Returns a SubMap that is backed up by the Map that is backing this
		 * SubMap. Every modification that is made to the SubMap is represented
		 * in the Map and visa versa.<br>
		 * <br>
		 * This is equivalent to
		 * <code>new SubMap(fromIndex, true, toIndex, false)</code>
		 * 
		 * @param fromKeyIndex
		 *            The given key's index of the duplicated keys in this Map.
		 * @param fromKey
		 *            The key from where the SubMap should start.
		 * @param toKeyIndex
		 *            The given key's index of the duplicated keys in this Map.
		 * @param toKey
		 *            The key where the SubMap should end.
		 * @return
		 */
		public SubMap<K, V> subMap(int fromKeyIndex, K fromKey, int toKeyIndex, K toKey)
		{
			return this.subMap(fromKeyIndex, fromKey, true, toKeyIndex, toKey, true);
		}

		/**
		 * Returns a SubMap that is backed up by the Map that is backing this
		 * SubMap. Every modification that is made to the SubMap is represented
		 * in the Map and visa versa.
		 * 
		 * @param fromKeyIndex
		 *            The given key's index of the duplicated keys in this Map.
		 * @param fromKey
		 *            The key from where the SubMap should start.
		 * @param inclusiveLowKey
		 *            Whether the key at the given index should be included to
		 *            the SubMap.
		 * @param toKeyIndex
		 *            The given key's index of the duplicated keys in this Map.
		 * @param toKey
		 *            The key where the SubMap should end.
		 * @param inclusiveHighKey
		 *            Whether the key at the given index should be included to
		 *            the SubMap.
		 * @return
		 */
		public SubMap<K, V> subMap(int fromKeyIndex, K fromKey, boolean inclusiveLowKey, int toKeyIndex, K toKey, boolean inclusiveHighKey)
		{
			if(!this.containsKey(fromKey) || !this.containsKey(toKey))
				throw new NullPointerException("Couldn't find the given keys!");

			int from = -1;
			int to = -1;

			ListMapEntry<K, V> entry;

			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
			{
				entry = this.listMap.entrySet.entryList.get(i);
				if(entry.compareKey(fromKey) && fromKeyIndex > -1)
					--fromKeyIndex;
				if(entry.compareKey(toKey) && toKeyIndex > -1)
					--toKeyIndex;
				if(fromKeyIndex < 0 && from > -1)
					from = i;
				if(toKeyIndex < 0 && to > -1)
					to = i;
				if(from > 0 && to > 0)
					break;
			}
			return new SubMap<K, V>(this.listMap, from, inclusiveLowKey, to, inclusiveHighKey);
		}

		/**
		 * Returns a {@link SubMap tailMap} that is backed up by this Map. Every
		 * modification that is made to the SubMap is represented in the Map and
		 * visa versa.<br>
		 * <br>
		 * This is equivalent to <code>new tailMap(fromIndex, true)</code>
		 * 
		 * @param fromIndex
		 *            The index from where the SubMap should start.
		 * @return
		 */
		public SubMap<K, V> tailMap(int fromIndex)
		{
			return this.tailMap(fromIndex, true);
		}

		/**
		 * Returns a {@link SubMap tailMap} that is backed up by the Map that is
		 * backing this SubMap. Every modification that is made to the SubMap is
		 * represented in the Map and visa versa.<br>
		 * <br>
		 * This is equivalent to <code>new tailMap(fromKey, true)</code>
		 * 
		 * @param fromIndex
		 *            The index from where the SubMap should start.
		 * @param inclusiveKey
		 *            Whether the key at the given index should be included to
		 *            the SubMap.
		 * @return
		 */
		public SubMap<K, V> tailMap(int fromIndex, boolean inclusiveKey)
		{
			return new SubMap<K, V>(this.listMap, false, this.fromEntry + fromIndex, inclusiveKey, this.toEnd, this.toEnd ? -1 : this.toEntry, true);
		}

		/**
		 * Returns a {@link SubMap tailMap} that is backed up by the Map that is
		 * backing this SubMap. Every modification that is made to the SubMap is
		 * represented in the Map and visa versa.
		 * 
		 * @param key
		 *            The key from where the SubMap should start. Note that it
		 *            will pick the first appearance of the given key!
		 * @param inclusiveKey
		 *            Whether the key at the given index should be included to
		 *            the SubMap.
		 * @return
		 */
		public SubMap<K, V> tailMap(K key, boolean inclusiveKey)
		{
			if(!this.containsKey(key))
				throw new NullPointerException("Couldn't find the given keys!");

			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
				if(this.listMap.entrySet.entryList.get(i).compareKey(key))
					return new SubMap<K, V>(this.listMap, false, i, inclusiveKey, this.toEnd, this.toEnd ? -1 : this.toEntry, true);
			throw new ConcurrentModificationException();
		}

		/**
		 * Returns a {@link SubMap tailMap} that is backed up by the Map that is
		 * backing this SubMap. Every modification that is made to the SubMap is
		 * represented in the Map and visa versa.
		 * 
		 * @param keyIndex
		 *            The given key's index of the duplicated keys in this Map.
		 * @param key
		 *            The key from where the SubMap should start.
		 * @param inclusiveKey
		 *            Whether the key at the given index should be included to
		 *            the SubMap.
		 * @return
		 */
		public SubMap<K, V> tailMap(int keyIndex, K key, boolean inclusiveKey)
		{
			if(!this.containsKey(key))
				throw new NullPointerException("Couldn't find the given keys!");

			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
			{
				if(this.listMap.entrySet.entryList.get(i).compareKey(key))
					--keyIndex;
				if(keyIndex < 0)
					return new SubMap<K, V>(this.listMap, false, i, inclusiveKey, this.toEnd, this.toEnd ? -1 : this.toEntry, true);
			}
			throw new ConcurrentModificationException();
		}

		/**
		 * Returns a {@link SubMap headMap} that is backed up by this Map that
		 * is backing this SubMap. Every modification that is made to the SubMap
		 * is represented in the Map and visa versa.<br>
		 * <br>
		 * This is equivalent to <code>new headMap(toIndex, false)</code>
		 * 
		 * @param toIndex
		 *            The index where the SubMap should end.
		 * @return
		 */
		public SubMap<K, V> headMap(int toIndex)
		{
			return this.headMap(toIndex, true);
		}

		/**
		 * Returns a {@link SubMap headMap} that is backed up by this Map that
		 * is backing this SubMap. Every modification that is made to the SubMap
		 * is represented in the Map and visa versa.<br>
		 * <br>
		 * This is equivalent to <code>new headMap(toKey, false)</code>
		 * 
		 * @param toIndex
		 *            The index where the SubMap should end.
		 * @param inclusiveKey
		 *            Whether the key at the given index should be included to
		 *            the SubMap.
		 * @return
		 */
		public SubMap<K, V> headMap(int toIndex, boolean inclusiveKey)
		{
			return new SubMap<K, V>(this.listMap, this.fromStart, this.fromStart ? -1 : this.fromEntry, true, false, toIndex, inclusiveKey);
		}

		/**
		 * Returns a {@link SubMap headMap} that is backed up by this Map that
		 * is backing this SubMap. Every modification that is made to the SubMap
		 * is represented in the Map and visa versa.
		 * 
		 * @param key
		 *            The key where the SubMap should end. Note that it will
		 *            pick the first appearance of the given key!
		 * @param inclusiveKey
		 *            Whether the key at the given index should be included to
		 *            the SubMap.
		 * @return
		 */
		public SubMap<K, V> headMap(K key, boolean inclusiveKey)
		{
			if(!this.containsKey(key))
				throw new NullPointerException("Couldn't find the given keys!");

			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
				if(this.listMap.entrySet.entryList.get(i).compareKey(key))
					return new SubMap<K, V>(this.listMap, this.fromStart, this.fromStart ? -1 : this.fromEntry, true, false, i, inclusiveKey);
			throw new ConcurrentModificationException();
		}

		/**
		 * Returns a {@link SubMap headMap} that is backed up by this Map that
		 * is backing this SubMap. Every modification that is made to the SubMap
		 * is represented in the Map and visa versa.
		 * 
		 * @param keyIndex
		 *            The given key's index of the duplicated keys in this Map.
		 * @param key
		 *            The key where the SubMap should end.
		 * @param inclusiveKey
		 *            Whether the key at the given index should be included to
		 *            the SubMap.
		 * @return
		 */
		public SubMap<K, V> headMap(int keyIndex, K key, boolean inclusiveKey)
		{
			if(!this.containsKey(key))
				throw new NullPointerException("Couldn't find the given keys!");

			for(int i = (this.fromStart ? 0 : this.fromEntry); i < (this.toEnd ? this.listMap.size() : this.toEntry + 1); i++)
			{
				if(this.listMap.entrySet.entryList.get(i).compareKey(key))
					--keyIndex;
				if(keyIndex < 0)
					return new SubMap<K, V>(this.listMap, this.fromStart, this.fromStart ? -1 : this.fromEntry, true, false, i, inclusiveKey);
			}
			throw new ConcurrentModificationException();
		}

		public final class SubMapKeySet extends AbstractSet<K>
		{

			protected SubMap<K, V>	subMap;

			public SubMapKeySet(SubMap<K, V> subMap)
			{
				this.subMap = subMap;
			}

			/**
			 * Adds a key to the end of this {@link SubMapKeySet}. The value to
			 * this key will be set to null.
			 * 
			 * @param key
			 * @return Always {@code true}
			 * @see ListMap#add(Object, Object)
			 */
			@Override
			public boolean add(K key)
			{
				this.subMap.add(key, null);
				return true;
			}

			/**
			 * Adds the keys inside the Collection at the end of this SubMap.
			 * The values will be set to null
			 * 
			 * @param key
			 * @return Always {@code true}
			 * @see SubMap#addAll(Map)
			 */
			@Override
			public boolean addAll(Collection<? extends K> collection)
			{
				return super.addAll(collection);
			}

			/**
			 * Puts an Entry with the given key at the given index in of this
			 * SubMap, and shifts the remaining entries by one. The value will
			 * be null.
			 * 
			 * @param index
			 * @param key
			 * @see SubMap#put(int, Object, Object)
			 */
			public void put(int index, K key)
			{
				this.subMap.put(index, key, null);
			}

			/**
			 * Puts All the given keys in this SubMap starting at the
			 * beginIndex, and shifts the remaining entries by the amount of
			 * added entries.
			 * 
			 * @param beginIndex
			 * @param c
			 * @see SubMap#putAll(int, Map)
			 */
			public void putAll(int beginIndex, Collection<? extends K> c)
			{
				ArrayList<K> l = new ArrayList<K>(c);
				Collections.reverse(l);
				for(K k : l)
					this.put(beginIndex, k);
			}

			/**
			 * Replaces the key at the given index in this SubMap.
			 * 
			 * @param index
			 *            The index of the key that should be replaced.
			 * @param newKey
			 *            The new key.
			 * @return The key that was previously in this place.
			 * @throws IndexOutOfBoundsException
			 *             If index < 0 || index > {@link #size()}
			 */
			public K replace(int index, K newKey)
			{
				return this.subMap.replaceKey(index, newKey);
			}

			/**
			 * Replaces the first appearance of the given key in this SubMap.
			 * 
			 * @param oldKey
			 *            The key that should be replaced.
			 * @param newKey
			 *            The new key.
			 * @return {@code true} if the specified entry was present in this
			 *         SubMap, and its key was replaced.
			 */
			public boolean replace(K oldKey, K newKey)
			{
				return this.subMap.replaceKey(oldKey, newKey);
			}

			/**
			 * This method replaces the key at the index of duplicated keys. If
			 * there is a SubMap containing 4 times Object {@code example}, than
			 * {@code subMapKeySet.replace(2, oldKey, newKey);} will replace the
			 * the third key that is equal to {@code example}.If the given index
			 * is bigger than the amount of duplicated keys, an exception is
			 * thrown.
			 * 
			 * @param index
			 * @param oldKey
			 * @param newKey
			 * @return {@code true} if the specified entry was present in this
			 *         SubMap, and its key was replaced.
			 * @throws IndexOutOfBoundsException
			 *             If index < 0 || index > amount of keys that are equal
			 *             to the given key.
			 */
			public boolean replace(int index, K oldKey, K newKey)
			{
				return this.subMap.replaceKey(index, oldKey, newKey);
			}

			/**
			 * @param index
			 *            The index of the entry that should be removed
			 * @throws IndexOutOfBoundsException
			 *             If index < 0 || index > {@link #size()}
			 */
			public void remove(int index)
			{
				this.subMap.remove(index);
			}

			/**
			 * Removes the first entry with a key thats equal to the given key.
			 * 
			 * @param key
			 *            The key of the entry that should be removed
			 * @return {@code true} if this SubMap contained the given key.
			 */
			@Override
			public boolean remove(Object key)
			{
				if(this.contains(key))
				{
					this.subMap.remove(key);
					return true;
				}
				return false;
			}

			/**
			 * This method removes the entry with the given key at the index of
			 * duplicated keys. If there is a SubMap containing 4 times Object
			 * {@code example}, than {@code subMapKeySet.remove(2, example);}
			 * will remove the third entry with the key that is equal to
			 * {@code example} .If the given index is bigger than the amount of
			 * duplicated keys, an exception is thrown.
			 * 
			 * @param index
			 *            The index of the duplicated keys that should be
			 *            removed
			 * @param key
			 *            The key of the entry that should be removed
			 * @return {@code true} if this SubMap contained the given key.
			 * @throws IndexOutOfBoundsException
			 *             If index < 0 || index > amount of keys that are equal
			 *             to the given key.
			 */
			public boolean remove(int index, K key)
			{
				int frequency = this.subMap.keyfrequency(key);
				if(index < 0 || index > frequency)
					throw new IndexOutOfBoundsException(String.format("Index: %s, frequency: %s", index, frequency));

				if(this.contains(key))
				{
					this.subMap.remove(index, key);
					return true;
				}
				return false;
			}

			/**
			 * Removes all the entries in the SubMap with the given key.
			 * 
			 * @param key
			 *            The key to check for if an entry should be deleted.
			 * @return The amount of deleted entries.
			 */
			public int removeAll(Object key)
			{
				return this.subMap.removeAll(key);
			}

			/**
			 * This method clears all the entries from this SubMap.
			 */
			@Override
			public void clear()
			{
				this.subMap.clear();
			}

			/**
			 * Returns a {@link SubMapKeyIterator} that starts at the begin of
			 * the SubMap.
			 * 
			 * @return A new SubMapKeyIterator.
			 */
			@Override
			public Iterator<K> iterator()
			{
				return new SubMapKeyIterator(this.subMap);
			}

			/**
			 * Returns a {@link SubMapKeyIterator} that starts at the given
			 * position in the SubMap.
			 * 
			 * @param startIndex
			 *            The index where this Iterator should start iterating.
			 * @return A new SubMapKeyIterator.
			 */
			public Iterator<K> iterator(int startIndex)
			{
				return new SubMapKeyIterator(startIndex, this.subMap);
			}

			@Override
			public int size()
			{
				return this.subMap.size();
			}

			@Override
			public Spliterator<K> spliterator()
			{
				return new SubMapKeySpliterator<K>(this.subMap, 0, -1, -1);
			}
		}

		public final class SubMapValueSet extends AbstractSet<V>
		{

			protected SubMap<K, V>	subMap;

			public SubMapValueSet(SubMap<K, V> subMap)
			{
				this.subMap = subMap;
			}

			/**
			 * Adds a value to the end of this {@link SubMapValueSet}. The key
			 * of this value will be set to null.
			 * 
			 * @param key
			 * @return Always {@code true}
			 * @see SubMap#add(Object, Object)
			 */
			@Override
			public boolean add(V value)
			{
				this.subMap.add(null, value);
				return true;
			}

			/**
			 * Adds the values inside the Collection at the end of this SubMap.
			 * The keys will be set to null
			 * 
			 * @param key
			 * @return Always {@code true}
			 * @see SubMap#addAll(Map)
			 */
			@Override
			public boolean addAll(Collection<? extends V> collection)
			{
				return super.addAll(collection);
			}

			/**
			 * Puts an Entry with the given value at the given index in of this
			 * SubMap, and shifts the remaining entries by one. The key will be
			 * null.
			 * 
			 * @param index
			 * @param key
			 * @see SubMap#put(int, Object, Object)
			 */
			public void put(int index, V value)
			{
				this.subMap.put(index, null, value);
			}

			/**
			 * Puts All the given values in this SubMap starting at the
			 * beginIndex, and shifts the remaining entries by the amount of
			 * added entries.
			 * 
			 * @param beginIndex
			 * @param c
			 * @see SubMap#putAll(int, Map)
			 */
			public void putAll(int beginIndex, Collection<? extends V> c)
			{
				ArrayList<V> l = new ArrayList<V>(c);
				Collections.reverse(l);
				for(V v : l)
					this.put(beginIndex, v);
			}

			/**
			 * Sets the value at the given index in this SubMap.
			 * 
			 * @param index
			 *            The index of the value that should be replaced.
			 * @param newValue
			 *            The new value.
			 * @return The value that was previously in this place.
			 * @throws IndexOutOfBoundsException
			 *             If index < 0 || index > {@link #size()}
			 */
			public V set(int index, V newValue)
			{
				return this.subMap.setValue(index, newValue);
			}

			/**
			 * @param index
			 *            The index of the entry that should be removed
			 * @throws IndexOutOfBoundsException
			 *             If index < 0 || index > {@link #size()}
			 */
			public void remove(int index)
			{
				this.subMap.remove(index);
			}

			/**
			 * Removes all the entries in the SubMap with the given value.
			 * 
			 * @param value
			 *            The value to check for if an entry should be deleted.
			 * @return The amount of deleted entries.
			 */
			public int removeAll(V value)
			{
				int removedAmount = 0;
				SubMapValueIterator iterator = (SubMap<K, V>.SubMapValueIterator)this.iterator();

				while(iterator.hasNext())
				{
					if(compareObjects(iterator.next(), value))
					{
						iterator.remove();
						++removedAmount;
					}
				}
				return removedAmount;
			}

			/**
			 * This method clears all the entries from this SubMap.
			 */
			@Override
			public void clear()
			{
				this.subMap.clear();
			}

			/**
			 * Returns a {@link SubMapValueIterator} that starts at the begin of
			 * the SubMap.
			 * 
			 * @return A new SubMapValueIterator.
			 */
			@Override
			public Iterator<V> iterator()
			{
				return new SubMapValueIterator(this.subMap);
			}

			/**
			 * Returns a {@link SubMapValueIterator} that starts at the given
			 * position in the SubMap.
			 * 
			 * @param startIndex
			 *            The index where this Iterator should start iterating.
			 * @return A new SubMapValueIterator.
			 */
			public Iterator<V> iterator(int startIndex)
			{
				return new SubMapValueIterator(startIndex, this.subMap);
			}

			@Override
			public int size()
			{
				return this.subMap.size();
			}

			@Override
			public Spliterator<V> spliterator()
			{
				return new SubMapValueSpliterator<V>(this.subMap, 0, -1, -1);
			}
		}

		public final class SubMapEntrySet extends AbstractSet<ListMapEntry<K, V>>
		{

			protected SubMap<K, V>	subMap;

			public SubMapEntrySet(SubMap<K, V> subMap)
			{
				this.subMap = subMap;
			}

			/**
			 * @param entry
			 * @return Always {@code true}
			 */
			@Override
			public boolean add(ListMapEntry<K, V> entry)
			{
				this.subMap.add(entry.getKey(), entry.getValue());
				return true;
			}

			/** Adds a new entry at the end of this SubMap */
			public void add(K key, V value)
			{
				this.subMap.add(key, value);
			}

			/**
			 * @param collection
			 * @return Always {@code true}
			 */
			@Override
			public boolean addAll(Collection<? extends ListMapEntry<K, V>> collection)
			{
				collection.forEach(E -> this.add(E));
				return true;
			}

			/**
			 * Adds the entries from the given Map at the end of this Map in the
			 * order the Iterator is presenting the map entries
			 */
			public void addAll(Map<? extends K, ? extends V> map)
			{
				map.entrySet().forEach(E -> this.add(E.getKey(), E.getValue()));
			}

			/**
			 * Puts a new Entry at the given index in this SubMap. and shifts
			 * the remaining entries in the SubMap by one.
			 * 
			 * @param index
			 *            The position where this entry should be put.
			 * @param key
			 *            The key which the new entry.
			 * @param value
			 *            The value which the new entry.
			 * @throws IndexOutOfBoundsException
			 *             if index < 0 || index > {@link #size()}
			 */
			public void put(int index, K key, V value)
			{
				this.subMap.put(index, key, value);
			}

			/**
			 * Puts all the given entries from the given map in the order the
			 * Iterator is presenting the Map entries, starting from the given
			 * index. The remaining entries that were already in the list will
			 * shift the amount of entries that are present in the given Map.
			 * 
			 * @param index
			 *            The index where to start implementing the given map
			 * @param map
			 *            The map of entries that should be placed into this
			 *            SubMap.
			 * @throws IndexOutOfBoundsException
			 *             if index < 0 || index > {@link #size()}
			 */
			public void putAll(int index, Map<? extends K, ? extends V> map)
			{
				this.subMap.putAll(index, map);
			}

			/**
			 * Sets the value at the given index.
			 * 
			 * @param index
			 *            The index of the entry which value should be set.
			 * @param value
			 *            The value to be set.
			 * @return The value that was previous at the given index.
			 * @throws IndexOutOfBoundsException
			 *             if index < 0 || index > {@link #size()}
			 */
			public V setValue(int index, V value)
			{
				return this.subMap.setValue(index, value);
			}

			/**
			 * Sets the value of the first key that is equal to the given key.
			 * 
			 * @param key
			 *            The key which value should be bound to.
			 * @param value
			 *            The value to be bound.
			 * @return The value that was previously bound to the given key.
			 * @throws NullPointerException
			 *             If this SubMap does not contain the given key.
			 */
			public V setValue(K key, V value)
			{
				return this.subMap.setValue(key, value);
			}

			/**
			 * Sets the value of the key at the index of the duplicated keys
			 * that occurs in the map. If there is a SubMap containing 4 times
			 * Object {@code exampleKey}, than
			 * {@code subMapEntrySet.setValue(2, exampleKey, value);} will set
			 * the value of the third key that is equal to {@code exampleKey}.
			 * If the given index is bigger than the amount of duplicated keys,
			 * an exception is thrown.
			 * 
			 * @param index
			 *            The index of this key of the duplicated keys this
			 *            SubMap contains.
			 * @param key
			 *            The key the value should be bound to.
			 * @param value
			 *            The value that should be bound to the key.
			 * @return The value that was previous bound to the specified key.
			 * @throws IndexOutOfBoundsException
			 *             if index < 0 || index > amount of keys that are equal
			 *             to the given key.
			 */
			public V setValue(int index, K key, V value)
			{
				return this.subMap.setValue(index, key, value);
			}

			/**
			 * Sets the value of the first entry that has the same key and value
			 * that is given key and value.
			 * 
			 * @param key
			 *            The key of the entry that the value should be set of.
			 * @param oldValue
			 *            The value the entry should have.
			 * @param newValue
			 *            The new value that should be bound to the specific
			 *            entry.
			 * @return {@code true} if the specified entry was present in this
			 *         SubMap, and its value was replaced.
			 */
			public boolean setValue(K key, V oldValue, V newValue)
			{
				return this.subMap.setValue(key, oldValue, newValue);
			}

			/**
			 * Sets the value of the entry at the index of the duplicated
			 * entries that occurs in the SubMap. If there is a SubMap
			 * containing 4 times exact the same entries , than
			 * {@code subMapEntrySet.setValue(2, dupeEntryKey, dupeEntryValue, newEntryValue);}
			 * will set the value of the third entry that is equal to the
			 * specified entry. If the given index is bigger than the amount of
			 * the specified duplicated entries, an exception will be thrown.
			 * 
			 * @param index
			 *            The index of the entry of the duplicated entries.
			 * @param key
			 *            The key of the entry that the value should be set of.
			 * @param oldValue
			 *            The value that should be set to the entry.
			 * @param newValue
			 *            The new value that should be bound to the specific
			 *            entry.
			 * @return {@code true} if the specified entry was present in this
			 *         SubMap, and its value was replaced.
			 * @throws IndexOutOfBoundsException
			 *             if index < 0 || index > the amount of the specified
			 *             duplicated entries.
			 */
			public boolean setValue(int index, K key, V oldValue, V newValue)
			{
				return this.subMap.setValue(index, key, oldValue, newValue);
			}

			/**
			 * Replaces the key at the given index in this SubMap.
			 * 
			 * @param index
			 *            The index of the key that should be replaced.
			 * @param newKey
			 *            The Key which the old key should be replaced with.
			 * @return The key that is replaced.
			 * @throws IndexOutOfBoundsException
			 *             if index < 0 || index > {@link #size()}
			 */
			public K replaceKey(int index, K newKey)
			{
				return this.subMap.replaceKey(index, newKey);
			}

			/**
			 * Replaces the first key in this SubMap that is equal to the given
			 * key
			 * 
			 * @param key
			 * @param newKey
			 *            The Key which the old key should be replaced with.
			 * @return {@code true} if the specified entry was present in this
			 *         SubMap, and its key was replaced.
			 * @throws NullPointerException
			 *             If this SubMap does not contain the given key.
			 */
			public boolean replaceKey(K key, K newKey)
			{
				return this.subMap.replaceKey(key, newKey);
			}

			/**
			 * Replaces the key at the index of the duplicated keys that occurs
			 * in the map. If there is a SubMap containing 4 times Object
			 * {@code exampleKey}, than
			 * {@code subMapEntrySet.replaceKey(2, exampleKey, value);} will
			 * replace the third key that is equal to {@code exampleKey}. If the
			 * given index is bigger than the amount of duplicated keys, an
			 * exception is thrown.
			 * 
			 * @param index
			 *            The index of this key of the duplicated keys this
			 *            SubMap contains.
			 * @param key
			 *            The key the value should be replaced.
			 * @param newKey
			 *            The new key.
			 * @return {@code true} if the specified entry was present in this
			 *         SubMap, and its key was replaced.
			 * @throws IndexOutOfBoundsException
			 *             if index < 0 || index > amount of keys that are equal
			 *             to the given key.
			 */
			public boolean replaceKey(int index, K key, K newKey)
			{
				return this.subMap.replaceKey(index, key, newKey);
			}

			/**
			 * Sets the Key of the first entry that has the same key and value
			 * that is given key and value.
			 * 
			 * @param oldKey
			 *            The key of the entry that the value should be set of.
			 * @param value
			 *            The value the entry should have.
			 * @param newKey
			 *            The new Key that should be set to the specific entry.
			 * @return {@code true} if the specified entry was present in this
			 *         SubMap.
			 */
			public boolean replaceKey(K oldKey, V value, K newKey)
			{
				return this.subMap.replaceKey(oldKey, value, newKey);
			}

			/**
			 * Sets the Key of the entry at the index of the duplicated entries
			 * that occurs in the SubMap. If there is a SubMap containing 4
			 * times exact the same entries , than
			 * {@code subMapEntrySet.replaceKey(2, dupeEntryKey, dupeEntryValue, newEntryKey);}
			 * will set the key of the third entry that is equal to the
			 * specified entry. If the given index is bigger than the amount of
			 * the specified duplicated entries, an exception will be thrown.
			 * 
			 * @param index
			 *            The index of the entry of the duplicated entries.
			 * @param oldKey
			 *            The key of the entry that the key should be set from.
			 * @param value
			 *            The value of the entry that the should be set from.
			 * @param newKey
			 *            The new key that should be set to the specific entry.
			 * @return {@code true} if the specified entry was present in this
			 *         SubMap, and its key was replaced.
			 * @throws IndexOutOfBoundsException
			 *             if index < 0 || index > the amount of the specified
			 *             duplicated entries.
			 */
			public boolean replaceKey(int index, K oldKey, V value, K newKey)
			{
				return this.subMap.replaceKey(index, oldKey, value, newKey);
			}

			/**
			 * @param index
			 *            The index of the entry that should be removed.
			 * @throws IndexOutOfBoundsException
			 *             if index < 0 || index > {@link #size()}
			 */
			public void remove(int index)
			{
				this.subMap.remove(index);
			}

			/**
			 * Removes the Entry with the key at the index of the duplicated
			 * keys that occurs in the map. If there is a SubMap containing 4
			 * times Object {@code exampleKey}, than
			 * {@code subMapEntrySet.remove(2, exampleKey);} will remove the
			 * third entry with the key that is equal to {@code exampleKey}. If
			 * the given index is bigger than the amount of duplicated keys, an
			 * exception is thrown.
			 * 
			 * @param index
			 *            The index of this key of the duplicated keys this
			 *            SubMap contains.
			 * @param key
			 *            The key the value should be replaced.
			 * @throws IndexOutOfBoundsException
			 *             if index < 0 || index > amount of keys that are equal
			 *             to the given key.
			 */
			public V remove(int index, K key)
			{
				return this.subMap.remove(index, key);
			}

			/**
			 * Removes the first Entry with the same key and value.
			 * 
			 * @param key
			 * @param value
			 * @return {@code true} if this SubMap contained the specified
			 *         entry.
			 */
			public boolean remove(Object key, Object value)
			{
				return this.subMap.remove(key, value);
			}

			/**
			 * Removes the entry at the index of the duplicated entries that
			 * occurs in the SubMap. If there is a SubMap containing 4 times
			 * exact the same entries , than
			 * {@code subMapEntrySet.remove(2, dupeEntryKey, dupeEntryValue);}
			 * will remove the third entry that is equal to the specified entry.
			 * 
			 * @param index
			 *            The index of the entry of the duplicated entries.
			 * @param key
			 *            The key of the entry that should be removed.
			 * @param value
			 *            The value of the entry that should be removed.
			 * @return {@code true} if the specified entry at the index of the
			 *         specified duplicated entries was present in this SubMap.
			 *         So if index < 0 or index > the amount of specified
			 *         duplicated entries, than False is returned.
			 */
			public boolean remove(int index, Object key, Object value)
			{
				return this.subMap.remove(index, key, value);
			}

			/**
			 * Removes all the entries with this specific key.
			 * 
			 * @param key
			 * @return The amount of entries that have been removed.
			 */
			public int removeAll(Object key)
			{
				return this.subMap.removeAll(key);
			}

			/**
			 * Removes all the entries with the specific key and value
			 * 
			 * @param key
			 * @param value
			 * @return The amount of entries that have been removed
			 */
			public int removeAll(K key, V value)
			{
				return this.subMap.removeAll(key, value);
			}

			@Override
			public void clear()
			{
				this.subMap.clear();
			}

			/**
			 * Returns a {@link SubMapEntryIterator} that starts at the begin of
			 * the SubMap.
			 * 
			 * @return A new SubMapEntryIterator.
			 */
			@Override
			public Iterator<ListMapEntry<K, V>> iterator()
			{
				return new SubMapEntryIterator(this.subMap);
			}

			/**
			 * Returns a {@link SubMapEntryIterator} that starts at the given
			 * position in the SubMap.
			 * 
			 * @param startIndex
			 *            The index where this Iterator should start iterating.
			 * @return A new SubMapEntryIterator.
			 */
			public Iterator<ListMapEntry<K, V>> iterator(int startIndex)
			{
				return new SubMapEntryIterator(startIndex, this.subMap);
			}

			@Override
			public int size()
			{
				return this.subMap.size();
			}

			@Override
			public Spliterator<ListMapEntry<K, V>> spliterator()
			{
				return new SubMapEntrySpliterator<ListMapEntry<K, V>>(this.subMap, 0, -1, -1);
			}
		}

		public abstract class SubMapIterator
		{

			protected SubMap<K, V>			subMap;

			protected ListMapEntry<K, V>	current;
			protected int					index;
			protected int					_expectedModCount;

			/**
			 * @param startIndex
			 *            The index where this iterator should start in this
			 *            Map.
			 */
			public SubMapIterator(int startIndex, SubMap<K, V> subMap)
			{
				if(startIndex < 0 || startIndex >= subMap.size())
					throw new IndexOutOfBoundsException(String.format("StartIndex: %s, Size: %s", startIndex, this.subMap.listMap.entrySet.size()));

				this.subMap = subMap;
				this._expectedModCount = this.subMap.listMap._modCount;
				this.index = startIndex;
				this.current = null;
			}

			public SubMapIterator(SubMap<K, V> subMap)
			{
				this(0, subMap);
			}
			
			/** @return {@code true} if there is a previous entry, {@code false} otherwise. */
			public boolean hasPrevious()
			{
				return this.index > 0;
			}
			
			/** @return The previous index. */
			public int previousIndex()
			{
				return this.index - 1;
			}
			
			/** @return {@code true} if the current element is not removed. */
			public boolean hasCurrent()
			{
				return this.current != null;
			}
			
			/** @return The current index (cursor position). */
			public int currentIndex()
			{
				return this.index;
			}

			/** @return {@code true} if there is a next Entry, {@code false} otherwise. */
			public boolean hasNext()
			{
				return this.index < this.subMap.size() - 1;
			}
			
			/** @return The next index. */
			public int nextIndex()
			{
				return this.index + 1;
			}
			
			protected ListMapEntry<K, V> getNextEntry()
			{
				if(this.index >= this.subMap.size() - 1)
					throw new NoSuchElementException();
				this.checkCoModifications();
				
				if(this.current != null)
					this.current = this.subMap.listMap.entrySet.entryList.get(this.subMap.fromEntry + (++this.index));
				else this.current = this.subMap.listMap.entrySet.entryList.get(this.subMap.fromEntry + this.index);
				return this.current;
			}
			
			protected ListMapEntry<K, V> peekForeward()
			{
				if(this.index >= this.subMap.size() - 1)
					throw new NoSuchElementException();
				this.checkCoModifications();
				return this.subMap.listMap.entrySet.entryList.get(this.subMap.fromEntry + this.index + 1);
			}
			
			protected ListMapEntry<K, V> getCurrent()
			{
				if(current == null)
					throw new IllegalStateException();
				this.checkCoModifications();
				return this.current;
			}
			
			protected ListMapEntry<K, V> getPreviousEntry()
			{
				if(this.index <= 0)
					throw new NoSuchElementException();
				this.checkCoModifications();
				
				if(this.current != null)
					this.current = this.subMap.listMap.entrySet.entryList.get(this.subMap.fromEntry + --this.index);
				else this.current = this.subMap.listMap.entrySet.entryList.get(this.subMap.fromEntry + this.index);
				return this.current;
			}
			
			protected ListMapEntry<K, V> peekBackwards()
			{
				if(this.index <= 0)
					throw new NoSuchElementException();
				this.checkCoModifications();
				return this.subMap.listMap.entrySet.entryList.get(this.subMap.fromEntry + this.index - 1);
			}
			
			protected ListMapEntry<K, V> set(K key, V value)
			{
				this.checkCoModifications();
				if(current == null)
					throw new IllegalStateException();
				return new ListMapEntry<K, V>(this.current.setKey(key), this.current.setValue(value));
			}
			
			protected void add(K key, V value)
			{
				this.checkCoModifications();
				this.subMap.put(this.index, key, value);
				this.index++;
				this._expectedModCount = this.subMap.listMap._modCount;
			}

			public void remove()
			{
				if(this.current == null)
					throw new IllegalStateException();
				this.checkCoModifications();
				
				this.current = null;
				this.subMap.remove(this.index);
				this._expectedModCount = this.subMap.listMap._modCount;
			}

			protected void checkCoModifications()
			{
				if(this._expectedModCount != this.subMap.listMap._modCount
						|| this.index < 0 || this.index >= this.subMap.size())
					throw new ConcurrentModificationException();
			}
		}

		public final class SubMapKeyIterator extends SubMapIterator implements Iterator<K>
		{
			/** @param startIndex The index where this iterator should start in this Map. */
			public SubMapKeyIterator(int startIndex, SubMap<K, V> subMap)	{super(startIndex, subMap);}
			public SubMapKeyIterator(SubMap<K, V> subMap)					{super(subMap);}
			public K previous()		{return this.getPreviousEntry().getKey();}
			public K peekPrevious()	{return this.peekBackwards().getKey();}
			public K current()		{return this.getCurrent().getKey();}
			@Override
			public K next()			{return this.getNextEntry().getKey();}
			public K peekNext()		{return this.peekForeward().getKey();}
			public K set(K key)		{return this.set(key, this.getCurrent().getValue()).getKey();}
			public void add(K key)	{this.add(key, this.getCurrent().getValue());}
		}

		public final class SubMapValueIterator extends SubMapIterator implements Iterator<V>
		{
			/** @param startIndex The index where this iterator should start in this Map. */
			public SubMapValueIterator(int startIndex, SubMap<K, V> subMap)	{super(startIndex, subMap);}
			public SubMapValueIterator(SubMap<K, V> subMap)					{super(subMap);}
			public V previous()		{return this.getPreviousEntry().getValue();}
			public V peekPrevious()	{return this.peekBackwards().getValue();}
			public V current()		{return this.getCurrent().getValue();}
			@Override
			public V next()			{return this.getNextEntry().getValue();}
			public V peekNext()		{return this.peekForeward().getValue();}
			public V set(V value)	{return this.set(this.getCurrent().getKey(), value).getValue();}
			public void add(V value){this.add(this.getCurrent().getKey(), value);}
		}

		public final class SubMapEntryIterator extends SubMapIterator implements Iterator<ListMapEntry<K, V>>
		{
			/** @param startIndex The index where this iterator should start in this Map. */
			public SubMapEntryIterator(int startIndex, SubMap<K, V> subMap)	{super(startIndex, subMap);}
			public SubMapEntryIterator(SubMap<K, V> subMap)					{super(subMap);}
			public ListMapEntry<K, V> previous()			{return this.getPreviousEntry();}
			public ListMapEntry<K, V> peekPrevious()		{return this.peekBackwards();}
			public ListMapEntry<K, V> current()				{return this.getCurrent();}
			@Override
			public ListMapEntry<K, V> next()				{return this.getNextEntry();}
			public ListMapEntry<K, V> peekNext()			{return this.peekForeward();}
			@Override
			public ListMapEntry<K, V> set(K key, V value)	{return super.set(key, value);}
			@Override
			public void add(K key, V value)					{super.add(key, value);}
		}

		public static abstract class SubMapSpliterator<E>
		{

			protected final SubMap<?, ?>	subMap;
			// current index, modified on advance/split
			protected int					index;
			// -1 if not used; then one past last index
			protected int					lowBoundery;
			// -1 if not used; then first index inclusive
			protected int					highBoundery;
			// initialized when fence set
			protected int					_expectedModCount;

			/** Create new spliterator covering the given range */
			public SubMapSpliterator(SubMap<?, ?> subMap, int startIndex, int lowBoundery, int highBoundery)
			{
				if(subMap != null && (highBoundery < 0 ? subMap.size() : highBoundery) < (lowBoundery < 0 ? 0 : lowBoundery))
					throw new IllegalArgumentException("HigherBoundery < lowerBoundery!");
				this.subMap = subMap;
				this.index = startIndex;
				this.highBoundery = highBoundery;
				this.lowBoundery = lowBoundery;
				this._expectedModCount = subMap != null ? subMap.listMap._modCount : -1;
			}

			protected abstract Spliterator<E> getNewSpilterator(SubMap<?, ?> subMap, int startIndex, int lowBoundery, int highBoundery);

			protected abstract E get(int index);

			private void _initBounds()
			{
				if(this.highBoundery < 0)
				{
					if(this.subMap == null)
						highBoundery = -1;
					else
					{
						_expectedModCount = this.subMap.listMap._modCount;
						highBoundery = this.subMap.size();
					}
				}

				if(this.lowBoundery < 0)
					if(this.subMap == null)
						this.lowBoundery = -1;
					else this.lowBoundery = 0;
			}

			public Spliterator<E> trySplit()
			{
				this._initBounds();
				int mid = (this.lowBoundery + this.highBoundery) >>> 1;
				Spliterator<E> split = (this.lowBoundery >= mid) ? null : getNewSpilterator(this.subMap, this.index < mid ? this.index : mid, this.lowBoundery, mid);
				if(this.index < mid)
					this.index = mid;
				this.lowBoundery = mid;
				return split;
			}

			public boolean tryAdvance(Consumer<? super E> action)
			{
				if(action == null)
					throw new NullPointerException();
				this._initBounds();
				if(this.highBoundery < 0 ? this.index < this.subMap.size() : this.index < this.highBoundery)
				{
					E object = this.get(this.index++);
					action.accept(object);
					if(this.subMap.listMap._modCount != this._expectedModCount)
						throw new ConcurrentModificationException();
					return true;
				}
				return false;
			}

			public boolean tryWithdraw(Consumer<? super E> action)
			{
				if(action == null)
					throw new NullPointerException();
				this._initBounds();
				if(this.lowBoundery < 0 ? this.index > -1 : this.index >= this.lowBoundery)
				{
					E object = this.get(this.index--);
					action.accept(object);
					if(this.subMap.listMap._modCount != this._expectedModCount)
						throw new ConcurrentModificationException();
					return true;
				}
				return false;
			}

			public void forEachRemaining(Consumer<? super E> action)
			{
				if(action == null)
					throw new NullPointerException();
				this._initBounds();
				int expModCount = this._expectedModCount;
				if(this.subMap != null)
					if(this.index >= (this.lowBoundery < 0 ? 0 : this.lowBoundery) && index < (this.highBoundery < 0 ? this.subMap.size() : this.highBoundery))
					{
						for(int i = this.index; i < (this.highBoundery < 0 ? this.subMap.size() : this.highBoundery); ++i)
							action.accept(this.get(i));
						if(this.subMap.listMap._modCount == expModCount)
							return;
					}
				throw new ConcurrentModificationException();
			}

			public void forEachPassed(Consumer<? super Object> action)
			{
				if(action == null)
					throw new NullPointerException();
				this._initBounds();
				int expModCount = this._expectedModCount;
				if(this.subMap != null)
					if(this.index >= (this.lowBoundery < 0 ? 0 : this.lowBoundery) && index < (this.highBoundery < 0 ? this.subMap.size() : this.highBoundery))
					{
						for(int i = this.index; i < (this.highBoundery < 0 ? this.subMap.size() : this.highBoundery); ++i)
							action.accept(this.get(i));
						if(this.subMap.listMap._modCount == expModCount)
							return;
					}
				throw new ConcurrentModificationException();
			}

			public long estimateSize()
			{
				return (long)(this.highBoundery < 0 ? this.subMap.size() : this.highBoundery - this.index);
			}

			public int characteristics()
			{
				return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
			}
		}

		public static class SubMapKeySpliterator<K> extends SubMapSpliterator<K> implements Spliterator<K>
		{

			public SubMapKeySpliterator(SubMap<?, ?> subMap, int startIndex, int lowBoundery, int highBoundery)
			{
				super(subMap, startIndex, lowBoundery, highBoundery);
			}

			@Override
			protected Spliterator<K> getNewSpilterator(SubMap<?, ?> subMap, int startIndex, int lowBoundery, int highBoundery)
			{
				return new SubMapKeySpliterator<K>(subMap, startIndex, lowBoundery, highBoundery);
			}

			@SuppressWarnings("unchecked")
			@Override
			protected K get(int index)
			{
				return ((SubMap<K, ?>)this.subMap).listMap.entrySet.entryList.get(this.subMap.fromEntry + index).getKey();
			}
		}

		public static class SubMapValueSpliterator<V> extends SubMapSpliterator<V> implements Spliterator<V>
		{

			public SubMapValueSpliterator(SubMap<?, ?> subMap, int startIndex, int lowBoundery, int highBoundery)
			{
				super(subMap, startIndex, lowBoundery, highBoundery);
			}

			@Override
			protected Spliterator<V> getNewSpilterator(SubMap<?, ?> subMap, int startIndex, int lowBoundery, int highBoundery)
			{
				return new SubMapValueSpliterator<V>(subMap, startIndex, lowBoundery, highBoundery);
			}

			@SuppressWarnings("unchecked")
			@Override
			protected V get(int index)
			{
				return ((SubMap<?, V>)this.subMap).listMap.entrySet.entryList.get(this.subMap.fromEntry + index).getValue();
			}
		}

		public static class SubMapEntrySpliterator<E extends ListMapEntry<?, ?>> extends SubMapSpliterator<E> implements Spliterator<E>
		{

			public SubMapEntrySpliterator(SubMap<?, ?> subMap, int startIndex, int lowBoundery, int highBoundery)
			{
				super(subMap, startIndex, lowBoundery, highBoundery);
			}

			@Override
			protected Spliterator<E> getNewSpilterator(SubMap<?, ?> subMap, int startIndex, int lowBoundery, int highBoundery)
			{
				return new SubMapEntrySpliterator<E>(subMap, startIndex, lowBoundery, highBoundery);
			}

			@SuppressWarnings("unchecked")
			@Override
			protected E get(int index)
			{
				return (E)this.subMap.listMap.entrySet.entryList.get(this.subMap.fromEntry + index);
			}
		}
	}
}