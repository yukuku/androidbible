package yuku.alkitab.test;

import junit.framework.*;
import android.test.suitebuilder.TestSuiteBuilder;

/**
 * A test suite containing all tests for my application.
 */
public class SemuaTes extends TestSuite {
	public static Test suite() {
		return new TestSuiteBuilder(SemuaTes.class).includeAllPackagesUnderHere().build();
	}
}
