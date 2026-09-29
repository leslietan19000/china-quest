package org.chinaquest.app

/** A small, finite illustration of one everyday use of a character. No scoring is attached. */
data class SceneDefinition(
    val id: String,
    val characterId: String,
    val character: String,
    val title: String,
    val family: SceneFamily,
)

enum class SceneFamily { EATING, DOOR, DRINKING }

/** Step is safe to save per child and restore after process death. */
data class SceneState(val sceneId: String, val step: Int)

data class SceneStage(
    val title: String,
    val prompt: String,
    val glyph: String,
    val actionLabel: String?,
    val hotspotDescription: String?,
    val complete: Boolean,
    val completionText: String?,
)

object SceneCatalog {
    private val scenes = listOf(
        SceneDefinition("mouth-eats", "hanzi-53E3", "口", "口 · 小嘴吃苹果", SceneFamily.EATING),
        SceneDefinition("eat-apple", "hanzi-5403", "吃", "吃 · 吃苹果", SceneFamily.EATING),
        SceneDefinition("open-door", "hanzi-5F00", "开", "开 · 打开门", SceneFamily.DOOR),
        SceneDefinition("close-door", "hanzi-5173", "关", "关 · 关上门", SceneFamily.DOOR),
        SceneDefinition("water-cup", "hanzi-6C34", "水", "水 · 一杯水", SceneFamily.DRINKING),
        SceneDefinition("drink-water", "hanzi-559D", "喝", "喝 · 喝水", SceneFamily.DRINKING),
    )
    private val byId = scenes.associateBy { it.id }
    private val byCharacterId = scenes.associateBy { it.characterId }

    fun forCharacter(characterId: String): SceneDefinition? = byCharacterId[characterId]
    fun definition(sceneId: String): SceneDefinition? = byId[sceneId]
    fun initial(sceneId: String): SceneState = SceneState(requireNotNull(byId[sceneId]) { "Unknown scene: $sceneId" }.id, 0)
    fun maxStep(sceneId: String): Int = when (requireNotNull(byId[sceneId]).family) {
        SceneFamily.EATING -> 3
        SceneFamily.DOOR, SceneFamily.DRINKING -> 2
    }

    fun normalized(state: SceneState): SceneState = state.copy(step = state.step.coerceIn(0, maxStep(state.sceneId)))
    fun advance(state: SceneState): SceneState {
        require(state.step in 0..maxStep(state.sceneId)) { "Invalid scene step: ${state.step}" }
        return state.copy(step = (state.step + 1).coerceAtMost(maxStep(state.sceneId)))
    }
    fun replay(state: SceneState): SceneState = initial(state.sceneId)

    fun stage(state: SceneState): SceneStage {
        val scene = requireNotNull(byId[state.sceneId])
        require(state.step in 0..maxStep(scene.id)) { "Invalid scene step: ${state.step}" }
        val step = state.step
        val done = step == maxStep(scene.id)
        val prompt = when (scene.family) {
            SceneFamily.EATING -> when (step) {
                0 -> "点苹果，把它拿起来。"
                1 -> "把苹果送到小嘴边。"
                2 -> "点小嘴，咬一口。"
                else -> "小嘴吃了一口苹果。"
            }
            SceneFamily.DOOR -> if (scene.id == "open-door") when (step) {
                0 -> "点门把手，打开门。"
                1 -> "再推一下，看看门后是谁。"
                else -> "门开了！熊猫在向你招手。"
            } else when (step) {
                0 -> "熊猫要休息了。点门把手。"
                1 -> "轻轻再推一下，关上门。"
                else -> "门关上了。熊猫可以休息。"
            }
            SceneFamily.DRINKING -> when (step) {
                0 -> "点水杯，拿起一杯水。"
                1 -> "把水杯送到嘴边。"
                else -> "喝了一口水。"
            }
        }
        val action = when (scene.family) {
            SceneFamily.EATING -> listOf("拿起苹果", "送到嘴边", "咬一口").getOrNull(step)
            SceneFamily.DOOR -> if (scene.id == "open-door") listOf("打开门", "再推开门").getOrNull(step)
                else listOf("合上门", "关上门").getOrNull(step)
            SceneFamily.DRINKING -> listOf("拿起水杯", "喝一口水").getOrNull(step)
        }
        return SceneStage(
            title = scene.title,
            prompt = prompt,
            glyph = scene.character,
            actionLabel = action,
            hotspotDescription = action?.let { "插画：$it" },
            complete = done,
            completionText = if (done) "做到了。可以停在这里，或再玩一次。" else null,
        )
    }
}
