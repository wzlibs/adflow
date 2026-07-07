package com.adflow.adflow_flutter.callbacks

import com.adflow.adflow_flutter.generated.AdFlowFlutterApi
import com.adflow.adflow_flutter.toPigeon
import com.adflow.core.AdRevenueEvent
import com.adflow.core.RevenueLogger

/** Forward [AdFlowCore.addRevenueLogger] events sang Dart qua [AdFlowFlutterApi.onRevenuePaid]. */
class RevenueLoggerBridge(private val flutterApi: AdFlowFlutterApi) : RevenueLogger {
    override fun onRevenuePaid(event: AdRevenueEvent) {
        flutterApi.onRevenuePaid(event.toPigeon()) {}
    }
}
