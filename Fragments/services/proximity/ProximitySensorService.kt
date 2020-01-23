package com.nn.my2ncommunicator.main.services.proximity

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import com.google.android.gms.tasks.RuntimeExecutionException
import com.nn.my2ncommunicator.App
import com.nn.my2ncommunicator.main.linphone.BaseCallStateListener
import com.nn.my2ncommunicator.main.linphone.CallType
import com.nn.my2ncommunicator.main.services.audio.AudioOutputType
import com.nn.my2ncommunicator.main.services.audio.RxAudioOutputService
import io.reactivex.subjects.PublishSubject
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.linphone.core.LinphoneCall
import org.linphone.core.LinphoneCallLog
import org.linphone.core.Reason
import quanti.com.kotlinlog.Log
import java.util.concurrent.TimeUnit

/**
 * Transition table of audio output state changes
 *           +-------------------------------------------+
 *           |               Current state               |
 * +---------+------------+------------+-----------------+
 * | sensor  | HANDS_FREE |  HANDS_ON  | Manual HANDS_ON |
 * +---------+------------+------------+-----------------+
 * | isFar   | HANDS_FREE | HANDS_FREE | Manual HANDS_ON |
 * | isClose | HANDS_ON   | HANDS_ON   | Manual HANDS_ON |
 * +---------+------------+------------+-----------------+
 */

class ProximitySensorService : BaseCallStateListener(), SensorEventListener, KoinComponent {

    companion object {
        const val WAKE_LOCK_TAG = "com.nn.my2ncommunicator:my2nWakeLock"
    }

    private val powerManager: PowerManager by inject()

    private val sensorManager: SensorManager by inject()

    private val audioOutputService: RxAudioOutputService by inject()

    private val wakeLock: PowerManager.WakeLock by lazy {
        powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK.or(PowerManager.ACQUIRE_CAUSES_WAKEUP), WAKE_LOCK_TAG)
    }
    private val proximitySensor: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    }

    private var isFar: Boolean = true
    private var threshold: Float = 0.5f
    private var isManual: Boolean = false
    private var shouldReturnWakeLock = false

    private val source = PublishSubject.create<AudioOutputType>()

    init {
        register()
    }

    @Throws(RuntimeExecutionException::class)
    private fun register() {
        if (proximitySensor == null) {
            throw NoSensorException("Proximity sensor not found")
        } else {
            threshold = proximitySensor!!.maximumRange / 2
            source.buffer(
                    source.debounce(500, TimeUnit.MILLISECONDS)
                            .doOnNext({ type ->
                                audioOutputService.requestOutput(type)
                            })
            )
                    .takeLast(1)
                    .subscribe()
            Log.d("Proximity sensor registered")
        }
    }

    fun unregister() {
        source.onComplete()
        Log.d("Proximity sensor unregistered")
    }

    private fun enable() {
        // Release wakelock if active in ScreenService
        if (App.getInstance().screenService.isHeld) {
            App.getInstance().screenService.releaseWakeLock()
            // remember to put it back
            shouldReturnWakeLock = true
        }
        val proximitySensor = this.proximitySensor ?: return
        isFar = powerManager.isScreenOn
        isManual = false
        threshold = proximitySensor.maximumRange / 2
        sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_FASTEST)
        wakeLock.acquire()
    }

    private fun disable() {
        proximitySensor ?: return
        if (wakeLock.isHeld) {
            wakeLock.release(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK.or(PowerManager.ACQUIRE_CAUSES_WAKEUP))
            sensorManager.unregisterListener(this)
        }
        if (shouldReturnWakeLock)
            App.getInstance().screenService.acquireWakeLock()
    }

    override fun onCallConnected(callType: CallType) {
        if (!wakeLock.isHeld) {
            enable()
        }
        if (callType != CallType.CALL_INCOMING) {
            processState()
        }
    }

    override fun onOutgoingCallInitiated(callType: CallType?) {
        if (!wakeLock.isHeld) {
            enable()
        }
        processState()
    }

    override fun onCallFinished(callType: CallType, reason: Reason, linphoneCallLog: LinphoneCallLog, state: LinphoneCall.State) {
        disable()
    }

    // From SensorEventListener
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        isFar = event.values.get(0) > threshold
        Log.i("Proximity change far=$isFar")
        processState()
    }

    private fun processState() {
        val currentOutput = RxAudioOutputService.audioOutputSelectedObservable.value
        Log.d("Proximity sensor: Processing proximity state - currentOutput: $currentOutput, isManual: $isManual, isFar: $isFar")
        if (currentOutput == AudioOutputType.BLUETOOTH || RxAudioOutputService.headsetConnectedObservable.value == true) {
            return
        }
        if (!isManual) {
            if (isFar) {
                Log.d("Proximity sensor: Processing state - new output: HANDS_FREE")
                source.onNext(AudioOutputType.HANDS_FREE)
            } else {
                Log.d("Proximity sensor: Processing state - new output: HANDS_ON")
                source.onNext(AudioOutputType.HANDS_ON)
            }
        }
    }

    fun changeState(newState: AudioOutputType) {
        isManual = newState != AudioOutputType.HANDS_FREE
    }
}