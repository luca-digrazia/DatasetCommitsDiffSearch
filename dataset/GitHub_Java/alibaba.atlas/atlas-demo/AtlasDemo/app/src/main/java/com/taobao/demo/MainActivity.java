package com.taobao.demo;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.taobao.atlas.remote.RemoteFactory;
import android.taobao.atlas.remote.fragment.RemoteFragment;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Toast;

import com.middleware.dialog.Dialog;
import com.taobao.update.Updater;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {


    //    private ActivityGroupDelegate mActivityDelegate;
    private ViewGroup mActivityGroupContainer;

    private final BottomNavigationView.OnNavigationItemSelectedListener
            mOnNavigationItemSelectedListener

            = item -> {
                switch (item.getItemId()) {
                    case R.id.navigation_home:
                        switchToActivity("home",
                                "atlas.fragment.intent.action.FIRST_FRAGMENT"/*"com.taobao
                                .firstbundle.FirstBundleActivity"*/);
                        Toast.makeText(RuntimeVariables.androidApplication,"on click",Toast.LENGTH_SHORT).show();
                        return true;
                    case R.id.navigation_dashboard:
                        switchToActivity("second",
                                "atlas.fragment.intent.action.SECOND_BUNDLE_FRAGMENT"/*"com
                                .taobao.secondbundle.SecondBundleActivity"*/);
                        return true;
                    case R.id.navigation_notifications:
    //                    Intent intent3 = new Intent();
    //                    intent3.setClassName(getBaseContext(),"com.taobao.firstBundle.FirstBundleActivity");
    //                    mActivityDelegate.execStartChildActivityInternal(mActivityGroupContainer,"third",intent3);
                        return true;
                }
                return false;
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();
        Log.e("aa","bbb");


        ((BottomNavigationView)findViewById(R.id.navigation)).setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
//        mActivityDelegate = new ActivityGroupDelegate(this,savedInstanceState);
        mActivityGroupContainer = (ViewGroup) findViewById(R.id.content);
        switchToActivity("home",
                "atlas.fragment.intent.action.FIRST_FRAGMENT"/*"com.taobao.firstbundle
                .FirstBundleActivity"*/);
    }

    public void switchToActivity(String key,String activityName){
        RemoteFactory.requestRemote(RemoteFragment.class, this, new Intent(activityName),
                new RemoteFactory.OnRemoteStateListener<RemoteFragment>() {
                    @Override
                    public void onRemotePrepared(RemoteFragment iRemote) {
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.content, iRemote)
                                .commit();
                    }

                    @Override
                    public void onFailed(String s) {
                        Log.e("UserRemoteActivity", s);
                    }
                });
//        Intent intent = new Intent();
//        intent.setClassName(getBaseContext(),activityName);
//        mActivityDelegate.startChildActivity(mActivityGroupContainer,key,intent);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent();
            intent.setClassName(this,"com.taobao.firstbundle.WebViewDemoActivity");
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

//        if (id == R.id.nav_camera) {
//            // Handle the camera action
//        }
//        else
            if (id == R.id.nav_slideshow) {
            Intent intent = new Intent();
            intent.setClassName(this,"com.taobao.demo.UpdateDemoActivity");
            startActivity(intent);

        } else if (id == R.id.nav_manage) {

            Intent intent = new Intent();
                intent.setPackage(getPackageName());
            intent.setClassName(this,"com.taobao.demo.RemoteDemoActivity");
            startActivity(intent);

        } else if (id == R.id.awo_manager) {
            Dialog dialog = new Dialog(this,"???bundle??????",
                    "1????????????????????????????????????\n\n"+
                     "2???????????????bundle??????????????????????????????????????????????????????\n\n"+
                            "3???bundle???????????????????????? ../gradlew clean assemblePatchDebug,??????????????????????????????????????????????????????");

            dialog.show();


        } else if (id == R.id.nav_dex_patch) {
            new AsyncTask<Void, Void, Boolean>() {
                @Override
                protected Boolean doInBackground(Void... voids) {
                    boolean update = Updater.dexPatchUpdate(getBaseContext());
                    return update;
                }

                @Override
                protected void onPostExecute(Boolean aVoid) {
                    if (aVoid) {
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }
                }
            }.execute();
        }else if (id == R.id.nav_databind_bundle) {

                Intent intent = new Intent();
                intent.setPackage(getPackageName());
                intent.setClassName(this,"com.taobao.databindbundle.databind.DataBundleSampleActivity");
                startActivity(intent);
            }else if (id == R.id.nav_remote) {

                Intent intent = new Intent();
                intent.setPackage(getPackageName());
                intent.setClassName(this,"com.taobao.firstbundle.UseremoteActivity");
                startActivity(intent);
            }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
}
