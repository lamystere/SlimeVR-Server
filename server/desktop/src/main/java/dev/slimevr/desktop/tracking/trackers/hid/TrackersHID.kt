package dev.slimevr.desktop.tracking.trackers.hid

import com.jme3.math.FastMath
import dev.slimevr.VRServer
import dev.slimevr.tracking.trackers.Device
import dev.slimevr.tracking.trackers.Tracker
import dev.slimevr.tracking.trackers.TrackerStatus
import dev.slimevr.tracking.trackers.udp.IMUType
import io.eiren.util.logging.LogManager
import io.github.axisangles.ktmath.Quaternion
import io.github.axisangles.ktmath.Quaternion.Companion.fromRotationVector
import io.github.axisangles.ktmath.Vector3
import org.hid4java.HidDevice
import org.hid4java.HidManager
import org.hid4java.HidServices
import org.hid4java.HidServicesListener
import org.hid4java.HidServicesSpecification
import org.hid4java.event.HidServicesEvent
import java.util.function.Consumer
import kotlin.experimental.and

/**
 * Receives trackers data by UDP using extended owoTrack protocol.
 */
class TrackersHID(name: String, private val trackersConsumer: Consumer<Tracker>) : Thread(name), HidServicesListener {
	private val devices: MutableList<HIDDevice> = mutableListOf()
	private val devicesBySerial: MutableMap<String, MutableList<Int>> = HashMap()
	private val devicesByHID: MutableMap<HidDevice, MutableList<Int>> = HashMap()
	private val hidServicesSpecification = HidServicesSpecification()
	private val hidServices: HidServices

	init {
		hidServicesSpecification.isAutoStart = false
		hidServices = HidManager.getHidServices(hidServicesSpecification)
		hidServices.addHidServicesListener(this)
		val dataReadThread = Thread(dataReadRunnable)
		dataReadThread.isDaemon = true
		dataReadThread.name = "hid4java data reader"
		dataReadThread.start()
	}

	private fun checkConfigureDevice(hidDevice: HidDevice) {
		if (hidDevice.vendorId == 0x2FE3 && hidDevice.productId == 0x5652) { // TODO: Use correct ids
			if (!hidDevice.isOpen) {
				check(hidDevice.open()) { "Unable to open device" }
			}
			// TODO: Configure the device here
			val serial = hidDevice.serialNumber ?: "Unknown HID Device"
			// val product = hidDevice.product
			// val manufacturer = hidDevice.manufacturer
			this.devicesBySerial[serial]?.let {
				this.devicesByHID[hidDevice] = it
				synchronized(this.devices) {
					for (id in it) {
						val device = this.devices[id]
						for (value in device.trackers.values) {
							if (value.status == TrackerStatus.DISCONNECTED) value.status = TrackerStatus.OK
						}
					}
				}
				LogManager.info("[TrackerServer] Linked HID device reattached: $serial")
				return
			}

			// TODO: Need to check that the hidDevice is the same or different?
			// TODO: Get firmware and manufacturer from the device?
			val list: MutableList<Int> = mutableListOf()
			this.devicesBySerial[serial] = list
			this.devicesByHID[hidDevice] = list
			LogManager.info("[TrackerServer] (Probably) Compatible HID device detected: $serial")
		}
	}

	private fun removeDevice(hidDevice: HidDevice) {
		this.devicesByHID[hidDevice]?.let {
			synchronized(this.devices) {
				for (id in it) {
					val device = this.devices[id]
					for (value in device.trackers.values) {
						if (value.status == TrackerStatus.OK) {
							value.status =
								TrackerStatus.DISCONNECTED
						}
					}
				}
			}
			this.devicesByHID.remove(hidDevice)
			LogManager.info("[TrackerServer] Linked HID device removed: ${hidDevice.serialNumber}")
		}
	}

	private fun setUpSensor(device: HIDDevice, trackerId: Int, sensorType: IMUType, sensorStatus: TrackerStatus) {
		// LogManager.info("[TrackerServer] Sensor $trackerId for ${device.name}, status $sensorStatus")
		var imuTracker = device.getTracker(trackerId)
		if (imuTracker == null) {
			imuTracker = Tracker(
				device,
				VRServer.getNextLocalTrackerId(),
				device.name + "/" + trackerId,
				"IMU Tracker #" + VRServer.currentLocalTrackerId,
				null,
				trackerNum = trackerId,
				hasRotation = true,
				hasAcceleration = true,
				userEditable = true,
				imuType = sensorType,
				allowFiltering = true,
				needsReset = true,
				needsMounting = true,
				usesTimeout = false
			)
			// usesTimeout false because HID trackers aren't "Disconnected" unless receiver is physically removed probably
			// TODO: Could tracker maybe use "Timed out" status without marking as disconnecting?
			device.trackers[trackerId] = imuTracker
			trackersConsumer.accept(imuTracker)
			imuTracker.status = sensorStatus
			LogManager
				.info(
					"[TrackerServer] Added sensor $trackerId for ${device.name}, type $sensorType"
				)
		}
	}

