package yuku.alkitabintegration.test;

import junit.framework.*;
import android.test.suitebuilder.TestSuiteBuilder;

/**
 * A test suite containing all tests for my application.
 */
public class AllTests extends TestSuite {
	public static Test suite() {
		return new TestSuiteBuilder(AllTests.class).includeAllPackagesUnderHere().build();
	}
}
