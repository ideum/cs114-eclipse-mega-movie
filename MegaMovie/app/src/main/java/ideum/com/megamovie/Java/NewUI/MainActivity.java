package ideum.com.megamovie.Java.NewUI;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import ideum.com.megamovie.Java.LocationAndTiming.MyTimer;
import ideum.com.megamovie.R;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        CalibrationFragment.OnFragmentInteractionListener,
        MyTimer.MyTimerListener{

    TextView comingSoonView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean previouslyStarted = prefs.getBoolean(getResources().getString(R.string.previously_started_key),false);
        if (!previouslyStarted) {
            SharedPreferences.Editor edit = prefs.edit();
            edit.putBoolean(getResources().getString(R.string.previously_started_key),true);
            edit.commit();
           // loadIntroActivity();
        }
        setContentView(R.layout.activity_main);

        /**
         * Keep activity in portrait mode
         */
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        if (!hasAllPermissionsGranted()) {
            requestAllPermissions();
        }


        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(getString(R.string.eclipse_info_section_title));

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        comingSoonView = (TextView)
                MenuItemCompat.getActionView(navigationView.getMenu().findItem(R.id.full_moon_test));
        initializeComingSoonViews();


        loadFragment(EclipseInfoFragment.class);

        boolean safetyWarningSeen = prefs.getBoolean(getResources().getString(R.string.safety_warning_seen),false);
        if (!safetyWarningSeen) {
            SharedPreferences.Editor edit = prefs.edit();
            edit.putBoolean(getResources().getString(R.string.safety_warning_seen),true);
            edit.commit();
            showSafetyWarning();
        }
    }

    private void initializeComingSoonViews() {
        comingSoonView.setGravity(Gravity.CENTER_VERTICAL);
        comingSoonView.setTextColor(Color.WHITE);
        comingSoonView.setTextSize(10);
        comingSoonView.setHeight(12);
        comingSoonView.setPadding(4,0,0,4);
        comingSoonView.setBackgroundColor(getResources().getColor(R.color.coming_soon_background));
        comingSoonView.setText("COMING SOON!");
    }

    public void onAssistantButtonPressed(View view) {

        loadFragment(OrientationIntroFragment.class);
//        Intent intent = new Intent(this,OrientationIntroFragment.class);
//        startActivity(intent);
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
        getMenuInflater().inflate(R.menu.path_of_totality, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_info) {
            loadAboutActivity();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void loadIntroActivity() {
        Intent intent = new Intent(this, IntroActivity.class);
        startActivity (intent);
    }

    private void loadAboutActivity() {
        Intent intent = new Intent(this, AboutActivity.class);
        startActivity (intent);

    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.path_of_totality) {
            getSupportActionBar().setTitle(getString(R.string.eclipse_info_section_title));
           loadFragment(EclipseInfoFragment.class);
        } else if (id == R.id.assistant) {
            getSupportActionBar().setTitle(getString(R.string.orientation_section_title));
            loadFragment(OrientationIntroFragment.class);
        } else if (id == R.id.about_eclipse_app) {
            getSupportActionBar().setTitle(getString(R.string.about_section_title));
            loadAboutActivity();
        } else if (id == R.id.my_eclipse) {
            loadActivity(MyEclipseActivity.class);
        } else if (id == R.id.image) {

        } else if (id == R.id.gallery) {
            getSupportActionBar().setTitle("Image Gallery");
            loadFragment(GalleryFragment.class);
        } else if (id == R.id.credits) {
            getSupportActionBar().setTitle("Credits");
            loadFragment(CreditsFragment.class);
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void loadActivity(Class c) {
        Intent intent = new Intent(this,c);
        startActivity(intent);
    }

    public void loadFragment(Class c) {
        Fragment fragment = null;
        try {
            fragment = (Fragment) c.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();

        transaction.replace(R.id.flContent, fragment);
        transaction.commit();
    }

    public void loadAssistantFragment(int index) throws ClassNotFoundException {


        AssistantEquipmentChoiceInfoFragment fragment = AssistantEquipmentChoiceInfoFragment.newInstance(index);

            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();

            transaction.replace(R.id.flContent, fragment);
            transaction.commit();
    }

    @Override
    public void onFragmentInteraction(Uri uri) {

    }

    @Override
    public void onTick() {

    }

    private static final int REQUEST_PERMISSIONS = 0;

    private static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private void showSafetyWarning() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getResources()
                .getString(R.string.safety_warning))
                .setTitle(getResources().getString(R.string.safety_warning_title))
        .setPositiveButton("Got It",null)
        .setCancelable(false);
        AlertDialog dialog = builder.create();
        dialog.show();
    }


    private void requestAllPermissions() {
        ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_PERMISSIONS);

    }

    private boolean hasAllPermissionsGranted() {
        for (String permission : PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}
