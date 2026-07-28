package com.hefoundsomethinginthestore.vhs.model

enum class VhsTint(val displayName: String, val description: String) {
    STANDARD("Standard VHS", "Classic 90s Home Video"),
    NIGHT_VISION("Night Vision", "Infrared CRT Phosphor Green"),
    WARM_SEPIA("80s Sepia", "Warm Golden Sunlit Vintage"),
    MONO_BW("B&W Monochrome", "1980 Noir Video Tape"),
    CYBER_BLUE("Cyber Blue", "Low-Light Night Cam"),
    BACKROOMS("Liminal Yellow", "Level 0 Fluorescent Tint")
}

enum class VhsSpeed(val label: String) {
    SP("SP"),
    SLP("SLP"),
    EP("EP")
}

data class VhsOsdConfig(
    val customTitle: String = "HE FOUND SOMETHING",
    val customDateText: String = "OCT. 14 1995",
    val showTimestamp: Boolean = true,
    val showPlayIndicator: Boolean = true,
    val showBattery: Boolean = true,
    val speed: VhsSpeed = VhsSpeed.SP,
    val noiseIntensity: Float = 0.35f,
    val trackingDistortion: Float = 0.0f
)
