package com.axzae.homeassistant

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.database.ContentObserver
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import androidx.viewpager.widget.ViewPager
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.MaterialDialog.SingleButtonCallback
import com.axzae.homeassistant.databinding.ActivityMainBinding
import com.axzae.homeassistant.fragment.ConnectionFragment
import com.axzae.homeassistant.fragment.EntityFragment
import com.axzae.homeassistant.model.Changelog
import com.axzae.homeassistant.model.Entity
import com.axzae.homeassistant.model.ErrorMessage
import com.axzae.homeassistant.model.Group
import com.axzae.homeassistant.model.HomeAssistantServer
import com.axzae.homeassistant.model.MDIFont
import com.axzae.homeassistant.model.rest.CallServiceRequest
import com.axzae.homeassistant.model.rest.RxPayload
import com.axzae.homeassistant.provider.DatabaseManager.Companion.getInstance
import com.axzae.homeassistant.provider.DummyContentProvider
import com.axzae.homeassistant.provider.EntityContentProvider
import com.axzae.homeassistant.provider.ServiceProvider
import com.axzae.homeassistant.service.DataSyncService
import com.axzae.homeassistant.service.DataSyncService.LocalBinder
import com.axzae.homeassistant.shared.EntityProcessInterface
import com.axzae.homeassistant.shared.EventEmitterInterface
import com.axzae.homeassistant.util.BottomNavigationViewHelper
import com.axzae.homeassistant.util.CommonUtil
import com.axzae.homeassistant.util.FaultUtil
import com.axzae.homeassistant.view.ChangelogView
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayout
import com.jaeger.library.StatusBarUtil
import com.miguelcatalan.materialsearchview.MaterialSearchView
import com.miguelcatalan.materialsearchview.MaterialSearchView.SearchViewListener
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.ArrayList
import java.util.Locale
import kotlin.coroutines.CoroutineContext

