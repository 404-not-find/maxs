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

package org.projectmaxs.main.util;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.projectmaxs.main.R;
import org.projectmaxs.shared.global.GlobalConstants;
import org.projectmaxs.shared.global.util.Log;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

public class PermCheck {

	private static final Log LOG = Log.getLog();

	@TargetApi(16)
	public static List<PackageProblem> performCheck(Context context) {
		List<PackageProblem> res = new LinkedList<PackageProblem>();
		PackageManager packageManager = context.getPackageManager();
		for (PackageInfo packageInfoIter : packageManager.getInstalledPackages(0)) {
			PackageInfo packageInfo;
			try {
				packageInfo = packageManager.getPackageInfo(packageInfoIter.packageName,
						PackageManager.GET_PERMISSIONS);
			} catch (NameNotFoundException e) {
				LOG.d("Seems like a package has been uninstalled in the meantime", e);
				continue;
			}
			if (packageInfo.permissions != null) {
				for (PermissionInfo permissionInfo : packageInfo.permissions) {
					if (permissionInfo.name.startsWith(GlobalConstants.PACKAGE)
							&& !packageInfo.packageName.equals(GlobalConstants.MAIN_PACKAGE)) {
						res.add(new PackageProblem(packageInfo.packageName,
								"Non MAXS Main Package " + packageInfo.packageName
										+ " declares MAXS permission " + permissionInfo.name));
					}
				}
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
				if (packageInfo.packageName.startsWith(GlobalConstants.MODULE_PACKAGE)) {
					for (int i = 0; i < packageInfo.requestedPermissionsFlags.length; i++) {
						if ((packageInfo.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) == 0) {
							res.add(new PackageProblem(packageInfo.packageName, "MAXS Component "
									+ packageInfo.packageName
									+ " was not granted requested permission "
									+ packageInfo.requestedPermissions[i]));
						}
					}
				}
			}
		}

		return res;
	}

	public static class PermCheckAsyncTask extends AsyncTask<Context, Void, List<PackageProblem>> {

		private final TextView statusTextView;
		private final Context context;

		public PermCheckAsyncTask(TextView statusTextView, Context context) {
			this.statusTextView = statusTextView;
			this.context = context;
		}

		@Override
		protected void onPreExecute() {
			statusTextView.setText("Checking… 😐");
		}

		@Override
		protected List<PackageProblem> doInBackground(Context... params) {
			try {
				return performCheck(params[0]);
			} catch (Exception e) {
				LOG.w("Exception while performing check", e);
				return Collections.emptyList();
			}
		}

		@Override
		protected void onPostExecute(List<PackageProblem> problems) {
			if (problems.isEmpty()) {
				statusTextView.setText("OK 😃");
			} else {
				SpannableStringBuilder sb = new SpannableStringBuilder();
				sb.append(Html.fromHtml("<h1>" + "The following problems where found" + "</h1>"));
				for (final PackageProblem packageProblem : problems) {
					// Start with a nice bullet point
					sb.append(Html.fromHtml("&#8226; "));
					// Report the problem and make it clickable
					SpannableStringBuilder problemSpan = new SpannableStringBuilder(
							packageProblem.problem);
					int start = packageProblem.problem.indexOf(packageProblem.pkg);
					if (start >= 0) {
						int end = start + packageProblem.pkg.length();
						problemSpan.setSpan(new ClickableSpan() {
							@Override
							public void onClick(View widget) {
								Intent intent = new Intent(
										Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
								intent.setData(Uri.fromParts("package", packageProblem.pkg, null));
								context.startActivity(intent);
							}
						}, start, end, 0);
						sb.append(problemSpan);
					} else {
						sb.append(packageProblem.problem);
					}
					sb.append(Html.fromHtml("<br>"));
				}
				TextView textView = new TextView(context);
				textView.setText(sb);
				textView.setMovementMethod(LinkMovementMethod.getInstance());
				final AlertDialog alertDialog = new AlertDialog.Builder(context)
						.setPositiveButton(context.getResources().getString(R.string.close), null)
						.setView(textView).create();
				statusTextView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						alertDialog.show();
					}
				});

				// Resize the text view in case there where problems detected.
				float oldSize = statusTextView.getTextSize();
				statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, oldSize * 1.7f);

				statusTextView.setText("Not OK! Click for more details. 😞");
			}
		}
	}

	private static class PackageProblem {
		public final String pkg;
		public final String problem;

		PackageProblem(String pkg, String problem) {
			this.pkg = pkg;
			this.problem = problem;
		}
	}
}
