package gg.growly.co2

import io.github.s5uishida.iot.device.mhz19b.driver.MHZ19BDriver
import org.litote.kmongo.KMongo
import org.litote.kmongo.getCollection
import java.io.IOException
import java.time.Instant
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * @author GrowlyX
 * @since 5/16/2024
 */
fun main()
{

    val client = KMongo.createClient()
    val database = client.getDatabase("APStatistics")
    val collection = database.getCollection<CO2Record>()

    val co2Sensor = MHZ19BDriver.getInstance("/dev/ttyAMA0")
    co2Sensor.open()
    co2Sensor.setDetectionRange5000()
    co2Sensor.setAutoCalibration(false)

    val scheduler = Executors.newSingleThreadScheduledExecutor()

    val currentSecond = Calendar.getInstance().get(Calendar.SECOND)
    val secondsLeftUntilNextMinute = 60 - currentSecond

    val currentMinute = Calendar.getInstance().get(Calendar.MINUTE)
    val minutesLeftUntilNextHour = 60 - currentMinute
    val minutesLeftUntilNext10M = minutesLeftUntilNextHour % 10

    println("${minutesLeftUntilNext10M - 1}m${secondsLeftUntilNextMinute}s left until next 10M mark")
    val totalSecondsToWait = (minutesLeftUntilNext10M - 1) * 60L + secondsLeftUntilNextMinute
    val totalMilliseconds = totalSecondsToWait * 1000L

    scheduler.scheduleAtFixedRate(
        {
            val value = co2Sensor.gasConcentration
            MHZ19BDriver.LOG.info("CO2: $value")

            collection.insertOne(
                CO2Record(
                    timestamp = Instant.now(),
                    concentration = value,
                )
            )
        },
        totalMilliseconds,
        TimeUnit.MINUTES.toMillis(10L),
        TimeUnit.MILLISECONDS
    )

    Runtime.getRuntime().addShutdownHook(Thread {
        co2Sensor.close()
        println("Closed the CO2 sensor instance")
    })

    while (true)
    {
        Thread.sleep(500L)
    }
}
