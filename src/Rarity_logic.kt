import java.io.File
import kotlin.random.Random
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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
    println("=== 가챠 데이터 로딩 중 ===")
    val itemPool = loadItemPoolFromFile("assets/items.json")

    if (itemPool.isEmpty()) {
        println("아이템 풀이 비어있어 실행을 중단합니다.")
        return
    }

    val gachaManager = GachaManager(itemPool)

    println("\n=== 10연차 뽑기 실행 ===")
    val results = gachaManager.drawTen()
    results.forEachIndexed { index, item ->
        println("${index + 1}번째: [${item.rarity}] ${item.name}")
    }
}