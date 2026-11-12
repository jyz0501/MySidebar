package com.qianxian.MySidebar

import android.app.Application

class SidebarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
