package uk.gov.justice.digital.hmpps.prisonersearch.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

internal class CanonicalPncKtTest {

  @ParameterizedTest
  @CsvSource(
    "2003/1234567a,         2003/1234567A",
    "2003/0234567a,         2003/234567A",
    "2003/0034567a,         2003/34567A",
    "2003/0004567a,         2003/4567A",
    "2003/0000567a,         2003/567A",
    "2003/0000067a,         2003/67A",
    "2003/0000007a,         2003/7A",
    "2003/0000000a,         2003/0A",
    "03/1234567a,           03/1234567A",
    "03/0234567a,           03/234567A",
    "03/0034567a,           03/34567A",
    "03/0004567a,           03/4567A",
    "03/0000567a,           03/567A",
    "03/0000067a,           03/67A",
    "03/0000007a,           03/7A",
    "03/0000000a,           03/0A",
  )
  internal fun `will convert to canonical form of PNC when it is valid`(input: String, expected: String) {
    assertThat(input.canonicalPNCNumber()).isEqualTo(expected)
  }

  @ParameterizedTest
  @CsvSource(
    "2003/A,          2003/A",
    "203/1234567A,    203/1234567A",
    "203/1234567,     203/1234567",
    "1234567A,        1234567A",
    "2013,            2013",
    "john smith,      john smith",
    "john/smith,      john/smith",
    "16/11/2018,      16/11/2018",
    "16-11-2018,      16-11-2018",
    "111111/11A,      111111/11A",
    "SF68/945674U,    SF68/945674U",
    " , ",
    "2010/BBBBBBBA,   2010/BBBBBBBA",
    " - 20/0009n ,    - 20/0009n ",
  )
  internal fun `will not convert to canonical form of PNC when it is not valid`(input: String?, expected: String?) {
    assertThat(input?.canonicalPNCNumber()).isEqualTo(expected)
  }

  @ParameterizedTest
  @CsvSource(
    "2003/1234567a,   03/1234567A",
    "1993/0234567a,   93/234567A",
    "2000/0034567a,   00/34567A",
    "2003/0004567a,   03/4567A",
    "1993/0000567a,   93/567A",
    "2000/0000067a,   00/67A",
    "2003/0000007a,   03/7A",
    "2000/0000000a,   00/0A",
    "03/1234567a,     03/1234567A",
    "93/0234567a,     93/234567A",
    "00/0034567a,     00/34567A",
    "03/0004567a,     03/4567A",
    "93/0000567a,     93/567A",
    "00/0000067a,     00/67A",
    "03/0000007a,     03/7A",
    "93/0000000a,     93/0A",
  )
  internal fun `will convert to canonical form of PNC with short year when it is valid`(input: String, expected: String) {
    assertThat(input.canonicalPNCNumberShort()).isEqualTo(expected)
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "2003/A",
      "203/1234567A",
      "203/1234567",
      "1234567A",
      "2013",
      "john smith",
      "john/smith",
      "16/11/2018",
      "16-11-2018",
      "111111/11A",
      "SF68/945674U",
      "",
      "2010/BBBBBBBA",
      " - 20/0009n ",
    ],
  )
  internal fun `will return null and not convert to canonical form of PNC with short year when it is not valid`(input: String?) {
    assertThat(input?.canonicalPNCNumberShort()).isNull()
  }

  @ParameterizedTest
  @CsvSource(
    "2003/1234567a, 2003/1234567A",
    "1993/0234567a, 1993/234567A",
    "2000/0034567a, 2000/34567A",
    "2003/0004567a, 2003/4567A",
    "1993/0000567a, 1993/567A",
    "2000/0000067a, 2000/67A",
    "2003/0000007a, 2003/7A",
    "1983/0000000a, 1983/0A",
    "03/1234567a,   2003/1234567A",
    "93/0234567a,   1993/234567A",
    "00/0034567a,   2000/34567A",
    "03/0004567a,   2003/4567A",
    "93/0000567a,   1993/567A",
    "00/0000067a,   2000/67A",
    "03/0000007a,   2003/7A",
    "83/0000000a,   1983/0A",
  )
  internal fun `will convert to canonical form of PNC with long year  when it is valid`(input: String, expected: String) {
    assertThat(input.canonicalPNCNumberLong()).isEqualTo(expected)
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "2003/A",
      "203/1234567A",
      "203/1234567",
      "1234567A",
      "2013",
      "john smith",
      "john/smith",
      "16/11/2018",
      "16-11-2018",
      "111111/11A",
      "SF68/945674U",
      "",
      "2010/BBBBBBBA",
      " - 20/0009n ",
    ],
  )
  internal fun `will return null and not convert to canonical form of PNC with long year when it is not valid`(input: String?) {
    assertThat(input?.canonicalPNCNumberLong()).isNull()
  }
}
