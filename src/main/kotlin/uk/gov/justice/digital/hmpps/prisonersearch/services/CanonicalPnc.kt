package uk.gov.justice.digital.hmpps.prisonersearch.services

internal fun String.canonicalPNCNumber(): String = if (isPNCNumber()) combinePNC(splitPNC()) else this

internal fun String.canonicalPNCNumberShort(): String? =
  when {
    isPNCNumberShort() -> combinePNC(splitPNC())
    isPNCNumberLong() -> with(splitPNC()) { combinePNC(Pnc(year.substring(2, 4), serialNumber, checksum)) }
    else -> null
  }

internal fun String.canonicalPNCNumberLong(): String? =
  when {
    isPNCNumberLong() -> combinePNC(splitPNC())
    isPNCNumberShort() -> with(splitPNC()) { combinePNC(Pnc(addCenturyToYear(year), serialNumber, checksum)) }
    else -> null
  }

private fun combinePNC(pnc: Pnc) = with(pnc) { "$year/$serialNumber$checksum".uppercase() }

private fun String.splitPNC(): Pnc {
  val (year, serial) = split("/")
  val serialNumber = serial.substring(0, serial.length - 1).toInt()
  val checksum = last()
  return Pnc(year, serialNumber, checksum)
}

data class Pnc(val year: String, val serialNumber: Int, val checksum: Char)

private fun String.isPNCNumber() = matches("^([0-9]{2}|[0-9]{4})/[0-9]+[a-zA-Z]".toRegex())

private fun String.isPNCNumberShort() = matches("^([0-9]{2})/[0-9]+[a-zA-Z]".toRegex())

private fun String.isPNCNumberLong() = matches("^([0-9]{4})/[0-9]+[a-zA-Z]".toRegex())

private fun addCenturyToYear(year: String): String = if (year.toInt() in 39..99) "19$year" else "20$year"
