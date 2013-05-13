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

package org.projectmaxs.main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jivesoftware.smack.util.StringUtils;
import org.projectmaxs.shared.util.Log;

import android.content.Context;

public class Settings {

	private static Settings sSettings;

	public static Settings getInstance(Context ctx) {
		if (sSettings == null) {
			sSettings = new Settings(ctx);
		}
		return sSettings;
	}

	private Context ctx;

	private boolean debugLog = true;
	private List<String> mMasterJids = new ArrayList<String>(Arrays.asList(new String[] { "flo@freakempire.de" }));

	private Settings(Context ctx) {
		this.ctx = ctx;
	}

	public boolean connectionSettingsObsolete() {
		// TODO
		return true;
	}

	public void resetConnectionSettingsObsolete() {
		// TODO
	}

	public String login() {
		return "maxs@freakempire.de";
	}

	public String password() {
		return "maxs";
	}

	public boolean manualServerSettings() {
		return true;
	}

	public String serverHost() {
		return "mate.freakempire.de";
	}

	public int serverPort() {
		return 5222;
	}

	public String serviceName() {
		return "freakempire.de";
	}

	public List<String> getMasterJids() {
		return mMasterJids;
	}

	public boolean isMasterJID(String jid) {
		String bareJID = StringUtils.parseBareAddress(jid);
		for (String s : mMasterJids)
			if (s.equals(bareJID)) return true;

		return false;
	}

	public Log.LogSettings getLogSettings() {
		return new Log.LogSettings() {

			@Override
			public boolean debugLog() {
				return debugLog;
			}

		};
	}

}
