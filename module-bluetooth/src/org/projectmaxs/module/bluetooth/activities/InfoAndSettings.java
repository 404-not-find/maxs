package org.projectmaxs.module.bluetooth.activities;

import org.projectmaxs.module.bluetooth.R;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class InfoAndSettings extends PreferenceActivity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.infoandsettings);
	}

}
