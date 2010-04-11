package yuku.andoutil;

public class ThreadSleep {
	/**
	 * @return true kalo diinterupt
	 */
	public static boolean giveUpOnInterrupt(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			return true;
		}
		return false;
	}
	
	/**
	 * @return time actually taken
	 */
	public static long ignoreInterrupt(long ms) {
		long wmulai;
		long total = 0;
		
		while (true) {
			wmulai = System.currentTimeMillis();
			
			try {
				Thread.sleep(ms - total);
			} catch (InterruptedException e) {
			}
			
			long kini = System.currentTimeMillis();
			long pake = kini - wmulai;
			wmulai = kini;
			
			total += pake;
			if (pake < 0 || total >= ms) {
				break;
			}
		}
		
		return total;
	}
	
}
