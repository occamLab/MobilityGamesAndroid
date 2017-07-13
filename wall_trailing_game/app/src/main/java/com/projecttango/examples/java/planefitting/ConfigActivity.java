package com.projecttango.examples.java.planefitting;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.content.Intent;
import android.view.*;


public class ConfigActivity extends Activity {

    Button mButton = null;

    @Override
    protected void onCreate(Bundle saveIntentState) {
        super.onCreate(saveIntentState);
        setContentView(R.layout.activity_config);

        mButton = (Button) findViewById(R.id.goCamera);
        mButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(v.getContext(), PlaneFittingActivity.class);
                startActivity(i);
            }
        });


    }
}
