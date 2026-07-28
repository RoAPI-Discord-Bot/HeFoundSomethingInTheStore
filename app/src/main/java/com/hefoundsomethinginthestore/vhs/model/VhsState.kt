package com.hefoundsomethinginthestore.vhs.model

enum class VhsTint(val displayName: String, val description: String) {
    BLANK("Blank / Clean", "No Color Tint"),
    STANDARD("Standard VHS", "Classic 90s Home Video"),
    NIGHT_VISION("Night Vision", "Infrared CRT Phosphor Green"),
    GLITCH_MAX("Tape Glitch", "High Distortion Noise"),
    WARM_SEPIA("80s Sepia", "Warm Golden Sunlit Vintage"),
    MONO_BW("B&W Monochrome", "1980 Noir Video Tape"),
    CYBER_BLUE("Cyber Blue", "Low-Light Night Cam"),
    LIMINAL_YELLOW("Liminal Space", "Fluorescent Yellow Tint")
}

enum class VhsSpeed(val label: String) {
    SP("SP"),
    SLP("SLP"),
    EP("EP")
}

enum class VhsIntroType(val label: String, val description: String) {
    NONE("None", "Direct start"),
    BLUE_SCREEN("Blue VHS Screen", "VCR Blue screen with PLAY indicator"),
    COLOR_BARS("SMPTE Color Bars", "1990s TV Test Card Signal"),
    STATIC_NOISE("Static Noise", "VCR static white noise sequence")
}

enum class VhsOutroType(val label: String, val description: String) {
    NONE("None", "Direct end"),
    TAPE_STOP("Tape Spin Stop", "VHS head spin down to black"),
    BLUE_SCREEN("Blue VHS Screen", "VCR Blue screen STOP message"),
    STATIC_NOISE("Static Fade", "Static noise tape end")
}

enum class VhsTransitionEffect(val label: String, val description: String) {
    NONE("None", "Standard cut"),
    GLITCH_SPLICE("Glitch Tear", "Analog tracking distortion tear"),
    SCANLINE_FADE("Scanline Dissolve", "CRT scanline dissolve effect")
}

data class VhsOsdConfig(
    val customTitle: String = "RARE-VHS 90S",
    val customDateText: String = "OCT. 14 1995",
    val showTimestamp: Boolean = true,
    val showPlayIndicator: Boolean = true,
    val showBattery: Boolean = true,
    val speed: VhsSpeed = VhsSpeed.SP,
    val noiseIntensity: Float = 0.35f,
    val trackingDistortion: Float = 0.0f
)

data class VideoStudioConfig(
    val introType: VhsIntroType = VhsIntroType.BLUE_SCREEN,
    val outroType: VhsOutroType = VhsOutroType.TAPE_STOP,
    val transitionEffect: VhsTransitionEffect = VhsTransitionEffect.GLITCH_SPLICE,
    val introDurationSeconds: Float = 2.0f,
    val outroDurationSeconds: Float = 2.0f
)
