/*
    Bible Plus A Bible Reader for Blackberry
    Copyright (C) 2010 Yohanes Nugroho

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

    Yohanes Nugroho (yohanes@gmail.com)
 */

package com.compactbyte.bibleplus.reader;

public interface SearchProgressListener {

	/**
	 * The listener will be called each time a progress has been made
	 * 
	 * @param currentstep
	 *            current step from maxstep
	 * @param maxstep
	 *            max step
	 * @param currentbook
	 *            number, if -1, means still building index/preparing
	 * @param found
	 *            number of matches
	 */
	public void notify(int currentstep, int maxstep,
				int currentbook, int found);

	/**
	 * if this returns true, we will stop the search and return whatever we got
	 */
	public boolean searchCanceled();

	/**
	 * called when the search is done
	 */
	public void searchFinished();

	/**
	 * When search is started, listener will be called. When this is called, we can prepare the progress bar.
	 * 
	 * @param maxstep
	 *            the maximum number of step that notify will be called
	 */
	public void searchStarted(int maxstep);
}
