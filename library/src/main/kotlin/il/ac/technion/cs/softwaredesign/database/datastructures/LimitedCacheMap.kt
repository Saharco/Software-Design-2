package il.ac.technion.cs.softwaredesign.database.datastructures

class LimitedCacheMap<K, V>(var limit: Int) {

    private val map = mutableMapOf<K, V>()

    fun containsKey(key: K): Boolean {
        return map.containsKey(key)
    }

    fun containsValue(value: V): Boolean {
        return map.containsValue(value)
    }

    operator fun get(key: K): V? {
        return map[key]
    }

    operator fun set(key: K, value: V) {
        if (map.size >= limit)
            flush()
        map[key] = value
    }

    fun isEmpty(): Boolean {
        return map.isEmpty()
    }

    fun flush() {
        map.clear()
    }
}