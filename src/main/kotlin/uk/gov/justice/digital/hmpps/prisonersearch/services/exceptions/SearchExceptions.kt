package uk.gov.justice.digital.hmpps.prisonersearch.services.exceptions

class BadRequestException(msg: String) : RuntimeException(msg)

class InvalidRequestException(msg: String) : RuntimeException(msg)

class NotFoundException(msg: String) : RuntimeException(msg)

class UnauthorisedException(msg: String) : RuntimeException(msg)