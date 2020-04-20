package uk.org.justice.digital.hmpps.prisonersearch.resource

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.RestClientResponseException
import javax.validation.ValidationException

@RestControllerAdvice(basePackages = ["uk.org.justice.hmpps.prisonersearch.resource"])
class PrisonToNhsExceptionHandler {
    @ExceptionHandler(RestClientResponseException::class)
    fun handleRestClientResponseException(e: RestClientResponseException): ResponseEntity<ByteArray>? {
        return ResponseEntity
                .status(e.rawStatusCode)
                .body(e.responseBodyAsByteArray)
    }

    @ExceptionHandler(ValidationException::class)
    fun handleValidationException(e: java.lang.Exception): ResponseEntity<ErrorResponse?>? {
        log.info("Validation exception: {}", e.message)
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse(status = HttpStatus.BAD_REQUEST, developerMessage = e.message))
    }

    companion object {
        val log = LoggerFactory.getLogger(PrisonToNhsExceptionHandler::class.java)
    }
}

data class ErrorResponse(val status: Int,
                         val errorCode: Int? = null,
                         val userMessage: String? = null,
                         val developerMessage: String? = null,
                         val moreInfo: String? = null) {
    constructor(status: HttpStatus,
                errorCode: Int? = null,
                userMessage: String? = null,
                developerMessage: String? = null,
                moreInfo: String? = null)
            : this(status.value(), errorCode, userMessage, developerMessage, moreInfo)
}