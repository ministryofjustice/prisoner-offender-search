package uk.gov.justice.digital.hmpps.prisonersearch.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class IndexQueueStatusTest {

  companion object {
    @JvmStatic
    private fun activeTestSource() = listOf(
      Arguments.of(0, 0, 0, false),
      Arguments.of(1, 0, 0, true),
      Arguments.of(0, 1, 0, true),
      Arguments.of(0, 0, 1, true),
      Arguments.of(0, 1, 1, true),
      Arguments.of(1, 1, 0, true),
      Arguments.of(0, 1, 1, true),
      Arguments.of(1, 0, 1, true),
      Arguments.of(1, 1, 1, true)
    )
  }

  @ParameterizedTest
  @MethodSource("activeTestSource")
  fun `index queue status active`(
    messagesOnQueue: Int,
    messagesOnDlq: Int,
    messagesInFlight: Int,
    expectedActive: Boolean
  ) {
    assertThat(IndexQueueStatus(messagesOnQueue, messagesOnDlq, messagesInFlight).active).isEqualTo(expectedActive)
  }
}
