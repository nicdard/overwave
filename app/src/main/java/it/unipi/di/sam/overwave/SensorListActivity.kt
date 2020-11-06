package it.unipi.di.sam.overwave

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView

class SensorListActivity : BaseMenuActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensor_list)
        val smm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lv = findViewById<View>(R.id.sensorList) as ListView
        val sensor = smm.getSensorList(Sensor.TYPE_ALL)
        lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, sensor)
    }
}