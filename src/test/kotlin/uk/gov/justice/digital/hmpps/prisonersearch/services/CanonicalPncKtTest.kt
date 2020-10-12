package uk.gov.justice.digital.hmpps.prisonersearch.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CanonicalPncKtTest {

  @Test
  internal fun `will convert to canonical form of PNC when it is valid`() {
    assertThat("2003/1234567a".canonicalPNCNumber()).isEqualTo("2003/1234567A")
    assertThat("2003/0234567a".canonicalPNCNumber()).isEqualTo("2003/234567A")
    assertThat("2003/0034567a".canonicalPNCNumber()).isEqualTo("2003/34567A")
    assertThat("2003/0004567a".canonicalPNCNumber()).isEqualTo("2003/4567A")
    assertThat("2003/0000567a".canonicalPNCNumber()).isEqualTo("2003/567A")
    assertThat("2003/0000067a".canonicalPNCNumber()).isEqualTo("2003/67A")
    assertThat("2003/0000007a".canonicalPNCNumber()).isEqualTo("2003/7A")
    assertThat("2003/0000000a".canonicalPNCNumber()).isEqualTo("2003/0A")
    assertThat("03/1234567a".canonicalPNCNumber()).isEqualTo("03/1234567A")
    assertThat("03/0234567a".canonicalPNCNumber()).isEqualTo("03/234567A")
    assertThat("03/0034567a".canonicalPNCNumber()).isEqualTo("03/34567A")
    assertThat("03/0004567a".canonicalPNCNumber()).isEqualTo("03/4567A")
    assertThat("03/0000567a".canonicalPNCNumber()).isEqualTo("03/567A")
    assertThat("03/0000067a".canonicalPNCNumber()).isEqualTo("03/67A")
    assertThat("03/0000007a".canonicalPNCNumber()).isEqualTo("03/7A")
    assertThat("03/0000000a".canonicalPNCNumber()).isEqualTo("03/0A")
  }

  @Test
  internal fun `will not convert to canonical form of PNC when it is not valid`() {
    assertThat("2003/A".canonicalPNCNumber()).isEqualTo("2003/A")
    assertThat("203/1234567A".canonicalPNCNumber()).isEqualTo("203/1234567A")
    assertThat("203/1234567".canonicalPNCNumber()).isEqualTo("203/1234567")
    assertThat("1234567A".canonicalPNCNumber()).isEqualTo("1234567A")
    assertThat("2013".canonicalPNCNumber()).isEqualTo("2013")
    assertThat("john smith".canonicalPNCNumber()).isEqualTo("john smith")
    assertThat("john/smith".canonicalPNCNumber()).isEqualTo("john/smith")
    assertThat("16/11/2018".canonicalPNCNumber()).isEqualTo("16/11/2018")
    assertThat("16-11-2018".canonicalPNCNumber()).isEqualTo("16-11-2018")
    assertThat("111111/11A".canonicalPNCNumber()).isEqualTo("111111/11A")
    assertThat("SF68/945674U".canonicalPNCNumber()).isEqualTo("SF68/945674U")
    assertThat("".canonicalPNCNumber()).isEqualTo("")
    assertThat("2010/BBBBBBBA".canonicalPNCNumber()).isEqualTo("2010/BBBBBBBA")
    assertThat(" - 20/0009n ".canonicalPNCNumber()).isEqualTo(" - 20/0009n ")
  }

  @Test
  internal fun `will convert to canonical form of PNC with short year when it is valid`(){
    assertThat("2003/1234567a".canonicalPNCNumberShort()).isEqualTo("03/1234567A")
    assertThat("1993/0234567a".canonicalPNCNumberShort()).isEqualTo("93/234567A")
    assertThat("2000/0034567a".canonicalPNCNumberShort()).isEqualTo("00/34567A")
    assertThat("2003/0004567a".canonicalPNCNumberShort()).isEqualTo("03/4567A")
    assertThat("1993/0000567a".canonicalPNCNumberShort()).isEqualTo("93/567A")
    assertThat("2000/0000067a".canonicalPNCNumberShort()).isEqualTo("00/67A")
    assertThat("2003/0000007a".canonicalPNCNumberShort()).isEqualTo("03/7A")
    assertThat("2000/0000000a".canonicalPNCNumberShort()).isEqualTo("00/0A")
    assertThat("03/1234567a".canonicalPNCNumberShort()).isEqualTo("03/1234567A")
    assertThat("93/0234567a".canonicalPNCNumberShort()).isEqualTo("93/234567A")
    assertThat("00/0034567a".canonicalPNCNumberShort()).isEqualTo("00/34567A")
    assertThat("03/0004567a".canonicalPNCNumberShort()).isEqualTo("03/4567A")
    assertThat("93/0000567a".canonicalPNCNumberShort()).isEqualTo("93/567A")
    assertThat("00/0000067a".canonicalPNCNumberShort()).isEqualTo("00/67A")
    assertThat("03/0000007a".canonicalPNCNumberShort()).isEqualTo("03/7A")
    assertThat("93/0000000a".canonicalPNCNumberShort()).isEqualTo("93/0A")
  }

  @Test
  internal fun `will return null and not convert to canonical form of PNC with short year when it is not valid`() {
    assertThat("2003/A".canonicalPNCNumberShort()).isNull()
    assertThat("203/1234567A".canonicalPNCNumberShort()).isNull()
    assertThat("203/1234567".canonicalPNCNumberShort()).isNull()
    assertThat("1234567A".canonicalPNCNumberShort()).isNull()
    assertThat("2013".canonicalPNCNumberShort()).isNull()
    assertThat("john smith".canonicalPNCNumberShort()).isNull()
    assertThat("john/smith".canonicalPNCNumberShort()).isNull()
    assertThat("16/11/2018".canonicalPNCNumberShort()).isNull()
    assertThat("16-11-2018".canonicalPNCNumberShort()).isNull()
    assertThat("111111/11A".canonicalPNCNumberShort()).isNull()
    assertThat("SF68/945674U".canonicalPNCNumberShort()).isNull()
    assertThat("".canonicalPNCNumberShort()).isNull()
    assertThat("2010/BBBBBBBA".canonicalPNCNumberShort()).isNull()
    assertThat(" - 20/0009n ".canonicalPNCNumberShort()).isNull()
  }

  @Test
  internal fun `will convert to canonical form of PNC with long year  when it is valid`() {
    assertThat("2003/1234567a".canonicalPNCNumberLong()).isEqualTo("2003/1234567A")
    assertThat("1993/0234567a".canonicalPNCNumberLong()).isEqualTo("1993/234567A")
    assertThat("2000/0034567a".canonicalPNCNumberLong()).isEqualTo("2000/34567A")
    assertThat("2003/0004567a".canonicalPNCNumberLong()).isEqualTo("2003/4567A")
    assertThat("1993/0000567a".canonicalPNCNumberLong()).isEqualTo("1993/567A")
    assertThat("2000/0000067a".canonicalPNCNumberLong()).isEqualTo("2000/67A")
    assertThat("2003/0000007a".canonicalPNCNumberLong()).isEqualTo("2003/7A")
    assertThat("1983/0000000a".canonicalPNCNumberLong()).isEqualTo("1983/0A")
    assertThat("03/1234567a".canonicalPNCNumberLong()).isEqualTo("2003/1234567A")
    assertThat("93/0234567a".canonicalPNCNumberLong()).isEqualTo("1993/234567A")
    assertThat("00/0034567a".canonicalPNCNumberLong()).isEqualTo("2000/34567A")
    assertThat("03/0004567a".canonicalPNCNumberLong()).isEqualTo("2003/4567A")
    assertThat("93/0000567a".canonicalPNCNumberLong()).isEqualTo("1993/567A")
    assertThat("00/0000067a".canonicalPNCNumberLong()).isEqualTo("2000/67A")
    assertThat("03/0000007a".canonicalPNCNumberLong()).isEqualTo("2003/7A")
    assertThat("83/0000000a".canonicalPNCNumberLong()).isEqualTo("1983/0A")
  }

  @Test
  internal fun `will return null and not convert to canonical form of PNC with long year when it is not valid`() {
    assertThat("2003/A".canonicalPNCNumberLong()).isNull()
    assertThat("203/1234567A".canonicalPNCNumberLong()).isNull()
    assertThat("203/1234567".canonicalPNCNumberLong()).isNull()
    assertThat("1234567A".canonicalPNCNumberLong()).isNull()
    assertThat("2013".canonicalPNCNumberLong()).isNull()
    assertThat("john smith".canonicalPNCNumberLong()).isNull()
    assertThat("john/smith".canonicalPNCNumberLong()).isNull()
    assertThat("16/11/2018".canonicalPNCNumberLong()).isNull()
    assertThat("16-11-2018".canonicalPNCNumberLong()).isNull()
    assertThat("111111/11A".canonicalPNCNumberLong()).isNull()
    assertThat("SF68/945674U".canonicalPNCNumberLong()).isNull()
    assertThat("".canonicalPNCNumberLong()).isNull()
    assertThat("2010/BBBBBBBA".canonicalPNCNumberLong()).isNull()
    assertThat(" - 20/0009n ".canonicalPNCNumberLong()).isNull()
  }
}
