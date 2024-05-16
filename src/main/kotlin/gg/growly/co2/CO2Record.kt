package gg.growly.co2

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import java.time.Instant

/**
 * @author GrowlyX
 * @since 5/16/2024
 */
@Serializable
data class CO2Record(
    val timestamp: @Contextual Instant,
    val concentration: Int,
    @SerialName("_id")
    val id: @Contextual ObjectId = ObjectId()
)
