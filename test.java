

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.lang.management.ManagementFactory;		
import java.lang.management.ThreadMXBean;
import zoho.crm.security.iast.agent.ConfigUtil;

public class WeakReferenceHashMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V>,Runnable
{
	private final ConcurrentMap<WeakKey<K>,V> map;
	private final ReferenceQueue<K> referenceKeyQueue = new ReferenceQueue<K>();
	private final ScheduledThreadPoolExecutor expungeStaleEntriesThread = new ScheduledThreadPoolExecutor(1);
	long lastExpungedTime = 0;		
	long expungeCount = 0;		
	long keysAdded = 0;		
	long cpuTimeExpunge = 0;
	
	WeakReferenceHashMap()
	{
		this.map = new ConcurrentHashMap<WeakKey<K>,V>();
		if(ConfigUtil.sqlDBTainting)
		{
			
			expungeStaleEntriesThread.scheduleWithFixedDelay(this, 0, 1, TimeUnit.MILLISECONDS);
		}
	}
	
	@Override
	public void run()
	{
		expungeStaleEntries();
	}
	
	public void expungeStaleEntries()
	{
		ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
		long startUserTime = (threadMXBean.getCurrentThreadUserTime()/1000000);
		
		Reference<?> reference;
	    while ((reference = referenceKeyQueue.poll()) != null) {
	    	map.remove(reference);
	    }
	    
	    long endUserTime = (threadMXBean.getCurrentThreadUserTime()/1000000);
	    cpuTimeExpunge = cpuTimeExpunge+(endUserTime-startUserTime);
	    expungeCount++;
	}
	
	@Override
	public int size()
	{
		//expungeStaleEntries();
		return map.size();
	}
	
	public long getTotalEntriesAdded()		
	{		
		return keysAdded;		
	}
	
	public long getTotalExpungeCount()
	{
		return expungeCount;
	}
	
	public long getTotalExpungeTime()
	{
		return cpuTimeExpunge;
	}

	@Override
	public boolean isEmpty()
	{
		return size()==0;
	}

	@Override
	public boolean containsKey(Object key)
	{
		//expungeStaleEntries();
		return map.containsKey(new WeakKey<Object>(key, null));
	}

	@Override
	public boolean containsValue(Object value)
	{
		//expungeStaleEntries();
		return map.containsValue(value);
	}

	@Override
	public V get(Object key)
	{
		//expungeStaleEntries();
		long currentTime = System.currentTimeMillis();
		if(currentTime-lastExpungedTime > 50)
		{
			expungeStaleEntries();
			lastExpungedTime = currentTime;
		}
		return map.get(new WeakKey<Object>(key,null));
	}

	@Override
	public V put(K key, V value)
	{
		//expungeStaleEntries();
		long currentTime = System.currentTimeMillis();
		if(currentTime-lastExpungedTime > 50)
		{
			expungeStaleEntries();
			lastExpungedTime = currentTime;
		}
		keysAdded++;
		return map.put(new WeakKey<K>(key, referenceKeyQueue), value);
	}

	@Override
	public V remove(Object key)
	{
		//expungeStaleEntries();
		return map.remove(new WeakKey<Object>(key, null));
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m)
	{
		//expungeStaleEntries();
		super.putAll(m);
	}

	@Override
	public void clear()
	{
		Reference<?> reference;
		while ((reference = referenceKeyQueue.poll()) != null)
		{
			map.remove(reference);
		}
        map.clear();
	}

	@Override
	public Set<K> keySet()
	{
		//expungeStaleEntries();
		return super.keySet();
	}

	@Override
	public Collection<V> values()
	{
		//expungeStaleEntries();
		return super.values();
	}

	@Override
	public Set<Entry<K, V>> entrySet()
	{
		return null;
	}

	@Override
	public V putIfAbsent(K key, V value)
	{
		//expungeStaleEntries();
		return map.putIfAbsent(new WeakKey<K>(key, referenceKeyQueue), value);
	}

	@Override
	public boolean remove(Object key, Object value)
	{
		//expungeStaleEntries();
		return map.remove(new WeakKey<Object>(key, null),value);
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue)
	{
		//expungeStaleEntries();
		return map.replace(new WeakKey<K>(key, null), oldValue, newValue);
	}

	@Override
	public V replace(K key, V value)
	{
		//expungeStaleEntries();
		return map.replace(new WeakKey<K>(key, null), value);
	}
	
	public static class WeakKey<T> extends WeakReference<T>
	{
		public int hash;
		
		public WeakKey(T referent, ReferenceQueue<T> queue)
		{
			super(referent,queue);
			hash = System.identityHashCode(referent);
		}
		
		@Override
        public boolean equals(Object obj)
		{
			if(this==obj)
			{
				return true;
			}
			if(obj instanceof WeakKey && ((WeakKey<?>) obj).get() == get())
			{
				return true;
			}
            return false;
        }
		
		@Override
		public int hashCode()
		{
            return hash;
        }
	}
}
