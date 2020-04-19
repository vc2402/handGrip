package com.vc24.handgrip

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity

import kotlinx.android.synthetic.main.activity_main.*
import com.vc24.handgrip.services.BluetoothLeService
import android.util.Log
import android.content.*
import android.os.IBinder
import android.os.Handler
import com.vc24.handgrip.data.GRIP_PRESSURE_CHARACTERISTIC_CONFIG
import com.vc24.handgrip.data.GRIP_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import kotlin.math.sign


const val REQUEST_ENABLE_BT = 10001
const val PERMISSION_REQUEST_COARSE_LOCATION = 10002
const val SCAN_PERIOD = 30000L

class MainActivity : AppCompatActivity() {
    private var mScanning: Boolean = false
    private var mBluetoothLeService: BluetoothLeService? = null
    private var mDeviceName: String = ""
    private var mDeviceAddress: String = ""
    private lateinit var mHandler: Handler

    private lateinit var mConnectionState: TextView
    private lateinit var mDataField: TextView
    private lateinit var mFractionField: TextView
    private lateinit var mGattServicesList: ExpandableListView
    private lateinit var mProgress: ProgressBar
    private val mBluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val leScanCallback = object:ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.v(TAG,"onScanResult")
            if(result.device.name == "SuperGripFishka") {
                mDeviceAddress = result.device.address
                if (mBluetoothLeService != null) {
                    scanLeDevice(false)
                    mProgress.visibility = View.VISIBLE
                    val result = mBluetoothLeService?.connect(mDeviceAddress)
                    Log.d(TAG, "Connect request result=" + result)
                    updateConnectionState(R.string.connecting)
                }
            }
        }
        override fun onBatchScanResults(results: List<ScanResult>) {
            Log.v(TAG,"onBatchScanResult")

        }

        override fun onScanFailed(errorCode: Int) {
            Log.v(TAG,"onScanFailed: $errorCode")

        }
    }

    var connected = false
    private val TAG = MainActivity::class.java!!.getSimpleName()

        // Code to manage Service lifecycle.
    private val mServiceConnection = object: ServiceConnection {

        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            mBluetoothLeService = (service as BluetoothLeService.LocalBinder).service
            if (mBluetoothLeService?.initialize() != true) {
                Log.e(TAG, "Unable to initialize Bluetooth")
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            if(mDeviceAddress != "")
                mBluetoothLeService?.connect(mDeviceAddress)
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            mBluetoothLeService = null
        }
    }
    // Handles various events fired by the Service.

    private val mGattUpdateReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            when (action){
                BluetoothLeService.ACTION_GATT_CONNECTED -> {
                    connected = true
                    updateConnectionState(R.string.connected)
                    invalidateOptionsMenu()
                }
                BluetoothLeService.ACTION_GATT_DISCONNECTED -> {
                    connected = false
                    updateConnectionState(R.string.disconnected)
                    invalidateOptionsMenu()
                    clearUI()
                }
                BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED -> {
                    // Show all the supported services and characteristics on the
                    // user interface.
                    displayGattServices(mBluetoothLeService?.getSupportedGattServices())
                }
                BluetoothLeService.ACTION_DATA_AVAILABLE -> {
                    dataAvailable((intent.getIntExtra(BluetoothLeService.EXTRA_PRESSURE, 0)/100).toFloat())
                }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mConnectionState = findViewById(R.id.connection_state)
        mGattServicesList = findViewById(R.id.services_list)
        mDataField = findViewById(R.id.data)
        mFractionField = findViewById(R.id.fraction)
        mDataField.visibility = View.GONE
        mFractionField.visibility = View.GONE
        mProgress = findViewById(R.id.progress)
        setSupportActionBar(toolbar)
        mHandler = Handler()

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish()
            return
        }
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show()
            finish()
        }
        fab.setOnClickListener { view ->
           scanLeDevice(!mScanning)
        }
        val gattServiceIntent = Intent(this, BluetoothLeService::class.java)
        bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            val result = mBluetoothLeService?.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
        checkIfEnabled()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                onSettingsSelected()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun onSettingsSelected() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun dataAvailable(data: Float) {
        Log.i(TAG, "dataAvailable:$data")

        mDataField.visibility = View.VISIBLE
        mFractionField.visibility = View.VISIBLE
        val sign = data.sign.toInt()
        val kg = data/1000
        val kgi = kg.toInt()
        val fraction = (data - kgi*1000).toInt() * sign
        mDataField.text = if(kgi == 0 && sign < 0 ) "-0" else kgi.toString()
        mFractionField.text = resources.getString(R.string.fraction_template, fraction)
    }
    private fun scanLeDevice(enable: Boolean) {
        when (enable) {
            true -> {
                // Stops scanning after a pre-defined scan period.
                mHandler.postDelayed({
                    mScanning = false
                    mBluetoothAdapter?.bluetoothLeScanner?.stopScan(leScanCallback)
                    mProgress.visibility = View.INVISIBLE
                }, SCAN_PERIOD)
                mScanning = true
                mBluetoothAdapter?.bluetoothLeScanner?.startScan(leScanCallback)
                updateConnectionState(R.string.discovering)
                mProgress.visibility = View.VISIBLE
            }
            else -> {
                mScanning = false
                mBluetoothAdapter?.bluetoothLeScanner?.stopScan(leScanCallback)
                mProgress.visibility = View.INVISIBLE
            }
        }
    }
    private fun clearUI() {
        mGattServicesList.setAdapter(null as SimpleExpandableListAdapter?)
        mDataField.setText(R.string.no_data)
    }
    private fun updateConnectionState(resourceId: Int) {
        runOnUiThread { mConnectionState.setText(resourceId) }
    }
    private fun displayGattServices(gattServices: List<BluetoothGattService>?) {
        if (gattServices == null) return
        var uuid: String?

        // Loops through available GATT Services.
        gattServices.forEach { gattService ->
            uuid = gattService.uuid.toString()
            if(uuid == GRIP_SERVICE) {

                gattService.characteristics.forEach { gattCharacteristic ->
                    val charUUID = gattCharacteristic.uuid.toString()
                    if(charUUID == GRIP_PRESSURE_CHARACTERISTIC_CONFIG) {
                        Log.i(TAG,"Found!")
//                        mBluetoothLeService?.sendCommand("start")
                        subscribe()
                        mProgress.visibility = View.INVISIBLE
                    }

                }
            }

        }
    }


    fun subscribe(count: Int = 10) {
        val res = mBluetoothLeService?.setNotification(true)
        Log.v(TAG, "trying to subscribe ($count)")
        if(res != true && count > 0) {
            mHandler.postDelayed({
                subscribe(count - 1)
            }, 300)
        } else {
            Log.v(TAG, "subscribe: $res")
        }
    }
    private fun checkIfEnabled() {
        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        mBluetoothAdapter?.takeIf { !it.isEnabled }?.apply {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("This app needs location access")
            builder.setMessage("Please grant location access so this app can detect peripherals.")
            builder.setPositiveButton(android.R.string.ok, null)
            builder.setOnDismissListener{
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                    PERMISSION_REQUEST_COARSE_LOCATION
                )
            }
            builder.show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        //TODO process request rejection
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun makeGattUpdateIntentFilter(): IntentFilter {
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED)
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED)
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE)
        return intentFilter
    }
    inner class ViewHolder(var deviceName: TextView, var deviceAddress: TextView)
}
