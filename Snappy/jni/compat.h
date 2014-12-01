#ifdef __FreeBSD__
#  include <sys/endian.h>
#else
#  include <endian.h>
#endif

#include <stdlib.h>
#include <assert.h>
#include <string.h>
#include <errno.h>
#include <stdbool.h>
#include <limits.h>
#include <sys/uio.h>

typedef unsigned char u8;
typedef unsigned short u16;
typedef unsigned u32;
typedef unsigned long long u64;

#define BUG_ON(x) assert(!(x))

#define get_unaligned(x) (*(x))
#define get_unaligned_le32(x) (letoh32(*(u32 *)(x)))
#define put_unaligned(v,x) (*(x) = (v))
#define put_unaligned_le16(v,x) (*(u16 *)(x) = htole16(v))

#define vmalloc(x) malloc(x)
#define vfree(x) free(x)

#define EXPORT_SYMBOL(x)

#define ARRAY_SIZE(x) (sizeof(x) / sizeof(*(x)))

#define likely(x) __builtin_expect((x), 1)
#define unlikely(x) __builtin_expect((x), 0)

#define min_t(t,x,y) ((x) < (y) ? (x) : (y))
#define max_t(t,x,y) ((x) > (y) ? (x) : (y))

#if __BYTE_ORDER == __LITTLE_ENDIAN
#define __LITTLE_ENDIAN__ 1
#endif

#define BITS_PER_LONG (__SIZEOF_LONG__ * 8)
