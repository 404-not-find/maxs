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

package org.projectmaxs.shared.global;

import java.io.File;

import org.projectmaxs.shared.global.util.Log;
import org.projectmaxs.shared.global.util.PackageManagerUtil;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

public class FilereadUtil {

	public static final String FILEREAD_MODULE_PACKAGE = GlobalConstants.MODULE_PACKAGE
			+ ".fileread";

	public static final Uri FILEREAD_MODULE_AUTHORITY = Uri.parse("content://"
			+ FILEREAD_MODULE_PACKAGE);

	public static final String ACTION_SET_CWD = FILEREAD_MODULE_PACKAGE + ".ACTION_SET_CWD";

	public static final String COLUMN_NAME_CWD = "CWD";

	public static final String[] FILEREAD_PROVIDER_COLUMN_NAMES = new String[] { COLUMN_NAME_CWD };

	private static final Log LOG = Log.getLog();

	public static boolean filereadModuleInstalled(Context context) {
		return PackageManagerUtil.getInstance(context).isPackageInstalled(FILEREAD_MODULE_PACKAGE);
	}

	public static void setCwd(Context context, File cwd) {
		if (!filereadModuleInstalled(context)) {
			LOG.d("setCwd: fileread module not installed");
			return;
		}

		Intent intent = new Intent(ACTION_SET_CWD);
		intent.putExtra(GlobalConstants.EXTRA_CONTENT, cwd.getAbsolutePath());
		intent.setClassName(GlobalConstants.FILEREAD_MODULE_PACKAGE,
				GlobalConstants.FILEREAD_MODULE_PACKAGE + ".SetCWDService");
		context.startService(intent);
	}

	public static File getCwd(Context context) {
		if (!filereadModuleInstalled(context))
			return new File(GlobalConstants.MAXS_EXTERNAL_STORAGE.getAbsolutePath());

		Cursor c = context.getContentResolver().query(FILEREAD_MODULE_AUTHORITY, null, null, null, null);
		if (c == null) {
			LOG.e("getCwd: returned cursor is null");
			return null;
		}
		String cwd;
		try {
			cwd = c.getString(c.getColumnIndexOrThrow(COLUMN_NAME_CWD));
		} finally {
			c.close();
		}

		return new File(cwd);
	}
}
