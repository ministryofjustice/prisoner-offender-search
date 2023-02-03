package uk.gov.justice.digital.hmpps.prisonersearch

import org.junit.jupiter.api.ClassDescriptor
import org.junit.jupiter.api.ClassOrderer
import org.junit.jupiter.api.ClassOrdererContext

class SearchDataClassOrderer : ClassOrderer {
  override fun orderClasses(classOrdererContext: ClassOrdererContext) {
    classOrdererContext.classDescriptors.sortWith(Comparator.comparingInt { getOrder(it) })
  }

  private fun getOrder(classDescriptor: ClassDescriptor): Int =
    if (AbstractSearchDataIntegrationTest::class.java.isAssignableFrom(classDescriptor.testClass)) {
      1
    } else
      2
}
