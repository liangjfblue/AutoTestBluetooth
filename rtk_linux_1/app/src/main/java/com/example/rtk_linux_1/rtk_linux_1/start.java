package com.example.rtk_linux_1.rtk_linux_1;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;

public class start extends AppCompatActivity {
    private ImageView welcome_img = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        SysApplication.getInstance().addActivity(this);

        welcome_img = (ImageView)findViewById(R.id.welcome);

        final View view = View.inflate(this, R.layout.activity_start, null);
        setContentView(view);

        //渐变启动动画
        AlphaAnimation movie = new AlphaAnimation(0.3f, 1.0f);
        movie.setDuration(3000);
        view.startAnimation(movie);
        movie.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                welcome_img.setBackgroundResource(R.mipmap.bg);
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                redirectTo();
            }

            @Override
            public void onAnimationRepeat(Animation animation) { }
        });
    }

    public void redirectTo() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
