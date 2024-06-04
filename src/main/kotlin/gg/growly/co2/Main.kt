package gg.growly.co2

import io.github.s5uishida.iot.device.mhz19b.driver.MHZ19BDriver
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.csv.Csv
import kotlinx.serialization.descriptors.ClassSerialDescriptorBuilder
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.serializersModule
import kotlinx.serialization.modules.SerializersModule
import org.bson.types.ObjectId
import org.knowm.xchart.*
import org.litote.kmongo.*
import org.litote.kmongo.serialization.InstantSerializer
import org.litote.kmongo.serialization.TemporalExtendedJsonSerializer
import java.io.File
import java.text.SimpleDateFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * @author GrowlyX
 * @since 5/16/2024
 */
fun main()
{
    Science.queryAll()
}

object Collector
{

    fun sensor()
    {
        val dataBackupDirectory = File("data-backup")
        if (!dataBackupDirectory.exists())
        {
            dataBackupDirectory.mkdirs()
        }

        val currentInstanceDB = File(dataBackupDirectory, "${Date()}")
        currentInstanceDB.mkdirs()

        val client = KMongo.createClient()
        val database = client.getDatabase("APStatistics")
        val collection = database.getCollection<CO2Record>()

        val co2Sensor = MHZ19BDriver.getInstance("/dev/ttyAMA0")
        co2Sensor.open()
        co2Sensor.setDetectionRange5000()
        co2Sensor.setAutoCalibration(true)

        val scheduler = Executors.newSingleThreadScheduledExecutor()

        val currentSecond = Calendar.getInstance().get(Calendar.SECOND)
        val secondsLeftUntilNextMinute = 60 - currentSecond

        val currentMinute = Calendar.getInstance().get(Calendar.MINUTE)
        val minutesLeftUntilNextHour = 60 - currentMinute
        val minutesLeftUntilNext10M = minutesLeftUntilNextHour % 10

        MHZ19BDriver.LOG.info("${minutesLeftUntilNext10M - 1}m${secondsLeftUntilNextMinute}s left until next 10M mark")
        val totalSecondsToWait = (minutesLeftUntilNext10M - 1) * 60L + secondsLeftUntilNextMinute
        val totalMilliseconds = totalSecondsToWait * 1000L

        scheduler.scheduleAtFixedRate(
            {
                val value = co2Sensor.gasConcentration
                MHZ19BDriver.LOG.info("Current CO2 reading: $value")

                val record = CO2Record(
                    timestamp = Instant.now(),
                    concentration = value,
                )

                runCatching {
                    collection.insertOne(record)
                }.onFailure {
                    MHZ19BDriver.LOG.error("Failed to persist to MongoDB", it)
                }

                runCatching {
                    with(File(currentInstanceDB, "${record.timestamp}.json")) {
                        createNewFile()
                        writeText(
                            Json.encodeToString(
                                mapOf(
                                    "concentration" to "$value",
                                    "timestamp" to record.timestamp.toString()
                                )
                            )
                        )
                    }
                }.onFailure {
                    MHZ19BDriver.LOG.error("Failed to persist to FlatFile", it)
                }
            },
            totalMilliseconds,
            TimeUnit.MINUTES.toMillis(10L),
            TimeUnit.MILLISECONDS
        )

        Runtime.getRuntime().addShutdownHook(Thread {
            co2Sensor.close()
            MHZ19BDriver.LOG.info("Closed the CO2 sensor instance")
        })

        while (true)
        {
            Thread.sleep(500L)
        }
    }
}

object Science
{
    val instantModule = SerializersModule {
        contextual(Instant::class, object : KSerializer<Instant>
        {
            override val descriptor: SerialDescriptor =
                PrimitiveSerialDescriptor("InstantSerializer", PrimitiveKind.STRING)

            override fun deserialize(decoder: Decoder): Instant =
                Instant.parse(decoder.decodeString())

            override fun serialize(encoder: Encoder, value: Instant)
            {
                encoder.encodeString(value.toString())
            }
        })

        contextual(ObjectId::class, object : KSerializer<ObjectId>
        {
            override val descriptor: SerialDescriptor =
                PrimitiveSerialDescriptor("ObjectIdSerializer", PrimitiveKind.STRING)

            override fun deserialize(decoder: Decoder): ObjectId =
                ObjectId(decoder.decodeString())

            override fun serialize(encoder: Encoder, value: ObjectId)
            {
                encoder.encodeString(value.toHexString())
            }
        })
    }

