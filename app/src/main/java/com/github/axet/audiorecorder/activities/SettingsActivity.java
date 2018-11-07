package com.github.axet.audiorecorder.activities;


import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.SwitchPreferenceCompat;
import android.view.MenuItem;
import android.widget.Toast;

import com.github.axet.androidlibrary.widgets.AppCompatSettingsThemeActivity;
import com.github.axet.androidlibrary.widgets.NameFormatPreferenceCompat;
import com.github.axet.androidlibrary.widgets.SeekBarPreference;
import com.github.axet.androidlibrary.widgets.SilencePreferenceCompat;
import com.github.axet.androidlibrary.widgets.StoragePathPreferenceCompat;
import com.github.axet.audiolibrary.app.Sound;
import com.github.axet.audiolibrary.encoders.Factory;
import com.github.axet.audiolibrary.widgets.RecordingVolumePreference;
import com.github.axet.audiorecorder.R;
import com.github.axet.audiorecorder.app.AudioApplication;
import com.github.axet.audiorecorder.app.Storage;
import com.github.axet.audiorecorder.services.RecordingService;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p/>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends AppCompatSettingsThemeActivity implements PreferenceFragmentCompat.OnPreferenceDisplayDialogCallback {

    public static final int RESULT_STORAGE = 1;
    public static final int RESULT_CALL = 2;

    Handler handler = new Handler();

    public static String[] PREMS = new String[]{Manifest.permission.READ_PHONE_STATE};

    @SuppressWarnings("unchecked")
    public static <T> T[] removeElement(Class<T> c, T[] aa, int i) {
        List<T> ll = Arrays.asList(aa);
        ll = new ArrayList<>(ll);
        ll.remove(i);
        return ll.toArray((T[]) Array.newInstance(c, ll.size()));
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();
            String key = preference.getKey();

            if (preference instanceof SeekBarPreference) {
                preference.setSummary(((SeekBarPreference) preference).format((Float) value));
            } else if (preference instanceof NameFormatPreferenceCompat) {
                preference.setSummary(((NameFormatPreferenceCompat) preference).getFormatted(stringValue));
            } else if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);

            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getAll().get(preference.getKey()));
    }

    @Override
    public int getAppTheme() {
        return AudioApplication.getTheme(this, R.style.RecThemeLight, R.style.RecThemeDark);
    }

    @Override
    public String getAppThemeKey() {
        return AudioApplication.PREFERENCE_THEME;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupActionBar();

        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, new GeneralPreferenceFragment()).commit();
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
//            actionBar.setBackgroundDrawable(new ColorDrawable(AudioApplication.getActionbarColor(this)));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    @TargetApi(11)
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || GeneralPreferenceFragment.class.getName().equals(fragmentName);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);
        if (key.equals(AudioApplication.PREFERENCE_CONTROLS)) {
            if (sharedPreferences.getBoolean(AudioApplication.PREFERENCE_CONTROLS, false)) {
                RecordingService.start(this);
            } else {
                RecordingService.stopService(this);
            }
        }
        if (key.equals(AudioApplication.PREFERENCE_STORAGE)) {
            Storage.migrateLocalStorageDialog(this, handler, new Storage(this));
        }
        if (key.equals(AudioApplication.PREFERENCE_RATE)) {
            int sampleRate = Integer.parseInt(sharedPreferences.getString(AudioApplication.PREFERENCE_RATE, ""));
            if (sampleRate != Sound.getValidRecordRate(Sound.getInMode(this), sampleRate)) {
                Toast.makeText(this, "Not supported Hz", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        MainActivity.startActivity(this);
        finish();
    }

    @Override
    public boolean onPreferenceDisplayDialog(PreferenceFragmentCompat caller, Preference pref) {
        if (pref instanceof NameFormatPreferenceCompat) {
            NameFormatPreferenceCompat.show(caller, pref.getKey());
            return true;
        }
        if (pref instanceof SeekBarPreference) {
            RecordingVolumePreference.show(caller, pref.getKey());
            return true;
        }
        return false;
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    public static class GeneralPreferenceFragment extends PreferenceFragmentCompat {
        public GeneralPreferenceFragment() {
        }

        void initPrefs(PreferenceManager pm, PreferenceScreen screen) {
            final Context context = screen.getContext();
            ListPreference enc = (ListPreference) pm.findPreference(AudioApplication.PREFERENCE_ENCODING);
            String v = enc.getValue();
            CharSequence[] ee = Factory.getEncodingTexts(context);
            CharSequence[] vv = Factory.getEncodingValues(context);
            if (ee.length > 1) {
                enc.setEntries(ee);
                enc.setEntryValues(vv);

                int i = enc.findIndexOfValue(v);
                if (i == -1) {
                    enc.setValueIndex(0);
                } else {
                    enc.setValueIndex(i);
                }

                bindPreferenceSummaryToValue(enc);
            } else {
                screen.removePreference(enc);
            }

            bindPreferenceSummaryToValue(pm.findPreference(AudioApplication.PREFERENCE_RATE));
            bindPreferenceSummaryToValue(pm.findPreference(AudioApplication.PREFERENCE_THEME));
            bindPreferenceSummaryToValue(pm.findPreference(AudioApplication.PREFERENCE_CHANNELS));
            bindPreferenceSummaryToValue(pm.findPreference(AudioApplication.PREFERENCE_FORMAT));
            bindPreferenceSummaryToValue(pm.findPreference(AudioApplication.PREFERENCE_VOLUME));

            StoragePathPreferenceCompat s = (StoragePathPreferenceCompat) pm.findPreference(AudioApplication.PREFERENCE_STORAGE);
            s.setStorage(new Storage(getContext()));
            s.setPermissionsDialog(this, Storage.PERMISSIONS_RW, RESULT_STORAGE);
            if (Build.VERSION.SDK_INT >= 21)
                s.setStorageAccessFramework(this, RESULT_STORAGE);

            AudioManager am = (AudioManager) context.getSystemService(AUDIO_SERVICE);
            Preference bluetooth = pm.findPreference(AudioApplication.PREFERENCE_SOURCE);
            if (!am.isBluetoothScoAvailableOffCall()) {
                bluetooth.setVisible(false);
            }
            bindPreferenceSummaryToValue(bluetooth);

            Preference p = pm.findPreference(AudioApplication.PREFERENCE_CALL);
            p.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean b = (boolean) newValue;
                    if (b) {
                        if (!Storage.permitted(GeneralPreferenceFragment.this, PREMS, RESULT_CALL))
                            return false;
                    }
                    return true;
                }
            });
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setHasOptionsMenu(true);
            addPreferencesFromResource(R.xml.pref_general);
            initPrefs(getPreferenceManager(), getPreferenceScreen());
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                getActivity().onBackPressed();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            StoragePathPreferenceCompat s = (StoragePathPreferenceCompat) findPreference(AudioApplication.PREFERENCE_STORAGE);
            switch (requestCode) {
                case RESULT_STORAGE:
                    s.onRequestPermissionsResult(permissions, grantResults);
                    break;
                case RESULT_CALL:
                    SwitchPreferenceCompat p = (SwitchPreferenceCompat) findPreference(AudioApplication.PREFERENCE_CALL);
                    if (!Storage.permitted(getContext(), PREMS))
                        p.setChecked(false);
                    else
                        p.setChecked(true);
                    break;
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            StoragePathPreferenceCompat s = (StoragePathPreferenceCompat) findPreference(AudioApplication.PREFERENCE_STORAGE);
            switch (requestCode) {
                case RESULT_STORAGE:
                    s.onActivityResult(resultCode, data);
                    break;
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            SilencePreferenceCompat silent = (SilencePreferenceCompat) findPreference(AudioApplication.PREFERENCE_SILENT);
            silent.onResume();
        }
    }
}
