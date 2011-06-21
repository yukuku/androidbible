# Imports the monkeyrunner modules used by this program
from com.android.monkeyrunner import *
import time

# Connects to the current device, returning a MonkeyDevice object
device = MonkeyRunner.waitForConnection()

for i in range(0, 400):
	device.press('KEYCODE_DPAD_RIGHT', 'DOWN_AND_UP')
	time.sleep(0.1)
	device.drag((40, 400), (40, 50), 0.05, 5)
	device.drag((40, 400), (40, 50), 0.05, 5)
	device.drag((40, 400), (40, 50), 0.05, 5)
	time.sleep(0.3)
	device.drag((40, 400), (40, 50), 0.05, 5)
	device.drag((40, 400), (40, 50), 0.05, 5)
	time.sleep(0.3)
	device.drag((40, 400), (40, 50), 0.05, 5)
	device.drag((40, 400), (40, 50), 0.05, 5)
	time.sleep(0.4)
	device.drag((40, 400), (40, 50), 0.05, 5)
	device.drag((40, 400), (40, 50), 0.05, 5)
	time.sleep(0.4)
	device.drag((40, 400), (40, 50), 0.05, 5)
	device.drag((40, 400), (40, 50), 0.05, 5)
	time.sleep(0.6)
	
