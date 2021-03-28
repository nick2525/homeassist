package com.axzae.homeassistant

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import androidx.core.content.res.ResourcesCompat
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isGone
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.StackingBehavior
import com.axzae.homeassistant.model.Entity
import com.axzae.homeassistant.model.ErrorMessage
import com.axzae.homeassistant.model.HomeAssistantServer
import com.axzae.homeassistant.provider.DatabaseManager
import com.axzae.homeassistant.provider.EntityWidgetProvider
import com.axzae.homeassistant.provider.ServiceProvider
import com.axzae.homeassistant.util.CommonUtil
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Locale

/**
 * A login screen that offers login via username/password.
 */
class ConnectActivity : BaseActivity() {
    private var mSharedPref: SharedPreferences? = null
    private var settingCountDown = 5

    // UI references.
    private var mIpAddressView: EditText? = null
    private var mPasswordView: EditText? = null
    private var mTextProgress: TextView? = null
    private var mProgressBar: ProgressBar? = null
    private var mSnackbar: Snackbar? = null
    private var mConnectButton: Button? = null
    private var mAuthTask: UserLoginTask? = null
    private var mLayoutMain: LinearLayout? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connect)
        mLayoutMain = findViewById(R.id.main_layout)
        mLayoutMain?.setVisibility(View.GONE)
        //Send a Google Analytics screen view.
        //Tracker tracker = getAppController().getDefaultTracker();
        //tracker.send(new HitBuilders.ScreenViewBuilder().build());
        try {
            val df2 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ", Locale.ENGLISH)
            Log.d("YouQi", "Date1: " + df2.parse("2017-10-01T23:00:12+00:00").time)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d("YouQi", "Date2: " + e.message)
        }
        findViewById<View>(R.id.splash_logo).setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val scaleDownX = ObjectAnimator.ofFloat(v, "scaleX", 0.95f)
                    val scaleDownY = ObjectAnimator.ofFloat(v, "scaleY", 0.95f)
                    scaleDownX.duration = 200
                    scaleDownY.duration = 200
                    val scaleDown = AnimatorSet()
                    scaleDown.play(scaleDownX).with(scaleDownY)
                    scaleDown.interpolator = OvershootInterpolator()
                    scaleDown.start()
                    if (--settingCountDown <= 0) {
                        startSettingActivity()
                        settingCountDown = 5
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val scaleDownX2 = ObjectAnimator.ofFloat(v, "scaleX", 1f)
                    val scaleDownY2 = ObjectAnimator.ofFloat(v, "scaleY", 1f)
                    scaleDownX2.duration = 200
                    scaleDownY2.duration = 200
                    val scaleDown2 = AnimatorSet()
                    scaleDown2.play(scaleDownX2).with(scaleDownY2)
                    scaleDown2.interpolator = OvershootInterpolator()
                    scaleDown2.start()
                }
            }
            true
        }
        mProgressBar = findViewById(R.id.progressBar)
        mIpAddressView = findViewById(R.id.text_ipaddress)
        mPasswordView = findViewById(R.id.text_password)
        mPasswordView?.setOnEditorActionListener { _, id, _ ->
            if (id == R.id.button_connect || id == EditorInfo.IME_NULL || id == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
            }
            false
        }
        mConnectButton = findViewById(R.id.button_connect)
        mConnectButton?.setOnClickListener(View.OnClickListener { attemptLogin() })
        mTextProgress = findViewById(R.id.text_progress)
    }

    override fun onStart() {
        super.onStart()
        if (mSharedPref == null) {
            SharedPreferenceLoadingTask().execute()
        }
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private fun attemptLogin() {
        if (mSnackbar != null) {
            mSnackbar!!.dismiss()
        }
        if (mSharedPref == null) {
            Toast.makeText(this, "Please wait awhile before retrying", Toast.LENGTH_SHORT).show()
            return
        }
        val inputManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocus = currentFocus
        if (currentFocus != null) {
            inputManager.hideSoftInputFromWindow(currentFocus.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
        }

        // Reset errors.
        mIpAddressView!!.error = null
        mPasswordView!!.error = null

        // Store values at the time of the login attempt.
        var baseURL = mIpAddressView!!.text.toString().trim { it <= ' ' }
        val password = mPasswordView!!.text.toString()
        if (baseURL.endsWith("/")) {
            baseURL = baseURL.substring(0, baseURL.length - 1)
            mIpAddressView!!.setText(baseURL)
        }
        var cancel = false
        var focusView: View? = null

        // Check for a valid password, if the user entered one.
        if (!TextUtils.isEmpty(password) && !isPasswordValid(password)) {
            mPasswordView!!.error = getString(R.string.error_invalid_password)
            focusView = mPasswordView
            cancel = true
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(baseURL)) {
            mIpAddressView!!.error = getString(R.string.error_field_required)
            focusView = mIpAddressView
            cancel = true
        } else if (!(baseURL.startsWith("http://") || baseURL.startsWith("https://"))) {
            mIpAddressView!!.error = getString(R.string.error_invalid_baseurl)
            focusView = mIpAddressView
            cancel = true
        }
        if (cancel) {
            focusView!!.requestFocus()
        } else {
            showProgress(true, getString(R.string.progress_connecting))
            val host = Uri.parse(baseURL).host.orEmpty()
            Log.d("YouQi", "baseURL: $baseURL")
            Log.d("YouQi", "host: $host")
            if (mAuthTask == null) {
                mAuthTask = UserLoginTask(baseURL, host, password)
                mAuthTask!!.execute(null as Void?)
            }
        }
    }

    private inner class UserLoginTask internal constructor(
        private val mUri: String,
        private val mIpAddress: String,
        private val mPassword: String
    ) : AsyncTask<Void?, String?, ErrorMessage?>() {
        private var mBoostrapData: String? = null
        override fun doInBackground(vararg params: Void?): ErrorMessage? {
            try {
                publishProgress(getString(R.string.progress_connecting))

                //Response<BootstrapResponse> response = ServiceProvider.getApiService(mUri).bootstrap(mPassword).execute();
                val response = ServiceProvider.getRawApiService(mUri).rawStates(mPassword)!!.execute()
                if (response.code() != 200) {
                    if (response.code() == 401) {
                        return ErrorMessage("Error 401", getString(R.string.error_invalid_password))
                    }
                    return if (response.code() == 404) {
                        ErrorMessage("Error 404", getString(R.string.error_invalid_ha_server))
                    } else ErrorMessage("Error" + response.code(), response.message())

                    //OAuthToken token = new Gson().fromJson(response.errorBody().string(), OAuthToken.class);
                }
                mBoostrapData = response.body()
                val bootstrapResponse = CommonUtil.inflate<ArrayList<Entity>>(
                    mBoostrapData,
                    object : TypeToken<ArrayList<Entity?>?>() {}.type
                )
                //final BootstrapResponse bootstrapResponse = CommonUtil.inflate(CommonUtil.readFromAssets(ConnectActivity.this, "bootstrap.txt"), BootstrapResponse.class);
                //final BootstrapResponse bootstrapResponse = response.body();
                CommonUtil.logLargeString("YouQi", "bootstrapResponse: $bootstrapResponse")
                publishProgress(getString(R.string.progress_bootstrapping))
                val editor = mSharedPref!!.edit()
                editor.putString(EXTRA_FULL_URI, mUri)
                editor.putString(EXTRA_IPADDRESS, mIpAddress)
                editor.putString(EXTRA_PASSWORD, mPassword)
                editor.putInt("connectionIndex", 0)
                editor.putLong(EXTRA_LAST_REQUEST, System.currentTimeMillis()).apply()
                editor.apply()
                val databaseManager = DatabaseManager.getInstance(this@ConnectActivity)
                databaseManager?.updateTables(bootstrapResponse)
                databaseManager?.addConnection(HomeAssistantServer(mUri, mPassword))
                //                ArrayList<Entity> entities = databaseManager.getEntities();
                //                for (Entity entity : entities) {
                //                    Log.d("YouQi", "Entity: " + entity.entityId);
                //                }

                //Crashlytics.setUserIdentifier(settings.bootstrapResponse.profile.loginId);
            } catch (e: JsonSyntaxException) {
                e.printStackTrace()
                return ErrorMessage("JsonSyntaxException", e)
            } catch (e: Exception) {
                Log.d("YouQi", "ERROR!")
                e.printStackTrace()
                return ErrorMessage(e.message, e.toString())
            }
            return null
        }

        protected override fun onProgressUpdate(vararg values: String?) {
            super.onProgressUpdate(*values)
            mConnectButton!!.text = values.first()
        }

        override fun onPostExecute(errorMessage: ErrorMessage?) {
            mAuthTask = null
            if (errorMessage == null) {
                mConnectButton!!.setText(R.string.progress_starting)
                startMainActivity()
            } else {
                mIpAddressView!!.isEnabled = true
                mPasswordView!!.isEnabled = true
                mConnectButton!!.isEnabled = true
                mConnectButton!!.setText(R.string.button_connect)
                mProgressBar!!.visibility = View.GONE
                mTextProgress!!.visibility = View.GONE
                mPasswordView!!.requestFocus()
                showError(errorMessage.message)
                if (errorMessage.throwable != null) {
                    sendEmail(mBoostrapData, errorMessage.throwable)
                }
            }
        }

        override fun onCancelled() {
            mAuthTask = null
            //showProgress(false);
        }

        init {
            mIpAddressView!!.isEnabled = false
            mPasswordView!!.isEnabled = false
            mConnectButton!!.isEnabled = false
            mProgressBar!!.visibility = View.VISIBLE
            mTextProgress!!.visibility = View.VISIBLE
        }
    }

    private fun showError(message: String) {
        val warningIcon = ResourcesCompat.getDrawable(resources, R.drawable.ic_warning_white_18dp, null)
        val builder = SpannableStringBuilder()
        builder.append(message)
        mSnackbar = Snackbar.make(findViewById(android.R.id.content), builder, Snackbar.LENGTH_LONG)
            .setAction(getString(R.string.action_retry)) { attemptLogin() }
        mSnackbar!!.view.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.md_red_A200, null))
        mSnackbar!!.show()
    }

    private fun isPasswordValid(password: String): Boolean {
        return password.length > 0
    }

    private fun showProgress(show: Boolean, message: String?) {
        runOnUiThread {
            mTextProgress!!.visibility = View.GONE
            if (show) {
                mIpAddressView!!.isEnabled = false
                mPasswordView!!.isEnabled = false
                mConnectButton!!.isEnabled = false
                mConnectButton!!.text = message
                mProgressBar!!.visibility = View.VISIBLE
                mTextProgress!!.visibility = View.VISIBLE
            } else {
                mIpAddressView!!.isEnabled = true
                mPasswordView!!.isEnabled = true
                mConnectButton!!.isEnabled = true
                mConnectButton!!.setText(R.string.button_connect)
                mProgressBar!!.visibility = View.GONE
                mTextProgress!!.visibility = View.GONE
            }
        }
    }

    private fun startMainActivity() {
        val i = Intent(this@ConnectActivity, MainActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(Intent(this@ConnectActivity, MainActivity::class.java))
        overridePendingTransition(R.anim.stay_still, R.anim.fade_out)
        finish()
    }

    private fun startSettingActivity() {
        val i = Intent(this, SettingsActivity::class.java)
        startActivityForResult(i, 2000)
    }

    private inner class SharedPreferenceLoadingTask internal constructor() : AsyncTask<Void?, Void?, ErrorMessage?>() {
        override fun doInBackground(vararg param: Void?): ErrorMessage? {
            PreferenceManager.setDefaultValues(applicationContext, R.xml.preferences, false)
            mSharedPref = appController.sharedPref
            val ids = AppWidgetManager.getInstance(this@ConnectActivity)
                .getAppWidgetIds(ComponentName(this@ConnectActivity, EntityWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                val appWidgetIds = ArrayList<String>()
                for (id in ids) {
                    appWidgetIds.add(Integer.toString(id))
                }
                val databaseManager = DatabaseManager.getInstance(this@ConnectActivity)
                databaseManager?.forceCreate()
                databaseManager?.housekeepWidgets(appWidgetIds!!)
            }
            //mBundle = getIntent().getExtras();
            return null
        }

        override fun onPostExecute(errorMessage: ErrorMessage?) {
            if (errorMessage == null) {
                if (mSharedPref!!.getString(EXTRA_IPADDRESS, null) != null) {
                    val databaseManager = DatabaseManager.getInstance(this@ConnectActivity)
                    val groups = databaseManager?.groups
                    val connections = databaseManager?.connections
                    val dashboardCount = databaseManager?.dashboardCount
                    Log.d("YouQi", "dashboardCount: $dashboardCount")
                    if (groups?.size != 0 && connections?.size != 0 && dashboardCount!! > 0) {
                        startMainActivity()
                        return
                    }
                }
                mLayoutMain!!.visibility = View.VISIBLE
                mIpAddressView!!.setText(mSharedPref!!.getString(EXTRA_FULL_URI, ""))
                if (mIpAddressView!!.text.toString().trim { it <= ' ' }.length != 0) {
                    mPasswordView!!.requestFocus()
                } else {
                    mIpAddressView!!.requestFocus()
                }
                showProgress(false, null)
            } else {
                mProgressBar?.isGone = true
                mConnectButton!!.visibility = View.GONE
                mTextProgress!!.text = errorMessage.message
            }
            super.onPostExecute(errorMessage)
        }

        init {
            showProgress(true, getString(R.string.progress_initializing))
        }
    }

    private fun sendEmail(content: String?, throwable: Throwable) {
        val bootstrapFile = writeToSDFile(content)
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val sStackTrace = sw.toString() // stack trace as a string
        //System.out.println(sStackTrace);
        MaterialDialog.Builder(this)
            .title(R.string.title_send_crash_report)
            .content(R.string.message_crash_report)
            .negativeText(getString(R.string.action_dont_send_report))
            .positiveText(getString(R.string.action_send_report))
            .negativeColorRes(R.color.md_blue_500)
            .stackingBehavior(StackingBehavior.ADAPTIVE)
            .positiveColorRes(R.color.md_red_500)
            .onPositive { dialog, which ->
                val emailIntent = Intent(Intent.ACTION_SENDTO)
                emailIntent.data = Uri.parse("mailto:")
                emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf("support@axzae.com"))
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, "HomeAssist Bootstrap Crash Report")
                emailIntent.putExtra(Intent.EXTRA_TEXT, sStackTrace)
                val uri = Uri.fromFile(bootstrapFile)
                emailIntent.putExtra(Intent.EXTRA_STREAM, uri)
                startActivity(Intent.createChooser(emailIntent, getString(R.string.title_send_email)))
            }
            .show()
    }

    private fun writeToSDFile(content: String?): File? {
        val rootDir = externalCacheDir
        if (rootDir != null) {
            if (!rootDir.exists()) {
                val isSuccess = rootDir.mkdirs()
                if (!isSuccess) {
                    Log.d("YouQi", "failed to create" + rootDir.absolutePath)
                }
            }
            val bootstrapFile = File(rootDir, "states.json")
            if (bootstrapFile.exists()) {
                val isSuccess = bootstrapFile.delete()
            }
            Log.d("YouQi", "External file system root: " + bootstrapFile.absolutePath)
            try {
                val f = FileOutputStream(bootstrapFile)
                val pw = PrintWriter(f)
                pw.println(content)
                pw.flush()
                pw.close()
                f.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return bootstrapFile
        }
        return null
    }

    companion object {
        const val EXTRA_IPADDRESS = "ip_address"
        const val EXTRA_FULL_URI = "full_uri"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_LAST_REQUEST = "last_request"
    }
}