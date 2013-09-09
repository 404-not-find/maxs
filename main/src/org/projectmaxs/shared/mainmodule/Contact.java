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

package org.projectmaxs.shared.mainmodule;

import java.util.ArrayList;
import java.util.List;

import android.os.Parcel;
import android.os.Parcelable;

public class Contact implements Parcelable {
	private final String mDisplayName;
	private final List<ContactNumber> mNumbers;

	private String mLookupKey;
	private String mNickname;

	public Contact(String displayName) {
		mDisplayName = displayName;
		mNumbers = new ArrayList<ContactNumber>(1);
	}

	public Contact(String displayName, List<ContactNumber> numbers) {
		mDisplayName = displayName;
		mNumbers = numbers;
	}

	public Contact(String displayName, String lookupKey) {
		mDisplayName = displayName;
		mLookupKey = lookupKey;
		mNumbers = new ArrayList<ContactNumber>();
	}

	private Contact(Parcel in) {
		mDisplayName = in.readString();
		mNumbers = in.createTypedArrayList(ContactNumber.CREATOR);
		mLookupKey = in.readString();
		mNickname = in.readString();
	}

	public void addNumber(String number, int type, String label) {
		mNumbers.add(new ContactNumber(number, type, label));
	}

	public void setNickname(String nickname) {
		mNickname = nickname;
	}

	public String getLookupKey() {
		return mLookupKey;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(mDisplayName);
		dest.writeTypedList(mNumbers);
		dest.writeString(mLookupKey);
		dest.writeString(mNickname);
	}

	public static final Creator<Contact> CREATOR = new Creator<Contact>() {

		@Override
		public Contact createFromParcel(Parcel source) {
			return new Contact(source);
		}

		@Override
		public Contact[] newArray(int size) {
			return new Contact[size];
		}

	};

	public String toPrettyString() {
		StringBuilder sb = new StringBuilder();
		sb.append(mDisplayName);
		if (mNumbers.size() == 1) sb.append(" (" + mNumbers.get(0).mNumber + ")");
		return sb.toString();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Contact ");
		sb.append("name=" + (mDisplayName.equals("") ? "noName" : mDisplayName));

		ContactNumber number = ContactNumber.getBest(mNumbers);
		if (number != null) sb.append(" bestNumber='" + number.toString() + "'");

		return sb.toString();
	}
}
