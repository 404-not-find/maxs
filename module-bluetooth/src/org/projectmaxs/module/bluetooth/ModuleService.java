/*
    This file is part of Project MAXS.

    MAXS and its modules is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    MAXS is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with MAXS.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.projectmaxs.module.bluetooth;

import org.projectmaxs.shared.Command;
import org.projectmaxs.shared.GlobalConstants;
import org.projectmaxs.shared.Message;
import org.projectmaxs.shared.ModuleInformation;
import org.projectmaxs.shared.UserMessage;
import org.projectmaxs.shared.util.Log;
import org.projectmaxs.shared.util.Log.LogSettings;

import android.app.IntentService;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.IBinder;

public class ModuleService extends IntentService {

	private BluetoothAdapter mAdapter;

	public ModuleService() {
		super("MAXSModule:bluetooth");
	}

	public static final ModuleInformation sMODULE_INFORMATION = new ModuleInformation(
			"org.projectmaxs.module.bluetooth", new ModuleInformation.Command[] { new ModuleInformation.Command(
					"bluetooth", "bt", "status", null, new String[] { "status" }), });

	@Override
	public void onCreate() {
		super.onCreate();
		Log.initialize("module-bluetooth", new LogSettings() {
			@Override
			public boolean debugLog() {
				return true;
			}
		});
		mAdapter = BluetoothAdapter.getDefaultAdapter();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		Log.d("onHandleIntent");
		Command command = intent.getParcelableExtra(GlobalConstants.EXTRA_COMMAND);
		String subCmd = command.getSubCommand();

		Message msg;
		if (subCmd.equals("status")) {
			msg = new Message("Bluetooth is enabled: " + mAdapter.isEnabled());
		}
		else {
			msg = new Message("Unkown command");
		}
		Intent replyIntent = new Intent(GlobalConstants.ACTION_SEND_USER_MESSAGE);
		replyIntent.putExtra(GlobalConstants.EXTRA_USER_MESSAGE, new UserMessage(msg));
		startService(replyIntent);
	}

}
