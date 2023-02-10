package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

import io.swagger.v3.oas.annotations.media.Schema

data class PhysicalDetailRequest(
  @Schema(
    description = "List of prison codes to filter results by",
    example = "['LEI', 'MDI']",
    required = true,
  )
  val prisonIds: List<String>? = emptyList(),

  @Schema(
    description = "Filter for the prisoners cell location. A block wing or cell can be specified. With prison id can be included or absent so HEI-3-1 and 3-1 are equivalent when the prison id is HEI",
    example = "3-1",
  )
  val cellLocationPrefix: String? = null,

  @Schema(
    description = "Gender, searching on the description in the domain SEX in the REFERENCE_CODES table.",
    example = "Male",
    allowableValues = ["Female", "Male", "Not Known / Not Recorded", "Not Specified (Indeterminate)", "Refused"],
  )
  val gender: String? = null,

  @Schema(
    description = "Ethnicity, searching on the description in the domain ETHNICITY in the REFERENCE_CODES table.",
    example = "White : Irish",
    allowableValues = [
      "Asian/Asian British: Indian", "Asian/Asian British: Pakistani", "Asian/Asian British: Bangladeshi",
      "Asian/Asian British: Chinese", "Asian/Asian British: Any other backgr'nd", "Black/Black British: Caribbean",
      "Black/Black British: African", "Black/Black British: Any other Backgr'nd", "Mixed: White and Black Caribbean",
      "Mixed: White and Black African", "Mixed: White and Asian", "Mixed: Any other background",
      "Needs to be confirmed following Merge", "Prefer not to say", "Chinese", "Other: Arab",
      "Other: Any other background", "White: Eng./Welsh/Scot./N.Irish/British", "White : Irish",
      "White: Gypsy or Irish Traveller", "White : Irish Traveller/Gypsy", "White: Any other background",
    ],
  )
  val ethnicity: String? = null,

  @Schema(
    description = "Minimum height of the prisoner in centimetres",
    example = "170",
  )
  val minHeight: Int? = null,

  @Schema(
    description = "Maximum height of the prisoner in centimetres",
    example = "198",
  )
  val maxHeight: Int? = null,

  @Schema(
    description = "Minimum weight of the prisoner in kilograms",
    example = "80",
  )
  val minWeight: Int? = null,

  @Schema(
    description = "Maximum weight of the prisoner in kilograms",
    example = "90",
  )
  val maxWeight: Int? = null,

  @Schema(
    description = "Hair colour, searching on the description in the type HAIR in the PROFILE_CODES table. Allowable values extracted 07/02/2023.",
    example = "Brunette",
    allowableValues = ["Bald", "Balding", "Black", "Blonde", "Brown", "Brunette", "Dark", "Dyed", "Ginger", "Grey", "Light", "Mouse", "Multi-coloured", "Red", "White"],
  )
  val hairColour: String? = null,
  @Schema(
    description = "Right eye colour, searching on the description in the type R_EYE_C in the PROFILE_CODES table. Allowable values extracted 07/02/2023.",
    example = "Green",
    allowableValues = ["Blue", "Brown", "Clouded", "Green", "Grey", "Hazel", "Missing", "Pink", "White"],
  )
  val rightEyeColour: String? = null,
  @Schema(
    description = "Left eye colour, searching on the description in the type L_EYE_C in the PROFILE_CODES table. Allowable values extracted 07/02/2023.",
    example = "Hazel",
    allowableValues = ["Blue", "Brown", "Clouded", "Green", "Grey", "Hazel", "Missing", "Pink", "White"],
  )
  val leftEyeColour: String? = null,
  @Schema(
    description = "Facial hair, searching on the description in the type FACIAL_HAIR in the PROFILE_CODES table. Allowable values extracted 07/02/2023.",
    example = "Goatee Beard",
    allowableValues = ["Full Beard", "Clean Shaven", "Goatee Beard", "Moustache Only", "Not Applicable (Female Offender)", "No Facial Hair", "Sideburns"],
  )
  val facialHair: String? = null,
  @Schema(
    description = "Shape of face, searching on the description in the type FACE in the PROFILE_CODES table. Allowable values extracted 07/02/2023.",
    example = "Bullet",
    allowableValues = ["Angular", "Bullet", "Oval", "Round", "Square", "Triangular"],
  )
  val shapeOfFace: String? = null,
  @Schema(
    description = "Physical build, searching on the description in the type BUILD in the PROFILE_CODES table. Allowable values extracted 07/02/2023.",
    example = "Medium",
    allowableValues = ["Fat", "Frail", "Heavy", "Medium", "Muscular", "Obese", "Proportional", "Slight", "Small", "Stocky", "Stooped", "Thin"],
  )
  val build: String? = null,

  @Schema(
    description = "Minimum UK shoe size of the prisoner",
    example = "5",
  )
  val minShoeSize: Int? = null,

  @Schema(
    description = "Maximum UK shoe size of the prisoner",
    example = "10",
  )
  val maxShoeSize: Int? = null,

  @Schema(
    description = "List of body parts that have tattoos",
  )
  val tattoos: List<BodyPart>? = null,

  @Schema(
    description = "List of body parts that have marks",
  )
  val marks: List<BodyPart>? = null,

  @Schema(
    description = "List of body parts that have scars",
  )
  val scars: List<BodyPart>? = null,

  @Schema(
    description = "List of body parts that have a different mark",
  )
  val otherMarks: List<BodyPart>? = null,

  @Schema(
    description = """
        Whether all terms are required to match. If set to true then only matches on all fields will return a result.
        If set to false then matches will return a higher score than non matches, but all will be returned.
        Prison and cell location will always be required to match.""",
    example = "false",
  )
  val lenient: Boolean = false,

  @Schema(
    description = "Pagination options. Will default to the first page if omitted.",
    required = false,
  )
  val pagination: PaginationRequest = PaginationRequest(0, 10),
)

data class BodyPart(
  @Schema(
    description = "Body part that has the physical mark, searching on the description in the type BODY_PART in the REFERENCE_CODES table. Allowable values extracted 08/02/2023.",
    example = "Arm",
    allowableValues = ["Ankle", "Arm", "Ear", "Elbow", "Face", "Finger", "Foot", "Hand", "Head", "Knee", "Leg", "Lip", "Neck", "Nose", "Shoulder", "Thigh", "Toe", "Torso"],
  )
  val bodyPart: String? = null,
  @Schema(
    description = "Comment on the physical mark.",
    example = "dragon",
  )
  val comment: String? = null,
)