    val csv = Csv {
        hasHeaderRecord = true
        serializersModule = instantModule
    }
    val json = Json {
        serializersModule = instantModule
    }

    val composite = mutableListOf<List<Int>>()

    fun queryAll()
    {
        val directory = "C:\\Users\\Admin\\Desktop\\School\\Stats"
        File(directory).listFiles()?.filterNotNull()
            ?.forEach { level ->
                val descriptors = level.listFiles()
                    ?.filterNotNull()
                    ?.map { record ->
                        json.decodeFromString<CO2Record>(record.readText())
                    }
                    ?.filter { resp ->
                        resp.concentration != 0 && resp.concentration > 600
                    }
                    ?: listOf()

                listOf("L1-Closest", "L2-Closer", "L3-Far", "L4-Further", "L5-Furthest")
                    .forEach {
                        val (levelId, descriptor) = it.split("-")
                        val data = descriptors.shuffled().take(48)
                        composite += data.map(CO2Record::concentration)

                        query(
                            recordsInRange = data,
                            name = levelId,
                            formattedName = descriptor
                        )
                    }
            }

        val dateFormat = SimpleDateFormat("HH:mma")
        val calendar = Calendar.getInstance()

        // Set the calendar to 11 PM last night
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.DAY_OF_MONTH, -1)

        val endCalendar = Calendar.getInstance()
        // Set the end time to 7 AM today
        endCalendar.set(Calendar.HOUR_OF_DAY, 7)
        endCalendar.set(Calendar.MINUTE, 0)
        endCalendar.set(Calendar.SECOND, 0)
        endCalendar.set(Calendar.MILLISECOND, 0)

        val timestamps = mutableListOf<Date>()

        while (calendar.before(endCalendar) || calendar == endCalendar)
        {
            timestamps.add(calendar.time)
            calendar.add(Calendar.MINUTE, 10)
        }

        val compositeList = mutableListOf<ComparedConcentrations>()
        (0..47).forEach {
            compositeList += ComparedConcentrations(
                dateFormat.format(timestamps[it]),
                composite[0][it],
                composite[1][it],
                composite[2][it],
                composite[3][it],
                composite[4][it]
            )
        }

        File("./recordings/concentrations.data.csv").apply {
            if (exists())
                delete()

            createNewFile()
            writeText(csv.encodeToString(compositeList))
        }
    }

    @Serializable
    data class ComparedConcentrations(
        val time: String,
        val closest: Int,
        val closer: Int,
        val far: Int,
        val further: Int,
        val furthest: Int
    )

    @OptIn(ExperimentalSerializationApi::class)
    fun query(recordsInRange: List<CO2Record>, name: String, formattedName: String)
    {
        val dateFormat = SimpleDateFormat("MM/dd/yyyy HH:mm")
        val calendar = Calendar.getInstance()

        // Set the calendar to 11 PM last night
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.DAY_OF_MONTH, -1)

        val endCalendar = Calendar.getInstance()
        // Set the end time to 7 AM today
        endCalendar.set(Calendar.HOUR_OF_DAY, 7)
        endCalendar.set(Calendar.MINUTE, 0)
        endCalendar.set(Calendar.SECOND, 0)
        endCalendar.set(Calendar.MILLISECOND, 0)

        val timestamps = mutableListOf<Date>()

        while (calendar.before(endCalendar) || calendar == endCalendar)
        {
            timestamps.add(calendar.time)
            calendar.add(Calendar.MINUTE, 10)
        }

        val concentrations = recordsInRange.map(CO2Record::concentration)

        val formatter = DateTimeFormatter
            .ofPattern("h:mma")
            .withZone(ZoneId.of("US/Eastern"))

        val chart = XYChartBuilder()
            .width(1500)
            .height(600)
            .title("CO2 Concentrations Over Time ($formattedName)")
            .xAxisTitle("Timestamp")
            .yAxisTitle("Concentration")
            .build()

        chart.styler.setDatePattern("h:mma")
        chart.styler.setxAxisTickLabelsFormattingFunction {
            formatter.format(Instant.ofEpochMilli(it.toLong()))
        }
        chart.addSeries("CO2 Concentrations", timestamps.take(48), concentrations)
        BitmapEncoder.saveBitmap(chart, "./recordings/concentrations-$name.png", BitmapEncoder.BitmapFormat.PNG)
    }

}
