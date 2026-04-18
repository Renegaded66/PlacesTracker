package com.d_drostes_apps.placestracker.ui.settings

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.service.PcEditServer
import com.google.android.material.materialswitch.MaterialSwitch
import java.net.Inet4Address
import java.net.NetworkInterface

class PcEditFragment : Fragment(R.layout.fragment_pc_edit) {

    private var server: PcEditServer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        val switchServer = view.findViewById<MaterialSwitch>(R.id.switchServer)
        val layoutUrl = view.findViewById<View>(R.id.layoutUrl)
        val divider = view.findViewById<View>(R.id.divider)
        val tvUrl = view.findViewById<TextView>(R.id.tvUrl)

        val ipAddress = getLocalIpAddress()
        val port = 8080
        val url = "http://$ipAddress:$port"
        tvUrl.text = url

        switchServer.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startServer(port)
                layoutUrl.visibility = View.VISIBLE
                divider.visibility = View.VISIBLE
            } else {
                stopServer()
                layoutUrl.visibility = View.GONE
                divider.visibility = View.GONE
            }
        }
    }

    private fun startServer(port: Int) {
        if (server == null) {
            server = PcEditServer(requireContext(), port)
        }
        server?.start()
    }

    private fun stopServer() {
        server?.stop()
        server = null
    }

    private fun getLocalIpAddress(): String? {
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return "127.0.0.1"
    }

    override fun onDestroy() {
        super.onDestroy()
        // stopServer()
    }
}
