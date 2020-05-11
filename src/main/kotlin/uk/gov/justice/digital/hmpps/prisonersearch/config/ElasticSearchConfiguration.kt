package uk.gov.justice.digital.hmpps.prisonersearch.config

import org.apache.http.HttpHost
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


@Configuration
@EnableElasticsearchRepositories(basePackages = ["uk.gov.justice.digital.hmpps.prisonersearch.repository"])
class ElasticSearchConfiguration : AbstractElasticsearchConfiguration() {

    @Value("\${elasticsearch.port}")
    private val port = 0

    @Value("\${elasticsearch.host}")
    private val host: String? = null

    @Value("\${elasticsearch.scheme}")
    private val scheme: String? = null

    @Bean("elasticSearchClient")
    override fun elasticsearchClient(): RestHighLevelClient {
        return RestHighLevelClient(RestClient.builder(HttpHost(host, port, scheme)))
    }

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
                StringToDateConverter(),
                DateTimeToStringConverter(),
                StringToDateTimeConverter()
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

    @WritingConverter
    class DateTimeToStringConverter : Converter<LocalDateTime, String> {
        override fun convert(localDateTime: LocalDateTime): String {
            return localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        }
    }

    @ReadingConverter
    class StringToDateTimeConverter : Converter<String, LocalDateTime> {
        override fun convert(dateStr: String): LocalDateTime {
            return LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }

}

