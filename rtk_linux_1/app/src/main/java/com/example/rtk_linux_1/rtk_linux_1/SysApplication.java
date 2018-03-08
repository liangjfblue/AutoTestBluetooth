package com.example.rtk_linux_1.rtk_linux_1;

import android.app.Activity;
import android.app.Application;

import java.util.LinkedList;
import java.util.List;

public class SysApplication extends Application{
    private List mlist = new LinkedList();
    private static SysApplication instance;

    private SysApplication() {
    }

    public synchronized static SysApplication getInstance() {
        if(null == instance) {
            instance = new SysApplication();
        }
        return instance;
    }

    public void addActivity(Activity activity) {
        mlist.add(activity);
    }

    public void exit() {
        try {
            for (int i = 0; i<mlist.size(); i++) {
                Activity activity = (Activity) mlist.get(i);
                activity.finish();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            System.exit(0);
        }
    }

    public void onLowMemory() {
        super.onLowMemory();
        System.gc();
    }
}




