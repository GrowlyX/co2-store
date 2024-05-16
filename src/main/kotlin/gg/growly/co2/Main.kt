package gg.growly.co2

import io.github.s5uishida.iot.device.mhz19b.driver.MHZ19BDriver
import java.io.IOException

/**
 * @author GrowlyX
 * @since 5/16/2024
 */
fun main()
{
    var mhz19b: MHZ19BDriver? = null
    try
    {
        mhz19b = MHZ19BDriver.getInstance("/dev/ttyAMA0")
        mhz19b.open()
        mhz19b.setDetectionRange5000()
        mhz19b.setAutoCalibration(false)

        while (true)
        {
            val value = mhz19b.gasConcentration
            MHZ19BDriver.LOG.info("co2: $value")

            Thread.sleep(10000)
        }
    } catch (exception: InterruptedException)
    {
        MHZ19BDriver.LOG.warn("caught - {}", exception.toString())
    } catch (exception: IOException)
    {
        MHZ19BDriver.LOG.warn("caught - {}", exception.toString())
    } finally
    {
        mhz19b?.close()
    }
}