	private fun deviceIdLookup(hidDevice: HidDevice, deviceId: Int, deviceList: MutableList<Int>): HIDDevice {
		synchronized(this.devices) {
			deviceList.map { this.devices[it] }.find { it.hidId == deviceId }?.let { return it }
			val device = HIDDevice(deviceId)
			// server wants tracker to be unique, so use combination of hid serial and full id
			device.name = hidDevice.serialNumber ?: "Unknown HID Device"
			device.name += "-$deviceId"
			device.manufacturer = "HID Device" // TODO:
			this.devices.add(device)
			deviceList.add(this.devices.size - 1)
			VRServer.instance.deviceManager.addDevice(device) // actually add device to the server
			return device
		}
	}

	@get:Synchronized
	private val dataReadRunnable: Runnable
		get() = Runnable {
			try {
				sleep(100) // Delayed start
			} catch (e: InterruptedException) {
				currentThread().interrupt()
				return@Runnable
			}
			hidServices.start()
			while (true) {
				try {
					sleep(0) // Possible performance impact
				} catch (e: InterruptedException) {
					currentThread().interrupt()
					break
				}
				dataRead()
			}
		}

	private fun dataRead() {
		synchronized(devicesByHID) {
			for ((hidDevice, deviceList) in devicesByHID) {
				val dataReceived: Array<Byte> = try {
					hidDevice.read(80, 1) // Read up to 80 bytes, timeout 1ms
				} catch (e: NegativeArraySizeException) {
					continue // Skip devices with read error (Maybe disconnected)
				}
				if (dataReceived.isNotEmpty()) {
					// Process data
					// TODO: make this less bad
					if (dataReceived.size % 20 != 0) {
						LogManager.info("[TrackerServer] Malformed HID packet, ignoring")
					}
					val packetCount = dataReceived.size / 20
					var i = 0
					while (i < packetCount * 20) {
						// dataReceived[i] //for later
						val idCombination = dataReceived[i + 1].toInt()
						val rssi = -dataReceived[i + 2].toInt()
						val battery = dataReceived[i + 3].toInt()
						val battery_mV = dataReceived[i + 5].toInt() and 255 shl 8 or (dataReceived[i + 4].toInt() and 255)
						val q = floatArrayOf(0f, 0f, 0f, 0f)
						val a = floatArrayOf(0f, 0f, 0f)
						for (j in 0..3) { // quat received as fixed 14
							var buf =
								dataReceived[i + 6 + j * 2 + 1].toInt() and 255 shl 8 or (dataReceived[i + 6 + j * 2].toInt() and 255)
							buf -= 32768 // uint to int
							q[j] = buf / (1 shl 14).toFloat() // fixed 14 to float
						}
						for (j in 0..2) { // accel received as fixed 7, in m/s
							var buf =
								dataReceived[i + 14 + j * 2 + 1].toInt() and 255 shl 8 or (dataReceived[i + 14 + j * 2].toInt() and 255)
							buf -= 32768 // uint to int
							a[j] = buf / (1 shl 7).toFloat() // fixed 7 to float
						}
						val trackerId = idCombination and 0b1111
						val deviceId = (idCombination shr 4) and 0b1111
						val device = deviceIdLookup(hidDevice, deviceId, deviceList)
						// server wants tracker to be unique, so use combination of hid serial and full id
						setUpSensor(device, trackerId, IMUType.UNKNOWN, TrackerStatus.OK)
						val tracker = device.getTracker(trackerId)!!

						tracker.signalStrength = rssi
						// tracker.batteryVoltage = if (battery and 128 == 128) 5f else 0f // Charge status
						tracker.batteryVoltage = battery_mV.toFloat() * 0.001f
						tracker.batteryLevel = (battery and 127).toFloat()
						var rot = Quaternion(q[0], q[1], q[2], q[3])
						rot = AXES_OFFSET.times(rot)
						tracker.setRotation(rot)
						// TODO: I think the acceleration is wrong???
						var acceleration = Vector3(a[0], a[1], a[2])
						tracker.setAcceleration(acceleration)
						tracker.dataTick()
						i += 20
					}
					// LogManager.info("[TrackerServer] HID received $packetCount tracker packets")
				}
			}
		}
	}

	override fun run() { // Doesn't seem to run
	}

	fun getDevices(): List<Device> {
		return devices
	}

	override fun hidDeviceAttached(event: HidServicesEvent) {
		checkConfigureDevice(event.hidDevice)
	}

	override fun hidDeviceDetached(event: HidServicesEvent) {
		removeDevice(event.hidDevice)
	}

	override fun hidFailure(event: HidServicesEvent) {
		// TODO:
	}

	companion object {
		/**
		 * Change between IMU axes and OpenGL/SteamVR axes
		 */
		private val AXES_OFFSET = fromRotationVector(-FastMath.HALF_PI, 0f, 0f)
		private const val resetSourceName = "TrackerServer"
	}
}
