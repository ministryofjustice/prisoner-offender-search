package uk.org.justice.digital.hmpps.prisonersearch.config

import com.amazonaws.auth.AWS4Signer
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import org.apache.http.HttpHost
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.core.convert.support.DefaultConversionService
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration
import org.springframework.data.elasticsearch.core.ElasticsearchEntityMapper
import org.springframework.data.elasticsearch.core.EntityMapper
import org.springframework.data.elasticsearch.core.convert.ElasticsearchCustomConversions
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories
import java.time.LocalDate
import java.time.format.DateTimeFormatter


@Configuration
@EnableElasticsearchRepositories(basePackages = ["uk.org.justice.digital.hmpps.prisonersearch.repository"])
class ElasticSearchConfiguration : AbstractElasticsearchConfiguration() {

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
    override fun elasticsearchClient(): RestHighLevelClient {
        if (shouldSignRequests) {
            val signer = AWS4Signer()
            signer.serviceName = "es"
            signer.regionName = awsRegion
            val clientBuilder = RestClient.builder(HttpHost(host, port, scheme)).setHttpClientConfigCallback { callback: HttpAsyncClientBuilder ->
                callback.addInterceptorLast(
                    AWSRequestSigningApacheInterceptor(signer.serviceName, signer, DefaultAWSCredentialsProviderChain()))
            }
            return RestHighLevelClient(clientBuilder)
        }
        return RestHighLevelClient(RestClient.builder(HttpHost(host, port, scheme)))    }

    @Bean
    override fun entityMapper(): EntityMapper? {
        val entityMapper = ElasticsearchEntityMapper(elasticsearchMappingContext(), DefaultConversionService())
        entityMapper.setConversions(elasticsearchCustomConversions())
        return entityMapper
    }

    override fun elasticsearchCustomConversions(): ElasticsearchCustomConversions? {
        return ElasticsearchCustomConversions(
            listOf(
                DateToStringConverter(),
                StringToDateConverter()
            )
        )
    }

    @WritingConverter
    class DateToStringConverter : Converter<LocalDate, String> {
        override fun convert(localDate: LocalDate): String {
            return localDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        }
    }

    @ReadingConverter
    class StringToDateConverter : Converter<String, LocalDate> {
        override fun convert(dateStr: String): LocalDate {
            return LocalDate.parse(dateStr);
        }
    }

}

