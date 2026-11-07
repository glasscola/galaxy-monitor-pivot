package com.pivotcontrol.app;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.net.Uri;

/** Notification activity closes the notification shade before opening the overlay. */
public final class RegionEntryActivity extends Activity {
    @Override public void onCreate(Bundle b){super.onCreate(b);
        if(!MirrorService.running){startActivity(new Intent(this,MirrorActivity.class));finish();return;}
        if(!Settings.canDrawOverlays(this)){startActivity(new Intent(this,MirrorActivity.class).putExtra("overlayPermission",true));finish();return;}
        startService(new Intent(this,MirrorService.class).setAction(MirrorService.EDIT));finish();
    }
}
