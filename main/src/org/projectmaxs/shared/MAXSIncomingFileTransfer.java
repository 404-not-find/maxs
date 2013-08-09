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

package org.projectmaxs.shared;

import java.io.FileInputStream;
import java.io.InputStream;

import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;

public class MAXSIncomingFileTransfer extends MAXSFileTransfer implements Parcelable {

	final String mInitiator;

	public MAXSIncomingFileTransfer(String filename, long size, String description, ParcelFileDescriptor pdf,
			String initiator) {
		super(filename, size, description, pdf);
		mInitiator = initiator;
	}

	private MAXSIncomingFileTransfer(Parcel in) {
		super(in);
		mInitiator = in.readString();
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		super.writeToParcel(dest, flags);
		dest.writeString(mInitiator);
	}

	public InputStream getOutputStream() {
		return new FileInputStream(mPfd.getFileDescriptor());
	}

	public static final Creator<MAXSIncomingFileTransfer> CREATOR = new Creator<MAXSIncomingFileTransfer>() {

		@Override
		public MAXSIncomingFileTransfer createFromParcel(Parcel source) {
			return new MAXSIncomingFileTransfer(source);
		}

		@Override
		public MAXSIncomingFileTransfer[] newArray(int size) {
			return new MAXSIncomingFileTransfer[size];
		}

	};
}
