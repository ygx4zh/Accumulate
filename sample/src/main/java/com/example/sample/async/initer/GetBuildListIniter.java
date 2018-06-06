package com.example.sample.async.initer;

import android.os.SystemClock;

import com.example.apphelper.AppHelper;


/**
 * 获取楼栋列表
 */
public class GetBuildListIniter extends AbsIniter {

    @Override
    public Object getHintExtra() {
        return "获取楼栋列表...";
    }

    @Override
    protected void onInit() {

        // 设置初始化类型为可以获取进度的类型
        setInitType(TYPE_PROGRESS);

        long value = 10 * 1000;
        long start = System.currentTimeMillis();
        long cur = 0;
        while (start + value > (cur = System.currentTimeMillis())) {
            SystemClock.sleep(AppHelper.randomInt(400) + 100);
            onProgress(value,cur-start);
            if(!isCanContinute()){
                onNext();
                return;
            }
        }
        onProgress(value,cur-start);
        onEventException(new NullPointerException("hahaha"));
    }

}
