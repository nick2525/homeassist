package com.axzae.homeassistant

import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.axzae.homeassistant.model.Entity
import com.axzae.homeassistant.model.HomeAssistantServer
import com.axzae.homeassistant.model.LogSheet
import com.axzae.homeassistant.model.MDIFont
import com.axzae.homeassistant.provider.DatabaseManager.Companion.getInstance
import com.axzae.homeassistant.provider.ServiceProvider
import com.axzae.homeassistant.shared.LogSheetDiffUtilCallback
import com.axzae.homeassistant.util.CommonUtil
import com.axzae.homeassistant.util.FaultUtil
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.Collections
import java.util.HashMap
import java.util.Locale
import java.util.TimeZone

class LogbookActivity : AppCompatActivity() {
    var mCurrentServer: HomeAssistantServer? = null
    private var mCall: Call<ArrayList<LogSheet?>?>? = null
    private var mProgressBar: ProgressBar? = null
    private var mEmptyList: View? = null
    private var mConnError: View? = null
    private var mRecyclerView: RecyclerView? = null
    private var mAdapter: LogsheetAdapter? = null
    private var mSwipeRefresh: SwipeRefreshLayout? = null
    private val mEntities = HashMap<String, Entity?>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logbook)
        mProgressBar = findViewById(R.id.progressbar)
        mEmptyList = findViewById(R.id.list_empty)
        mConnError = findViewById(R.id.list_conn_error)
        val params = Bundle()
        params.putString("name", this.javaClass.name)
        val bundle = intent.extras
        if (bundle != null) {
            mCurrentServer = CommonUtil.inflate(bundle.getString("server", ""), HomeAssistantServer::class.java)
        }
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        if (supportActionBar != null) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.title = getString(R.string.menu_logbook)
        }
        setupRecyclerView()
        refreshApi()
    }

    fun setupRecyclerView() {
        mSwipeRefresh = findViewById(R.id.swipe_refresh_layout)
        mSwipeRefresh?.setOnRefreshListener(OnRefreshListener { refreshApi() })
        mRecyclerView = findViewById(R.id.recycler_view)
        mAdapter = LogsheetAdapter(null)
        mRecyclerView?.setLayoutManager(LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false))
        mRecyclerView?.setAdapter(mAdapter)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        //int id = item.getItemId();
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            overridePendingTransition(R.anim.activity_open_scale, R.anim.activity_close_translate)
        }
    }

    fun refreshApi() {
        val df: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
        df.timeZone = TimeZone.getTimeZone("UTC")
        val currentDate = Calendar.getInstance().time
        val now = Calendar.getInstance()
        now[Calendar.HOUR_OF_DAY] = 0
        now[Calendar.MINUTE] = 0
        now[Calendar.SECOND] = 0
        now[Calendar.MILLISECOND] = 0
        Log.d("YouQi", "Date: " + df.format(now.time))
        if (mCall == null) {
            //showNetworkBusy();
            mCall = ServiceProvider.getApiService(mCurrentServer!!.baseUrl).getLogbook(
                mCurrentServer!!.getPassword(), df.format(now.time)
            )
            mCall!!.enqueue(object : Callback<ArrayList<LogSheet?>?> {
                override fun onResponse(call: Call<ArrayList<LogSheet?>?>, response: Response<ArrayList<LogSheet?>?>) {
                    mCall = null
                    showNetworkIdle()
                    mConnError!!.visibility = View.GONE
                    if (FaultUtil.isRetrofitServerError(response)) {
                        showError(response.message())
                        return
                    }
                    val restResponse = response.body()
                    CommonUtil.logLargeString("YouQi", "service restResponse: " + CommonUtil.deflate(restResponse))
                    if (restResponse != null) {
                        if (restResponse.size == 0) {
                            mEmptyList!!.visibility = View.VISIBLE
                        } else {
                            mEmptyList!!.visibility = View.GONE
                            Log.d("YouQi", "restResponse.size: " + restResponse.size)
                            Collections.sort(restResponse) { lhs, rhs ->
                                (rhs!!.`when`.time - lhs!!.`when`.time).toInt() //descending order
                            }
                            mAdapter!!.setItems(restResponse.orEmpty().filterNotNull())

                            //for (LogSheet logsheet : restResponse) {
                            //    Log.d("YouQi", String.format(Locale.ENGLISH, "%s, %s", df2.format(logsheet.when.getTime()), DateUtils.getRelativeTimeSpanString(logsheet.when.getTime())));
                            //    //getContentResolver().update(Uri.parse("content://com.axzae.homeassistant.provider.EntityContentProvider/"), entity.getContentValues(), "ENTITY_ID='" + entity.entityId + "'", null);
                            //}
                        }
                    }
                }

                override fun onFailure(call: Call<ArrayList<LogSheet?>?>, t: Throwable) {
                    mCall = null
                    showNetworkIdle()
                    mConnError!!.visibility = View.VISIBLE
                }
            })
        }
    }

    private fun showError(message: String) {
        mConnError!!.visibility = View.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showNetworkBusy() {
        mSwipeRefresh!!.isRefreshing = true
        mProgressBar!!.visibility = View.VISIBLE
    }

    private fun showNetworkIdle() {
        mSwipeRefresh!!.isRefreshing = false
        mProgressBar!!.visibility = View.GONE
    }

    internal inner class LogbookViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        //private final View rootView;
        var mItemView: ViewGroup
        var mIconView: TextView
        var mNameView: TextView
        var mSubText: TextView
        var mStateText: TextView

        init {
            //rootView = v;
            mIconView = v.findViewById(R.id.text_mdi)
            mItemView = v.findViewById(R.id.item)
            mNameView = v.findViewById(R.id.main_text)
            mSubText = v.findViewById(R.id.sub_text)
            mStateText = v.findViewById(R.id.state_text)
        }
    }

    private fun getEntity(entityId: String): Entity? {
        var result = mEntities[entityId]
        if (result == null) {
            val databaseManager = getInstance(this@LogbookActivity)
            val entity = databaseManager!!.getEntityById(entityId)
            mEntities[entityId] = entity
            result = entity
        }
        return result
    }

    private inner class LogsheetAdapter internal constructor(items: MutableList<LogSheet>?) :
        RecyclerView.Adapter<LogbookViewHolder>() {
        private val items: MutableList<LogSheet>
        private val dateFormat: DateFormat = SimpleDateFormat("HH:mm:ss aa", Locale.ENGLISH)
        fun setItems(newItems: List<LogSheet>?) {
            val diffResult = DiffUtil.calculateDiff(LogSheetDiffUtilCallback(items, newItems))
            items.clear()
            items.addAll(newItems.orEmpty())
            diffResult.dispatchUpdatesTo(this)
            //this.items = items;
            //notifyDataSetChanged();
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): LogbookViewHolder {
            Log.d("YouQi", "Created ViewHolder Library")
            val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_logbook, viewGroup, false)
            return LogbookViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: LogbookViewHolder, position: Int) {
            val logSheet = items!![position]
            Log.d("YouQi", "rendering: " + position + "logSheet.message: " + logSheet.message)
            //viewHolder.mNameView.setText(logSheet.name);
            viewHolder.mStateText.text = TextUtils.concat(
                dateFormat.format(logSheet!!.`when`.time),
                "\n",
                CommonUtil.getSpanText(
                    this@LogbookActivity,
                    DateUtils.getRelativeTimeSpanString(logSheet.`when`.time).toString(),
                    null,
                    0.9f
                )
            )
            viewHolder.mIconView.text = MDIFont.getIcon("mdi:information-outline")
            val entity = getEntity(logSheet.entityId.orEmpty())
            if (entity != null) {
                viewHolder.mIconView.text = entity.mdiIcon
            }
            val wordtoSpan1: Spannable = SpannableString(logSheet.name)
            wordtoSpan1.setSpan(RelativeSizeSpan(1.0f), 0, wordtoSpan1.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            wordtoSpan1.setSpan(StyleSpan(Typeface.BOLD), 0, wordtoSpan1.length, 0)
            val wordtoSpan2: Spannable = SpannableString(logSheet.message.orEmpty())
            wordtoSpan2.setSpan(
                ForegroundColorSpan(ResourcesCompat.getColor(resources, R.color.md_grey_500, null)),
                0,
                wordtoSpan2.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            wordtoSpan2.setSpan(RelativeSizeSpan(0.95f), 0, wordtoSpan2.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            //viewHolder.mNameView.setText(TextUtils.concat(wordtoSpan1, " ", wordtoSpan2));
            viewHolder.mNameView.text = logSheet.name
            viewHolder.mSubText.text = logSheet.message
        }

        override fun getItemCount(): Int {
            return items.size
        }

        init {
            var items = items
            if (items == null) items = ArrayList()
            this.items = items
        }
    }
}