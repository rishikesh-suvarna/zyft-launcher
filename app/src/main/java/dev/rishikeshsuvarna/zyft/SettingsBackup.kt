package dev.rishikeshsuvarna.zyft

/**
 * Plain-text launcher settings, so they can be moved to another TV (`make backup`) and read or
 * edited by hand. One `type<TAB>key<TAB>value` line per SharedPreferences entry after a version
 * line; string sets are comma-joined, which is safe because they only ever hold package names.
 */
const val SETTINGS_VERSION = "zyft-settings-v1"
private const val FIELD = '\t'
private const val SET_SEPARATOR = ","

private enum class ValueType(val tag: String) { STRING("s"), BOOLEAN("b"), INT("i"), LONG("l"), STRING_SET("set") }

fun encodeSettings(entries: Map<String, Any?>): String = buildString {
    append(SETTINGS_VERSION).append('\n')
    entries.toSortedMap().forEach { (key, value) ->
        val (type, text) = when (value) {
            is String -> ValueType.STRING to value
            is Boolean -> ValueType.BOOLEAN to value.toString()
            is Int -> ValueType.INT to value.toString()
            is Long -> ValueType.LONG to value.toString()
            is Set<*> -> ValueType.STRING_SET to value.map { it as String }.sorted().also { items ->
                require(items.none { SET_SEPARATOR in it }) { "set item with a comma in $key" }
            }.joinToString(SET_SEPARATOR)
            else -> throw IllegalArgumentException("unsupported value for $key: $value")
        }
        require(listOf(key, text).none { FIELD in it || '\n' in it }) { "tab or newline in $key" }
        append(type.tag).append(FIELD).append(key).append(FIELD).append(text).append('\n')
    }
}

/** All or nothing: a file that is from another version or has any bad line is rejected. */
fun decodeSettings(text: String): Map<String, Any> {
    val lines = text.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotEmpty() }.toList()
    require(lines.firstOrNull() == SETTINGS_VERSION) { "not a $SETTINGS_VERSION file" }
    return lines.drop(1).associate { line ->
        val f = line.split(FIELD, limit = 3)
        require(f.size == 3 && f[1].isNotEmpty()) { "bad line: $line" }
        val (tag, key, value) = f
        val type = ValueType.entries.firstOrNull { it.tag == tag } ?: throw IllegalArgumentException("bad type in: $line")
        key to when (type) {
            ValueType.STRING -> value
            ValueType.BOOLEAN -> requireNotNull(value.toBooleanStrictOrNull()) { "bad boolean in: $line" }
            ValueType.INT -> requireNotNull(value.toIntOrNull()) { "bad int in: $line" }
            ValueType.LONG -> requireNotNull(value.toLongOrNull()) { "bad long in: $line" }
            ValueType.STRING_SET -> value.split(SET_SEPARATOR).filter { it.isNotEmpty() }.toSet()
        }
    }
}
