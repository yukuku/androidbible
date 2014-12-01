#include <linux/types.h>
#include "snappy.h"

typedef __u8 u8;
typedef __u32 u32;
typedef __u64 u64;


//////////////////////////////////

class SnappyImplNative {
	struct snappy_env env;

public:
	SnappyImplNative();

	/**
	 * Returns non-negative compressed length or negative on error.
	 */
	int compress(u8* in, int inOffset, u8 *out, int outOffset, int len);

	/**
	 * Returns 0 on success or negative on error.
	 */
	int decompress(u8* in, int inOffset, u8 *out, int outOffset, int len);
};
