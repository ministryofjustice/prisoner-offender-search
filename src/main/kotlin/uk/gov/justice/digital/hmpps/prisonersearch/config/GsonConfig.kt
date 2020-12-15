package uk.gov.justice.digital.hmpps.prisonersearch.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Configuration
class GsonConfig() {

  @Bean
  fun gson(): Gson {
    return GsonBuilder().setPrettyPrinting()
      .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
      .create()
  }

  internal class LocalDateAdapter : JsonSerializer<LocalDate?>, JsonDeserializer<LocalDate?> {
    override fun serialize(src: LocalDate?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
      return JsonPrimitive(src?.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): LocalDate? {
      return LocalDate.parse(json?.asJsonPrimitive?.asString)
    }
  }
}
