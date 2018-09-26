/*
 *    Copyright (C) 2017 MINDORKS NEXTGEN PRIVATE LIMITED
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.downloader;

/**
 * Created by amitshekhar on 13/11/17.
 */

public class Error {
	private final boolean isServerError;
	private final boolean isConnectionError;
	private final Exception exception;

	private Error(final boolean isServerError, final boolean isConnectionError, final Exception exception) {
		this.isServerError = isServerError;
		this.isConnectionError = isConnectionError;
		this.exception = exception;
	}

	public static Error newServerError(final Exception exception) {
		return new Error(true, false, exception);
	}

	public static Error newConnectionError(final Exception exception) {
		return new Error(false, true, exception);
	}

	public boolean isServerError() {
		return isServerError;
	}

	public boolean isConnectionError() {
		return isConnectionError;
	}

	public Exception getException() {
		return exception;
	}
}
