import java.io.File
import kotlin.random.Random
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class Egg(
    val id: String,
    val item: Item,
    val createdAtMillis: Long,
    val hatchAtMillis: Long
)

class StepEggManager(
    private val gachaManager: GachaManager,
    private val stepsPerEgg: Long = 1_000L,
    private val hatchHours: Map<Rarity, Long> = mapOf(
        Rarity.SSR_PILGRIM to 8L,
        Rarity.SSR_NORMAL to 4L,
        Rarity.SR to 2L,
        Rarity.N to 1L
    )
) {
    private var lastSteps = 0L
    private var stepRemainder = 0L

    // 아직 부화하지 않은 알
    private val eggs = mutableListOf<Egg>()

    fun updateSteps(totalSteps: Long, nowMillis: Long = System.currentTimeMillis()): List<Egg> {
        require(totalSteps >= 0) { "걸음 수는 음수일 수 없습니다." }

        // 걸음 수가 초기화되거나 줄어든 경우, 중복 지급을 막기 위해 기준만 갱신합니다.
        if (totalSteps < lastSteps) {
            lastSteps = totalSteps
            return emptyList()
        }

        val gainedSteps = totalSteps - lastSteps
        lastSteps = totalSteps
        stepRemainder += gainedSteps

        val newEggs = mutableListOf<Egg>()

        while (stepRemainder >= stepsPerEgg) {
            stepRemainder -= stepsPerEgg

            // 기존 뽑기 로직과 희귀도 확률·천장을 그대로 사용합니다.
            val item = gachaManager.drawOne()
            val hours = hatchHours[item.rarity]
                ?: error("${item.rarity}의 부화 시간이 설정되지 않았습니다.")

            val egg = Egg(
                id = UUID.randomUUID().toString(),
                item = item,
                createdAtMillis = nowMillis,
                hatchAtMillis = nowMillis + hours * 60 * 60 * 1_000L
            )

            eggs += egg
            newEggs += egg
        }

        return newEggs
    }

    fun hatchReady(nowMillis: Long = System.currentTimeMillis()): List<Item> {
        val readyEggs = eggs.filter { it.hatchAtMillis <= nowMillis }
        eggs.removeAll(readyEggs.toSet())
        return readyEggs.map { it.item }
    }

    fun getPendingEggs(): List<Egg> = eggs.toList()
}
// 1. 등급 정의 (val baseRate: Double 선언이 들어가야 baseRate와 SSR_PILGRIM/NORMAL 에러가 안 난다)
enum class Rarity(val baseRate: Double) {
    SSR_PILGRIM(0.3), // 필그림급 5성
    SSR_NORMAL(0.6),  // 일반 5성
    SR(5.1),          // 4성
    N(94.0)           // 3성 (꽝/잡동사니)
}

// 2. 아이템 데이터 모델
data class Item(
    val id: String,
    val name: String,
    val rarity: Rarity
)

// 3. 가챠 로직 매니저
class GachaManager(
    private val itemPool: Map<Rarity, List<Item>>
) {
    var ssrPityCount = 0
        private set
    var srPityCount = 0
        private set

    private val defaultDudItem = Item("dud_default", "하급 경험치 포션", Rarity.N)

    fun drawOne(): Item {
        ssrPityCount++
        srPityCount++

        val currentSsrRate = calculateSsrRate()
        val randomValue = Random.nextDouble(0.0, 100.0)
        val selectedRarity: Rarity

        if (randomValue < currentSsrRate) {
            val subRandom = Random.nextDouble(0.0, 100.0)
            selectedRarity = if (subRandom < 30.0) {
                Rarity.SSR_PILGRIM
            } else {
                Rarity.SSR_NORMAL
            }
            ssrPityCount = 0
            srPityCount = 0
        } else if (srPityCount >= 10) {
            selectedRarity = Rarity.SR
            srPityCount = 0
        } else {
            val srThreshold = currentSsrRate + Rarity.SR.baseRate
            selectedRarity = if (randomValue < srThreshold) Rarity.SR else Rarity.N
        }

        val candidates = itemPool[selectedRarity]
        return if (!candidates.isNullOrEmpty()) {
            candidates.random()
        } else {
            defaultDudItem
        }
    }

    fun drawTen(): List<Item> {
        return List(10) { drawOne() }
    }

    private fun calculateSsrRate(): Double {
        val baseTotalSsrRate = Rarity.SSR_PILGRIM.baseRate + Rarity.SSR_NORMAL.baseRate
        return when {
            ssrPityCount >= 90 -> 100.0
            ssrPityCount >= 74 -> baseTotalSsrRate + ((ssrPityCount - 73) * 6.0)
            else -> baseTotalSsrRate
        }
    }
}

// 4. JSON 파일 로더
fun loadItemPoolFromFile(filePath: String = "assets/items.json"): Map<Rarity, List<Item>> {
    val file = File(filePath)
    if (!file.exists()) {
        println("❌ 오류: $filePath 파일을 찾을 수 없습니다.")
        return emptyMap()
    }

    val jsonString = file.readText()
    val itemType = object : TypeToken<List<Item>>() {}.type
    val itemList: List<Item> = Gson().fromJson(jsonString, itemType)

    return itemList.groupBy { it.rarity }
}

// 5. 실행 메인 함수
fun main() {
    val eggManager = StepEggManager(gachaManager)

// 실제 앱의 만보기/API에서 읽은 누적 걸음 수로 교체하세요.
val totalStepsFromExternalSource = 2_450L

val newEggs = eggManager.updateSteps(totalStepsFromExternalSource)
println("새로 얻은 알: ${newEggs.size}개")

val hatchedItems = eggManager.hatchReady()
hatchedItems.forEach { item ->
    println("부화 완료: [${item.rarity}] ${item.name}")
}
}
