package com.axzae.homeassistant.fragment.control

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.axzae.homeassistant.R
import com.axzae.homeassistant.databinding.ControlSensorBinding
import com.axzae.homeassistant.model.Entity
import com.axzae.homeassistant.model.HomeAssistantServer
import com.axzae.homeassistant.provider.ServiceProvider
import com.axzae.homeassistant.util.CommonUtil
import com.axzae.homeassistant.util.FaultUtil
import lecho.lib.hellocharts.model.Axis
import lecho.lib.hellocharts.model.AxisValue
import lecho.lib.hellocharts.model.Line
import lecho.lib.hellocharts.model.LineChartData
import lecho.lib.hellocharts.model.PointValue
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

class SensorFragment : BaseControlFragment(), View.OnClickListener {
    private var mCall: Call<ArrayList<ArrayList<Entity?>?>?>? = null
    private var mServer: HomeAssistantServer? = null

    private var _binding: ControlSensorBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mServer = CommonUtil.inflate(arguments!!.getString("server"), HomeAssistantServer::class.java)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        _binding = ControlSensorBinding.inflate(requireActivity().layoutInflater, null, false)
        val rootView = binding.root
        builder.setView(rootView)
        builder.setTitle(mEntity.friendlyName)
        callService()
        return builder.create()
    }

    fun callService() {
        if (mCall == null) {
            binding.progressbar.isVisible = true
            mCall = ServiceProvider.getApiService(mServer!!.baseUrl).getHistory(
                mServer!!.getPassword(), mEntity.entityId
            )
            mCall!!.enqueue(object : Callback<ArrayList<ArrayList<Entity?>?>?> {
                override fun onResponse(
                    call: Call<ArrayList<ArrayList<Entity?>?>?>,
                    response: Response<ArrayList<ArrayList<Entity?>?>?>
                ) {
                    mCall = null
                    binding.progressbar.isGone = true
                    if (FaultUtil.isRetrofitServerError(response)) {
                        return
                    }
                    val restResponse = response.body()
                    if (restResponse != null && restResponse.size > 0) {
                        val histories = restResponse.first()
                        if (histories?.size ?: 0 <= 1) {
                            binding.listEmpty.isVisible = true
                        } else {
                            setupChart(histories.orEmpty().filterNotNull().toMutableList())
                        }
                    } else {
                        binding.listEmpty.isVisible = true
                    }
                }

                override fun onFailure(call: Call<ArrayList<ArrayList<Entity?>?>?>, t: Throwable) {
                    mCall = null
                    binding.progressbar.isVisible = false
                    binding.listConnError.isVisible = true
                    val activity: Activity? = activity
                    if (activity != null && !activity.isFinishing) {
                        Log.d("YouQi", FaultUtil.getPrintableMessage(getActivity(), t))
                    }
                    //                    showError(FaultUtil.getPrintableMessage(t));
                }
            })
        }
    }

    private inner class DataItem(var date: Date, var yValue: Float) {
        val xValue: Long
            get() = date.time
    }

    private fun setupChart(histories: MutableList<Entity>) {
        val df: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZZZZZ", Locale.ENGLISH)
        val dfShort: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ", Locale.ENGLISH)
        val mData = ArrayList<DataItem>()
        for (history in histories) {
            try {
                if (history.lastUpdated.length == 25) {
                    mData.add(DataItem(dfShort.parse(history.lastUpdated), history.state.toFloatOrNull() ?: 0.0f))
                } else {
                    mData.add(DataItem(df.parse(history.lastUpdated), history.state.toFloatOrNull() ?: 0.0f))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val yValues: MutableList<PointValue> = ArrayList()
        val axisValues: List<AxisValue> = ArrayList()
        for (x in mData.indices) {
            val dataItem = mData[x]
            val yValue = dataItem.yValue
            yValues.add(PointValue(dataItem.xValue.toFloat(), yValue))
        }
        val values: MutableList<PointValue> = ArrayList()
        mData.indices.forEach {
            val dataItem = mData[it]
            values.add(PointValue(dataItem.xValue.toFloat(), dataItem.yValue))
        }

        //In most cased you can call data model methods in builder-pattern-like manner.
        val line =
            Line(values)
                .setColor(Color.parseColor("#3366cc"))
                .setCubic(false)
                .setHasLabels(true)
                .setHasPoints(false)
                .setCubic(false)
        val lines: MutableList<Line> = ArrayList()
        lines.add(line)
        val data = LineChartData()
        data.lines = lines
        data.lines = lines
        val axisY = Axis().setHasLines(true)
        axisY.name = mEntity.attributes.unitOfMeasurement
        data.axisYLeft = axisY
        val axisX: Axis
        if (mData.size != 0) {
            val startTime = mData[0].xValue
            val endTime = mData[mData.size - 1].xValue
            val step = (endTime - startTime) / 10
            val xValues: MutableList<AxisValue> = ArrayList()
            val df2 = SimpleDateFormat("HH:mm", Locale.ENGLISH)
            var i = startTime
            while (i < endTime) {
                xValues.add(AxisValue(i.toFloat()).setLabel(df2.format(Date(i))))
                i += step
            }
            xValues.add(AxisValue(endTime.toFloat()).setLabel(df2.format(Date(endTime))))
            axisX = Axis(xValues)
            axisX.name = "Time (Last 24 Hours)"
        } else {
            axisX = Axis().setName("Time (Last 24 Hours)")
        }
        data.axisXBottom = axisX
        binding.chart.lineChartData = data
        binding.chart.isVisible = true
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.button_cancel -> dismiss()
            R.id.button_set -> dismiss()
        }
    }

    companion object {
        fun newInstance(entity: Entity?, server: HomeAssistantServer?): SensorFragment {
            val fragment = SensorFragment()
            val args = Bundle()
            args.putString("entity", CommonUtil.deflate(entity))
            args.putString("server", CommonUtil.deflate(server))
            fragment.arguments = args
            return fragment
        }
    }
}