class MainActivity : BaseActivity(), BottomNavigationView.OnNavigationItemSelectedListener, EntityProcessInterface,
    EventEmitterInterface, CoroutineScope {
    private val mEventEmitter: Subject<RxPayload> = PublishSubject.create()
    private var mCall2: Call<String?>? = null
    private var mServerSpinner: Spinner? = null
    private var spinnerCheck = 0
    private var mServers: ArrayList<HomeAssistantServer>? = null
    private var mServerAdapter: ServerAdapter? = null
    override fun getEventSubject(): Subject<RxPayload> {
        return mEventEmitter
    }

    //Cursor Loader
    private var mEntityChangeObserver: EntityChangeObserver? = null
    private var mSharedPref: SharedPreferences? = null
    private var mCurrentServer: HomeAssistantServer? = null
    private var mCall: Call<ArrayList<Entity?>?>? = null
    private var doubleBackToExitPressedOnce = false
    private var mToast: Toast? = null
    private var runonce = false
    private var mMenuHoursand: MenuItem? = null
    private var mViewPagerAdapter: ViewPagerAdapter? = null

    //Data
    private var mGroups: ArrayList<Group>? = null

    //Bound Service (Experimental)
    private var mService: DataSyncService? = null
    private var mBound = false
    private lateinit var binding: ActivityMainBinding
    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as LocalBinder
            mService = binder.service
            mBound = true
            if (mSharedPref!!.getBoolean("websocket_mode", true)) {
                mService?.startWebSocket(mCurrentServer, true)
            }
            Log.d("YouQi", "Service Bound")
            binder.eventSubject
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<RxPayload> {
                    override fun onSubscribe(d: Disposable) {}
                    override fun onNext(rxPayload: RxPayload) {
                        mEventEmitter.onNext(rxPayload)
                    }

                    override fun onError(e: Throwable) {}
                    override fun onComplete() {}
                })
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + SupervisorJob()

    private fun showNetworkBusy() {
        binding.progressBar.isVisible = true
    }

    private fun showNetworkIdle() {
        binding.progressBar.isGone = true
    }

    private fun showDatabaseBusy() {
        if (mMenuHoursand != null) mMenuHoursand!!.isVisible = true
    }

    private fun showDatabaseIdle() {
        if (mMenuHoursand != null) mMenuHoursand!!.isVisible = false
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_main, menu)
        CommonUtil.setMenuDrawableColor(this, menu.findItem(R.id.action_edit), R.color.md_white_1000)
        CommonUtil.setMenuDrawableColor(this, menu.findItem(R.id.action_refresh), R.color.md_white_1000)
        CommonUtil.setMenuDrawableColor(this, menu.findItem(R.id.action_sort), R.color.md_white_1000)
        CommonUtil.setMenuDrawableColor(this, menu.findItem(R.id.action_webui), R.color.md_white_1000)
        CommonUtil.setMenuDrawableColor(this, menu.findItem(R.id.action_hoursand), R.color.md_white_1000)
        Log.d("YouQi", "onCreateOptionsMenu")
        mMenuHoursand = menu.findItem(R.id.action_hoursand)
        //mSearchView.setMenuItem(mMenuSearch);
        return super.onCreateOptionsMenu(menu)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        setupContentObserver()
        mSharedPref = appController.sharedPref
        mServers = getInstance(this)!!.connections
        //mCurrentServer = HomeAssistantServer.newInstance(mSharedPref);
        mCurrentServer = mServers!![mSharedPref!!.getInt("connectionIndex", 0)]
        Log.d("YouQi", "onCreate")
        setupToolbar()
        setupDrawer()
        setupViewPager()
        setupBottomNavigation()
        setupSearchView()
        setupWhatsNew()
        binding.progressBar.isGone = true
    }

    //https://stackoverflow.com/questions/21380914/contentobserver-onchange
    private fun setupContentObserver() {
        // creates and starts a new thread set up as a looper
        val thread = HandlerThread("COHandlerThread")
        thread.start()

        // creates the handler using the passed looper
        val handler = Handler(thread.looper)
        mEntityChangeObserver = EntityChangeObserver(handler)
    }

    private fun setupSearchView() {

        binding.searchView.setOnQueryTextListener(object : MaterialSearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                currentEntityFragment.search(query)
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                //Do some magic
                Log.d("YouQi", "onQueryTextChange: $newText")
                return false
            }
        })
        binding.searchView.setOnSearchViewListener(object : SearchViewListener {
            override fun onSearchViewShown() {
                //Do some magic
                Log.d("YouQi", "onSearchViewShown")
            }

            override fun onSearchViewClosed() {
                Log.d("YouQi", "onSearchViewClosed")
                //Do some magic
            }
        })
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        if (supportActionBar != null) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(false)
            supportActionBar!!.title = getString(R.string.app_name)
            //getSupportActionBar().setSubtitle(mFullUri);
        }
    }

    private fun setupDrawer() {
        val mHeaderView = binding.navView.getHeaderView(0)
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val summary = String.format(Locale.ENGLISH, "v%s", packageInfo.versionName)
            binding.versionText.text = summary
        } catch (e: Exception) {
            binding.versionText.text = ""
        }
        val mProfileImage = mHeaderView?.findViewById<View>(R.id.profile_image)
        CommonUtil.setBouncyTouch(mProfileImage)
        val websocketButton = mHeaderView?.findViewById<TextView>(R.id.text_websocket)
        websocketButton?.setOnClickListener { mService!!.startWebSocket(mCurrentServer) }

        //TextView mainText = mHeaderView.findViewById(R.id.main_text);
        //TextView subText = mHeaderView.findViewById(R.id.sub_text);
        //mainText.setText(getString(R.string.app_name));
        //subText.setText(mFullUri);
        mServerSpinner = mHeaderView?.findViewById(R.id.spinner_server)
        //servers.add(new HomeAssistantServer(mCurrentServer.getBaseUrl(), mCurrentServer.getPassword()));
        mServerAdapter = ServerAdapter(this, 0, mServers!!)
        mServerSpinner?.adapter = mServerAdapter
        mServerSpinner?.setSelection(mSharedPref!!.getInt("connectionIndex", 0), false) //must
        mServerSpinner?.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View, pos: Int, id: Long) {
                if (++spinnerCheck <= 1) return
                Log.d("YouQi", "mServerSpinner selected: $pos")
                mSharedPref!!.edit().putInt("connectionIndex", pos).apply()
                switchConnection(mServers!![pos])
                //                HomeAssistantServer mServer = (HomeAssistantServer) mServerAdapter.getSelectedItem();
                //                if (mServer == null) {
                //                    logOut();
                //                    return;
                //                }
                //setTitle(CommonUtil.getPrintableMSISDN(mSubscriber.msisdn));
                //getSupportActionBar().setSubtitle(mSubscriber.primaryOfferName);
                //getSupportActionBar().setSubtitle(CommonUtil.getPrintableMSISDN(mSubscriber.msisdn));
                binding.drawerLayout.closeDrawers()
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }

        //Hack in place to make blurdialogfragment statusbar height calculation works
        val w = window // in Activity's onCreate() for instance
        //w.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION, WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        w.setFlags(
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
        )

        val contentView = findViewById<ViewGroup>(android.R.id.content)
        val fakeTranslucentView = contentView.findViewById<View>(com.jaeger.library.R.id.statusbarutil_translucent_view)
        if (fakeTranslucentView != null) {
            if (fakeTranslucentView.isGone) {
                fakeTranslucentView.isVisible = true
            }
            val colorString = "#" + Integer.toHexString(128) + Integer.toHexString(
                ResourcesCompat.getColor(
                    resources, R.color.colorPrimaryDarkBeforeAlpha50, null
                )
            ).substring(2)
            Log.d("YouQi", "colorString: $colorString")
            fakeTranslucentView.setBackgroundColor(Color.parseColor(colorString))
        }
        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            findViewById<View>(R.id.toolbar) as Toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener {
            var isSelected = false
            when (it.itemId) {
                R.id.nav_webui -> showWebUI()
                R.id.nav_logbook -> showLogbook()
                R.id.nav_map -> showMap()
                R.id.nav_help -> {
                    showWiki()
                    binding.drawerLayout.closeDrawers()
                }
                R.id.nav_settings -> {
                    showSettings()
                    binding.drawerLayout.closeDrawers()
                }
                R.id.nav_share -> shareApp()
                R.id.nav_bug_report -> {
                    sendBugReport()
                    binding.drawerLayout.closeDrawers()
                }
                R.id.nav_logout -> {
                    showSwitch()
                    binding.drawerLayout.closeDrawers()
                }
                else -> isSelected = true
            }
            isSelected
        }
    }

    private fun switchConnection(homeAssistantServer: HomeAssistantServer) {
        mCurrentServer = homeAssistantServer
        if (mSharedPref!!.getBoolean("websocket_mode", true)) {
            mService!!.stopWebSocket()
            mService!!.startWebSocket(mCurrentServer)
        }
    }

    private fun showLogbook() {
        val intent = Intent(this@MainActivity, LogbookActivity::class.java)
        intent.putExtra("server", CommonUtil.deflate(mCurrentServer))
        startActivity(intent)
        overridePendingTransition(R.anim.activity_open_translate, R.anim.activity_close_scale)
    }

    private fun showMap() {
        val intent = Intent(this@MainActivity, MapActivity::class.java)
        intent.putExtra("server", CommonUtil.deflate(mCurrentServer))
        startActivity(intent)
        overridePendingTransition(R.anim.activity_open_translate, R.anim.activity_close_scale)
    }

    private fun shareApp() {
        val sendIntent = Intent()
        sendIntent.action = Intent.ACTION_SEND
        sendIntent.putExtra(
            Intent.EXTRA_TEXT,
            getString(R.string.message_recommendation, "https://goo.gl/5rkPnP  #homeassistant #android")
        )
        sendIntent.type = "text/plain"
        startActivity(sendIntent)
    }

    private fun setupViewPager() {
        val mTabLayout = findViewById<TabLayout>(R.id.tabs)
        mViewPagerAdapter = ViewPagerAdapter(supportFragmentManager)
        mGroups = getInstance(this)!!.groups
        for (group: Group in mGroups!!) {
            if (group.groupId == 1) {
                group.groupName = getString(R.string.title_home)
            }
            mViewPagerAdapter!!.addFragment(EntityFragment.getInstance(group), group.friendlyName)
        }
        binding.viewpager.adapter = mViewPagerAdapter
        binding.viewpager.offscreenPageLimit = 20
        mTabLayout.setupWithViewPager(binding.viewpager)
        mTabLayout.setSelectedTabIndicatorHeight(CommonUtil.pxFromDp(this, 4f))
        for (i in mGroups!!.indices) {
            val group = mGroups!![i]
            if (group.hasMdiIcon()) {
                val currentTab = mTabLayout.getTabAt(i)
                if (currentTab != null) {
                    val tab = LayoutInflater.from(this).inflate(R.layout.custom_tab, mTabLayout, false)
                    val mdiText = tab.findViewById<TextView>(R.id.text_mdi)
                    val nameText = tab.findViewById<TextView>(R.id.text_name)
                    mdiText.text = MDIFont.getIcon(group.attributes.icon)
                    nameText.text = group.friendlyName
                    currentTab.customView = tab
                }
            }
        }
        mTabLayout.addOnTabSelectedListener(object : TabLayout.ViewPagerOnTabSelectedListener(binding.viewpager) {
            override fun onTabSelected(tab: TabLayout.Tab) {
                super.onTabSelected(tab)
                val actionBar = supportActionBar
                actionBar?.title = if (tab.position == 0) getString(R.string.app_name) else tab.text
                showBottomNavigation()
                //numTab = tab.getPosition();
                //prefs.edit().putInt("numTab", numTab).apply();
            }
        })
    }

    fun showBottomNavigation() {
        binding.navigation.clearAnimation()
        binding.navView.animate().translationY(0f).duration = 200
    }

    private fun setupBottomNavigation() {
        binding.swipeRefreshLayout.setOnRefreshListener(object : OnRefreshListener {
            override fun onRefresh() {
                refreshApi()
            }
        })
        binding.swipeRefreshLayout.setSwipeableChildren(binding.viewpager)
        binding.navigation.setOnNavigationItemSelectedListener(this)
        binding.navigation.selectedItemId = -1
        binding.navigation.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        Log.d("YouQi", "navigation: " + binding.navigation.measuredHeight)
        //CoordinatorLayout.LayoutParams params = new CoordinatorLayout.LayoutParams(CoordinatorLayout.LayoutParams.MATCH_PARENT, CoordinatorLayout.LayoutParams.WRAP_CONTENT);
        //params.setMargins(0, 0, 0, mNavigation.getMeasuredHeight());  // left, top, right, bottom

        //CoordinatorLayout.LayoutParams params2 = (CoordinatorLayout.LayoutParams) mSwipeRefresh.getLayoutParams();
        //Log.d("YouQi", "Margin: " + params2.leftMargin + ", " + params2.topMargin + ", " + params2.rightMargin + ", " + mNavigation.getMeasuredHeight());
        //params2.setMargins(params2.leftMargin, params2.topMargin, params2.rightMargin, mNavigation.getMeasuredHeight());
        //mSwipeRefresh.setLayoutParams(params2);
        BottomNavigationViewHelper.disableShiftMode(binding.navigation)
        //Log.d("YouQi", "FULL URI: " + mSharedPref.getString(ConnectActivity.EXTRA_FULL_URI, null));

        //CoordinatorLayout.LayoutParams layoutParams = (CoordinatorLayout.LayoutParams) mNavigation.getLayoutParams();
        //layoutParams.setBehavior(new BottomNavigationViewBehavior());
    }

    private fun setupWhatsNew() {
        val prefKey = "whatsnew" + BuildConfig.VERSION_CODE
        val changelog = Changelog.getLatest()
        val changelogView = ChangelogView(this)
        changelogView.loadLogs(changelog.logs)
        MaterialDialog.Builder(this)
            .title("Community Edition")
            .customView(changelogView, true)
            .positiveText(getString(R.string.action_gotit))
            .positiveColorRes(R.color.md_blue_500)
            .show()
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, DataSyncService::class.java)
        applicationContext.bindService(intent, mConnection, BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineContext[Job]!!.cancel()
        Log.d("YouQi", "Destroying MainActivity")
        if (mBound) {
            applicationContext.unbindService(mConnection)
            mBound = false
        }
        mEventEmitter.onComplete()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_refresh -> {
                refreshApi()
                return true
            }
            R.id.action_edit -> {
                showEdit(null)
                return true
            }
            R.id.action_sort -> {
                showSortOptions()
                return true
            }
            android.R.id.home -> {
                onBackPressed()
                return true
            }
            R.id.action_settings -> {
                showSettings()
                return true
            }
            R.id.action_clear_search -> {
                clearSearch()
                return true
            }
            R.id.action_webui -> {
                showWebUI()
                return true
            }
            R.id.action_switch -> {
                showSwitch()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun clearSearch() {
        currentEntityFragment.clearSearch()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            2000 -> {
                if (resultCode == RESULT_OK) {
                    mEventEmitter.onNext(RxPayload.getInstance("SETTINGS"))
                }
            }
            2001 -> {
                if (resultCode == RESULT_OK) {
                    clearSearch()
                    val group = CommonUtil.inflate(
                        data!!.getStringExtra("group"), Group::class.java
                    )
                    Log.d("YouQi", "Received Group:" + group.groupId)
                    val payload = RxPayload.getInstance("EDIT")
                    payload.group = group
                    mEventEmitter.onNext(payload)
                    //Toast.makeText(this, "OK!", Toast.LENGTH_SHORT).show();
                    //getContentResolver().notifyChange(EntityContentProvider.getUrl(), null);
                }
            }
        }
    }

    private fun showSettings() {
        val i = Intent(this, SettingsActivity::class.java)
        //i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivityForResult(i, 2000)
        overridePendingTransition(R.anim.activity_open_translate, R.anim.activity_close_scale)
    }

    private fun showWiki() {
        val i = Intent(this, WikiActivity::class.java)
        //i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i)
        overridePendingTransition(R.anim.activity_open_translate, R.anim.activity_close_scale)
    }

    private fun sendBugReport() {
        MaterialDialog.Builder(this)
            .title(R.string.pref_bug_report)
            .content(R.string.message_attach_bootstrap)
            .positiveText(getString(R.string.action_yes))
            .negativeText(getString(R.string.action_no))
            .onNegative { _, _ -> sendBugReport(null) }
            .onPositive(object : SingleButtonCallback {
                override fun onClick(dialog: MaterialDialog, which: DialogAction) {
                    if (mCall2 == null) {
                        showNetworkBusy()
                        mCall2 = ServiceProvider.getRawApiService(mCurrentServer!!.baseUrl)!!.rawStates(
                            mCurrentServer!!.getPassword()
                        )
                        mCall2?.enqueue(object : Callback<String?> {
                            override fun onResponse(call: Call<String?>, response: Response<String?>) {
                                mCall2 = null
                                showNetworkIdle()
                                if (FaultUtil.isRetrofitServerError(response)) {
                                    showError(response.message())
                                    return
                                }
                                val bootstrapFile =
                                    CommonUtil.writeToExternalCache(this@MainActivity, "states.json", response.body())
                                val uri = Uri.fromFile(bootstrapFile)
                                sendBugReport(uri)
                            }

                            override fun onFailure(call: Call<String?>, t: Throwable) {
                                mCall2 = null
                                showNetworkIdle()
                                showError(FaultUtil.getPrintableMessage(this@MainActivity, t))
                            }
                        })
                    }
                }
            })
            .show()
    }

    private fun sendBugReport(uri: Uri?) {
        val emailIntent = Intent(Intent.ACTION_SENDTO)
        emailIntent.data = Uri.parse("mailto:")
        emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf("support@axzae.com"))
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "HomeAssist Bug Report")
        emailIntent.putExtra(
            Intent.EXTRA_TEXT,
            "\n\nAndroid Version: " + Build.VERSION.RELEASE + "\nHomeAssist Version: " + BuildConfig.VERSION_NAME
        )
        if (uri != null) {
            emailIntent.putExtra(Intent.EXTRA_STREAM, uri)
        }
        startActivity(Intent.createChooser(emailIntent, getString(R.string.title_send_email)))
    }

    private fun showSwitch() {
        MaterialDialog.Builder(this)
            .content(getString(R.string.message_signout, mCurrentServer!!.baseUrl))
            .positiveText(getString(R.string.action_logout))
            .negativeText(getString(R.string.action_cancel))
            .onPositive { _, _ -> logOut() }
            .show()
    }

    private fun showWebUI() {
        val builder = CustomTabsIntent.Builder()
        //builder.setStartAnimations(mActivity, R.anim.right_in, R.anim.left_out);
        builder.setStartAnimations(this, R.anim.activity_open_translate, R.anim.activity_close_scale)
        builder.setExitAnimations(this, R.anim.activity_open_scale, R.anim.activity_close_translate)

        val params = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(ResourcesCompat.getColor(resources, R.color.md_blue_500, null))
            .build()

        builder.setDefaultColorSchemeParams(params)
        val customTabsIntent = builder.build()
        try {
            customTabsIntent.launchUrl(this, mCurrentServer!!.baseUri)
        } catch (e: ActivityNotFoundException) {
            showToast(getString(R.string.exception_no_chrome))
        }
    }

    private fun addConnection() {
        //mDrawerLayout.closeDrawers();
        val fragment = ConnectionFragment.newInstance(null)
        fragment.show(fragmentManager, null)
        //Toast.makeText(mService, "add connection", Toast.LENGTH_SHORT).show();
    }

    fun refreshConnections() {
        Log.d("YouQi", "refreshConnections")
        mServers = getInstance(this)!!.connections
        mServerAdapter!!.setItems(mServers!!)
        mServerAdapter!!.notifyDataSetChanged()
    }

    override fun onBackPressed() {
        //super.onBackPressed();
        if (binding.drawerLayout.isDrawerOpen((binding.navView))) {
            binding.drawerLayout.closeDrawers()
        } else if (binding.searchView.isSearchOpen) {
            binding.searchView.closeSearch()
        } else if (currentEntityFragment.isFilterState) {
            currentEntityFragment.clearSearch()
        } else {
            if (doubleBackToExitPressedOnce) {
                super.onBackPressed()
                return
            }
            doubleBackToExitPressedOnce = true
            Handler().postDelayed(object : Runnable {
                override fun run() {
                    doubleBackToExitPressedOnce = false
                }
            }, 2000)
        }
    }

    fun showError(status: String?) {
        Toast.makeText(this, status, Toast.LENGTH_SHORT).show()
    }

    fun refreshApi() {
        makeRefreshWork()
    }

    override fun onPause() {
        super.onPause()
        contentResolver.unregisterContentObserver((mEntityChangeObserver)!!)
    }

    override fun onResume() {
        super.onResume()
        contentResolver.registerContentObserver(DummyContentProvider.getUrl(), true, (mEntityChangeObserver)!!)
        if (!runonce || (mService != null && !mService!!.isWebSocketRunning)) {
            runonce = true
            refreshApi()
        }
    }

    override fun callService(domain: String, service: String, serviceRequest: CallServiceRequest) {
        if (mService != null && mService!!.isWebSocketRunning) {
            Log.d("YouQi", "Using WebSocket")
            mService!!.callService(domain, service, serviceRequest)
        } else if (mCall == null) {
            Log.d("YouQi", "Using HTTP")
            showNetworkBusy()
            mCall = ServiceProvider.getApiService(mCurrentServer!!.baseUrl)!!.callService(
                mCurrentServer!!.getPassword()!!, domain, service, serviceRequest!!
            )
            mCall?.enqueue(object : Callback<ArrayList<Entity?>?> {
                override fun onResponse(call: Call<ArrayList<Entity?>?>, response: Response<ArrayList<Entity?>?>) {
                    mCall = null
                    showNetworkIdle()
                    if (FaultUtil.isRetrofitServerError(response)) {
                        showError(response.message())
                        return
                    }
                    val restResponse = response.body()
                    CommonUtil.logLargeString("YouQi", "service restResponse: " + CommonUtil.deflate(restResponse))
                    if (("script" == domain) || (("automation" == domain) || ("scene" == domain) || ("trigger" == service))) {
                        showToast(getString(R.string.toast_triggered))
                    }
                    if (restResponse != null) {
                        for (entity: Entity? in restResponse) {
                            contentResolver.update(
                                Uri.parse("content://com.axzae.homeassistant.provider.EntityContentProvider/"),
                                entity!!.contentValues,
                                "ENTITY_ID='" + entity.entityId + "'",
                                null
                            )
                        }

                        //RxPayload payload = RxPayload.getInstance("UPDATE_ALL");
                        //payload.entities = restResponse;
                        //mEventEmitter.onNext(payload);
                    }
                }

                override fun onFailure(call: Call<ArrayList<Entity?>?>, t: Throwable) {
                    mCall = null
                    showNetworkIdle()
                    showError(FaultUtil.getPrintableMessage(this@MainActivity, t))
                }
            })
        }

        //ContentValues values = new ContentValues();
        //values.put(HabitTable.TIME); //whatever column you want to update, I dont know the name of it
        //getContentResolver().update(HabitTable.CONTENT_URI,values,HabitTable.ID+"=?",new String[] {String.valueOf(id)}); //id is the id of the row you wan to update
        //getContentResolver().update()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        showBottomNavigation()
        binding.appbar.setExpanded(true)
        when (item.itemId) {
            R.id.action_search -> showSearch()
            R.id.action_refresh -> {
                refreshApi()
            }
            R.id.action_edit -> showEdit(null)
            R.id.action_sort -> showSortOptions()
            R.id.action_switch -> showSwitch()
        }
        return false
    }

    private fun showSearch() {
        binding.searchView.showSearch(false)
    }

    override fun getServer(): HomeAssistantServer {
        return (mCurrentServer)!!
    }

    override fun getActivityContext(): Context {
        return this
    }

    override fun onPointerCaptureChanged(hasCapture: Boolean) {}

    private fun makeRefreshWork() {
        launch {
            showNetworkBusy()
            binding.navigation.menu.findItem(R.id.action_refresh).isEnabled = false
            val errorMessage:ErrorMessage? = refreshWork()
            showNetworkIdle()
            binding.navigation.menu.findItem(R.id.action_refresh).isEnabled = true
            binding.swipeRefreshLayout.isRefreshing = false
            if (errorMessage != null) {
                showError(errorMessage.message)
            }
        }
    }

    suspend fun refreshWork() = withContext(Dispatchers.IO) {
        var result: ErrorMessage? = null

        try {
            val response = ServiceProvider.getApiService(
                mCurrentServer!!.baseUrl
            ).getStates(mCurrentServer!!.getPassword())?.execute()
            if (response?.code() != 200) {
                result = ErrorMessage("Error", response?.message())
            }
            val statesResponse = response?.body() ?: throw RuntimeException("No Data")
            val values = ArrayList<ContentValues>()
            for (entity: Entity? in statesResponse) {
                values.add(entity!!.contentValues)
            }
            contentResolver.bulkInsert(EntityContentProvider.getUrl(), values.toTypedArray())
        } catch (e: Exception) {
            e.printStackTrace()
            result = ErrorMessage("System Exception", FaultUtil.getPrintableMessage(this@MainActivity, e))
        }
        result
    }

    private fun showSortOptions() {
        val popup = PopupMenu(this, (binding.navigation.getChildAt(0) as BottomNavigationMenuView).getChildAt(2))
        //Inflating the Popup using xml file
        popup.menuInflater.inflate(R.menu.menu_sort, popup.menu)

        //registering popup with OnMenuItemClickListener
        popup.setOnMenuItemClickListener { item ->
            val currentFragment = currentEntityFragment
            val group = currentFragment.group
            if (group != null) {
                currentFragment.sortEntity(item.order)
            }
            true
        }
        popup.show()
    }

    val currentEntityFragment: EntityFragment
        get() = mViewPagerAdapter!!.getItem(binding.viewpager.currentItem) as EntityFragment

    override fun showToast(message: String) {
        if (mToast != null) {
            mToast!!.cancel()
        }
        mToast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        mToast?.show()
    }

    fun showEdit(v: View?) {
        val bundle = Bundle()
        bundle.putString("group", CommonUtil.deflate(currentEntityFragment.group))
        val i = Intent(this, EditActivity::class.java)
        i.putExtras(bundle)
        startActivityForResult(i, 2001)
        overridePendingTransition(R.anim.stay_still, R.anim.fade_out)
    }

    private inner class ServerAdapter internal constructor(
        context: Context?,
        resource: Int,
        private var items: List<HomeAssistantServer>
    ) : ArrayAdapter<HomeAssistantServer?>(
        (context)!!, resource, items
    ) {
        fun setItems(objects: List<HomeAssistantServer>) {
            items = objects
        }

        override fun getCount(): Int {
            return 1 + items.size
        }

        override fun getDropDownView(position: Int, convertView: View, parent: ViewGroup): View {
            val mainText = convertView.findViewById<TextView>(R.id.main_text)
            val subText = convertView.findViewById<TextView>(R.id.sub_text)
            if (position == items.size) {
                mainText.text = TextUtils.concat(
                    CommonUtil.getSpanText(this@MainActivity, " \n", null, 0.2f),
                    CommonUtil.getSpanText(this@MainActivity, "Add Connection…", null, 0.9f),
                    CommonUtil.getSpanText(this@MainActivity, "\n ", null, 0.2f)
                )
                subText.visibility = View.GONE
                convertView.findViewById<View>(R.id.parent).isClickable = true
                convertView.findViewById<View>(R.id.parent).setOnClickListener {
                    addConnection()
                    val root = parent.rootView
                    root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
                    root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
                }
            } else {
                mainText.text = items.get(position).getName()
                subText.isVisible = true
                subText.text = items[position].baseUrl
                convertView.findViewById<View>(R.id.parent).isClickable = false
            }
            return convertView
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var convertView = convertView
            if (convertView == null) {
                convertView = View.inflate(context, android.R.layout.simple_list_item_1, null)
            }
            (convertView!!.findViewById<View>(android.R.id.text1) as TextView).text =
                items.get(position).getLine(context)
            (convertView.findViewById<View>(android.R.id.text1) as TextView).setTextColor(
                ResourcesCompat.getColor(
                    resources, R.color.md_white_1000, null
                )
            )
            return (convertView)
        }
    }

    private inner class ViewPagerAdapter internal constructor(manager: FragmentManager?) : FragmentPagerAdapter(
        (manager)!!
    ) {
        private val mFragmentList: MutableList<Fragment> = ArrayList()
        private val mFragmentTitleList: MutableList<String> = ArrayList()
        override fun getItem(position: Int): Fragment {
            return mFragmentList[position]
        }

        override fun getCount(): Int {
            return mFragmentList.size
        }

        fun addFragment(fragment: Fragment, title: String) {
            mFragmentList.add(fragment)
            mFragmentTitleList.add(title)
        }

        override fun getPageTitle(position: Int): CharSequence? {
            return mFragmentTitleList[position]
        }
    }

    inner class EntityChangeObserver internal constructor(handler: Handler?) : ContentObserver(handler) {

        override fun onChange(selfChange: Boolean) {
            this.onChange(selfChange, null)
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            Log.d(
                "YouQi",
                "Observer onChange (UIThread? " + (if (CommonUtil.isUiThread()) "Yes" else "No") + ") URI: " + (uri?.toString()
                    ?: "null")
            )
            if (uri == null) return
            val databaseManager = getInstance(this@MainActivity)
            if (("ALL" == uri.lastPathSegment)) {
                val payload = RxPayload.getInstance("UPDATE_ALL")
                payload.entities = databaseManager!!.entities
                mEventEmitter.onNext(payload)
            } else {
                val entity = databaseManager!!.getEntityById((uri.lastPathSegment)!!)
                if (entity != null) {
                    val payload = RxPayload.getInstance("UPDATE")
                    payload.entity = entity
                    mEventEmitter.onNext(payload)
                }
            }
        }
    }

}