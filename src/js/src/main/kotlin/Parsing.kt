import kotlinx.coroutines.yield

private val commentRegexp = Regex("\\s*#\\s+.*")
private val sectionRegexp = Regex("^\\[([^\\]/]+)(/i)?\\]")
// General section
private val generalNameRegexp = Regex("name = (.+)")
// Colors section
private val colorRegexp = Regex("([^=]+?)\\s*=\\s*(#[0-9A-F]{6})")
// Rules section
private val endTagRegexp = Regex("\\s*(#\\S+)$")
private val equalsRegexp = Regex("\\s+=\\s+")
private val weightListRegexp = Regex("(?:(\\d+%)(?:\\s+|$))+?")
private val weightedCategoriesRegexp = Regex("(\\d+%)\\s+(\\S.+?(?=\\s+\\d+%|$))")

class IllegalRuleFormatException(m : String) : Exception(m)
class CyclicRuleException(m : String) : Exception(m)

fun thr(e : String) : Nothing = throw RuntimeException(e)

fun checkNoCycle(regexp : Regex, cat : Category) {
  fun checkNoContains(needle : Category, haystack : Category) {
    if (needle == haystack) throw CyclicRuleException(haystack.name)
    haystack.parents.forEach {
      try {
        checkNoContains(needle, it.category)
      } catch (e : CyclicRuleException) {
        throw CyclicRuleException("${it.category.name} → ${e.message}")
      }
    }
  }
  try {
    cat.parents.forEach { checkNoContains(cat, it.category) }
  } catch (e : CyclicRuleException) {
    throw CyclicRuleException("Cyclic rules : ${cat.name} matches ${regexp} → ${e.message}")
  }
}

data class WeightedString(val name : String, val weight : Float)
fun String.parsePercentage() = if (this.last() != '%') throw NumberFormatException("Not a percentage : ${this}") else this.substring(0, length-1).toFloat() / 100

fun parseTags(classification : String) : Pair<String, List<String>> {
  var l = classification.trim()
  val tags = mutableListOf<String>()
  do {
    val m = endTagRegexp.find(l) ?: break
    l = l.substring(0, m.range.first)
    tags.add(m.groups[1]?.value!!)
  } while (true)
  return l.trim() to tags
}

fun parseCategoriesUnsafe(regexpSpec : String, catNames : String) : List<WeightedString> {
  if (null != weightListRegexp.matchEntire(catNames)) {
    val weights = weightListRegexp.findAll(catNames).toList()
      .map { it.groups[1]?.value ?: thr("wtf is this weight list ${catNames} ${it.groups}") }
    val categories = regexpSpec.split("\\+")
    if (categories.size != weights.size)
      throw IllegalRuleFormatException("Can only omit category names when regexp is '+'-separated list of the same size : ${regexpSpec} <> ${catNames} ; ${categories} <> ${weights}")
    return categories.zip(weights) { category, weight -> WeightedString(category.trim(), weight.parsePercentage()) }
  } else {
    val parsed = weightedCategoriesRegexp.findAll(catNames).toList()
    if (parsed.isNotEmpty()) return parsed.map { WeightedString(it.groups[2]?.value ?: thr("NPE DUDE"), it.groups[1]?.value?.parsePercentage() ?: thr("wtf is this percentage ${it}")) }
  }
  return listOf(WeightedString(catNames, 1f))
}

fun parseCategories(regexpSpec : String, catNames : String) = parseCategoriesUnsafe(regexpSpec, catNames).also {
  val total = it.fold(0f) { acc, e -> acc + e.weight }
  if (total !in 0.999..1.001) throw IllegalRuleFormatException("Percentages don't add to 100% (${total}) : ${catNames}")
}

private fun Char.parseHex() = when(this) {
  in '0'..'9' -> this - '0'
  in 'A'..'F' -> 10 + (this - 'A')
  in 'a'..'f' -> 10 + (this - 'a')
  else -> throw IllegalArgumentException("Can't parse hex char ${this}")
}

private fun String.parseColor() : Array<Float> {
  if (this[0] != '#' || this.length != 7) throw IllegalStateException("Somehow color not at correct format : \"${this}\"")
  return arrayOf(
    ((this[1].parseHex() shl 4) + this[2].parseHex()).toFloat() / 255f,
    ((this[3].parseHex() shl 4) + this[4].parseHex()).toFloat() / 255f,
    ((this[5].parseHex() shl 4) + this[6].parseHex()).toFloat() / 255f)
}

