package uk.gov.justice.digital.hmpps.prisonersearch

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.google.gson.Gson
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.services.SearchCriteria


@ActiveProfiles(profiles = ["test", "test-queue"])
abstract class QueueIntegrationTest : IntegrationTest() {

    @Autowired
    lateinit var queueUrl: String

    @Autowired
    lateinit var indexQueueUrl: String

    @Autowired
    lateinit var gson: Gson

    fun getNumberOfMessagesCurrentlyOnQueue(): Int? {
        val queueAttributes = awsSqsClient.getQueueAttributes(queueUrl, listOf("ApproximateNumberOfMessages"))
        return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
    }

    fun getNumberOfMessagesCurrentlyOnIndexQueue(): Int? {
        val queueAttributes = awsSqsClient.getQueueAttributes(indexQueueUrl, listOf("ApproximateNumberOfMessages"))
        return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
    }


    fun prisonRequestCountFor(url: String) = prisonMockServer.findAll(getRequestedFor(urlEqualTo(url))).count()

    fun indexPrisoners() {
        webTestClient.put().uri("/prisoner-index/build-index")
            .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
            .exchange()
            .expectStatus().isOk

        await untilCallTo { prisonRequestCountFor("/api/offenders/A7089EY") } matches { it == 1 }
        await untilCallTo { prisonRequestCountFor("/api/offenders/A7089EZ") } matches { it == 1 }
        await untilCallTo { prisonRequestCountFor("/api/offenders/A7089FA") } matches { it == 1 }
        await untilCallTo { prisonRequestCountFor("/api/offenders/A7089FB") } matches { it == 1 }
        await untilCallTo { prisonRequestCountFor("/api/offenders/A7089FC") } matches { it == 1 }

        await untilCallTo { getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it == 0 }
    }

    fun search(searchCriteria: SearchCriteria, fileAssert: String) {
        webTestClient.post().uri("/prisoner-search/match")
            .body(BodyInserters.fromValue(gson.toJson(searchCriteria)))
            .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
            .header("Content-Type", "application/json")
            .exchange()
            .expectStatus().isOk
            .expectBody().json(fileAssert.readResourceAsText())
    }

}

private fun String.readResourceAsText(): String = QueueIntegrationTest::class.java.getResource(this).readText()