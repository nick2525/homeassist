package com.axzae.homeassistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import br.tiagohm.markdownview.MarkdownView
import br.tiagohm.markdownview.css.styles.Github
import com.axzae.homeassistant.util.CommonUtil

class WikiActivity : AppCompatActivity() {
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_wiki, menu)
        CommonUtil.setMenuDrawableColor(this, menu.findItem(R.id.action_open_in_browser), R.color.md_white_1000)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wiki)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        if (supportActionBar != null) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.setTitle(R.string.menu_faq)
        }
        val css = Github()
        css.addRule("body", "line-height: 1.6", "padding: 0px")
        val mMarkdownView = findViewById<MarkdownView>(R.id.markdown_view)
        mMarkdownView.addStyleSheet(css)
        mMarkdownView.loadMarkdownFromUrl("https://raw.githubusercontent.com/nick2525/homeassist/master/README.md")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        //int id = item.getItemId();
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
            R.id.action_open_in_browser -> {
                openInBrowser()
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

    private fun openInBrowser() {
        val url = "https://raw.githubusercontent.com/nick2525/homeassist/master/README.md"
        val i = Intent(Intent.ACTION_VIEW)
        i.data = Uri.parse(url)
        startActivity(i)
    }
}