enum class RuleMode { INITIAL, GENERAL, COLORS, RULES, COUNTERS, MARKERS, EXCLUDE }
suspend fun parseRules(rules : String, progressReporter : (Int) -> Unit) : Rules {
  var mode = RuleMode.INITIAL
  var caseInsensitive = false

  // General section memory
  var fileName : String? = null
  // Colors section memory
  val colors = mutableMapOf<String, Array<Float>>()

  // Rules mode memory
  val allCategories = HashMap<String, Category>()
  val allTags = HashMap<String, Tag>()
  val associations = mutableListOf<Assoc>()
  fun makeCategory(name : String) =
    Category(name, associations.find { it.regexp.matches(name) }?.categories ?: mutableListOf(WeightedCategory(Category.TOP, 1f))).also { allCategories[name] = it }
  fun makeTag(name : String) =
    Tag(name, associations.find { it.regexp.matches(name) }?.tags ?: listOf(Tag.TOP)).also { allTags[name] = it }
  fun associate(regexpSpec : String, classification : String) {
    val regexp = if (caseInsensitive) Regex(regexpSpec, RegexOption.IGNORE_CASE) else Regex(regexpSpec)
    val (categoryNames, tagNames) = parseTags(classification)
    val categories = parseCategories(regexpSpec, categoryNames)
    // If the regexp starts with a #, then categories are not allowed on the right side
    when (regexpSpec.indexOf('#')) {
      // Tag spec
      0 -> if (categories.isNotEmpty()) throw IllegalRuleFormatException("Tags can only be categorized as other tags")
      -1 -> {} // No #, no problem
      else -> throw IllegalRuleFormatException("Can't match on a # unless it's a tag")
    }

    // Find existing parents or make new categories if none
    val parents = categories.map {
      WeightedCategory(allCategories[it.name] ?: makeCategory(it.name), it.weight)
    }
    val singleCategory = if (parents.size == 1) parents[0].category else null
    // Now find any top-level category that match this regexp and reparent it to the parents
    val reparent =
      allCategories.filter { it.value.isTopLevel() && regexp.matches(it.value.name) && it.value != singleCategory }
    reparent.forEach {
      (it.value.parents as MutableList).apply { clear(); addAll(parents) }
      checkNoCycle(regexp, it.value)
    }

    // Find existing tags or make new ones
    val tags = tagNames.map { allTags[it] ?: makeTag(it) }
    // Possibly reparent any top-level tag
    if (0 == regexpSpec.indexOf('#')) {
      @Suppress("NAME_SHADOWING") val reparent =
        allTags.filter { it.value.isTopLevel() && regexp.matches(it.value.name) }
      reparent.forEach { (it.value.parents as MutableList).apply { clear(); addAll(tags) } }
    }

    val assoc = Assoc(regexp, parents, tags)
    associations.add(assoc)
  }

  //Exclude mode memory
  val exclusions = mutableListOf<Assoc>()
  fun exclude(regexpSpec : String, classification : String) {
    val regexp = if (caseInsensitive) Regex(regexpSpec, RegexOption.IGNORE_CASE) else Regex(regexpSpec)
    val category = allCategories[classification] ?: Category(classification, emptyList()).also { allCategories[classification] = it }
    val excl = Assoc(regexp, listOf(WeightedCategory(category, 1f)), emptyList())
    exclusions.add(excl)
  }

  val lines = rules.lines()
  val total = lines.size
  var currentLine = 0
  lines.forEach { rawSpec ->
    currentLine += 1
    val spec = rawSpec.replace(commentRegexp, "").trim()
    if (spec.isEmpty()) return@forEach

    sectionRegexp.matchEntire(spec)?.let {
      mode = when (it.groups[1]?.value?.lowercase()) {
        "general" -> RuleMode.GENERAL
        "colors" -> RuleMode.COLORS
        "collapse", "rules" -> RuleMode.RULES
        "counters" -> RuleMode.COUNTERS
        "markers" -> RuleMode.MARKERS
        "exclude" -> RuleMode.EXCLUDE
        else -> throw IllegalRuleFormatException("Unknown section ${spec}")
      }
      caseInsensitive = it.groups[2].notNull()
      return@forEach
    }

    when (mode) {
      RuleMode.INITIAL -> throw IllegalRuleFormatException("Must start with a section, not \"${rawSpec}\"")
      RuleMode.GENERAL -> {
        generalNameRegexp.matchEntire(spec)?.let { fileName = it.groups[1]?.value }
      }
      RuleMode.COLORS -> {
        colorRegexp.matchEntire(spec)?.let { colors[it.groups[1]!!.value] = it.groups[2]!!.value.parseColor() } ?: throw IllegalRuleFormatException("Malformed color spec in color section : ${spec}")
      }
      RuleMode.COUNTERS -> {
        // Counters are not parsed yet because they haven't been used in forever and need rethinking anyway
      }
      RuleMode.MARKERS -> {
        // Markers are not parsed yet because they haven't been used in forever and need rethinking anyway
      }

      RuleMode.EXCLUDE -> {
        val spec = spec.split(equalsRegexp)
        val classification = spec[spec.size - 1]
        spec.dropLast(1).forEach { exclude(it, classification) }
      }
      RuleMode.RULES -> {
        val spec = spec.split(equalsRegexp)
        val classification = spec[spec.size - 1]
        spec.dropLast(1).forEach { associate(it, classification) }
      }
    }
    progressReporter((100 * currentLine) / total)
    yield()
  }
  progressReporter((100 * currentLine) / total)
  val name = fileName ?: throw IllegalRuleFormatException("No name for this set of rules, add [general] name=...")
  colors[Category.TOP.name] = arrayOf(1f, 1f, 1f)
  return Rules(name, associations + exclusions, AutoColorMap(colors.toMap()))
}
