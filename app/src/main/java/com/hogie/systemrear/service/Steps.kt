package com.hogie.systemrear.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.hogie.systemrear.data.Store

/** Feeds the phone's step counter into the "steps" quest. Reference-counted. */
object Steps : SensorEventListener {
    private var users = 0

    fun start(context: Context) {
        if (context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) return
        synchronized(this) {
            users++
            if (users > 1) return
            val sm = context.applicationContext.getSystemService(SensorManager::class.java)
            val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop(context: Context) {
        synchronized(this) {
            if (users == 0) return
            users--
            if (users == 0) context.applicationContext.getSystemService(SensorManager::class.java).unregisterListener(this)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        Store.onStepCounter(event.values[0].toLong())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
