if [ "$1" == "" ] ; then
	echo 'Usage: this_script <directory of translations>'
	exit 1
fi

set -e

DST="Alkitab/src/main/res/"
DST_FEEDBACK="AlkitabFeedback/src/main/res/"
SRC="$1"

if [ ! -e "$DST" ] ; then
	echo "This script is not run from the correct directory"
	exit 1
fi

if [ ! -e "$DST_FEEDBACK" ] ; then
	echo "This script is not run from the correct directory"
	exit 1
fi

if [ ! -e "$SRC" ] ; then
	echo "Source dir not found"
	exit 1
fi

# Keep this synced with build.gradle resConfigs!
PAIRS=(af af bg bg cs cs da da de de es es-ES fr fr in id it it ja ja ko ko lv lv ms ms nl nl pl pl pt pt-BR ro ro ru ru th th tr tr uk uk vi vi zh-rCN zh-CN zh-rTW zh-TW)

for ((i=0; i<${#PAIRS[@]}; i+=2)) ; do
	DSTLANG="${PAIRS[$i]}"
	SRCLANG="${PAIRS[$((i+1))]}"
	DSTSUBDIR="$DST/values-$DSTLANG"
	DSTSUBDIR_FEEDBACK="$DST_FEEDBACK/values-$DSTLANG"
	SRCSUBDIR="$SRC/$SRCLANG"

	if [ ! -e "$DSTSUBDIR" ] ; then
		echo "Dest sub dir not found: $DSTSUBDIR"
		exit 1
	fi

	if [ ! -e "$SRCSUBDIR" ] ; then
		echo "Source sub dir not found: $SRCSUBDIR"
		exit 1
	fi

	for f in pref_colortheme_labels.xml pref_labels.xml pref_volumebuttonnavigation_labels.xml strings.xml ; do
		cp "$SRCSUBDIR/$f" "$DSTSUBDIR/$f"
	done

	for f in alkitabfeedback_strings.xml ; do
		cp "$SRCSUBDIR/$f" "$DSTSUBDIR_FEEDBACK/$f"
	done

done

