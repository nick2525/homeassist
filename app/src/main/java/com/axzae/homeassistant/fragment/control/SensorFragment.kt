package com.axzae.homeassistant.fragment.control

import android.annotation.TargetApi
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.axzae.homeassistant.R
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
import lecho.lib.hellocharts.view.LineChartView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class SensorFragment : BaseControlFragment(), View.OnClickListener {
    private var mCall: Call<ArrayList<ArrayList<Entity?>?>?>? = null
    private var mProgressBar: View? = null
    private var mChart: LineChartView? = null
    private var mEmptyView: ViewGroup? = null
    private var mConnErrorView: ViewGroup? = null
    private var mServer: HomeAssistantServer? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mServer = CommonUtil.inflate(arguments!!.getString("server"), HomeAssistantServer::class.java)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        val rootView = activity!!.layoutInflater.inflate(R.layout.control_sensor, null)
        builder.setView(rootView)
        builder.setTitle(mEntity.friendlyName)
        mChart = rootView.findViewById(R.id.chart)
        mEmptyView = rootView.findViewById(R.id.list_empty)
        mConnErrorView = rootView.findViewById(R.id.list_conn_error)

        //rootView.findViewById(R.id.button_cancel).setOnClickListener(this);
        //rootView.findViewById(R.id.button_set).setOnClickListener(this);
        mProgressBar = rootView.findViewById(R.id.progressbar)
        callService()
        return builder.create()
    }

    fun callService() {
        if (mCall == null) {
            mProgressBar!!.visibility = View.VISIBLE
            mCall = ServiceProvider.getApiService(mServer!!.baseUrl).getHistory(
                mServer!!.getPassword(), mEntity.entityId
            )
            mCall!!.enqueue(object : Callback<ArrayList<ArrayList<Entity?>?>?> {
                override fun onResponse(
                    call: Call<ArrayList<ArrayList<Entity?>?>?>,
                    response: Response<ArrayList<ArrayList<Entity?>?>?>
                ) {
                    mCall = null
                    mProgressBar!!.visibility = View.GONE
                    if (FaultUtil.isRetrofitServerError(response)) {

                        //                        showError(response.message());
                        return
                    }
                    val restResponse = response.body()
                    //CommonUtil.logLargeString("YouQi", "HISTORY restResponse: " + CommonUtil.deflate(restResponse));
                    if (restResponse != null && restResponse.size > 0) {
                        val histories = restResponse[0]
                        if (histories?.size?:0 <= 1) {
                            mEmptyView!!.visibility = View.VISIBLE
                        } else {
                            setupChart(histories.orEmpty().filterNotNull().toMutableList())
                        }
                    } else {
                        mEmptyView!!.visibility = View.VISIBLE
                    }
                }

                override fun onFailure(call: Call<ArrayList<ArrayList<Entity?>?>?>, t: Throwable) {
                    mCall = null
                    mProgressBar!!.visibility = View.GONE
                    mConnErrorView!!.visibility = View.VISIBLE
                    val activity: Activity? = activity
                    if (activity != null && !activity.isFinishing) {
                        Log.d("YouQi", FaultUtil.getPrintableMessage(getActivity(), t))
                    }
                    //                    showError(FaultUtil.getPrintableMessage(t));
                }
            })
        }

        //ContentValues values = new ContentValues();
        //values.put(HabitTable.TIME); //whatever column you want to update, I dont know the name of it
        //getContentResolver().update(HabitTable.CONTENT_URI,values,HabitTable.ID+"=?",new String[] {String.valueOf(id)}); //id is the id of the row you wan to update
        //getContentResolver().update()
    }

    private inner class DataItem internal constructor(var date: Date, var yValue: Float) {
        var df = SimpleDateFormat("MMM-dd HH:mm", Locale.ENGLISH)
        val label: String
            get() = df.format(date)
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
                    mData.add(DataItem(dfShort.parse(history.lastUpdated), history.state.toFloatOrNull()?:0.0f))
                } else {
                    mData.add(DataItem(df.parse(history.lastUpdated), history.state.toFloatOrNull()?:0.0f))
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

            //            if (x == 0 || x == mData.size() - 1) {
            //                AxisValue axisValue = new AxisValue(dataItem.getXValue());
            //                axisValue.setLabel(dataItem.getLabel());
            //                axisValues.add(axisValue);
            //            }
        }
        val values: MutableList<PointValue> = ArrayList()
        for (x in mData.indices) {
            val dataItem = mData[x]
            values.add(PointValue(dataItem.xValue.toFloat(), dataItem.yValue))
        }

        //In most cased you can call data model methods in builder-pattern-like manner.
        val line =
            Line(values).setColor(Color.parseColor("#3366cc")).setCubic(false).setHasLabels(true).setHasPoints(false)
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
        mChart!!.lineChartData = data
        mChart!!.visibility = View.VISIBLE
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