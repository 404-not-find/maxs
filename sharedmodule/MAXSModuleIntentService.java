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

package org.projectmaxs.sharedmodule;

import org.projectmaxs.shared.Command;
import org.projectmaxs.shared.GlobalConstants;
import org.projectmaxs.shared.Message;
import org.projectmaxs.shared.MessageContent;
import org.projectmaxs.shared.util.Log;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

/**
 * MAXSModuleIntentService is meant for modules to handle their PERFORM_COMMAND
 * intents. This is done in {@link #handleCommand(Command)}, which must be
 * implemented by the modules service.
 * 
 * Extends IntentService, which does a stopSelf() if there are no more remaining
 * intents. Therefore stopSelf() is not needed in this class.
 * 
 * @author Florian Schmaus flo@freakempire.de
 * 
 */
public abstract class MAXSModuleIntentService extends IntentService {
	private final Log mLog;

	public MAXSModuleIntentService(Log log, String name) {
		super(name);
		mLog = log;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		initLog(this);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		mLog.d("onHandleIntent");
		Command command = intent.getParcelableExtra(GlobalConstants.EXTRA_COMMAND);

		MessageContent msgContent = handleCommand(command);
		if (msgContent == null) return;

		Intent replyIntent = new Intent(GlobalConstants.ACTION_SEND_USER_MESSAGE);
		replyIntent.putExtra(GlobalConstants.EXTRA_MESSAGE, new Message(msgContent));
		startService(replyIntent);
	}

	public abstract MessageContent handleCommand(Command command);

	public abstract void initLog(Context context);

}
