package uk.org.justice.digital.hmpps.prisonersearch.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.elasticsearch.client.RestHighLevelClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.elasticsearch.client.ClientConfiguration
import org.springframework.data.elasticsearch.client.RestClients
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder


@Configuration
@EnableElasticsearchRepositories(basePackages = ["uk.org.justice.digital.hmpps.prisonersearch.repository"])
class ElasticSearchConfiguration {

    @Value("\${elasticsearch.port}")
    private val port = 0
    @Value("\${elasticsearch.host}")
    private val host: String? = null
    @Value("\${elasticsearch.scheme}")
    private val scheme: String? = null
    @Value("\${elasticsearch.aws.signrequests}")
    private val shouldSignRequests = false
    @Value("\${aws.region:eu-west-2}")
    private val awsRegion: String? = null

    @Bean
    fun elasticsearchClient(): RestHighLevelClient {
        return RestClients.create(ClientConfiguration.create("$host:$port")).rest()
    }

    @Bean
    @Primary
    fun objectMapper(builder: Jackson2ObjectMapperBuilder): ObjectMapper? {
        val objectMapper: ObjectMapper = builder.build()
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        objectMapper.registerModule(JavaTimeModule())
        objectMapper.registerModule(KotlinModule())
        return objectMapper
    }

}

