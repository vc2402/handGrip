package com.vc24.handgrip

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Button
import android.widget.EditText
import com.vc24.handgrip.services.BluetoothLeService

class SettingsActivity : AppCompatActivity() {
    private lateinit var multiplierEdit: EditText
    private var mBluetoothLeService: BluetoothLeService? = null

    private val mServiceConnection = object: ServiceConnection {

        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            mBluetoothLeService = (service as BluetoothLeService.LocalBinder).service
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            mBluetoothLeService = null
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        multiplierEdit = findViewById(R.id.multiplier)
        findViewById<Button>(R.id.tare_button).setOnClickListener {
            mBluetoothLeService?.sendCommand("tare")
        }
        findViewById<Button>(R.id.multiplier_button).setOnClickListener {
            val wt = multiplierEdit.text.toString()
            val weight = wt.toFloatOrNull()
            if(weight != null) {
                mBluetoothLeService?.sendCommand("weight: $weight")
            }
        }
        val gattServiceIntent = Intent(this, BluetoothLeService::class.java)
        bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
