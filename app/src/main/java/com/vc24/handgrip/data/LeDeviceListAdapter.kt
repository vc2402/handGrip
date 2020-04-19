package com.vc24.handgrip.data

import android.R
import android.bluetooth.BluetoothDevice
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.coordinatorlayout.widget.CoordinatorLayout.Behavior.setTag
import android.widget.TextView
import android.view.ViewGroup
import java.nio.file.Files.size
import android.text.method.TextKeyListener.clear
import android.view.LayoutInflater
import android.widget.BaseAdapter


// Adapter for holding devices found through scanning.
