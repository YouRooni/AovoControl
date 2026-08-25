package dev.rooni.aovo.ble

import dev.rooni.aovo.R

/** How much damage a wrong value in an engineering parameter can do. */
enum class ParamRisk {
    /** Ordinary setting; a wrong value is an annoyance, not a problem. */
    NONE,

    /** Affects readings, heat or the battery gauge. Worth understanding before changing. */
    CAUTION,

    /** Can leave the scooter undrivable or take the pack below its safe voltage. */
    DANGER,

    /** Nobody has worked out what this does. Changing it is a experiment, not a setting. */
    UNKNOWN,
}

data class EngineeringParam(
    val number: Int,
    val index: Int,
    val titleRes: Int,
    val descriptionRes: Int,
    val min: Int,
    val max: Int,
    val default: Int,
    val risk: ParamRisk,
    val firmwareName: String? = null,
)

object EngineeringParams {

    val ALL: List<EngineeringParam> = listOf(
        EngineeringParam(1, 0, R.string.p1_title, R.string.p1_desc, 1, 7, 7, ParamRisk.NONE, "SMGlux"),
        EngineeringParam(2, 1, R.string.p2_title, R.string.p2_desc, 0, 1, 0, ParamRisk.NONE, "startMode"),
        EngineeringParam(3, 2, R.string.p3_title, R.string.p3_desc, 0, 1, 0, ParamRisk.NONE, "cuise"),
        EngineeringParam(4, 3, R.string.p4_title, R.string.p4_desc, 0, 1, 0, ParamRisk.NONE, "unit"),
        EngineeringParam(5, 4, R.string.p5_title, R.string.p5_desc, 1, 99, 60, ParamRisk.NONE, "offtime"),
        EngineeringParam(6, 5, R.string.p6_title, R.string.p6_desc, 1, 99, 81, ParamRisk.NONE, "brakeFilter"),
        EngineeringParam(7, 6, R.string.p7_title, R.string.p7_desc, 1, 99, 99, ParamRisk.NONE, "accFilter"),
        EngineeringParam(8, 7, R.string.p8_title, R.string.p8_desc, 1, 99, 12, ParamRisk.NONE, "speedLit1"),
        EngineeringParam(9, 8, R.string.p9_title, R.string.p9_desc, 1, 99, 18, ParamRisk.NONE, "speedLit2"),
        EngineeringParam(10, 9, R.string.p10_title, R.string.p10_desc, 1, 99, 35, ParamRisk.NONE, "speedLit3"),
        EngineeringParam(11, 10, R.string.p11_title, R.string.p11_desc, 1, 99, 10, ParamRisk.NONE, "speedLit4"),
        EngineeringParam(12, 11, R.string.p12_title, R.string.p12_desc, 1, 99, 10, ParamRisk.NONE, "speedLit5"),
        EngineeringParam(13, 12, R.string.p13_title, R.string.p13_desc, 1, 99, 15, ParamRisk.CAUTION, "car.Motorpairs"),
        EngineeringParam(14, 13, R.string.p14_title, R.string.p14_desc, 1, 99, 10, ParamRisk.CAUTION, "car.wheelSize"),
        EngineeringParam(15, 14, R.string.p15_title, R.string.p15_desc, 0, 9, 0, ParamRisk.CAUTION, null),
        EngineeringParam(16, 15, R.string.p16_title, R.string.p16_desc, 1, 99, 1, ParamRisk.NONE, "bat.batvFilter"),
        EngineeringParam(17, 16, R.string.p17_title, R.string.p17_desc, 1, 99, 30, ParamRisk.DANGER, "car.lowVol"),
        EngineeringParam(18, 17, R.string.p18_title, R.string.p18_desc, 1, 99, 17, ParamRisk.CAUTION, null),
        EngineeringParam(19, 18, R.string.p19_title, R.string.p19_desc, 0, 1, 1, ParamRisk.NONE, null),
        EngineeringParam(20, 19, R.string.p20_title, R.string.p20_desc, 1, 99, 99, ParamRisk.NONE, null),
        EngineeringParam(21, 20, R.string.p21_title, R.string.p21_desc, 1, 99, 65, ParamRisk.NONE, null),
        EngineeringParam(22, 21, R.string.p22_title, R.string.p22_desc, 0, 1, 0, ParamRisk.NONE, "lock"),
        EngineeringParam(23, 22, R.string.p23_title, R.string.p23_desc, 1, 99, 10, ParamRisk.DANGER, null),
        EngineeringParam(24, 23, R.string.p24_title, R.string.p24_desc, 0, 1, 0, ParamRisk.UNKNOWN, null),
        EngineeringParam(25, 24, R.string.p25_title, R.string.p25_desc, 1, 99, 10, ParamRisk.UNKNOWN, null),
        EngineeringParam(26, 25, R.string.p26_title, R.string.p26_desc, 1, 99, 10, ParamRisk.UNKNOWN, null),
        EngineeringParam(27, 26, R.string.p27_title, R.string.p27_desc, 1, 99, 5, ParamRisk.UNKNOWN, null),
        EngineeringParam(28, 27, R.string.p28_title, R.string.p28_desc, 1, 99, 1, ParamRisk.UNKNOWN, null),
        EngineeringParam(29, 28, R.string.p29_title, R.string.p29_desc, 1, 99, 34, ParamRisk.CAUTION, "bat.batV1"),
        EngineeringParam(30, 29, R.string.p30_title, R.string.p30_desc, 1, 99, 35, ParamRisk.CAUTION, "bat.batV2"),
        EngineeringParam(31, 30, R.string.p31_title, R.string.p31_desc, 1, 99, 36, ParamRisk.CAUTION, "bat.batV3"),
        EngineeringParam(32, 31, R.string.p32_title, R.string.p32_desc, 1, 99, 37, ParamRisk.CAUTION, "bat.batV4"),
        EngineeringParam(33, 32, R.string.p33_title, R.string.p33_desc, 1, 99, 39, ParamRisk.CAUTION, "bat.batV5"),
    )

    fun byNumber(number: Int): EngineeringParam? = ALL.firstOrNull { it.number == number }
}
