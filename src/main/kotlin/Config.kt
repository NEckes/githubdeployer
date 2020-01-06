import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

@Suppress("unused", "MemberVisibilityCanBePrivate")
object Config {
    private val pattern = "(?<key>[^=\\s]+)(?:(?<hasEqual>=)(?<value>(?:(?<hasQuotes>\").*?\"|.+?))\\s)?".toPattern()
    private const val prefix = "githubdeployer."

    private val properties = mutableMapOf<String, String>()

    fun load(args: Array<String>) {
        properties += fileToMap() + argsToMap(args) + propertiesToMap()
    }

    private fun fileToMap(): Map<String, String> {
        val configPath = getPath("configPath") ?: Paths.get("config.properties")
        return if (Files.exists(configPath)) {
            try {
                Properties().apply {
                    load(Files.newBufferedReader(configPath, StandardCharsets.UTF_8))
                }.cast<String, String>()
            } catch (e: IOException) {
                System.err.println("Cloud not open config file $configPath")
                e.printStackTrace()
                emptyMap<String, String>()
            }
        } else emptyMap()
    }

    private fun propertiesToMap(): Map<String, String> = System.getProperties().cast<String, String>()
        .filterKeys { it.startsWith(prefix) }.mapKeys { it.key.substring(prefix.length) }

    private fun argsToMap(args: Array<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val matcher = pattern.matcher(args.joinToString(separator = " ", postfix = " "))
        while (matcher.find()) {
            result[matcher.group("key")] = (matcher.group("value")?:"").let { if (matcher.group("hasQuotes") != null) it.substring(1, it.length - 1) else it }
        }
        return result
    }

    fun getBool(key: String?): Boolean? = tryParse(key) { s: String ->
        when (s.toLowerCase()) {
            "true", "" -> true
            "false" -> false
            else -> null
        }
    }
    fun getInt(key: String?): Int? = tryParse(key) { s: String -> s.toInt() }
    fun getLong(key: String?): Long? = tryParse(key) { s: String -> s.toLong() }
    fun getFloat(key: String?): Float? = tryParse(key) { s: String -> s.toFloat() }
    fun getDouble(key: String?): Double? = tryParse(key) { s: String -> s.toDouble() }
    fun getByte(key: String?): Byte? = tryParse(key) { s: String -> s.toByte() }
    fun getShort(key: String?): Short? = tryParse(key) { s: String -> s.toShort() }
    fun getChar(key: String?): Char? = tryParse(key) { s: String -> if (s.length == 1) s[0] else null }
    fun getString(key: String?): String? = tryParse(key) { s -> s }
    fun getStringList(key: String?): Array<String>? = tryParse(key) { s: String -> s.split(",").toTypedArray() }
    fun getPath(key: String?): Path? = tryParse(key) { s: String ->
        try {
            Paths.get(s)
        } catch (e: InvalidPathException) {
            null
        }
    }

    val bool = object : ReadOnlyProperty<Any?, Boolean?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = getBool(property.name)
    }
    val int = object : ReadOnlyProperty<Any?, Int?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = getInt(property.name)
    }
    val long = object : ReadOnlyProperty<Any?, Long?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = getLong(property.name)
    }
    val float = object : ReadOnlyProperty<Any?, Float?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = getFloat(property.name)
    }
    val double = object : ReadOnlyProperty<Any?, Double?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = getDouble(property.name)
    }
    val byte = object : ReadOnlyProperty<Any?, Byte?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = getByte(property.name)
    }
    val short = object : ReadOnlyProperty<Any?, Short?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = getShort(property.name)
    }
    val char = object : ReadOnlyProperty<Any?, Char?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = getChar(property.name)
    }
    val string = object : ReadOnlyProperty<Any?, String?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = getString(property.name)
    }
    val stringList = object : ReadOnlyProperty<Any?, Array<String>?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = getStringList(property.name)
    }
    val path = object : ReadOnlyProperty<Any?, Path?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = getPath(property.name)
    }

    private fun <T> tryParse(key: String?, parser: (String) -> T?): T? {
        return try {
            parser((if (key == null) null else properties[key]) ?: return null)
        } catch (e: NumberFormatException) {
            null
        }
    }
}

fun <T : Any> ReadOnlyProperty<Any?, T?>.or(def: T): ReadOnlyProperty<Any?, T> = object : ReadOnlyProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = this@or.getValue(thisRef, property) ?: def
}

fun <T : Any> ReadOnlyProperty<Any?, T?>.notNull(): ReadOnlyProperty<Any?, T> = object : ReadOnlyProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = this@notNull.getValue(thisRef, property)
        ?: throw IllegalArgumentException("Missing argument ${property.name} of type ${property.returnType}")
}

inline fun <reified K, reified V> Map<*, *>.cast(): Map<K, V> = mapNotNull { (k, v) -> (k as? K)?.let { sk -> (v as? V)?.let { sv -> sk to sv } } }.toMap()

